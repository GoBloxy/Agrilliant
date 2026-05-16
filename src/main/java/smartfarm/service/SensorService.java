package smartfarm.service;

import smartfarm.dao.SensorDAO;
import smartfarm.model.SensorReading;
import smartfarm.server.MqttBridge;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class SensorService {
    private final SensorDAO sensorDAO = new SensorDAO();
    private final AlertService alertService = new AlertService();

    private static final long DB_SAVE_INTERVAL_MS = 60_000; // Save to DB every 60 seconds
    private final ConcurrentHashMap<String, Long> lastSaveTime = new ConcurrentHashMap<>();

    // Push to live UI first (always), then persist to DB at a throttled rate
    public void processReading(SensorReading reading, String deviceCode) {
        // Always push to local dashboard UI regardless of DB state
        LiveSensorData.getInstance().update(reading, deviceCode);

        // Broadcast to all remote clients via MQTT (non-blocking async publish)
        MqttBridge.getInstance().publishReading(
                deviceCode, reading.getTemperature(), reading.getHumidity(), reading.getSoilMoisture(), reading.getLightLevel());

        // Only save to database every DB_SAVE_INTERVAL_MS per device
        String key = deviceCode != null ? deviceCode : "unknown";
        long now = System.currentTimeMillis();
        long lastSave = lastSaveTime.getOrDefault(key, 0L);

        if (now - lastSave < DB_SAVE_INTERVAL_MS) return;

        try {
            sensorDAO.save(reading);
            lastSaveTime.put(key, now);

            int plotId = resolvePlotId(deviceCode);
            alertService.checkAndAlert(reading, plotId);

            System.out.println("Saved: " + reading);
        } catch (Exception e) {
            System.err.println("DB/alert error (UI still updated): " + e.getMessage());
        }
    }

    // Maps device code to plot ID (e.g. "plot1_sensor" → 1)
    private int resolvePlotId(String deviceCode) {
        if (deviceCode == null) return 1;
        String digits = deviceCode.replaceAll("[^0-9]", "");
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
