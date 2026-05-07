package smartfarm.model;

import java.time.LocalDateTime;

public class SensorReading {
    private int readingId;
    private int deviceId;
    private float temperature;
    private float humidity;
    private LocalDateTime timestamp;

    public SensorReading(int readingId, int deviceId, float temperature, float humidity, LocalDateTime timestamp) {
        this.readingId = readingId;
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
    }

    public SensorReading(int deviceId, float temperature, float humidity, LocalDateTime timestamp) {
        this.readingId = -1;
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
    }

    public int getReadingId()        { return readingId; }
    public int getDeviceId()         { return deviceId; }
    public float getTemperature()    { return temperature; }
    public float getHumidity()       { return humidity; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setReadingId(int readingId)          { this.readingId = readingId; }
    public void setDeviceId(int deviceId)            { this.deviceId = deviceId; }
    public void setTemperature(float temperature)    { this.temperature = temperature; }
    public void setHumidity(float humidity)           { this.humidity = humidity; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "[" + timestamp + "] Device: " + deviceId + " | Temp: " + temperature + "C | Hum: " + humidity + "%";
    }
}
