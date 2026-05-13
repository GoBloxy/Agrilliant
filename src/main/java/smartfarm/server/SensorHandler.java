// Desktop build only — paired with FarmServer (see its header comment).
package smartfarm.server;

import smartfarm.dao.DeviceDAO;
import smartfarm.model.Device;
import smartfarm.model.SensorReading;
import smartfarm.service.AttendanceService;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SensorService;

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
                            deviceId, parsed.temperature, parsed.humidity, parsed.soilMoisture, LocalDateTime.now()
                        );
                        sensorService.processReading(reading, parsed.deviceCode);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Device disconnected: " + socket.getInetAddress());
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
            System.out.println("Fingerprint scan on " + deviceCode + " → " + result);
        } catch (Exception e) {
            System.err.println("Bad fingerprint data: " + raw);
        }
    }

    // Parses "DEVICE:plot1_sensor,TEMP:27.50,HUM:63.20,SOIL:54.30"
    private Parsed parseLine(String raw) {
        try {
            String[] parts = raw.split(",");
            String deviceCode = parts[0].split(":")[1];
            float temp = Float.parseFloat(parts[1].split(":")[1]);
            float hum  = Float.parseFloat(parts[2].split(":")[1]);
            float soil = parts.length > 3 ? Float.parseFloat(parts[3].split(":")[1]) : Float.NaN;
            return new Parsed(deviceCode, temp, hum, soil);
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
        Parsed(String deviceCode, float temperature, float humidity, float soilMoisture) {
            this.deviceCode = deviceCode;
            this.temperature = temperature;
            this.humidity = humidity;
            this.soilMoisture = soilMoisture;
        }
    }
}
