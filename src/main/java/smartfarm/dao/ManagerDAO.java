package smartfarm.dao;

import smartfarm.model.Manager;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ManagerDAO implements GenericDAO<Manager> {
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
