package smartfarm.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton database connection utility.
 *
 * <p>Public surface (unchanged from before the Android migration):
 * <pre>
 *   Connection conn = DBConnection.getInstance();
 * </pre>
 *
 * <p>New since H4 of the Android migration:
 * <pre>
 *   CompletableFuture&lt;Result&gt; f = DBConnection.runAsync(() -&gt; ...);
 *   DBConnection.closeQuietly();    // call on app shutdown
 * </pre>
 *
 * <h2>Credential layering</h2>
 * Credentials are loaded once at first use, in this order:
 * <ol>
 *   <li>Environment variables {@code DB_URL}, {@code DB_USER},
 *       {@code DB_PASSWORD} — desktop dev, CI.</li>
 *   <li>Gluon Attach {@code SettingsService} — looks up the keys
 *       {@code db.url}, {@code db.user}, {@code db.password}. On
 *       Android this is {@code SharedPreferences}; on desktop it is a
 *       per-user config file. Lets the user enter credentials once at
 *       first run and have them persist.</li>
 *   <li>Classpath resource {@code db.properties} — bundled fallback /
 *       Android asset. See {@code db.properties.example} in the
 *       resources root for the expected keys.</li>
 * </ol>
 *
 * <h2>Lifecycle</h2>
 * The {@link java.sql.Connection} returned by {@link #getInstance()} is
 * cached. If a {@link SQLException} surfaces in the rest of the codebase
 * the next caller can drop the cached handle via {@link #reset()} and a
 * fresh connection will be opened on the next {@code getInstance()} call.
 * The Android OS may freeze our process at any time, which closes any
 * sockets; this lazy / re-creatable pattern makes that survivable.
 *
 * <h2>Threading</h2>
 * JDBC calls block. On Android (and to be honest on desktop too) DB
 * calls must never run on the JavaFX UI thread. Use
 * {@link #runAsync(java.util.concurrent.Callable)} to schedule a DB
 * task on the background executor; chain {@code thenAcceptAsync(...,
 * Platform::runLater)} on the returned future to update UI.
 */
public final class DBConnection {

    /** Classpath resource that holds the fallback credentials. */
    private static final String PROPERTIES_FILE = "db.properties";

    /** Settings keys used when the Gluon Attach SettingsService is available. */
    private static final String SETTINGS_KEY_URL      = "db.url";
    private static final String SETTINGS_KEY_USER     = "db.user";
    private static final String SETTINGS_KEY_PASSWORD = "db.password";

    /** Background pool for blocking DB work. Two threads is plenty
     *  for the workload here and stays cheap on Android. Daemon
     *  threads so JVM shutdown isn't blocked. */
    private static final ExecutorService DB_POOL =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "agrilliant-db");
                t.setDaemon(true);
                return t;
            });

    /** Cached connection; set lazily, cleared on {@link #reset()}. */
    private static final AtomicReference<Connection> CACHE = new AtomicReference<>();

    /** Loaded credentials. Resolved on first {@link #getInstance()} call. */
    private static volatile Creds creds;

    private DBConnection() {}

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Returns a usable {@link Connection} to the configured MySQL host,
     * or {@code null} if no credentials are available on any layer.
     *
     * <p>The connection is cached. If the cached handle has been closed
     * (for example because Android put the app to sleep and the OS
     * killed our sockets), a new one is opened transparently.
     *
     * <p><b>Do not call this on the JavaFX UI thread.</b> Use
     * {@link #runAsync(java.util.concurrent.Callable)}.
     */
    public static Connection getInstance() {
        Creds c = resolveCreds();
        if (c == null) {
            return null;
        }
        Connection cached = CACHE.get();
        try {
            if (cached == null || cached.isClosed()) {
                Connection fresh = DriverManager.getConnection(c.url, c.user, c.password);
                CACHE.set(fresh);
                System.out.println("[DBConnection] Connected to " + c.url);
                return fresh;
            }
            return cached;
        } catch (SQLException e) {
            // Drop any half-dead handle so the next call retries.
            CACHE.set(null);
            System.err.println("[DBConnection] Connection failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Drops the cached connection (closing it if open). The next
     * {@link #getInstance()} call re-opens it. Safe to call from any
     * thread.
     */
    public static void reset() {
        Connection c = CACHE.getAndSet(null);
        closeSilently(c);
    }

    /**
     * Schedules {@code task} on the background DB executor and returns
     * a future that completes with its result (or failure).
     *
     * <p>Wraps the {@link java.util.concurrent.Callable}'s checked
     * exceptions into the future as {@link
     * java.util.concurrent.CompletionException} causes.
     */
    public static <T> CompletableFuture<T> runAsync(java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, DB_POOL);
    }

    /**
     * Close the cached connection and shut down the background pool.
     * Call once on app shutdown — for example from {@code Main#stop()}
     * on desktop and from a Gluon {@code LifecycleService.PAUSE}
     * listener on Android.
     */
    public static void closeQuietly() {
        reset();
        DB_POOL.shutdownNow();
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /** Resolves credentials lazily, once. */
    private static Creds resolveCreds() {
        Creds local = creds;
        if (local != null) return local;
        synchronized (DBConnection.class) {
            if (creds != null) return creds;
            creds = loadCreds();
            return creds;
        }
    }

    private static Creds loadCreds() {
        // 1) Environment variables (CI / desktop dev).
        String envUrl  = System.getenv("DB_URL");
        String envUser = System.getenv("DB_USER");
        String envPass = System.getenv("DB_PASSWORD");
        if (envUrl != null && envUser != null) {
            System.out.println("[DBConnection] Loaded credentials from environment.");
            return new Creds(envUrl, envUser, envPass != null ? envPass : "");
        }

        // 2) Gluon Attach SettingsService (Android SharedPreferences /
        //    desktop user-config). Looked up reflectively-via-Services
        //    so we don't crash if Attach is missing on a non-Gluon run.
        Creds fromSettings = tryLoadFromSettings();
        if (fromSettings != null) {
            System.out.println("[DBConnection] Loaded credentials from Gluon Settings.");
            return fromSettings;
        }

        // 3) db.properties on the classpath (Android asset).
        Creds fromProps = tryLoadFromProperties();
        if (fromProps != null) {
            System.out.println("[DBConnection] Loaded credentials from " + PROPERTIES_FILE + ".");
            return fromProps;
        }

        System.err.println("[DBConnection] No DB credentials on env/Settings/properties — "
                + "DB features disabled until configured.");
        return null;
    }

    private static Creds tryLoadFromSettings() {
        try {
            // com.gluonhq.attach.util.Services.get(SettingsService.class)
            return com.gluonhq.attach.util.Services
                    .get(com.gluonhq.attach.settings.SettingsService.class)
                    .flatMap(svc -> {
                        String url  = svc.retrieve(SETTINGS_KEY_URL);
                        String user = svc.retrieve(SETTINGS_KEY_USER);
                        String pwd  = svc.retrieve(SETTINGS_KEY_PASSWORD);
                        if (url != null && !url.isBlank()
                                && user != null && !user.isBlank()) {
                            return java.util.Optional.of(new Creds(url, user, pwd != null ? pwd : ""));
                        }
                        return java.util.Optional.empty();
                    })
                    .orElse(null);
        } catch (Throwable t) {
            // Service unavailable on this platform — fine, try next layer.
            return null;
        }
    }

    private static Creds tryLoadFromProperties() {
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (in == null) return null;
            Properties p = new Properties();
            p.load(in);
            String url  = p.getProperty("db.url");
            String user = p.getProperty("db.user");
            String pwd  = p.getProperty("db.password");
            if (url == null || user == null) return null;
            return new Creds(url, user, pwd != null ? pwd : "");
        } catch (IOException e) {
            System.err.println("[DBConnection] Failed to read " + PROPERTIES_FILE + ": " + e.getMessage());
            return null;
        }
    }

    private static void closeSilently(Connection c) {
        if (c == null) return;
        try {
            c.close();
        } catch (SQLException ignored) {
            // expected — connection may already be closed by the OS.
        }
    }

    /** Immutable bundle of the three values we need to call
     *  {@link DriverManager#getConnection(String, String, String)}. */
    private static final class Creds {
        final String url;
        final String user;
        final String password;

        Creds(String url, String user, String password) {
            this.url      = url;
            this.user     = user;
            this.password = password;
        }
    }
}
