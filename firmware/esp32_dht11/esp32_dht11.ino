/*
 * ESP32 + DHT11 + FC-28 + SH1106 OLED Firmware
 * Reads temperature, humidity, and soil moisture every 2 seconds,
 * displays them on a 1.3" OLED screen, and sends to the Java server via TCP.
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
 * Data format sent: DEVICE:<id>,TEMP:<value>,HUM:<value>,SOIL:<value>
 *
 * Libraries required (install via Arduino Library Manager):
 *   - DHT sensor library by Adafruit
 *   - Adafruit Unified Sensor
 *   - U8g2 by oliver
 *
 * Board: ESP32 Dev Module (select in Arduino IDE -> Tools -> Board)
 *
 * TODO: Update WIFI_SSID, WIFI_PASSWORD, and SERVER_IP before uploading.
 */

#include <WiFi.h>
#include <DHT.h>
#include <U8g2lib.h>
#include <Wire.h>

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

// FC-28 calibration (adjust after testing with your sensor)
#define SOIL_DRY      4095              // ADC value when sensor is in dry air
#define SOIL_WET      1200              // ADC value when sensor is in water
#define SOIL_NO_SENSOR_THRESHOLD 4000   // Above this = floating pin (no sensor)
// ─────────────────────────────────────────────────────────────────

DHT dht(DHT_PIN, DHT_TYPE);
WiFiClient client;
unsigned long lastRead = 0;
bool soilSensorDetected = false;        // Auto-detected at startup

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

    // Connection status
    oled.drawStr(0, 64, client.connected() ? "[Server OK]" : "[No Server]");

    oled.sendBuffer();
}
