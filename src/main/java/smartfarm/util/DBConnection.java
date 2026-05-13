package smartfarm.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * JDBC calls block, and {@link Connection} is not thread-safe per the
 * spec. {@link #runAsync(java.util.concurrent.Callable)} schedules
 * tasks on a <b>single</b> background thread so all DB work stays
 * serialized on the cached connection — same effective semantics the
 * desktop build had pre-migration when DB calls ran on the FX thread,
 * just moved off the UI thread so the UI can keep ticking.
 *
 * <p>Wire {@link #closeQuietly()} from {@code Main#stop()} on desktop
 * and from a Gluon {@code LifecycleEvent.DESTROY} listener on Android.
 * Do <b>not</b> wire it from {@code PAUSE} — that event is reversible
 * (the user backgrounds the app and may return), and shutting the
 * executor there breaks any subsequent {@code runAsync(...)} with
 * {@link java.util.concurrent.RejectedExecutionException}.
 */
public final class DBConnection {

    private static final String TAG = "DBConnection";

    /** Classpath resource that holds the fallback credentials. */
    private static final String PROPERTIES_FILE = "db.properties";

    /** Settings keys used when the Gluon Attach SettingsService is available. */
    private static final String SETTINGS_KEY_URL      = "db.url";
    private static final String SETTINGS_KEY_USER     = "db.user";
    private static final String SETTINGS_KEY_PASSWORD = "db.password";

    /** Background pool for blocking DB work. <b>Single thread</b> so
     *  all DB activity is serialized on the (not-thread-safe) cached
     *  {@link Connection} — matches the desktop's pre-migration
     *  one-thread-at-a-time pattern. Daemon thread so JVM shutdown
     *  isn't blocked. */
    private static final ExecutorService DB_POOL =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "agrilliant-db");
                t.setDaemon(true);
                return t;
            });

    /** Cached connection; set lazily, cleared on {@link #reset()}. */
    private static final AtomicReference<Connection> CACHE = new AtomicReference<>();

    /** Loaded credentials, wrapped in an Optional so the "no creds at
     *  any layer" decision is cached too — every previous design ran
     *  the env / Settings / properties lookups on every call. */
    private static volatile Optional<Creds> creds;

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
        Optional<Creds> c = resolveCreds();
        if (c.isEmpty()) {
            return null;
        }
        Connection cached = CACHE.get();
        try {
            if (cached != null && !cached.isClosed()) {
                return cached;
            }

            // Open + try to publish. A losing thread closes its
            // own (now-orphan) Connection so we don't leak.
            Creds creds = c.get();
            Connection fresh = DriverManager.getConnection(creds.url, creds.user, creds.password);
            if (CACHE.compareAndSet(cached, fresh)) {
                Logger.i(TAG, "Connected to " + creds.url);
                return fresh;
            }
            // Another thread already published a Connection; use it,
            // ditch ours.
            closeSilently(fresh);
            Connection winner = CACHE.get();
            return winner != null ? winner : fresh;
        } catch (SQLException e) {
            // Drop any half-dead handle so the next call retries.
            CACHE.compareAndSet(cached, null);
            closeSilently(cached);
            Logger.e(TAG, "Connection failed: " + e.getMessage());
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
     * Call once on app shutdown — see the Lifecycle note in the class
     * javadoc for which Gluon event to wire this from.
     */
    public static void closeQuietly() {
        reset();
        DB_POOL.shutdown();
        try {
            if (!DB_POOL.awaitTermination(2, TimeUnit.SECONDS)) {
                DB_POOL.shutdownNow();
            }
        } catch (InterruptedException ie) {
            DB_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /** Resolves credentials lazily, once. The {@link Optional} wrapper
     *  caches even the "nothing configured" decision, so repeated
     *  {@link #getInstance()} calls on a misconfigured machine don't
     *  re-walk the env / Settings / properties layers each time. */
    private static Optional<Creds> resolveCreds() {
        Optional<Creds> local = creds;
        if (local != null) return local;
        synchronized (DBConnection.class) {
            if (creds != null) return creds;
            creds = loadCreds();
            return creds;
        }
    }

    private static Optional<Creds> loadCreds() {
        // 1) Environment variables (CI / desktop dev).
        String envUrl  = trimOrNull(System.getenv("DB_URL"));
        String envUser = trimOrNull(System.getenv("DB_USER"));
        String envPass = System.getenv("DB_PASSWORD");
        if (envUrl != null && envUser != null) {
            Logger.i(TAG, "Loaded credentials from environment.");
            return Optional.of(new Creds(envUrl, envUser, envPass != null ? envPass : ""));
        }

        // 2) Gluon Attach SettingsService (Android SharedPreferences /
        //    desktop user-config). The lookup is wrapped so a missing
        //    Attach runtime just returns Optional.empty().
        Optional<Creds> fromSettings = tryLoadFromSettings();
        if (fromSettings.isPresent()) {
            Logger.i(TAG, "Loaded credentials from Gluon Settings.");
            return fromSettings;
        }

        // 3) db.properties on the classpath (Android asset).
        Optional<Creds> fromProps = tryLoadFromProperties();
        if (fromProps.isPresent()) {
            Logger.i(TAG, "Loaded credentials from " + PROPERTIES_FILE + ".");
            return fromProps;
        }

        Logger.w(TAG, "No DB credentials on env/Settings/properties — "
                + "DB features disabled until configured.");
        return Optional.empty();
    }

    private static Optional<Creds> tryLoadFromSettings() {
        try {
            return com.gluonhq.attach.util.Services
                    .get(com.gluonhq.attach.settings.SettingsService.class)
                    .flatMap(svc -> {
                        String url  = svc.retrieve(SETTINGS_KEY_URL);
                        String user = svc.retrieve(SETTINGS_KEY_USER);
                        String pwd  = svc.retrieve(SETTINGS_KEY_PASSWORD);
                        if (url != null && !url.isBlank()
                                && user != null && !user.isBlank()) {
                            return Optional.of(new Creds(url, user, pwd != null ? pwd : ""));
                        }
                        return Optional.empty();
                    });
        } catch (Throwable t) {
            // Service unavailable on this platform — fine, try next layer.
            return Optional.empty();
        }
    }

    private static Optional<Creds> tryLoadFromProperties() {
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (in == null) return Optional.empty();
            Properties p = new Properties();
            p.load(in);
            String url  = trimOrNull(p.getProperty("db.url"));
            String user = trimOrNull(p.getProperty("db.user"));
            String pwd  = p.getProperty("db.password");
            if (url == null || user == null) return Optional.empty();
            return Optional.of(new Creds(url, user, pwd != null ? pwd : ""));
        } catch (IOException e) {
            Logger.e(TAG, "Failed to read " + PROPERTIES_FILE, e);
            return Optional.empty();
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
