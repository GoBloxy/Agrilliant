package smartfarm.dao;

import smartfarm.model.Device;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DeviceDAO implements GenericDAO<Device> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(Device item) throws SQLException {
        if (conn == null) return;
        String sql = "INSERT INTO devices (device_code, type, status, plot_id, firmware_version, last_seen_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getDeviceCode());
            ps.setString(2, item.getType().name());
            ps.setString(3, item.getStatus().name());
            ps.setInt(4, item.getPlotId());
            ps.setString(5, item.getFirmwareVersion());
            ps.setObject(6, item.getLastSeenAt());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) item.setDeviceId(keys.getInt(1));
            }
        }
    }

    @Override
    public Device getById(int id) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM devices WHERE device_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Device getByCode(String deviceCode) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM devices WHERE device_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    @Override
    public List<Device> getAll() throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM devices ORDER BY device_code";
        List<Device> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    @Override
    public void update(Device item) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE devices SET device_code = ?, type = ?, status = ?, plot_id = ?, firmware_version = ?, last_seen_at = ? "
                   + "WHERE device_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getDeviceCode());
            ps.setString(2, item.getType().name());
            ps.setString(3, item.getStatus().name());
            ps.setInt(4, item.getPlotId());
            ps.setString(5, item.getFirmwareVersion());
            ps.setObject(6, item.getLastSeenAt());
            ps.setInt(7, item.getDeviceId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        if (conn == null) return;
        String sql = "DELETE FROM devices WHERE device_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void touchLastSeen(int deviceId) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE devices SET last_seen_at = NOW(), status = 'ONLINE' WHERE device_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deviceId);
            ps.executeUpdate();
        }
    }

    private Device mapRow(ResultSet rs) throws SQLException {
        return new Device(
            rs.getInt("device_id"),
            rs.getString("device_code"),
            Device.Type.valueOf(rs.getString("type")),
            Device.Status.valueOf(rs.getString("status")),
            rs.getInt("plot_id"),
            rs.getString("firmware_version"),
            rs.getObject("last_seen_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class)
        );
    }
}
