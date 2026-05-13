package smartfarm.service;

import smartfarm.dao.SensorDAO;
import smartfarm.model.SensorReading;
import smartfarm.util.Logger;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class SensorService {

    private static final String TAG = "SensorService";

    private final SensorDAO sensorDAO = new SensorDAO();
    private final AlertService alertService = new AlertService();

    private static final long DB_SAVE_INTERVAL_MS = 60_000; // Save to DB every 60 seconds
    private final ConcurrentHashMap<String, Long> lastSaveTime = new ConcurrentHashMap<>();

    // Push to live UI first (always), then persist to DB at a throttled rate
    public void processReading(SensorReading reading, String deviceCode) {
        // Always push to dashboard UI regardless of DB state
        LiveSensorData.getInstance().update(reading, deviceCode);

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

            Logger.i(TAG, "Saved: " + reading);
        } catch (Exception e) {
            Logger.e(TAG, "DB/alert error (UI still updated)", e);
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
            Logger.e(TAG, "Error fetching recent readings", e);
            return new java.util.ArrayList<>();
        }
    }
}
