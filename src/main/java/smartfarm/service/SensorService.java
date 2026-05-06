package smartfarm.service;

import smartfarm.dao.SensorDAO;
import smartfarm.model.SensorReading;

import java.sql.SQLException;

public class SensorService {
    private final SensorDAO sensorDAO = new SensorDAO();
    private final AlertService alertService = new AlertService();

    // Push to live UI first (always), then persist to DB
    public void processReading(SensorReading reading) {
        // Always push to dashboard UI regardless of DB state
        LiveSensorData.getInstance().update(reading);

        try {
            sensorDAO.save(reading);

            int plotId = resolvePlotId(reading.getDeviceId());
            alertService.checkAndAlert(reading, plotId);

            System.out.println("Processed: " + reading);
        } catch (Exception e) {
            System.err.println("DB/alert error (UI still updated): " + e.getMessage());
        }
    }

    // Maps device ID to plot ID (e.g. "plot1_sensor" → 1)
    private int resolvePlotId(String deviceId) {
        String digits = deviceId.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 1 : Integer.parseInt(digits);
    }

    public java.util.List<SensorReading> getRecentReadings(int limit) {
        try {
            return sensorDAO.getRecent(limit);
        } catch (SQLException e) {
            System.err.println("[SensorService] Error fetching recent readings: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }
}
