package smartfarm.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton database connection utility.
 * Provides a single shared Connection instance to the MySQL database.
 *
 * Usage:
 *   Connection conn = DBConnection.getInstance();
 *
 * Configuration:
 *   Update the URL, USER, and PASSWORD constants below to match your MySQL setup.
 */
public class DBConnection {

    private static final String URL  = "jdbc:mysql://localhost:3306/smart_farm";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    private static Connection connection;

    // Private constructor — prevents instantiation
    private DBConnection() {}

    /**
     * Returns the singleton Connection instance.
     * Creates the connection on first call; reuses it on subsequent calls.
     * If the connection was closed or lost, it will be re-created.
     */
    public static Connection getInstance() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("Database connected successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
        return connection;
    }
}
