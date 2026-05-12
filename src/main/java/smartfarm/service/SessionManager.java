package smartfarm.service;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Persists a lightweight session (email + role) to a local file
 * so the user stays logged in across app restarts.
 *
 * File: ~/.agrilliant/session.properties
 */
public class SessionManager {

    private static final Path SESSION_DIR = Paths.get(System.getProperty("user.home"), ".agrilliant");
    private static final Path SESSION_FILE = SESSION_DIR.resolve("session.properties");

    public static void saveSession(String email) {
        try {
            Files.createDirectories(SESSION_DIR);
            Properties props = new Properties();
            props.setProperty("email", email);
            try (OutputStream out = Files.newOutputStream(SESSION_FILE)) {
                props.store(out, "Agrilliant session");
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
            return props.getProperty("email");
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
}
