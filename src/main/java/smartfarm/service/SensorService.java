package smartfarm.service;

import smartfarm.dao.SensorDAO;
import smartfarm.model.SensorReading;

import java.sql.SQLException;

public class SensorService {
    private final SensorDAO sensorDAO = new SensorDAO();
    private final AlertService alertService = new AlertService();

    // Save the reading, then check if it triggers any alerts
    public void processReading(SensorReading reading) {
        try {
            sensorDAO.saveReading(reading);

            int plotId = resolvePlotId(reading.getDeviceId());
            alertService.checkAndAlert(reading, plotId);

            System.out.println("Processed: " + reading);
        } catch (SQLException e) {
            System.err.println("Failed to process reading: " + e.getMessage());
        }
    }

    // Maps device ID to plot ID (e.g. "plot1_sensor" → 1)
    private int resolvePlotId(String deviceId) {
        String digits = deviceId.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 1 : Integer.parseInt(digits);
    }
}
