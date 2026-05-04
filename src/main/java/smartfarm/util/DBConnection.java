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

    // Load credentials from properties file once
    static {
        try (InputStream input = DBConnection.class
                .getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new RuntimeException(
                    "db.properties not found in resources. "
                    + "Copy db.properties.example → db.properties and fill in your credentials.");
            }
            Properties props = new Properties();
            props.load(input);
            url      = props.getProperty("db.url");
            user     = props.getProperty("db.user");
            password = props.getProperty("db.password");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load db.properties: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the singleton Connection instance.
     * Creates the connection on first call; reuses it on subsequent calls.
     * If the connection was closed or lost, it will be re-created.
     */
    public static Connection getInstance() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, password);
                System.out.println("Database connected successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
        return connection;
    }
}
