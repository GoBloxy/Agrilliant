package smartfarm.dao;

import smartfarm.model.Attendance;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for {@link smartfarm.model.Attendance} rows.
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
public class AttendanceDAO {
    // TODO(phase-2): consider replacing this cached field with a
    //   private Connection conn() { return DBConnection.getInstance(); }
    // method so a torn connection auto-recovers via H4's getInstance()
    // re-create logic. Currently if this handle dies the DAO needs to
    // be re-instantiated; that's acceptable for now since DAOs are
    // typically short-lived (controller scope).
    private final Connection conn = DBConnection.getInstance();

    public void checkIn(Attendance record) throws SQLException {
        if (conn == null) return;
        String sql = "INSERT INTO attendance (worker_id, check_in, device_code) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, record.getWorkerId());
            ps.setTimestamp(2, Timestamp.valueOf(record.getCheckIn()));
            ps.setString(3, record.getDeviceCode());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) record.setAttendanceId(keys.getInt(1));
            }
        }
    }

    public void checkOut(int attendanceId) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE attendance SET check_out = ? WHERE attendance_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, attendanceId);
            ps.executeUpdate();
        }
    }

    public Attendance getOpenSession(int workerId) throws SQLException {
        if (conn == null) return null;
        String sql = "SELECT * FROM attendance WHERE worker_id = ? AND check_out IS NULL ORDER BY check_in DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, workerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Attendance> getAll() throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM attendance ORDER BY check_in DESC";
        List<Attendance> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<Attendance> getByWorker(int workerId) throws SQLException {
        if (conn == null) return new ArrayList<>();
        String sql = "SELECT * FROM attendance WHERE worker_id = ? ORDER BY check_in DESC";
        List<Attendance> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, workerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int getWorkerIdByFingerprint(int fingerprintId) throws SQLException {
        if (conn == null) return -1;
        String sql = "SELECT worker_id FROM worker WHERE fingerprint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fingerprintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("worker_id");
            }
        }
        return -1;
    }

    private Attendance mapRow(ResultSet rs) throws SQLException {
        Timestamp coTs = rs.getTimestamp("check_out");
        return new Attendance(
            rs.getInt("attendance_id"),
            rs.getInt("worker_id"),
            rs.getTimestamp("check_in").toLocalDateTime(),
            coTs != null ? coTs.toLocalDateTime() : null,
            rs.getString("device_code")
        );
    }
}
