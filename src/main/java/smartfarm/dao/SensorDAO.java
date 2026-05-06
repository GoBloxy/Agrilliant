package smartfarm.dao;

import smartfarm.model.SensorReading;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SensorDAO implements GenericDAO<SensorReading> {
    private final Connection conn = DBConnection.getInstance();

    @Override
    public void save(SensorReading item) throws SQLException {
        String sql = "INSERT INTO sensor_readings (device_id, temperature, humidity, soil_moisture, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getDeviceId());
            stmt.setFloat(2, item.getTemperature());
            stmt.setFloat(3, item.getHumidity());
            stmt.setFloat(4, 0f); // soil_moisture — not yet wired to a sensor
            stmt.setObject(5, item.getTimestamp());
            stmt.executeUpdate();
        }
    }

    // Get the last 50 readings for a device (newest first)
    public List<SensorReading> getLast50Readings(String deviceId) throws SQLException {
        return getRecentForDevice(deviceId, 50);
    }

    public List<SensorReading> getRecentForDevice(String deviceId, int limit) throws SQLException {
        List<SensorReading> list = new ArrayList<>();
        String sql = "SELECT * FROM sensor_readings WHERE device_id = ? ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, deviceId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new SensorReading(
                    rs.getInt("reading_id"),
                    rs.getString("device_id"),
                    rs.getFloat("temperature"),
                    rs.getFloat("humidity"),
                    rs.getObject("timestamp", LocalDateTime.class)
                ));
            }
        }
        return list;
    }

    public List<SensorReading> getRecent(int limit) throws SQLException {
        List<SensorReading> list = new ArrayList<>();
        String sql = "SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new SensorReading(
                    rs.getInt("reading_id"),
                    rs.getString("device_id"),
                    rs.getFloat("temperature"),
                    rs.getFloat("humidity"),
                    rs.getObject("timestamp", LocalDateTime.class)
                ));
            }
        }
        return list;
    }

    @Override
    public SensorReading getById(int id) throws SQLException {
        String sql = "SELECT * FROM sensor_readings WHERE reading_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new SensorReading(
                    rs.getInt("reading_id"),
                    rs.getString("device_id"),
                    rs.getFloat("temperature"),
                    rs.getFloat("humidity"),
                    rs.getObject("timestamp", LocalDateTime.class)
                );
            }
        }
        return null;
    }

    @Override
    public ArrayList<SensorReading> getAll() throws SQLException {
        ArrayList<SensorReading> list = new ArrayList<>();
        String sql = "SELECT * FROM sensor_readings ORDER BY timestamp DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new SensorReading(
                    rs.getInt("reading_id"),
                    rs.getString("device_id"),
                    rs.getFloat("temperature"),
                    rs.getFloat("humidity"),
                    rs.getObject("timestamp", LocalDateTime.class)
                ));
            }
        }
        return list;
    }

    @Override
    public void update(SensorReading item) throws SQLException {
        // Sensor readings are immutable — not updated after insert
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM sensor_readings WHERE reading_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }
}
