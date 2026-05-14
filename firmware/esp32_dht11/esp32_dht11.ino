/*
 * ESP32 + DHT11 + FC-28 + SH1106 OLED + R307 Fingerprint Firmware
 * Reads temperature, humidity, and soil moisture every 2 seconds,
 * displays them on a 1.3" OLED screen, and sends to the Java server via TCP.
 * Also handles R307 fingerprint scanner for worker attendance (check-in/out).
 *
 * Wiring:
 *   DHT11 VCC   -> ESP32 3.3V
 *   DHT11 DATA  -> ESP32 GPIO 4
 *   DHT11 GND   -> ESP32 GND
 *
 *   FC-28 VCC   -> ESP32 3.3V
 *   FC-28 GND   -> ESP32 GND
 *   FC-28 A0    -> ESP32 GPIO 34
 *
 *   SH1106 VCC  -> ESP32 3.3V
 *   SH1106 GND  -> ESP32 GND
 *   SH1106 SDA  -> ESP32 GPIO 21
 *   SH1106 SCL  -> ESP32 GPIO 22
 *
 *   R307  VCC   -> ESP32 5V (VIN)
 *   R307  GND   -> ESP32 GND
 *   R307  TX    -> ESP32 GPIO 16 (UART2 RX)
 *   R307  RX    -> ESP32 GPIO 17 (UART2 TX)
 *
 * Data formats sent:
 *   Sensor:      DEVICE:<id>,TEMP:<value>,HUM:<value>,SOIL:<value>
 *   Fingerprint: FINGERPRINT:<id>,ID:<fingerprint_id>
 *
 * Libraries required (install via Arduino Library Manager):
 *   - DHT sensor library by Adafruit
 *   - Adafruit Unified Sensor
 *   - U8g2 by oliver
 *   - Adafruit Fingerprint Sensor Library
 *
 * Board: ESP32 Dev Module (select in Arduino IDE -> Tools -> Board)
 *
 * TODO: Update WIFI_SSID, WIFI_PASSWORD, and SERVER_IP before uploading.
 */

#include <WiFi.h>
#include <DHT.h>
#include <U8g2lib.h>
#include <Wire.h>
#include <Adafruit_Fingerprint.h>

// ─── Configuration ───────────────────────────────────────────────
#define WIFI_SSID     "314Pi"
#define WIFI_PASSWORD "TheGreatPi123@"

#define SERVER_IP     "192.168.8.147"   // IP of the PC running the Java app
#define SERVER_PORT   8080

#define DHT_PIN       4                 // GPIO pin connected to DHT11 DATA
#define DHT_TYPE      DHT11
#define SOIL_PIN      34                // GPIO pin connected to FC-28 A0 (analog)
#define DEVICE_ID     "plot1_sensor"    // Unique ID for this sensor node

#define READ_INTERVAL 2000              // Milliseconds between readings (DHT11 min ~1s)
#define FP_RX_PIN     16               // GPIO pin for R307 TX -> ESP32 RX (UART2)
#define FP_TX_PIN     17               // GPIO pin for R307 RX -> ESP32 TX (UART2)
#define FP_CHECK_INTERVAL 3000         // Milliseconds between fingerprint scan checks
#define SERIAL_BRIDGE_TIMEOUT 45000    // Release R307 bridge lock if Java app goes silent

// FC-28 calibration (adjust after testing with your sensor)
#define SOIL_DRY      4095              // ADC value when sensor is in dry air
#define SOIL_WET      1200              // ADC value when sensor is in water
#define SOIL_NO_SENSOR_THRESHOLD 4000   // Above this = floating pin (no sensor)
// ─────────────────────────────────────────────────────────────────

DHT dht(DHT_PIN, DHT_TYPE);
WiFiClient client;
unsigned long lastRead = 0;
bool soilSensorDetected = false;        // Auto-detected at startup

// R307 Fingerprint on UART2
HardwareSerial fpSerial(2);
Adafruit_Fingerprint finger = Adafruit_Fingerprint(&fpSerial);
bool fingerprintDetected = false;
unsigned long lastFpCheck = 0;
bool serialBridgeActive = false;       // True when desktop app is using R307 via serial
unsigned long serialBridgeLastActivity = 0;  // Timestamp of last serial command (for auto-expiry)

// Cache the most recent autonomous scan result so handleScan() can reuse it
// when the login button is pressed right as (or just after) the autonomous scan runs.
int  cachedFpId   = -1;
unsigned long cachedFpTime = 0;
#define FP_CACHE_MS 8000   // Reuse cached result within 8 seconds

