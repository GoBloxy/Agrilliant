package smartfarm.model;

import java.time.LocalDateTime;

public class Attendance {
    private int attendanceId;
    private int workerId;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private String deviceCode;

    public Attendance(int attendanceId, int workerId, LocalDateTime checkIn,
                      LocalDateTime checkOut, String deviceCode) {
        this.attendanceId = attendanceId;
        this.workerId = workerId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.deviceCode = deviceCode;
    }

    public Attendance(int workerId, String deviceCode) {
        this.attendanceId = -1;
        this.workerId = workerId;
        this.checkIn = LocalDateTime.now();
        this.checkOut = null;
        this.deviceCode = deviceCode;
    }

    public int getAttendanceId() { return attendanceId; }
    public void setAttendanceId(int attendanceId) { this.attendanceId = attendanceId; }
    public int getWorkerId() { return workerId; }
    public void setWorkerId(int workerId) { this.workerId = workerId; }
    public LocalDateTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDateTime checkIn) { this.checkIn = checkIn; }
    public LocalDateTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDateTime checkOut) { this.checkOut = checkOut; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public boolean isCheckedOut() { return checkOut != null; }
}
