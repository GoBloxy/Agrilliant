package smartfarm.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton database connection utility.
 * Provides a single shared Connection instance to the MySQL database.
 *
 * Usage:
 *   Connection conn = DBConnection.getInstance();
 *
 * Configuration:
 *   Credentials are loaded from src/main/resources/db.properties (gitignored).
 *   Copy db.properties.example → db.properties and fill in your values.
 */
public class DBConnection {

    private static final String PROPERTIES_FILE = "db.properties";

    private static Connection connection;
    private static String url;
    private static String user;
    private static String password;

    // Private constructor — prevents instantiation
    private DBConnection() {}

    // Load credentials from properties file once. Failures are logged, not thrown,
    // so the JavaFX UI can still launch even if the DB is unconfigured.
    static {
        try (InputStream input = DBConnection.class
                .getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                System.err.println("[DBConnection] db.properties not found — DB features disabled.");
            } else {
                Properties props = new Properties();
                props.load(input);
                url      = props.getProperty("db.url");
                user     = props.getProperty("db.user");
                password = props.getProperty("db.password");
            }
        } catch (IOException e) {
            System.err.println("[DBConnection] Failed to load db.properties: " + e.getMessage());
        }
    }

    /**
     * Returns the singleton Connection instance.
     * Creates the connection on first call; reuses it on subsequent calls.
     * If the connection was closed or lost, it will be re-created.
     */
    public static Connection getInstance() {
        if (url == null) {
            return null; // db.properties missing — DB features disabled
        }
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, password);
                System.out.println("Database connected successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
        return connection;
    }
}
