package smartfarm.dao;

import smartfarm.model.Worker;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkerDAO implements GenericDAO<Worker> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Worker item) throws SQLException {
        if (conn == null) return;
        String sql = "INSERT INTO worker (full_name, phone, email, password_hash, job_title, skills, on_duty, fingerprint_id, manager_id) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getFullName());
            ps.setString(2, item.getPhone());
            ps.setString(3, item.getEmail());
            ps.setString(4, item.getPasswordHash());
            ps.setString(5, item.getJobTitle());
            ps.setString(6, item.getSkills());
            ps.setBoolean(7, item.isOnDuty());
            if (item.getFingerprintId() != null) ps.setInt(8, item.getFingerprintId());
            else ps.setNull(8, java.sql.Types.INTEGER);
            ps.setInt(9, item.getManagerId());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setWorkerId(keys.getInt(1));
                }
            }
        }
    }

    @Override
    public Worker getById(int id) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM worker WHERE worker_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    @Override
    public List<Worker> getAll() throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM worker ORDER BY full_name";
        List<Worker> workers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) workers.add(mapRow(rs));
        }
        return workers;
    }

    public List<Worker> getByManager(int managerId) throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM worker WHERE manager_id = ? ORDER BY full_name";
        List<Worker> workers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, managerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) workers.add(mapRow(rs));
            }
        }
        return workers;
    }

    @Override
    public void update(Worker item) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE worker SET full_name = ?, phone = ?, email = ?, password_hash = ?, job_title = ?, skills = ?, on_duty = ?, fingerprint_id = ?, manager_id = ? "
                   + "WHERE worker_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getFullName());
            ps.setString(2, item.getPhone());
            ps.setString(3, item.getEmail());
            ps.setString(4, item.getPasswordHash());
            ps.setString(5, item.getJobTitle());
            ps.setString(6, item.getSkills());
            ps.setBoolean(7, item.isOnDuty());
            if (item.getFingerprintId() != null) ps.setInt(8, item.getFingerprintId());
            else ps.setNull(8, java.sql.Types.INTEGER);
            ps.setInt(9, item.getManagerId());
            ps.setInt(10, item.getWorkerId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM worker_task WHERE worker_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance WHERE worker_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM worker WHERE worker_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Worker mapRow(ResultSet rs) throws SQLException {
        int fpRaw = rs.getInt("fingerprint_id");
        Integer fingerprintId = rs.wasNull() ? null : fpRaw;
        return new Worker(
            rs.getInt("worker_id"),
            rs.getString("full_name"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("job_title"),
            rs.getString("skills"),
            rs.getBoolean("on_duty"),
            fingerprintId,
            rs.getInt("manager_id"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
        );
    }
    
    public Worker getByPhoneOrEmail(String identifier) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM worker WHERE phone = ? OR email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }
}
