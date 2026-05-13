package smartfarm.service;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Communicates with the R307 fingerprint sensor connected to the desktop PC
 * via a USB-to-UART adapter (e.g., CP2102, FTDI, CH340).
 *
 * Protocol: Adafruit fingerprint sensor protocol at 57600 baud.
 * This service handles image capture, template conversion, and search.
 */
public class FingerprintService {

    private SerialPort port;
    private boolean connected = false;

    private static final int BAUD_RATE = 57600;
    private static final int TIMEOUT_MS = 3000;

    // R307 packet constants
    private static final byte[] HEADER = {(byte) 0xEF, (byte) 0x01};
    private static final byte[] DEFAULT_ADDR = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte PKT_COMMAND = 0x01;
    private static final byte PKT_ACK = 0x07;

    // R307 instruction codes
    private static final byte CMD_GEN_IMAGE = 0x01;
    private static final byte CMD_IMG_2_TZ = 0x02;
    private static final byte CMD_SEARCH = 0x04;
    private static final byte CMD_VERIFY_PWD = 0x13;

    /**
     * Attempts to connect to the R307 sensor on the given COM port.
     * @param portName e.g. "COM3" on Windows or "/dev/ttyUSB0" on Linux
     * @return true if connection successful and password verified
     */
    public boolean connect(String portName) {
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

        // Verify password (default 0x00000000)
        connected = verifyPassword();
        if (!connected) {
            port.closePort();
            System.err.println("R307 password verification failed on " + portName);
        }
        return connected;
    }

    /**
     * Auto-detect the R307 on any available COM port.
     * @return true if found and connected
     */
    public boolean autoConnect() {
        for (SerialPort sp : SerialPort.getCommPorts()) {
            String name = sp.getSystemPortName();
            System.out.println("Trying R307 on " + name + "...");
            if (connect(name)) {
                System.out.println("R307 connected on " + name);
                return true;
            }
        }
        System.err.println("R307 not found on any COM port");
        return false;
    }

    public void disconnect() {
        if (port != null && port.isOpen()) {
            port.closePort();
        }
        connected = false;
    }

    public boolean isConnected() {
        return connected && port != null && port.isOpen();
    }

    public String getPortName() {
        return port != null ? port.getSystemPortName() : "N/A";
    }

    /**
     * Scans a finger and returns the matched fingerprint ID, or -1 if not found/error.
     * This is a blocking call — waits up to TIMEOUT_MS for a finger.
     */
    public int scanAndMatch() {
        if (!isConnected()) return -1;

        // Step 1: Capture image
        if (!genImage()) return -1;

        // Step 2: Convert image to template in buffer 1
        if (!img2Tz((byte) 1)) return -1;

        // Step 3: Search library (all slots 0-999)
        return search();
    }

    private boolean verifyPassword() {
        byte[] data = {CMD_VERIFY_PWD, 0x00, 0x00, 0x00, 0x00};
        byte[] response = sendCommand(data);
        return response != null && response.length > 0 && response[0] == 0x00;
    }

    private boolean genImage() {
        byte[] data = {CMD_GEN_IMAGE};
        byte[] response = sendCommand(data);
        return response != null && response.length > 0 && response[0] == 0x00;
    }

    private boolean img2Tz(byte bufferId) {
        byte[] data = {CMD_IMG_2_TZ, bufferId};
        byte[] response = sendCommand(data);
        return response != null && response.length > 0 && response[0] == 0x00;
    }

    private int search() {
        // Search entire library: start=0, count=1000
        byte[] data = {CMD_SEARCH, 0x01, 0x00, 0x00, 0x03, (byte) 0xE8};
        byte[] response = sendCommand(data);
        if (response == null || response.length < 5 || response[0] != 0x00) {
            return -1;
        }
        // response[1..2] = finger ID (big-endian), response[3..4] = confidence
        int fingerId = ((response[1] & 0xFF) << 8) | (response[2] & 0xFF);
        return fingerId;
    }

    /**
     * Sends a command packet and reads the acknowledgement packet.
     */
    private byte[] sendCommand(byte[] instruction) {
        try {
            // Build packet: HEADER(2) + ADDR(4) + PKT_TYPE(1) + LENGTH(2) + DATA(N) + CHECKSUM(2)
            int length = instruction.length + 2; // data + checksum(2)
            byte[] packet = new byte[2 + 4 + 1 + 2 + instruction.length + 2];
            int i = 0;
            packet[i++] = HEADER[0];
            packet[i++] = HEADER[1];
            packet[i++] = DEFAULT_ADDR[0];
            packet[i++] = DEFAULT_ADDR[1];
            packet[i++] = DEFAULT_ADDR[2];
            packet[i++] = DEFAULT_ADDR[3];
            packet[i++] = PKT_COMMAND;
            packet[i++] = (byte) ((length >> 8) & 0xFF);
            packet[i++] = (byte) (length & 0xFF);
            System.arraycopy(instruction, 0, packet, i, instruction.length);
            i += instruction.length;

            // Checksum = PKT_TYPE + LENGTH(2) + DATA(N)
            int checksum = PKT_COMMAND + ((length >> 8) & 0xFF) + (length & 0xFF);
            for (byte b : instruction) checksum += (b & 0xFF);
            packet[i++] = (byte) ((checksum >> 8) & 0xFF);
            packet[i++] = (byte) (checksum & 0xFF);

            // Send
            port.writeBytes(packet, packet.length);

            // Read response header (9 bytes: HEADER(2) + ADDR(4) + PKT_TYPE(1) + LENGTH(2))
            byte[] respHeader = new byte[9];
            int read = port.readBytes(respHeader, 9);
            if (read < 9) return null;

            // Verify it's an ACK packet
            if (respHeader[6] != PKT_ACK) return null;

            int respLen = ((respHeader[7] & 0xFF) << 8) | (respHeader[8] & 0xFF);
            // Read data + checksum
            byte[] respData = new byte[respLen];
            read = port.readBytes(respData, respLen);
            if (read < respLen) return null;

            // Return data without checksum (last 2 bytes)
            byte[] result = new byte[respLen - 2];
            System.arraycopy(respData, 0, result, 0, result.length);
            return result;

        } catch (Exception e) {
            System.err.println("R307 communication error: " + e.getMessage());
            return null;
        }
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
