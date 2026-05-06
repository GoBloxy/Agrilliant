package smartfarm.dao;

import smartfarm.model.Worker;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkerDAO implements GenericDAO<Worker> {
    private final Connection conn = DBConnection.getInstance();

    // ═══════════════ SAVE ═══════════════
    @Override
    public void save(Worker item) throws SQLException {
        if (conn == null) return;
        String sql = "INSERT INTO users (email, password_hash, full_name, role, phone, active_task_count) "
                   + "VALUES (?, ?, ?, 'WORKER', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getEmail());
            ps.setString(2, item.getPasswordHash());
            ps.setString(3, item.getFullName());
            ps.setString(4, item.getPhone());
            ps.setInt(5, item.getActiveTaskCount());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setUserId(keys.getInt(1));
                }
            }
        }
    }

    // ═══════════════ GET BY ID ═══════════════
    @Override
    public Worker getById(int id) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM users WHERE user_id = ? AND role = 'WORKER'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // ═══════════════ GET BY EMAIL ═══════════════
    public Worker getByEmail(String email) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM users WHERE email = ? AND role = 'WORKER'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // ═══════════════ GET ALL ═══════════════
    @Override
    public List<Worker> getAll() throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'WORKER' ORDER BY full_name";
        List<Worker> workers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                workers.add(mapRow(rs));
            }
        }
        return workers;
    }

    // ═══════════════ UPDATE ═══════════════
    @Override
    public void update(Worker item) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE users SET email = ?, full_name = ?, phone = ?, "
                   + "active_task_count = ? "
                   + "WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getEmail());
            ps.setString(2, item.getFullName());
            ps.setString(3, item.getPhone());
            ps.setInt(4, item.getActiveTaskCount());
            ps.setInt(5, item.getUserId());
            ps.executeUpdate();
        }
    }

    // ═══════════════ DELETE ═══════════════
    @Override
    public void delete(int id) throws SQLException {
        if (conn == null) return;
        String sql = "DELETE FROM users WHERE user_id = ? AND role = 'WORKER'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ═══════════════ ROW MAPPER ═══════════════
    private Worker mapRow(ResultSet rs) throws SQLException {
        return new Worker(
            rs.getInt("user_id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("full_name"),
            rs.getString("phone"),
            rs.getInt("active_task_count")
        );
    }
}
