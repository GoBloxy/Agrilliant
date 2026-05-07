package smartfarm.model;

public class GisPlot {
    private int gisId;
    private int plotId;                // FK → plots (1:1)
    private double centerLatitude;
    private double centerLongitude;
    private String boundaryGeoJson;
    private String satelliteImageUrl;
    private String soilHeatmapUrl;

    public GisPlot(int gisId, int plotId, double centerLatitude, double centerLongitude,
                   String boundaryGeoJson, String satelliteImageUrl, String soilHeatmapUrl) {
        this.gisId = gisId;
        this.plotId = plotId;
        this.centerLatitude = centerLatitude;
        this.centerLongitude = centerLongitude;
        this.boundaryGeoJson = boundaryGeoJson;
        this.satelliteImageUrl = satelliteImageUrl;
        this.soilHeatmapUrl = soilHeatmapUrl;
    }

    public GisPlot(int plotId, double centerLatitude, double centerLongitude,
                   String boundaryGeoJson, String satelliteImageUrl, String soilHeatmapUrl) {
        this.gisId = -1;
        this.plotId = plotId;
        this.centerLatitude = centerLatitude;
        this.centerLongitude = centerLongitude;
        this.boundaryGeoJson = boundaryGeoJson;
        this.satelliteImageUrl = satelliteImageUrl;
        this.soilHeatmapUrl = soilHeatmapUrl;
    }

    public int getGisId() { return gisId; }
    public void setGisId(int gisId) { this.gisId = gisId; }
    public int getPlotId() { return plotId; }
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public double getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(double centerLatitude) { this.centerLatitude = centerLatitude; }
    public double getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(double centerLongitude) { this.centerLongitude = centerLongitude; }
    public String getBoundaryGeoJson() { return boundaryGeoJson; }
    public void setBoundaryGeoJson(String boundaryGeoJson) { this.boundaryGeoJson = boundaryGeoJson; }
    public String getSatelliteImageUrl() { return satelliteImageUrl; }
    public void setSatelliteImageUrl(String satelliteImageUrl) { this.satelliteImageUrl = satelliteImageUrl; }
    public String getSoilHeatmapUrl() { return soilHeatmapUrl; }
    public void setSoilHeatmapUrl(String soilHeatmapUrl) { this.soilHeatmapUrl = soilHeatmapUrl; }
}
