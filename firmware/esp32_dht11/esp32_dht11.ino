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
#define FP_CHECK_INTERVAL 500          // Milliseconds between fingerprint scan checks

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

// SH1106 OLED (128x64, I2C on default SDA=21, SCL=22)
U8G2_SH1106_128X64_NONAME_F_HW_I2C oled(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);

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

    // Initialize OLED
    oled.begin();
    oled.clearBuffer();
    oled.setFont(u8g2_font_6x10_tf);
    oled.drawStr(10, 30, "Agrilliant");
    oled.drawStr(10, 45, "Starting...");
    oled.sendBuffer();

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

    // Check fingerprint scanner (non-blocking, every 500ms)
    if (fingerprintDetected && millis() - lastFpCheck >= FP_CHECK_INTERVAL) {
        lastFpCheck = millis();
        checkFingerprint();
    }

    if (millis() - lastRead < READ_INTERVAL) return;
    lastRead = millis();

    float temp = dht.readTemperature();
    float hum  = dht.readHumidity();

    // Validate DHT reading
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

    // Update OLED display
    updateOLED(temp, hum, soilPercent);

    // Connect to server if not connected
    if (!client.connected()) {
        Serial.printf("Connecting to server %s:%d...\n", SERVER_IP, SERVER_PORT);
        if (!client.connect(SERVER_IP, SERVER_PORT)) {
            Serial.println("Connection failed!");
            return;
        }
        Serial.println("Connected to server.");
    }

    // Send reading in expected format
    String payload = "DEVICE:" + String(DEVICE_ID) +
                     ",TEMP:" + String(temp, 2) +
                     ",HUM:" + String(hum, 2);
    if (soilSensorDetected) {
        payload += ",SOIL:" + String(soilPercent, 2);
    }
    client.println(payload);
    client.flush();  // Force immediate send (no TCP buffering)
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
        delay(1500);
        return;
    }

    int fpId = finger.fingerID;
    int confidence = finger.confidence;
    Serial.printf("Fingerprint matched! ID: %d (confidence: %d)\n", fpId, confidence);

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

    // Send to server
    if (client.connected()) {
        String payload = "FINGERPRINT:" + String(DEVICE_ID) + ",ID:" + String(fpId);
        client.println(payload);
        client.flush();
        Serial.println("Sent: " + payload);
    } else {
        Serial.println("Cannot send fingerprint — not connected to server");
    }

    delay(2000);  // Debounce — prevent multiple reads of same finger
}

// ═══════════════ SERIAL BRIDGE COMMANDS ═══════════════
// The Java desktop app sends commands over USB serial.
// ESP32 relays to R307 and responds with results.
//
// Commands:
//   SCAN            → scan finger, respond: SCAN_OK:<id>,<confidence> or SCAN_FAIL
//   ENROLL:<slot>   → enroll finger at slot (two scans), respond: ENROLL_OK:<slot> or ENROLL_FAIL:<reason>
//   TEMPLATE_COUNT  → respond: TEMPLATE_COUNT:<n>
//   PING            → respond: PONG (connection test)

void handleSerialCommand(String cmd) {
    if (!fingerprintDetected) {
        Serial.println("FP_ERROR:No sensor");
        return;
    }

    if (cmd == "PING") {
        Serial.println("PONG");
    }
    else if (cmd == "SCAN") {
        handleScan();
    }
    else if (cmd.startsWith("ENROLL:")) {
        int slot = cmd.substring(7).toInt();
        if (slot <= 0) {
            Serial.println("ENROLL_FAIL:Invalid slot");
            return;
        }
        handleEnroll(slot);
    }
    else if (cmd == "TEMPLATE_COUNT") {
        finger.getTemplateCount();
        Serial.println("TEMPLATE_COUNT:" + String(finger.templateCount));
    }
}

void handleScan() {
    Serial.println("SCAN_WAITING");

    // Wait up to 10 seconds for a finger
    unsigned long start = millis();
    uint8_t p = FINGERPRINT_NOFINGER;
    while (millis() - start < 10000) {
        p = finger.getImage();
        if (p == FINGERPRINT_OK) break;
        delay(100);
    }
    if (p != FINGERPRINT_OK) { Serial.println("SCAN_FAIL:No finger"); return; }

    p = finger.image2Tz();
    if (p != FINGERPRINT_OK) { Serial.println("SCAN_FAIL:Image error"); return; }

    p = finger.fingerSearch();
    if (p != FINGERPRINT_OK) { Serial.println("SCAN_FAIL:Not recognized"); return; }

    Serial.println("SCAN_OK:" + String(finger.fingerID) + "," + String(finger.confidence));
}

void handleEnroll(int slot) {
    uint8_t p;

    // ── First scan ──
    Serial.println("ENROLL_PLACE1");
    unsigned long start = millis();
    while (millis() - start < 15000) {
        p = finger.getImage();
        if (p == FINGERPRINT_OK) break;
        delay(100);
    }
    if (p != FINGERPRINT_OK) { Serial.println("ENROLL_FAIL:No finger (scan 1)"); return; }

    p = finger.image2Tz(1);
    if (p != FINGERPRINT_OK) { Serial.println("ENROLL_FAIL:Image error (scan 1)"); return; }

    // ── Remove finger ──
    Serial.println("ENROLL_REMOVE");
    start = millis();
    while (millis() - start < 5000) {
        p = finger.getImage();
        if (p == FINGERPRINT_NOFINGER) break;
        delay(100);
    }

    // ── Second scan ──
    Serial.println("ENROLL_PLACE2");
    start = millis();
    while (millis() - start < 15000) {
        p = finger.getImage();
        if (p == FINGERPRINT_OK) break;
        delay(100);
    }
    if (p != FINGERPRINT_OK) { Serial.println("ENROLL_FAIL:No finger (scan 2)"); return; }

    p = finger.image2Tz(2);
    if (p != FINGERPRINT_OK) { Serial.println("ENROLL_FAIL:Image error (scan 2)"); return; }

    // ── Create model ──
    p = finger.createModel();
    if (p != FINGERPRINT_OK) { Serial.println("ENROLL_FAIL:Prints did not match"); return; }

    // ── Store ──
    p = finger.storeModel(slot);
    if (p != FINGERPRINT_OK) { Serial.println("ENROLL_FAIL:Store failed"); return; }

    Serial.println("ENROLL_OK:" + String(slot));
}
