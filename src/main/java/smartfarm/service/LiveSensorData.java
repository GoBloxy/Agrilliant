package smartfarm.service;

import javafx.application.Platform;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import smartfarm.model.SensorReading;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton that holds the latest sensor reading and exposes JavaFX properties.
 * TCP threads call {@link #update(SensorReading)} — the values are then pushed
 * to the JavaFX Application Thread so UI bindings update safely.
 */
public class LiveSensorData {

    private static final LiveSensorData INSTANCE = new LiveSensorData();

    private final SimpleFloatProperty temperature = new SimpleFloatProperty(Float.NaN);
    private final SimpleFloatProperty humidity = new SimpleFloatProperty(Float.NaN);
    private final SimpleFloatProperty soilMoisture = new SimpleFloatProperty(Float.NaN);
    private final SimpleStringProperty deviceId = new SimpleStringProperty("--");
    private final SimpleIntegerProperty activeSensors = new SimpleIntegerProperty(0);

    // Track unique connected device IDs and their latest readings
    private final Set<String> connectedDevices = ConcurrentHashMap.newKeySet();
    private final Map<String, SensorReading> latestReadings = new ConcurrentHashMap<>();

    private LiveSensorData() {}

    public static LiveSensorData getInstance() {
        return INSTANCE;
    }

    // Called from any thread (TCP handler)
    public void update(SensorReading reading, String deviceCode) {
        if (deviceCode != null) {
            connectedDevices.add(deviceCode);
            latestReadings.put(deviceCode, reading);
        }
        
        Platform.runLater(() -> {
            temperature.set(reading.getTemperature());
            humidity.set(reading.getHumidity());
            if (!Float.isNaN(reading.getSoilMoisture())) soilMoisture.set(reading.getSoilMoisture());
            if (deviceCode != null) deviceId.set(deviceCode);
            else deviceId.set(String.valueOf(reading.getDeviceId()));
            activeSensors.set(connectedDevices.size());
        });
    }
    
    // New method for notification service
    public void updateSensorData(String deviceId, double temperature, double humidity, double soilMoisture) {
        SensorReading reading = new SensorReading(0, 0, (float)temperature, (float)humidity, (float)soilMoisture, java.time.LocalDateTime.now());
        update(reading, deviceId);
    }

    // Called when a device disconnects
    public void removeDevice(String devId) {
        connectedDevices.remove(devId);
        latestReadings.remove(devId);
        Platform.runLater(() -> activeSensors.set(connectedDevices.size()));
    }
    
    // Get all latest readings for monitoring
    public Map<String, SensorReading> getAllLatestReadings() {
        return new HashMap<>(latestReadings);
    }
    
    // Get latest reading for specific device
    public SensorReading getLatestReading(String deviceId) {
        return latestReadings.get(deviceId);
    }
    
    // Get all connected device IDs
    public Set<String> getConnectedDevices() {
        return new HashSet<>(connectedDevices);
    }

    // JavaFX properties for binding/listening
    public SimpleFloatProperty temperatureProperty()    { return temperature; }
    public SimpleFloatProperty humidityProperty()       { return humidity; }
    public SimpleFloatProperty soilMoistureProperty()   { return soilMoisture; }
    public SimpleStringProperty deviceIdProperty()      { return deviceId; }
    public SimpleIntegerProperty activeSensorsProperty() { return activeSensors; }
}
