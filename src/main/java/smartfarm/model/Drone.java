package smartfarm.model;

public class Drone {
    public enum Status { IDLE, MAPPING, IRRIGATING, RETURNING, MAINT }

    private int droneId;
    private String serialNumber;
    private String model;
    private Status status;
    private double batteryPercent;
    private Integer assignedPlotId;        // nullable
    private int operatedByWorkerId;

    public Drone(int droneId, String serialNumber, String model, Status status, double batteryPercent,
                 Integer assignedPlotId, int operatedByWorkerId) {
        this.droneId = droneId;
        this.serialNumber = serialNumber;
        this.model = model;
        this.status = status;
        this.batteryPercent = batteryPercent;
        this.assignedPlotId = assignedPlotId;
        this.operatedByWorkerId = operatedByWorkerId;
    }

    public Drone(String serialNumber, String model, double batteryPercent,
                 Integer assignedPlotId, int operatedByWorkerId) {
        this.droneId = -1;
        this.serialNumber = serialNumber;
        this.model = model;
        this.status = Status.IDLE;
        this.batteryPercent = batteryPercent;
        this.assignedPlotId = assignedPlotId;
        this.operatedByWorkerId = operatedByWorkerId;
    }

    public int getDroneId() { return droneId; }
    public void setDroneId(int droneId) { this.droneId = droneId; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public double getBatteryPercent() { return batteryPercent; }
    public void setBatteryPercent(double batteryPercent) { this.batteryPercent = batteryPercent; }
    public Integer getAssignedPlotId() { return assignedPlotId; }
    public void setAssignedPlotId(Integer assignedPlotId) { this.assignedPlotId = assignedPlotId; }
    public int getOperatedByWorkerId() { return operatedByWorkerId; }
    public void setOperatedByWorkerId(int operatedByWorkerId) { this.operatedByWorkerId = operatedByWorkerId; }
}