// SH1106 OLED (128x64, I2C on default SDA=21, SCL=22)
U8G2_SH1106_128X64_NONAME_F_HW_I2C oled(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);

// Agrilliant heron logo bitmap (40x50 pixels, XBM format for drawXBMP)
// Auto-generated from logotransparent.png
#define LOGO_WIDTH  40
#define LOGO_HEIGHT 50
static const unsigned char PROGMEM agrilliant_logo[] = {
  0x00,0x00,0x00,0x0E,0x00,
  0x00,0x00,0x80,0x3F,0x00,
  0x00,0x00,0xC0,0x7F,0x00,
  0x00,0x00,0xC0,0xFF,0x07,
  0x00,0x00,0xC0,0xFF,0x7F,
  0x00,0x00,0xE0,0xFF,0x7F,
  0x00,0x00,0xC0,0x3F,0x00,
  0x00,0x00,0xC0,0x0F,0x00,
  0x00,0x00,0xC0,0x0F,0x00,
  0x00,0x00,0x80,0x1F,0x00,
  0x00,0x00,0x00,0x1F,0x00,
  0x00,0x00,0x00,0x1F,0x00,
  0x00,0x00,0x0E,0x3F,0x00,
  0x00,0xC0,0xFF,0x3F,0x00,
  0x00,0xF0,0xFF,0x3F,0x00,
  0x00,0xFC,0xFF,0x3F,0x00,
  0x00,0xFE,0xFF,0x3F,0x00,
  0x00,0xFF,0xFF,0x1F,0x00,
  0x80,0xFF,0xFF,0x1F,0x00,
  0xC0,0xFF,0xFF,0x1F,0x00,
  0xC0,0xFF,0xFF,0x0F,0x00,
  0xE0,0xFF,0xFF,0x0F,0x00,
  0xF0,0xFF,0xFF,0x07,0x00,
  0xF0,0xFF,0xFF,0x03,0x00,
  0xF8,0xFF,0xFF,0x03,0x00,
  0xF8,0xFF,0xFF,0x01,0x00,
  0xFC,0xFF,0x7F,0x00,0x00,
  0xFC,0xFF,0x3F,0x00,0x00,
  0xFE,0xFF,0x1F,0x00,0x00,
  0xFE,0xFF,0x07,0x00,0x00,
  0xFE,0xFF,0x02,0x00,0x00,
  0xFF,0x37,0x02,0x00,0x00,
  0x7E,0x30,0x03,0x00,0x00,
  0x1E,0x18,0x03,0x00,0x00,
  0x0E,0x18,0x03,0x00,0x00,
  0x07,0x08,0x01,0x00,0x00,
  0x01,0x0C,0x01,0x00,0x00,
  0x00,0x08,0x03,0x00,0x00,
  0x00,0x08,0x03,0x00,0x00,
  0x00,0x08,0x02,0x00,0x00,
  0x00,0x18,0x02,0x00,0x00,
  0x00,0x18,0x06,0x00,0x00,
  0x00,0x18,0x04,0x00,0x00,
  0x00,0x10,0x04,0x00,0x00,
  0x00,0x10,0x0C,0x00,0x00,
  0x00,0x10,0x0C,0x00,0x00,
  0x00,0xFC,0x7C,0x00,0x00,
  0x00,0x00,0x00,0x00,0x00,
  0x00,0x00,0x00,0x00,0x00,
  0x00,0x00,0x00,0x00,0x00,
};

void showIdleLogo() {
    oled.clearBuffer();
    // Draw heron logo centered horizontally, near top
    oled.drawXBMP((128 - LOGO_WIDTH) / 2, 2, LOGO_WIDTH, LOGO_HEIGHT, agrilliant_logo);
    // Draw "Agrilliant" text centered below logo
    oled.setFont(u8g2_font_6x10_tf);
    oled.drawStr((128 - 60) / 2, 62, "Agrilliant");
    oled.sendBuffer();
}

