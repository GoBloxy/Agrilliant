package smartfarm.server;

import smartfarm.model.SensorReading;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SensorService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.time.LocalDateTime;

public class SensorHandler implements Runnable {
    private final Socket socket;
    private final SensorService sensorService = new SensorService();
    private String lastDeviceId;

    public SensorHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                SensorReading reading = parseReading(line);
                if (reading != null) {
                    lastDeviceId = reading.getDeviceId();
                    sensorService.processReading(reading);
                }
            }
        } catch (IOException e) {
            System.out.println("Device disconnected: " + socket.getInetAddress());
        } finally {
            if (lastDeviceId != null) {
                LiveSensorData.getInstance().removeDevice(lastDeviceId);
            }
        }
    }

    // Parses "DEVICE:plot1_sensor,TEMP:27.50,HUM:63.20" into a SensorReading
    private SensorReading parseReading(String raw) {
        try {
            String[] parts = raw.split(",");
            String deviceId = parts[0].split(":")[1];
            float temp = Float.parseFloat(parts[1].split(":")[1]);
            float hum = Float.parseFloat(parts[2].split(":")[1]);
            return new SensorReading(deviceId, temp, hum, LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("Bad data format: " + raw);
            return null;
        }
    }
}
