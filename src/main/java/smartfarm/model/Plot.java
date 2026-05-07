package smartfarm.model;

public class Plot {
    private int plotId;
    private String name;
    private String location;
    private double sizeAcres;
    private String soilType;
    private int ownerId;

    // Full constructor (loading from DB)
    public Plot(int plotId, String name, String location, double sizeAcres, String soilType, int ownerId) {
        this.plotId = plotId;
        this.name = name;
        this.location = location;
        this.sizeAcres = sizeAcres;
        this.soilType = soilType;
        this.ownerId = ownerId;
    }

    // Without plotId (creating new)
    public Plot(String name, String location, double sizeAcres, String soilType, int ownerId) {
        this.plotId = -1;
        this.name = name;
        this.location = location;
        this.sizeAcres = sizeAcres;
        this.soilType = soilType;
        this.ownerId = ownerId;
    }

    // ── Getters ──

    public int getPlotId() { return plotId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public double getSizeAcres() { return sizeAcres; }
    public String getSoilType() { return soilType; }
    public int getOwnerId() { return ownerId; }

    // ── Setters ──

    public void setPlotId(int plotId) { this.plotId = plotId; }
    public void setName(String name) { this.name = name; }
    public void setLocation(String location) { this.location = location; }
    public void setSizeAcres(double sizeAcres) { this.sizeAcres = sizeAcres; }
    public void setSoilType(String soilType) { this.soilType = soilType; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    @Override
    public String toString() {
        return name + " (" + sizeAcres + " acres, " + soilType + ") @ " + location;
    }
}
