package smartfarm.model;

public class Device {
    private String deviceId;
    private int plotId;
    private String deviceType;

    // Full constructor (loading from DB)
    public Device(String deviceId, int plotId, String deviceType) {
        this.deviceId = deviceId;
        this.plotId = plotId;
        this.deviceType = deviceType;
    }

    // ── Getters ──

    public String getDeviceId() { return deviceId; }
    public int getPlotId() { return plotId; }
    public String getDeviceType() { return deviceType; }

    // ── Setters ──

    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    @Override
    public String toString() {
        return deviceId + " [" + deviceType + "] on plot " + plotId;
    }
}
