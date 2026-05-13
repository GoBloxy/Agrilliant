package smartfarm.service.desktop;

import com.fazecast.jSerialComm.SerialPort;
import smartfarm.service.FingerprintService;
import smartfarm.util.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Desktop-only R307 fingerprint backend that talks to an ESP32 bridge
 * over USB serial via {@code com.fazecast.jSerialComm}.
 *
 * <p>This class is only compiled in the {@code desktop} Maven profile
 * (the {@code android} profile excludes {@code service/desktop/**} from
 * the source set since jSerialComm has native shared libraries that
 * don't link on Android).
 *
 * <p>It's not referenced statically from anywhere in the codebase —
 * {@link FingerprintService} resolves it by name via reflection at
 * runtime and falls back to a no-op stub if the class is missing.
 *
 * <p>Protocol (115200 baud, text-based commands over the ESP32 bridge):
 * <pre>
 *   PC → ESP32:  SCAN\n            ESP32 → PC: SCAN_OK:&lt;id&gt;,&lt;confidence&gt; | SCAN_FAIL:&lt;reason&gt;
 *   PC → ESP32:  ENROLL:&lt;slot&gt;\n  ESP32 → PC: ENROLL_OK:&lt;slot&gt; | ENROLL_FAIL:&lt;reason&gt;
 *   PC → ESP32:  TEMPLATE_COUNT\n  ESP32 → PC: TEMPLATE_COUNT:&lt;n&gt;
 *   PC → ESP32:  DELETE:&lt;slot&gt;\n  ESP32 → PC: DELETE_OK | DELETE_FAIL
 *   PC → ESP32:  PING\n            ESP32 → PC: PONG
 * </pre>
 */
public class FingerprintServiceDesktop implements FingerprintService.Backend {

    private static final String TAG = "FingerprintDesktop";

    private static final int BAUD_RATE  = 115200;
    private static final int TIMEOUT_MS = 20_000; // Enrollment can take a while.

    private SerialPort port;
    private BufferedReader reader;
    private OutputStream writer;
    private boolean connected = false;

    /** No-arg constructor — required so {@code FingerprintService} can
     *  instantiate the class reflectively. */
    public FingerprintServiceDesktop() {}

    @Override
    public boolean connect(String portName) {
        try {
            port = SerialPort.getCommPort(portName);
            port.setBaudRate(BAUD_RATE);
            port.setNumDataBits(8);
            port.setNumStopBits(1);
            port.setParity(SerialPort.NO_PARITY);
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, TIMEOUT_MS, TIMEOUT_MS);

            if (!port.openPort()) {
                Logger.e(TAG, "Failed to open port: " + portName);
                return false;
            }

            reader = new BufferedReader(new InputStreamReader(port.getInputStream(), StandardCharsets.UTF_8));
            writer = port.getOutputStream();

            // Small delay for the ESP32 boot loader.
            Thread.sleep(500);

            // Flush any pending output from ESP32 boot.
            while (port.bytesAvailable() > 0) {
                port.getInputStream().skip(port.bytesAvailable());
                Thread.sleep(50);
            }

            sendLine("PING");
            String resp = waitForResponse("PONG", 3000);
            if (resp != null) {
                connected = true;
                Logger.i(TAG, "ESP32 connected on " + portName);
                return true;
            }

            port.closePort();
            Logger.w(TAG, "ESP32 did not respond to PING on " + portName);
            return false;
        } catch (Exception e) {
            Logger.e(TAG, "Error connecting to " + portName, e);
            if (port != null && port.isOpen()) port.closePort();
            return false;
        }
    }

    @Override
    public boolean autoConnect() {
        for (SerialPort sp : SerialPort.getCommPorts()) {
            String name = sp.getSystemPortName();
            Logger.i(TAG, "Trying ESP32 on " + name + "...");
            if (connect(name)) return true;
        }
        Logger.w(TAG, "ESP32 not found on any COM port");
        return false;
    }

    @Override
    public void disconnect() {
        connected = false;
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        if (port != null && port.isOpen()) port.closePort();
    }

    @Override
    public boolean isConnected() {
        return connected && port != null && port.isOpen();
    }

    @Override
    public String getPortName() {
        return port != null ? port.getSystemPortName() : "N/A";
    }

    @Override
    public int scanAndMatch() {
        if (!isConnected()) return -1;
        try {
            sendLine("SCAN");
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                String line = readLineWithTimeout(15_000);
                if (line == null) return -1;
                if (line.startsWith("SCAN_OK:")) {
                    String[] parts = line.substring(8).split(",");
                    return Integer.parseInt(parts[0].trim());
                }
                if (line.startsWith("SCAN_FAIL")) return -1;
                // Ignore intermediate messages like SCAN_WAITING
            }
        } catch (Exception e) {
            Logger.e(TAG, "Scan error", e);
        }
        return -1;
    }

    @Override
    public int getTemplateCount() {
        if (!isConnected()) return -1;
        try {
            sendLine("TEMPLATE_COUNT");
            String resp = waitForResponse("TEMPLATE_COUNT:", 5000);
            if (resp != null) {
                return Integer.parseInt(resp.substring("TEMPLATE_COUNT:".length()).trim());
            }
        } catch (Exception e) {
            Logger.e(TAG, "Template count error", e);
        }
        return -1;
    }

    @Override
    public boolean deleteTemplate(int slotId) {
        if (!isConnected()) return false;
        try {
            sendLine("DELETE:" + slotId);
            String resp = waitForResponse("DELETE_", 5000);
            return resp != null && resp.startsWith("DELETE_OK");
        } catch (Exception e) {
            Logger.e(TAG, "Delete template error", e);
            return false;
        }
    }

    @Override
    public boolean enroll(int slotId, FingerprintService.EnrollCallback callback) {
        if (!isConnected()) return false;
        try {
            sendLine("ENROLL:" + slotId);

            long deadline = System.currentTimeMillis() + 45_000;
            while (System.currentTimeMillis() < deadline) {
                String line = readLineWithTimeout(15_000);
                if (line == null) {
                    if (callback != null) callback.onStatus("Timed out waiting for response.");
                    return false;
                }
                if (line.startsWith("ENROLL_OK:")) {
                    if (callback != null) callback.onStatus("Fingerprint enrolled (ID: " + slotId + ")");
                    return true;
                }
                if (line.startsWith("ENROLL_FAIL:")) {
                    String reason = line.substring("ENROLL_FAIL:".length());
                    if (callback != null) callback.onStatus("Enrollment failed: " + reason);
                    return false;
                }
                if (line.equals("ENROLL_PLACE1")) {
                    if (callback != null) callback.onStatus("Place your finger on the sensor...");
                } else if (line.equals("ENROLL_REMOVE")) {
                    if (callback != null) callback.onStatus("Remove your finger...");
                } else if (line.equals("ENROLL_PLACE2")) {
                    if (callback != null) callback.onStatus("Place the SAME finger again...");
                }
            }
            if (callback != null) callback.onStatus("Enrollment timed out.");
        } catch (Exception e) {
            if (callback != null) callback.onStatus("Error: " + e.getMessage());
        }
        return false;
    }

    @Override
    public String[] listAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] names = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            names[i] = ports[i].getSystemPortName() + " — " + ports[i].getDescriptivePortName();
        }
        return names;
    }

    private void sendLine(String command) throws Exception {
        writer.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }

    private String readLineWithTimeout(long timeoutMs) {
        try {
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, (int) timeoutMs, 0);
            String line = reader.readLine();
            if (line != null) line = line.trim();
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    private String waitForResponse(String prefix, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;
            String line = readLineWithTimeout(remaining);
            if (line == null) break;
            if (line.startsWith(prefix)) return line;
        }
        return null;
    }
}
