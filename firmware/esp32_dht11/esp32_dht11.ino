/*
 * ESP32 + DHT11 Firmware
 * Reads temperature & humidity every 5 seconds and sends to the Java server via TCP.
 *
 * Wiring:
 *   DHT11 VCC  -> ESP32 3.3V
 *   DHT11 DATA -> ESP32 GPIO 4
 *   DHT11 GND  -> ESP32 GND
 *
 * Data format sent: DEVICE:<id>,TEMP:<value>,HUM:<value>
 *
 * Libraries required (install via Arduino Library Manager):
 *   - DHT sensor library by Adafruit
 *   - Adafruit Unified Sensor
 *
 * Board: ESP32 Dev Module (select in Arduino IDE -> Tools -> Board)
 *
 * TODO: Update WIFI_SSID, WIFI_PASSWORD, and SERVER_IP before uploading.
 */

#include <WiFi.h>
#include <DHT.h>

// ─── Configuration ───────────────────────────────────────────────
#define WIFI_SSID     "314Pi"
#define WIFI_PASSWORD "TheGreatPi123@"

#define SERVER_IP     "192.168.8.141"   // IP of the PC running the Java app
#define SERVER_PORT   8080

#define DHT_PIN       4                 // GPIO pin connected to DHT11 DATA
#define DHT_TYPE      DHT11
#define DEVICE_ID     "plot1_sensor"    // Unique ID for this sensor node

#define READ_INTERVAL 2000              // Milliseconds between readings (DHT11 min ~1s)
// ─────────────────────────────────────────────────────────────────

DHT dht(DHT_PIN, DHT_TYPE);
WiFiClient client;
unsigned long lastRead = 0;

void setup() {
    Serial.begin(115200);
    dht.begin();

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
}

void loop() {
    if (millis() - lastRead < READ_INTERVAL) return;
    lastRead = millis();

    float temp = dht.readTemperature();
    float hum  = dht.readHumidity();

    // Validate reading
    if (isnan(temp) || isnan(hum)) {
        Serial.println("DHT read failed, retrying...");
        return;
    }

    Serial.printf("Temp: %.2f C  Hum: %.2f %%\n", temp, hum);

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
    client.println(payload);
    client.flush();  // Force immediate send (no TCP buffering)
    Serial.println("Sent: " + payload);
}