bool detectSoilSensor() {
    // A floating pin produces wildly varying readings (0–4095).
    // A real FC-28 gives stable readings. Take 10 samples and check:
    //   1. If all readings are near max (>4000) → no sensor (pin floating high)
    //   2. If the spread (max-min) is too large (>800) → no sensor (random noise)
    int minVal = 4095, maxVal = 0;
    int highCount = 0;
    for (int i = 0; i < 10; i++) {
        int val = analogRead(SOIL_PIN);
        if (val < minVal) minVal = val;
        if (val > maxVal) maxVal = val;
        if (val > SOIL_NO_SENSOR_THRESHOLD) highCount++;
        delay(50);
    }
    int spread = maxVal - minVal;
    if (highCount >= 8) return false;   // All near max = floating pin
    if (spread > 800) return false;     // Wild swings = no sensor
    return true;
}

void setup() {
    Serial.begin(115200);
    dht.begin();

    // Initialize OLED and show logo splash
    oled.begin();
    showIdleLogo();

    // Auto-detect FC-28 soil moisture sensor
    soilSensorDetected = detectSoilSensor();
    Serial.println(soilSensorDetected ? "FC-28 detected on GPIO 34" : "No FC-28 detected on GPIO 34");

    // Initialize R307 fingerprint sensor on UART2
    fpSerial.begin(57600, SERIAL_8N1, FP_RX_PIN, FP_TX_PIN);
    finger.begin(57600);
    if (finger.verifyPassword()) {
        fingerprintDetected = true;
        Serial.println("R307 fingerprint sensor detected");
        Serial.print("Enrolled fingerprints: ");
        finger.getTemplateCount();
        Serial.println(finger.templateCount);
    } else {
        Serial.println("No R307 fingerprint sensor detected");
    }

    // Connect to WiFi
    Serial.printf("Connecting to %s", WIFI_SSID);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\nWiFi connected!");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());

    // Show IP on OLED briefly
    oled.clearBuffer();
    oled.drawStr(10, 30, "WiFi OK!");
    oled.drawStr(10, 45, WiFi.localIP().toString().c_str());
    oled.sendBuffer();
    delay(1500);
}

void loop() {
    // Handle serial commands from desktop app (ESP32 as R307 bridge)
    if (Serial.available()) {
        String cmd = Serial.readStringUntil('\n');
        cmd.trim();
        handleSerialCommand(cmd);
    }

    // Auto-expire serial bridge lock if desktop app went silent
    if (serialBridgeActive && (millis() - serialBridgeLastActivity > SERIAL_BRIDGE_TIMEOUT)) {
        serialBridgeActive = false;
        Serial.println("BRIDGE_TIMEOUT: R307 lock released");
    }

    // Check fingerprint scanner — skip while desktop app holds the R307 bridge
    if (fingerprintDetected && !serialBridgeActive && millis() - lastFpCheck >= FP_CHECK_INTERVAL) {
        lastFpCheck = millis();
        checkFingerprint();
    }

    if (millis() - lastRead < READ_INTERVAL) return;
    lastRead = millis();

    float temp = dht.readTemperature();
    float hum  = dht.readHumidity();

    if (isnan(temp) || isnan(hum)) {
        Serial.println("DHT read failed, retrying...");
        return;
    }

    // Read FC-28 soil moisture (analog) — only if sensor was detected
    float soilPercent = NAN;
    if (soilSensorDetected) {
        int rawSoil = analogRead(SOIL_PIN);
        soilPercent = map(constrain(rawSoil, SOIL_WET, SOIL_DRY), SOIL_DRY, SOIL_WET, 0, 100);
        Serial.printf("Temp: %.2f C  Hum: %.2f %%  Soil: %.1f %%\n", temp, hum, soilPercent);
    } else {
        Serial.printf("Temp: %.2f C  Hum: %.2f %%\n", temp, hum);
    }

    // Establish (or re-establish) server connection BEFORE updating OLED
    // so the display reflects the actual connection state.
    if (!client.connected()) {
        client.stop();  // Ensure clean close before reconnecting
        Serial.printf("Connecting to server %s:%d...\n", SERVER_IP, SERVER_PORT);
        if (!client.connect(SERVER_IP, SERVER_PORT)) {
            Serial.println("Connection failed!");
            updateOLED(temp, hum, soilPercent);  // Show disconnected state
            return;
        }
        client.setNoDelay(true);  // Disable Nagle — send each packet immediately
        Serial.println("Connected to server.");
    }

    // Update OLED — connection state is now accurate
    updateOLED(temp, hum, soilPercent);

    // Send reading in expected format
    String payload = "DEVICE:" + String(DEVICE_ID) +
                     ",TEMP:" + String(temp, 2) +
                     ",HUM:" + String(hum, 2);
    if (soilSensorDetected) {
        payload += ",SOIL:" + String(soilPercent, 2);
    }
    client.println(payload);
    client.flush();
    // Drain any server response to keep the TCP stream clean
    while (client.available()) client.read();
    Serial.println("Sent: " + payload);
}

