package smartfarm.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Properties;

/**
 * Persists a signed session token to a local file so the user stays
 * logged in across app restarts.  The email is stored alongside an
 * HMAC-SHA256 signature so that manual tampering is detected.
 *
 * File: ~/.agrilliant/session.properties
 */
public class SessionManager {

    private static final Path SESSION_DIR  = Paths.get(System.getProperty("user.home"), ".agrilliant");
    private static final Path SESSION_FILE = SESSION_DIR.resolve("session.properties");
    private static final String HMAC_ALGO  = "HmacSHA256";
    private static final String SECRET_KEY = "AgR1ll1ant-S3cr3t!@2025";

    public static void saveSession(String email) {
        try {
            Files.createDirectories(SESSION_DIR);
            Properties props = new Properties();
            props.setProperty("email", email);
            props.setProperty("token", sign(email));
            try (OutputStream out = Files.newOutputStream(SESSION_FILE)) {
                props.store(out, "Agrilliant session — do not edit");
            }
        } catch (IOException e) {
            System.err.println("Failed to save session: " + e.getMessage());
        }
    }

    public static String loadSession() {
        if (!Files.exists(SESSION_FILE)) return null;
        try (InputStream in = Files.newInputStream(SESSION_FILE)) {
            Properties props = new Properties();
            props.load(in);
            String email = props.getProperty("email");
            String token = props.getProperty("token");
            if (email == null || token == null) return null;
            if (!token.equals(sign(email))) {
                System.err.println("Session tampered — signature mismatch. Clearing session.");
                clearSession();
                return null;
            }
            return email;
        } catch (IOException e) {
            System.err.println("Failed to load session: " + e.getMessage());
            return null;
        }
    }

    public static void clearSession() {
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (IOException e) {
            System.err.println("Failed to clear session: " + e.getMessage());
        }
    }

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
