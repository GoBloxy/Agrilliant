package smartfarm.server;

import smartfarm.dao.DeviceDAO;
import smartfarm.model.Device;
import smartfarm.model.SensorReading;
import smartfarm.service.AttendanceService;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SensorService;
import smartfarm.service.SystemLogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class SensorHandler implements Runnable {
    private final Socket socket;
    private final SensorService sensorService = new SensorService();
    private final AttendanceService attendanceService = new AttendanceService();
    private final DeviceDAO deviceDAO = new DeviceDAO();
    private String lastDeviceCode;

    public SensorHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("FINGERPRINT:")) {
                    handleFingerprint(line);
                } else {
                    Parsed parsed = parseLine(line);
                    if (parsed != null) {
                        lastDeviceCode = parsed.deviceCode;
                        int deviceId = resolveDeviceId(parsed.deviceCode);
                        SensorReading reading = new SensorReading(
                            0, deviceId, parsed.temperature, parsed.humidity, parsed.soilMoisture, parsed.lightLevel, LocalDateTime.now()
                        );
                        sensorService.processReading(reading, parsed.deviceCode);
                    }
                }
            }
        } catch (IOException e) {
            SystemLogManager.getInstance().info("SensorHandler",
                    "Device disconnected: " + (lastDeviceCode != null ? lastDeviceCode : socket.getInetAddress()), "system");
        } finally {
            if (lastDeviceCode != null) {
                LiveSensorData.getInstance().removeDevice(lastDeviceCode);
            }
        }
    }

    // Look up device by its code; returns 0 (untracked) if not registered or DB unavailable.
    private int resolveDeviceId(String deviceCode) {
        try {
            Device d = deviceDAO.getByCode(deviceCode);
            return d != null ? d.getDeviceId() : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    // Parses "FINGERPRINT:<device_code>,ID:<fingerprint_id>"
    private void handleFingerprint(String raw) {
        try {
            String[] parts = raw.split(",");
            String deviceCode = parts[0].split(":")[1];
            int fingerprintId = Integer.parseInt(parts[1].split(":")[1]);
            lastDeviceCode = deviceCode;
            String result = attendanceService.handleFingerprintScan(fingerprintId, deviceCode);
            SystemLogManager.getInstance().info("SensorHandler",
                    "Fingerprint scan on " + deviceCode + " → " + result, "system");
        } catch (Exception e) {
            System.err.println("Bad fingerprint data: " + raw);
        }
    }

    // Parses "DEVICE:plot1_sensor,TEMP:27.50,HUM:63.20,SOIL:54.30,LIGHT:72.00"
    private Parsed parseLine(String raw) {
        try {
            String[] parts = raw.split(",");
            String deviceCode = null;
            float temp = Float.NaN, hum = Float.NaN, soil = Float.NaN, light = Float.NaN;
            for (String part : parts) {
                String[] kv = part.split(":", 2);
                if (kv.length != 2) continue;
                switch (kv[0].trim()) {
                    case "DEVICE": deviceCode = kv[1].trim(); break;
                    case "TEMP":   temp  = Float.parseFloat(kv[1].trim()); break;
                    case "HUM":    hum   = Float.parseFloat(kv[1].trim()); break;
                    case "SOIL":   soil  = Float.parseFloat(kv[1].trim()); break;
                    case "LIGHT":  light = Float.parseFloat(kv[1].trim()); break;
                }
            }
            if (deviceCode == null || Float.isNaN(temp)) return null;
            return new Parsed(deviceCode, temp, hum, soil, light);
        } catch (Exception e) {
            System.err.println("Bad data format: " + raw);
            return null;
        }
    }

    private static final class Parsed {
        final String deviceCode;
        final float temperature;
        final float humidity;
        final float soilMoisture;
        final float lightLevel;
        Parsed(String deviceCode, float temperature, float humidity, float soilMoisture, float lightLevel) {
            this.deviceCode = deviceCode;
            this.temperature = temperature;
            this.humidity = humidity;
            this.soilMoisture = soilMoisture;
            this.lightLevel = lightLevel;
        }
    }
}
