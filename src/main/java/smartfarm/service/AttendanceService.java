package smartfarm.service;

import smartfarm.dao.AttendanceDAO;
import smartfarm.model.Attendance;

import java.sql.SQLException;
import java.util.List;

public class AttendanceService {
    private final AttendanceDAO attendanceDAO = new AttendanceDAO();

    public String handleFingerprintScan(int fingerprintId, String deviceCode) {
        try {
            int workerId = attendanceDAO.getWorkerIdByFingerprint(fingerprintId);
            if (workerId == -1) {
                System.err.println("No worker registered for fingerprint ID: " + fingerprintId);
                return "UNKNOWN";
            }

            Attendance openSession = attendanceDAO.getOpenSession(workerId);
            if (openSession != null) {
                attendanceDAO.checkOut(openSession.getAttendanceId());
                System.out.println("Worker " + workerId + " checked OUT via fingerprint " + fingerprintId);
                return "CHECK_OUT";
            } else {
                Attendance record = new Attendance(workerId, deviceCode);
                attendanceDAO.checkIn(record);
                System.out.println("Worker " + workerId + " checked IN via fingerprint " + fingerprintId);
                return "CHECK_IN";
            }
        } catch (SQLException e) {
            System.err.println("Attendance error: " + e.getMessage());
            return "ERROR";
        }
    }

    public List<Attendance> getAllRecords() {
        try {
            return attendanceDAO.getAll();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load attendance records: " + e.getMessage());
        }
    }

    public List<Attendance> getRecordsByWorker(int workerId) {
        try {
            return attendanceDAO.getByWorker(workerId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load attendance for worker: " + e.getMessage());
        }
    }
}