void updateOLED(float temp, float hum, float soil) {
    char buf[32];

    oled.clearBuffer();
    oled.setFont(u8g2_font_6x10_tf);

    // Header
    oled.drawStr(0, 10, "-- Agrilliant --");
    oled.drawHLine(0, 13, 128);

    // Temperature
    snprintf(buf, sizeof(buf), "Temp:  %.1f C", temp);
    oled.drawStr(0, 28, buf);

    // Humidity
    snprintf(buf, sizeof(buf), "Hum:   %.0f %%", hum);
    oled.drawStr(0, 40, buf);

    // Soil moisture
    if (!isnan(soil)) {
        snprintf(buf, sizeof(buf), "Soil:  %.0f %%", soil);
    } else {
        snprintf(buf, sizeof(buf), "Soil:  N/A");
    }
    oled.drawStr(0, 52, buf);

    // Connection status + fingerprint indicator
    if (fingerprintDetected) {
        oled.drawStr(0, 64, client.connected() ? "[Server OK] [FP]" : "[No Server] [FP]");
    } else {
        oled.drawStr(0, 64, client.connected() ? "[Server OK]" : "[No Server]");
    }

    oled.sendBuffer();
}

void checkFingerprint() {
    uint8_t p = finger.getImage();
    if (p != FINGERPRINT_OK) return;  // No finger on sensor

    p = finger.image2Tz();
    if (p != FINGERPRINT_OK) return;

    p = finger.fingerSearch();
    if (p != FINGERPRINT_OK) {
        Serial.println("Fingerprint not recognized");
        // Show on OLED briefly
        oled.clearBuffer();
        oled.setFont(u8g2_font_6x10_tf);
        oled.drawStr(10, 30, "Fingerprint");
        oled.drawStr(10, 45, "NOT RECOGNIZED");
        oled.sendBuffer();
        // Interruptible — exit early if a bridge command (LOCK/SCAN) arrives
        unsigned long t = millis();
        while (millis() - t < 1500) { if (Serial.available()) break; delay(50); }
        return;
    }

    int fpId = finger.fingerID;
    int confidence = finger.confidence;
    Serial.printf("Fingerprint matched! ID: %d (confidence: %d)\n", fpId, confidence);

    // Cache result — if the Java login button was just pressed, handleScan()
    // will pick this up instead of demanding a new scan on an empty sensor.
    cachedFpId   = fpId;
    cachedFpTime = millis();

    // Show on OLED
    char buf[32];
    oled.clearBuffer();
    oled.setFont(u8g2_font_6x10_tf);
    oled.drawStr(10, 25, "Fingerprint OK!");
    snprintf(buf, sizeof(buf), "ID: %d", fpId);
    oled.drawStr(10, 40, buf);
    snprintf(buf, sizeof(buf), "Conf: %d", confidence);
    oled.drawStr(10, 55, buf);
    oled.sendBuffer();

    // Ensure server connection before sending attendance event.
    // checkFingerprint() runs between sensor cycles, so TCP may have dropped.
    if (!client.connected()) {
        client.stop();
        client.connect(SERVER_IP, SERVER_PORT);
        if (client.connected()) client.setNoDelay(true);
    }
    if (client.connected()) {
        String payload = "FINGERPRINT:" + String(DEVICE_ID) + ",ID:" + String(fpId);
        client.println(payload);
        client.flush();
        Serial.println("Sent: " + payload);
    } else {
        Serial.println("Cannot send fingerprint — server unreachable");
    }

    // Interruptible debounce — if Java sends LOCK/SCAN, exit immediately so
    // the bridge command is processed without a 2s delay.
    unsigned long debounceStart = millis();
    while (millis() - debounceStart < 2000) {
        if (Serial.available()) break;
        delay(50);
    }
}

// ═══════════════ SERIAL BRIDGE COMMANDS ═══════════════
// The Java desktop app sends commands over USB serial.
// ESP32 relays to R307 and responds with results.
//
// Commands:
//   LOCK            → reserve R307 for desktop (prevents autonomous scan); respond: LOCKED
//   SCAN            → scan finger, respond: SCAN_OK:<id>,<confidence> or SCAN_FAIL
//   ENROLL:<slot>   → enroll finger at slot (two scans), respond: ENROLL_OK:<slot> or ENROLL_FAIL:<reason>
//   TEMPLATE_COUNT  → respond: TEMPLATE_COUNT:<n>
//   PING            → respond: PONG (connection test)

