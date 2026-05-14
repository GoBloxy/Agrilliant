package smartfarm.service;

import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Communicates with the R307 fingerprint sensor via the ESP32 as a serial bridge.
 * The ESP32 is connected to the PC via USB. Commands are sent as text over serial,
 * and the ESP32 relays them to the R307 and returns text results.
 *
 * Protocol (text-based over USB serial at 115200 baud):
 *   PC → ESP32:  SCAN\n              ESP32 → PC: SCAN_OK:<id>,<confidence> or SCAN_FAIL:<reason>
 *   PC → ESP32:  ENROLL:<slot>\n      ESP32 → PC: ENROLL_OK:<slot> or ENROLL_FAIL:<reason>
 *   PC → ESP32:  TEMPLATE_COUNT\n     ESP32 → PC: TEMPLATE_COUNT:<n>
 *   PC → ESP32:  PING\n              ESP32 → PC: PONG
 */
public class FingerprintService {

    private SerialPort port;
    private BufferedReader reader;
    private OutputStream writer;
    private boolean connected = false;

    private static final int BAUD_RATE = 115200;
    private static final int TIMEOUT_MS = 20000; // Enrollment can take a while

    /** Callback for enrollment/scan progress updates */
    public interface EnrollCallback {
        void onStatus(String message);
    }

    /**
     * Connect to the ESP32 on the given COM port.
     */
    public boolean connect(String portName) {
        try {
            port = SerialPort.getCommPort(portName);
            port.setBaudRate(BAUD_RATE);
            port.setNumDataBits(8);
            port.setNumStopBits(1);
            port.setParity(SerialPort.NO_PARITY);
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, TIMEOUT_MS, TIMEOUT_MS);

            if (!port.openPort()) {
                System.err.println("Failed to open port: " + portName);
                return false;
            }

            reader = new BufferedReader(new InputStreamReader(port.getInputStream(), StandardCharsets.UTF_8));
            writer = port.getOutputStream();

            // Small delay for ESP32 to be ready
            Thread.sleep(500);

            // Flush any pending output from ESP32 boot
            while (port.bytesAvailable() > 0) {
                port.getInputStream().skip(port.bytesAvailable());
                Thread.sleep(50);
            }

            // Test connection with PING
            sendLine("PING");
            String resp = waitForResponse("PONG", 3000);
            if (resp != null) {
                connected = true;
                // Lock R307 immediately — prevents autonomous check-in scan from firing
                // during the window between connect() and the user placing their finger.
                try { sendLine("LOCK"); waitForResponse("LOCKED", 2000); } catch (Exception ignored) {}
                System.out.println("ESP32 connected on " + portName);
                return true;
            }

            port.closePort();
            System.err.println("ESP32 did not respond to PING on " + portName);
            return false;
        } catch (Exception e) {
            System.err.println("Error connecting to " + portName + ": " + e.getMessage());
            if (port != null && port.isOpen()) port.closePort();
            return false;
        }
    }

    /**
     * Auto-detect the ESP32 on any available COM port.
     */
    public boolean autoConnect() {
        for (SerialPort sp : SerialPort.getCommPorts()) {
            String name = sp.getSystemPortName();
            System.out.println("Trying ESP32 on " + name + "...");
            if (connect(name)) return true;
        }
        System.err.println("ESP32 not found on any COM port");
        return false;
    }

    /**
     * Tell the ESP32 to release the R307 sensor lock so background check-in
     * scanning can resume. Always call this before disconnect() when an
     * enrollment session ends (success, failure, or cancellation).
     */
    public void release() {
        if (!isConnected()) return;
        try {
            sendLine("RELEASE");
        } catch (Exception ignored) {}
    }

    public void disconnect() {
        release();  // Always release R307 lock before closing so autonomous scans resume
        connected = false;
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        if (port != null && port.isOpen()) port.closePort();
    }

    public boolean isConnected() {
        return connected && port != null && port.isOpen();
    }

    public String getPortName() {
        return port != null ? port.getSystemPortName() : "N/A";
    }

    /**
     * Scan a finger and return the matched fingerprint ID, or -1 if not found.
     */
    public int scanAndMatch() {
        if (!isConnected()) return -1;
        try {
            // LOCK first: prevents the autonomous ESP32 scan from consuming the
            // finger image before handleScan() is ready to receive it.
            sendLine("LOCK");
            waitForResponse("LOCKED", 2000);
            sendLine("SCAN");
            // Wait for final result (SCAN_OK or SCAN_FAIL), up to 15s
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                String line = readLineWithTimeout(15000);
                if (line == null) return -1;
                if (line.startsWith("SCAN_OK:")) {
                    // Format: SCAN_OK:<id>,<confidence>
                    String[] parts = line.substring(8).split(",");
                    return Integer.parseInt(parts[0].trim());
                }
                if (line.startsWith("SCAN_FAIL")) return -1;
                // Ignore intermediate messages like SCAN_WAITING
            }
        } catch (Exception e) {
            System.err.println("Scan error: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Get the number of stored fingerprint templates on the R307.
     */
    public int getTemplateCount() {
        if (!isConnected()) return -1;
        try {
            sendLine("TEMPLATE_COUNT");
            String resp = waitForResponse("TEMPLATE_COUNT:", 5000);
            if (resp != null) {
                return Integer.parseInt(resp.substring("TEMPLATE_COUNT:".length()).trim());
            }
        } catch (Exception e) {
            System.err.println("Template count error: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Delete a fingerprint template from the R307 at the given slot.
     */
    public boolean deleteTemplate(int slotId) {
        if (!isConnected()) return false;
        try {
            sendLine("DELETE:" + slotId);
            String resp = waitForResponse("DELETE_", 5000);
            return resp != null && resp.startsWith("DELETE_OK");
        } catch (Exception e) {
            System.err.println("Delete template error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Enroll a new fingerprint at the given slot. Two scans required.
     * The callback receives progress messages from the ESP32.
     */
    public boolean enroll(int slotId, EnrollCallback callback) {
        if (!isConnected()) return false;
        try {
            sendLine("ENROLL:" + slotId);

            long deadline = System.currentTimeMillis() + 45000; // Enrollment can take up to 45s
            while (System.currentTimeMillis() < deadline) {
                String line = readLineWithTimeout(15000);
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

                // Progress messages from ESP32
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

    private void sendLine(String command) throws Exception {
        writer.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }

    /**
     * Read a line from the serial port with a timeout.
     */
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

    /**
     * Wait for a specific response prefix within timeout.
     */
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

    /**
     * Lists all available serial ports (for UI port selection).
     */
    public static String[] getAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] names = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            names[i] = ports[i].getSystemPortName() + " — " + ports[i].getDescriptivePortName();
        }
        return names;
    }
}
