package smartfarm.model;

import java.time.LocalDateTime;

public class Device {
    public enum Type { TEMP_HUM, SOIL }
    public enum Status { ONLINE, OFFLINE, MAINT }

    private int deviceId;
    private String deviceCode;
    private Type type;
    private Status status;
    private int plotId;
    private String firmwareVersion;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;

    public Device(int deviceId, String deviceCode, Type type, Status status, int plotId,
                  String firmwareVersion, LocalDateTime lastSeenAt, LocalDateTime createdAt) {
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.type = type;
        this.status = status;
        this.plotId = plotId;
        this.firmwareVersion = firmwareVersion;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
    }

    public Device(String deviceCode, Type type, int plotId, String firmwareVersion) {
        this.deviceId = -1;
        this.deviceCode = deviceCode;
        this.type = type;
        this.status = Status.OFFLINE;
        this.plotId = plotId;
        this.firmwareVersion = firmwareVersion;
        this.lastSeenAt = null;
        this.createdAt = LocalDateTime.now();
    }

    public int getDeviceId() { return deviceId; }
    public void setDeviceId(int deviceId) { this.deviceId = deviceId; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getPlotId() { return plotId; }
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return deviceCode + " [" + type + "/" + status + "] on plot " + plotId;
    }
}
