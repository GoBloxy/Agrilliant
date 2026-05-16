package smartfarm.dao;

import smartfarm.model.SystemLog;
import smartfarm.model.SystemLog.LogType;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LogDAO {
    private final Connection conn = DBConnection.getInstance();

    public void save(SystemLog log) {
        if (conn == null) return;
        String sql = "INSERT INTO system_logs (log_type, source, message, `user`, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getType().name());
            ps.setString(2, log.getSource());
            ps.setString(3, log.getMessage());
            ps.setString(4, log.getUser());
            ps.setTimestamp(5, Timestamp.valueOf(log.getTimestamp()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[LogDAO] Failed to save log: " + e.getMessage());
        }
    }

    public List<SystemLog> getAll() {
        List<SystemLog> list = new ArrayList<>();
        if (conn == null) return list;
        String sql = "SELECT * FROM system_logs ORDER BY timestamp DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[LogDAO] Failed to load logs: " + e.getMessage());
        }
        return list;
    }

    public void clearAll() {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM system_logs")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[LogDAO] Failed to clear logs: " + e.getMessage());
        }
    }

    private SystemLog mapRow(ResultSet rs) throws SQLException {
        return new SystemLog(
            LogType.valueOf(rs.getString("log_type")),
            rs.getString("source"),
            rs.getString("message"),
            rs.getString("user"),
            rs.getTimestamp("timestamp").toLocalDateTime()
        );
    }
}