// Helper: show 1-3 lines centered on OLED
void showOLED(const char* line1, const char* line2 = "", const char* line3 = "") {
    oled.clearBuffer();
    oled.setFont(u8g2_font_6x10_tf);
    oled.drawStr(10, 20, line1);
    if (strlen(line2) > 0) oled.drawStr(10, 35, line2);
    if (strlen(line3) > 0) oled.drawStr(10, 50, line3);
    oled.sendBuffer();
}

void handleSerialCommand(String cmd) {
    if (!fingerprintDetected) {
        Serial.println("FP_ERROR:No sensor");
        showOLED("[Bridge]", "No R307 sensor!");
        return;
    }

    if (cmd == "PING") {
        Serial.println("PONG");
        showOLED("[Bridge]", "Desktop connected");
        return;
    }
    else if (cmd == "LOCK") {
        // Grab bridge lock immediately — blocks autonomous scan so user can
        // place their finger in response to the Java UI prompt, not the OLED.
        serialBridgeActive = true;
        serialBridgeLastActivity = millis();
        Serial.println("LOCKED");
        showOLED("[Bridge]", "Scan ready...");
        return;
    }
    else if (cmd == "RELEASE") {
        // Desktop app explicitly releases the R307 sensor lock
        serialBridgeActive = false;
        Serial.println("RELEASED");
        return;
    }

    // All commands below lock the R307 for the desktop app session.
    // The lock persists across retries until the desktop sends RELEASE
    // or SERIAL_BRIDGE_TIMEOUT elapses — this prevents background
    // checkFingerprint() from corrupting UART2 between retry attempts.
    serialBridgeActive = true;
    serialBridgeLastActivity = millis();

    if (cmd == "SCAN") {
        handleScan();
        // Release immediately — SCAN is a one-shot operation
        serialBridgeActive = false;
    }
    else if (cmd.startsWith("ENROLL:")) {
        int slot = cmd.substring(7).toInt();
        if (slot <= 0) {
            Serial.println("ENROLL_FAIL:Invalid slot");
            return;
        }
        handleEnroll(slot);
        // Do NOT release here — desktop may retry on failure.
        // Desktop must send RELEASE when enrollment session is done.
    }
    else if (cmd == "TEMPLATE_COUNT") {
        finger.getTemplateCount();
        Serial.println("TEMPLATE_COUNT:" + String(finger.templateCount));
        serialBridgeActive = false;
    }
    else if (cmd.startsWith("DELETE:")) {
        int slot = cmd.substring(7).toInt();
        uint8_t p = finger.deleteModel(slot);
        if (p == FINGERPRINT_OK) {
            Serial.println("DELETE_OK:" + String(slot));
            showOLED("[Delete]", "Removed ID:", String(slot).c_str());
        } else {
            Serial.println("DELETE_FAIL:" + String(slot));
            showOLED("[Delete]", "Failed!");
        }
        delay(1000);
        serialBridgeActive = false;
    }
}

