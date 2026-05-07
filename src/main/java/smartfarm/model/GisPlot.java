package smartfarm.model;

import java.time.LocalDateTime;

/**
 * TODO: GisPlot stores geographic/spatial data for each plot.
 *
 * HOW TO IMPLEMENT GIS:
 * - Store plot boundaries as a polygon (list of lat/lng coordinates).
 * - In MySQL, you can use the GEOMETRY/POLYGON spatial data type, or store as JSON text.
 * - For the frontend map: use Leaflet.js or Google Maps API to render plot boundaries.
 * - Drone can fly over and capture aerial imagery; the orthomosaic is stored as a URL.
 *
 * DATA SOURCES:
 * - Manual: manager draws boundaries on a map in the UI.
 * - Drone: autonomous flight captures GPS-tagged images → stitched into orthomosaic.
 * - Satellite: free imagery from Sentinel-2 (ESA) via API for NDVI vegetation index.
 *
 * Links to: Plot (1:1 relationship — each Plot has one GisPlot with its spatial data).
 * The drone captures field overview imagery stored here as orthomosaic URLs.
 */
public class GisPlot {
    private int gisPlotId;
    private int plotId;                // FK → plots (1:1)
    private String boundaryGeoJson;    // GeoJSON polygon string for plot boundary
    private double centerLatitude;
    private double centerLongitude;
    private double areaSquareMeters;   // computed from boundary
    private String orthomosaicUrl;     // latest drone aerial image URL
    private String ndviMapUrl;         // latest NDVI vegetation map URL (from satellite/drone)
    private LocalDateTime lastUpdated;

    // TODO: Implement constructors, getters, setters
    // TODO: Implement DAO (GisPlotDAO)
    // TODO: Implement Service (GisService):
    //       - updateBoundary(plotId, geoJson) → recalculate area
    //       - uploadOrthomosaic(plotId, imageUrl) → update after drone flight
    //       - fetchNdvi(plotId) → call satellite API (e.g. Sentinel Hub) for vegetation health
}
