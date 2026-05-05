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

public class SensorDAO {
    private final Connection conn = DBConnection.getInstance();

    // Save a reading to the database
    public void saveReading(SensorReading reading) throws SQLException {
        String sql = "INSERT INTO sensor_readings (device_id, temperature, humidity, timestamp) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reading.getDeviceId());
            stmt.setFloat(2, reading.getTemperature());
            stmt.setFloat(3, reading.getHumidity());
            stmt.setObject(4, reading.getTimestamp());
            stmt.executeUpdate();
        }
    }

    // Get the last 50 readings for a device (newest first)
    public List<SensorReading> getLast50Readings(String deviceId) throws SQLException {
        List<SensorReading> list = new ArrayList<>();
        String sql = "SELECT * FROM sensor_readings WHERE device_id = ? ORDER BY timestamp DESC LIMIT 50";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, deviceId);
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
}
