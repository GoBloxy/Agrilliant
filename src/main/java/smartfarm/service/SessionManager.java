package smartfarm.service;

import smartfarm.util.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

import com.gluonhq.attach.settings.SettingsService;
import com.gluonhq.attach.util.Services;

/**
 * Persists a signed session token so the user stays logged in across
 * app restarts.
 *
 * <h2>Storage layering (H6)</h2>
 * <ol>
 *   <li>Gluon Attach {@link SettingsService} — on Android this is
 *       {@code SharedPreferences}; on a Gluon desktop run this is
 *       Gluon's per-user config file. The primary backend on both
 *       targets <b>if</b> Attach has a runtime impl on the classpath.</li>
 *   <li>Local properties file at
 *       {@code ~/.agrilliant/session.properties} — original desktop
 *       behaviour. Used when Attach has no impl available (e.g. a
 *       bare {@code mvn javafx:run} dev session where only the
 *       Settings API jar is on the classpath, no impl) — and as the
 *       legacy migration source for installs that pre-date H6.</li>
 * </ol>
 *
 * The HMAC-SHA256 tamper check is identical to the pre-H6 implementation —
 * only the storage layer changed. The values written to either backend
 * are {@code session.email} and {@code session.token}.
 *
 * <h2>Threading</h2>
 * All public methods are static and have no internal locking. The
 * intended caller is the JavaFX UI thread (sign-in / sign-up / sign-out
 * flows). Concurrent calls from multiple threads are technically racy —
 * fine in practice for our app.
 *
 * Public API (unchanged):
 * <pre>
 *   SessionManager.saveSession(email);
 *   String email = SessionManager.loadSession();
 *   SessionManager.clearSession();
 * </pre>
 */
public final class SessionManager {

    private SessionManager() {}

    private static final String TAG = "SessionManager";

    private static final String HMAC_ALGO  = "HmacSHA256";
    private static final String SECRET_KEY = "AgR1ll1ant-S3cr3t!@2025";

    private static final String KEY_EMAIL = "session.email";
    private static final String KEY_TOKEN = "session.token";

    private static final Path SESSION_DIR  = Paths.get(System.getProperty("user.home"), ".agrilliant");
    private static final Path SESSION_FILE = SESSION_DIR.resolve("session.properties");

    /** Cached Settings backend lookup. {@code Services.get(...)} is
     *  cheap-but-not-free (it instantiates the platform impl on first
     *  call and warns on every miss); resolving once at class load
     *  means the warning fires at most once per process. */
    private static final Optional<SettingsService> SETTINGS = lookupSettings();

    private static Optional<SettingsService> lookupSettings() {
        try {
            return Services.get(SettingsService.class);
        } catch (Throwable t) {
            // Attach isn't on the classpath at runtime — fine, fall back.
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------
    // Public API — same shape as before, behaviour just dispatches
    // through whichever storage layer is available.
    // ---------------------------------------------------------------------

    public static void saveSession(String email) {
        if (email == null || email.isEmpty()) {
            clearSession();
            return;
        }
        String token = sign(email);

        if (SETTINGS.isPresent()) {
            try {
                SettingsService s = SETTINGS.get();
                s.store(KEY_EMAIL, email);
                s.store(KEY_TOKEN, token);
                return;
            } catch (Throwable t) {
                Logger.w(TAG, "SettingsService.store failed — falling back to file", t);
            }
        }
        saveSessionToFile(email, token);
    }

    public static String loadSession() {
        if (SETTINGS.isPresent()) {
            try {
                SettingsService s = SETTINGS.get();
                String email = s.retrieve(KEY_EMAIL);
                String token = s.retrieve(KEY_TOKEN);
                if (email != null && token != null) {
                    if (token.equals(sign(email))) return email;
                    Logger.w(TAG, "Session tampered (Settings) — clearing");
                    clearSession();
                    return null;
                }
                // Settings present but empty — try migrating from the
                // legacy file (a user who upgraded an existing install).
                String migrated = loadSessionFromFile();
                if (migrated != null) {
                    Logger.i(TAG, "Migrating legacy session file to SettingsService");
                    s.store(KEY_EMAIL, migrated);
                    s.store(KEY_TOKEN, sign(migrated));
                    deleteFileSilently();
                }
                return migrated;
            } catch (Throwable t) {
                Logger.w(TAG, "SettingsService.retrieve failed — falling back to file", t);
            }
        }
        return loadSessionFromFile();
    }

    public static void clearSession() {
        SETTINGS.ifPresent(s -> {
            try {
                s.remove(KEY_EMAIL);
                s.remove(KEY_TOKEN);
            } catch (Throwable t) {
                Logger.w(TAG, "SettingsService.remove failed", t);
            }
        });
        deleteFileSilently();
    }

    // ---------------------------------------------------------------------
    // File-backed storage (the original implementation, kept as fallback)
    // ---------------------------------------------------------------------

    private static void saveSessionToFile(String email, String token) {
        try {
            Files.createDirectories(SESSION_DIR);
            Properties props = new Properties();
            props.setProperty("email", email);
            props.setProperty("token", token);
            try (OutputStream out = Files.newOutputStream(SESSION_FILE)) {
                props.store(out, "Agrilliant session — do not edit");
            }
        } catch (IOException e) {
            Logger.e(TAG, "Failed to save session to file", e);
        }
    }

    private static String loadSessionFromFile() {
        if (!Files.exists(SESSION_FILE)) return null;
        try (InputStream in = Files.newInputStream(SESSION_FILE)) {
            Properties props = new Properties();
            props.load(in);
            String email = props.getProperty("email");
            String token = props.getProperty("token");
            if (email == null || token == null) return null;
            if (!token.equals(sign(email))) {
                Logger.w(TAG, "Session tampered (file) — clearing");
                deleteFileSilently();
                return null;
            }
            return email;
        } catch (IOException e) {
            Logger.e(TAG, "Failed to load session from file", e);
            return null;
        }
    }

    private static void deleteFileSilently() {
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (IOException e) {
            Logger.w(TAG, "Failed to delete session file", e);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
}
