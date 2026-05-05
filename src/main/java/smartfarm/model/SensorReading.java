package smartfarm.model;

import java.time.LocalDateTime;

public class SensorReading {
    private int readingId;
    private String deviceId;
    private float temperature;
    private float humidity;
    private LocalDateTime timestamp;

    // Full constructor (when loading from DB)
    public SensorReading(int readingId, String deviceId, float temperature, float humidity, LocalDateTime timestamp) {
        this.readingId = readingId;
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
    }

    // Without readingId (when receiving from ESP32 — DB auto-generates the ID)
    public SensorReading(String deviceId, float temperature, float humidity, LocalDateTime timestamp) {
        this.readingId = -1;
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
    }

    // ── Getters ──
    public int getReadingId()        { return readingId; }
    public String getDeviceId()      { return deviceId; }
    public float getTemperature()    { return temperature; }
    public float getHumidity()       { return humidity; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // ── Setters ──
    public void setReadingId(int readingId)          { this.readingId = readingId; }
    public void setDeviceId(String deviceId)         { this.deviceId = deviceId; }
    public void setTemperature(float temperature)    { this.temperature = temperature; }
    public void setHumidity(float humidity)           { this.humidity = humidity; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "[" + timestamp + "] Device: " + deviceId + " | Temp: " + temperature + "C | Hum: " + humidity + "%";
    }
}