void handleScan() {
    Serial.println("SCAN_WAITING");

    // Fast path: the autonomous checkFingerprint() ran just before LOCK arrived,
    // which is the common case when the user touches the sensor then clicks login.
    // Reuse that cached result instead of asking for a new scan on an empty sensor.
    if (cachedFpId > 0 && (millis() - cachedFpTime) < FP_CACHE_MS) {
        char buf[32];
        int id = cachedFpId;
        cachedFpId = -1;  // Consume so it isn't reused again
        snprintf(buf, sizeof(buf), "ID: %d", id);
        Serial.println("SCAN_OK:" + String(id) + ",100");
        showOLED("[Login Scan]", "Match found!", buf);
        delay(1000);
        return;
    }

    // Normal path: no cached result — prompt user to scan now.
    for (int attempt = 1; attempt <= 2; attempt++) {
        if (attempt == 1) showOLED("[Login Scan]", "Place finger...");
        else              showOLED("[Login Scan]", "Place again...");

        // Wait up to 8 seconds for a finger on each attempt
        unsigned long start = millis();
        uint8_t p = FINGERPRINT_NOFINGER;
        while (millis() - start < 8000) {
            p = finger.getImage();
            if (p == FINGERPRINT_OK) break;
            delay(100);
        }
        if (p != FINGERPRINT_OK) {
            Serial.println("SCAN_FAIL:No finger");
            showOLED("[Login Scan]", "No finger", "Timed out");
            delay(1000);
            return;
        }

        showOLED("[Login Scan]", "Processing...");
        p = finger.image2Tz();
        if (p != FINGERPRINT_OK) {
            if (attempt < 2) { delay(300); continue; }
            Serial.println("SCAN_FAIL:Image error");
            showOLED("[Login Scan]", "Image error");
            delay(1000);
            return;
        }

        p = finger.fingerSearch();
        if (p == FINGERPRINT_OK) {
            char buf[32];
            snprintf(buf, sizeof(buf), "ID: %d  Conf: %d", finger.fingerID, finger.confidence);
            Serial.println("SCAN_OK:" + String(finger.fingerID) + "," + String(finger.confidence));
            showOLED("[Login Scan]", "Match found!", buf);
            delay(1500);
            return;
        }

        if (attempt < 2) {
            // Lift and retry
            showOLED("[Login Scan]", "Retry...", "Lift finger");
            unsigned long lift = millis();
            while (millis() - lift < 2000 && finger.getImage() != FINGERPRINT_NOFINGER) delay(100);
        }
    }

    Serial.println("SCAN_FAIL:Not recognized");
    showOLED("[Login Scan]", "Not recognized!");
    delay(1000);
}

void handleEnroll(int slot) {
    uint8_t p;
    char buf[32];
    snprintf(buf, sizeof(buf), "Slot: %d", slot);

    // ── First scan ──
    Serial.println("ENROLL_PLACE1");
    showOLED("[Enroll]", "Place finger...", buf);
    unsigned long start = millis();
    while (millis() - start < 15000) {
        p = finger.getImage();
        if (p == FINGERPRINT_OK) break;
        delay(100);
    }
    if (p != FINGERPRINT_OK) {
        Serial.println("ENROLL_FAIL:No finger (scan 1)");
        showOLED("[Enroll]", "No finger!", "Timed out");
        delay(1500);
        return;
    }

    showOLED("[Enroll]", "Processing 1/2...");
    p = finger.image2Tz(1);
    if (p != FINGERPRINT_OK) {
        Serial.println("ENROLL_FAIL:Image error (scan 1)");
        showOLED("[Enroll]", "Image error (1)");
        delay(1500);
        return;
    }

    // ── Remove finger ──
    Serial.println("ENROLL_REMOVE");
    showOLED("[Enroll]", "Remove finger...");
    start = millis();
    while (millis() - start < 5000) {
        p = finger.getImage();
        if (p == FINGERPRINT_NOFINGER) break;
        delay(100);
    }

    // ── Second scan ──
    Serial.println("ENROLL_PLACE2");
    showOLED("[Enroll]", "Same finger again", buf);
    start = millis();
    while (millis() - start < 15000) {
        p = finger.getImage();
        if (p == FINGERPRINT_OK) break;
        delay(100);
    }
    if (p != FINGERPRINT_OK) {
        Serial.println("ENROLL_FAIL:No finger (scan 2)");
        showOLED("[Enroll]", "No finger!", "Timed out");
        delay(1500);
        return;
    }

    showOLED("[Enroll]", "Processing 2/2...");
    p = finger.image2Tz(2);
    if (p != FINGERPRINT_OK) {
        Serial.println("ENROLL_FAIL:Image error (scan 2)");
        showOLED("[Enroll]", "Image error (2)");
        delay(1500);
        return;
    }

    // ── Create model ──
    showOLED("[Enroll]", "Creating model...");
    p = finger.createModel();
    if (p != FINGERPRINT_OK) {
        Serial.println("ENROLL_FAIL:Prints did not match");
        showOLED("[Enroll]", "No match!", "Try again");
        delay(1500);
        return;
    }

    // ── Store ──
    p = finger.storeModel(slot);
    if (p != FINGERPRINT_OK) {
        Serial.println("ENROLL_FAIL:Store failed");
        showOLED("[Enroll]", "Store failed!");
        delay(1500);
        return;
    }

    snprintf(buf, sizeof(buf), "Saved as ID: %d", slot);
    Serial.println("ENROLL_OK:" + String(slot));
    showOLED("[Enroll]", "Success!", buf);
    delay(2000);
}
