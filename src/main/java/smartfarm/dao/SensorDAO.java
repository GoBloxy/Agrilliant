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

/**
 * DAO for {@link smartfarm.model.SensorReading} rows.
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
public class SensorDAO implements GenericDAO<SensorReading> {
    // TODO(phase-2): consider replacing this cached field with a
    //   private Connection conn() { return DBConnection.getInstance(); }
    // method so a torn connection auto-recovers via H4's getInstance()
    // re-create logic. Currently if this handle dies the DAO needs to
    // be re-instantiated; that's acceptable for now since DAOs are
    // typically short-lived (controller scope).
    private final Connection conn = DBConnection.getInstance();

    private SensorReading map(ResultSet rs) throws SQLException {
        return new SensorReading(
            rs.getInt("reading_id"),
            rs.getInt("device_id"),
            rs.getFloat("temperature"),
            rs.getFloat("humidity"),
            rs.getFloat("soil_moisture"),
            rs.getObject("timestamp", LocalDateTime.class)
        );
    }

    @Override
    public void save(SensorReading item) throws SQLException {
        String sql = "INSERT INTO sensor_readings (device_id, temperature, humidity, soil_moisture, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, item.getDeviceId());
            stmt.setFloat(2, item.getTemperature());
            stmt.setFloat(3, item.getHumidity());
            if (Float.isNaN(item.getSoilMoisture())) {
                stmt.setNull(4, java.sql.Types.FLOAT);
            } else {
                stmt.setFloat(4, item.getSoilMoisture());
            }
            stmt.setObject(5, item.getTimestamp());
            stmt.executeUpdate();
        }
    }

    // Get the last 50 readings for a device (newest first)
    public List<SensorReading> getLast50Readings(int deviceId) throws SQLException {
        return getRecentForDevice(deviceId, 50);
    }

    public List<SensorReading> getRecentForDevice(int deviceId, int limit) throws SQLException {
        List<SensorReading> list = new ArrayList<>();
        String sql = "SELECT * FROM sensor_readings WHERE device_id = ? ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, deviceId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<SensorReading> getRecent(int limit) throws SQLException {
        List<SensorReading> list = new ArrayList<>();
        String sql = "SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    @Override
    public SensorReading getById(int id) throws SQLException {
        String sql = "SELECT * FROM sensor_readings WHERE reading_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return map(rs);
        }
        return null;
    }

    @Override
    public ArrayList<SensorReading> getAll() throws SQLException {
        ArrayList<SensorReading> list = new ArrayList<>();
        String sql = "SELECT * FROM sensor_readings ORDER BY timestamp DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(map(rs));
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
