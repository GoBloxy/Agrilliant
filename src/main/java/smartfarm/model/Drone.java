package smartfarm.model;

import java.time.LocalDateTime;

/**
 * TODO: Drone represents a UAV used for irrigation, crop monitoring, and disease detection.
 *
 * CAPABILITIES:
 * - Irrigation: carries a tank and sprays water/fertilizer over plots.
 * - Imaging: captures aerial photos for GIS orthomosaics and disease detection.
 * - Monitoring: follows a flight path over plots, recording sensor data.
 *
 * INTEGRATION:
 * - Drone communicates with backend via MQTT or REST API (like ESP32 sensors).
 * - Sends telemetry: battery, GPS position, flight status.
 * - Receives commands: start mission, return home, spray area.
 * - Flight missions can be auto-triggered by alerts or scheduled.
 *
 * Links to: Plot (which plot it's assigned/operating on, nullable when idle).
 * Referenced by: IrrigationLog (drone-based irrigation), DiseaseDetection (drone-captured images).
 */
public class Drone {
    public enum Status { IDLE, IN_FLIGHT, CHARGING, MAINTENANCE }
    public enum MissionType { IRRIGATION, IMAGING, MONITORING, SPRAYING }

    private int droneId;
    private String droneCode;         // e.g. "DRN-001"
    private String model;             // hardware model name
    private Status status;
    private Integer currentPlotId;    // nullable — null when idle/not assigned
    private double batteryPercent;
    private double latitude;
    private double longitude;
    private double altitudeMeters;
    private LocalDateTime lastHeartbeat;  // last telemetry ping
    private double totalFlightHours;

    // TODO: Implement constructors, getters, setters
    // TODO: Implement DAO (DroneDAO)
    // TODO: Implement Service (DroneService):
    //       - startMission(droneId, plotId, missionType)
    //       - updateTelemetry(droneId, battery, lat, lng, alt)
    //       - completeMission(droneId) → log results
    //       - getAvailableDrones() → drones with status IDLE and battery > threshold
    // TODO: Create DroneMission model to track individual flight missions with start/end times
}
