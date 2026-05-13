package smartfarm.dao;

import smartfarm.model.Attendance;
import smartfarm.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AttendanceDAO {
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
