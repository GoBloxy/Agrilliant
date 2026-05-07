package smartfarm.service;

import javafx.application.Platform;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import smartfarm.model.SensorReading;

import java.util.Set;
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
    private final SimpleStringProperty deviceId = new SimpleStringProperty("--");
    private final SimpleIntegerProperty activeSensors = new SimpleIntegerProperty(0);

    // Track unique connected device IDs
    private final Set<String> connectedDevices = ConcurrentHashMap.newKeySet();

    private LiveSensorData() {}

    public static LiveSensorData getInstance() {
        return INSTANCE;
    }

    // Called from any thread (TCP handler)
    public void update(SensorReading reading, String deviceCode) {
        if (deviceCode != null) connectedDevices.add(deviceCode);
        Platform.runLater(() -> {
            temperature.set(reading.getTemperature());
            humidity.set(reading.getHumidity());
            if (deviceCode != null) deviceId.set(deviceCode);
            else deviceId.set(String.valueOf(reading.getDeviceId()));
            activeSensors.set(connectedDevices.size());
        });
    }

    // Called when a device disconnects
    public void removeDevice(String devId) {
        connectedDevices.remove(devId);
        Platform.runLater(() -> activeSensors.set(connectedDevices.size()));
    }

    // JavaFX properties for binding/listening
    public SimpleFloatProperty temperatureProperty()    { return temperature; }
    public SimpleFloatProperty humidityProperty()       { return humidity; }
    public SimpleStringProperty deviceIdProperty()      { return deviceId; }
    public SimpleIntegerProperty activeSensorsProperty() { return activeSensors; }
}
