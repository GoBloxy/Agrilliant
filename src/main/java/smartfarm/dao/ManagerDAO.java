package smartfarm.dao;

import smartfarm.model.Manager;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for {@link smartfarm.model.Manager} rows.
 *
 * <h2>Thread-safety (post-Android-migration H4 / H5)</h2>
 *
 * All public methods on this class are safe to call from a
 * background thread. JDBC is blocking, so callers must invoke
 * them from
 * {@link smartfarm.util.DBConnection#runAsync(java.util.concurrent.Callable)}
 * (or a controller {@code Task<T>}) — never from the JavaFX UI
 * thread, otherwise the UI freezes for the duration of the
 * round-trip.
 *
 * <p>None of the methods log to {@link System#out} /
 * {@link System#err} in a hot loop (Android logcat noise).
 * Exceptions are propagated via {@code throws SQLException} so
 * the caller decides whether to surface them in the UI (typically
 * by calling {@link smartfarm.util.Logger#e(String, String, Throwable)}).
 */
public class ManagerDAO implements GenericDAO<Manager> {
    // TODO(phase-2): consider replacing this cached field with a
    //   private Connection conn() { return DBConnection.getInstance(); }
    // method so a torn connection auto-recovers via H4's getInstance()
    // re-create logic. Currently if this handle dies the DAO needs to
    // be re-instantiated; that's acceptable for now since DAOs are
    // typically short-lived (controller scope).
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Manager item) throws SQLException {
        if (conn == null) return;
        String sql = "INSERT INTO manager (full_name, username, email, password_hash, phone, active) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getFullName());
            ps.setString(2, item.getUsername());
            ps.setString(3, item.getEmail());
            ps.setString(4, item.getPasswordHash());
            ps.setString(5, item.getPhone());
            ps.setBoolean(6, item.isActive());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) item.setManagerId(keys.getInt(1));
            }
        }
    }

    @Override
    public Manager getById(int id) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM manager WHERE manager_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Manager getByUsername(String username) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM manager WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Manager getByEmail(String email) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM manager WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    @Override
    public List<Manager> getAll() throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM manager ORDER BY full_name";
        List<Manager> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    @Override
    public void update(Manager item) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE manager SET full_name = ?, username = ?, email = ?, password_hash = ?, phone = ?, active = ? "
                   + "WHERE manager_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getFullName());
            ps.setString(2, item.getUsername());
            ps.setString(3, item.getEmail());
            ps.setString(4, item.getPasswordHash());
            ps.setString(5, item.getPhone());
            ps.setBoolean(6, item.isActive());
            ps.setInt(7, item.getManagerId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        if (conn == null) return;
        String sql = "DELETE FROM manager WHERE manager_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Manager mapRow(ResultSet rs) throws SQLException {
        return new Manager(
            rs.getInt("manager_id"),
            rs.getString("full_name"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("phone"),
            rs.getBoolean("active"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
        );
    }
}
