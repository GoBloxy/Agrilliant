package smartfarm.model;

import java.time.LocalDateTime;

public class Plot {
    private int plotId;
    private String name;
    private String location;
    private double sizeAcres;
    private String soilType;
    private int managerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Full constructor (loading from DB)
    public Plot(int plotId, String name, String location, double sizeAcres, String soilType,
                int managerId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.plotId = plotId;
        this.name = name;
        this.location = location;
        this.sizeAcres = sizeAcres;
        this.soilType = soilType;
        this.managerId = managerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Without plotId (creating new)
    public Plot(String name, String location, double sizeAcres, String soilType, int managerId) {
        this.plotId = -1;
        this.name = name;
        this.location = location;
        this.sizeAcres = sizeAcres;
        this.soilType = soilType;
        this.managerId = managerId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public int getPlotId() { return plotId; }
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public double getSizeAcres() { return sizeAcres; }
    public void setSizeAcres(double sizeAcres) { this.sizeAcres = sizeAcres; }
    public String getSoilType() { return soilType; }
    public void setSoilType(String soilType) { this.soilType = soilType; }
    public int getManagerId() { return managerId; }
    public void setManagerId(int managerId) { this.managerId = managerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return name + " (" + sizeAcres + " acres, " + soilType + ") @ " + location;
    }
}
