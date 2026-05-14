/*
 * Fingerprint Reset Utility — Agrilliant
 *
 * Erases ALL fingerprint templates stored on the R307 sensor.
 * Upload this sketch, open Serial Monitor (115200 baud), and follow the prompt.
 * After reset is confirmed, re-upload esp32_dht11.ino to restore normal operation.
 *
 * Wiring (same as main firmware):
 *   R307 VCC  -> ESP32 5V (VIN)
 *   R307 GND  -> ESP32 GND
 *   R307 TX   -> ESP32 GPIO 16 (UART2 RX)
 *   R307 RX   -> ESP32 GPIO 17 (UART2 TX)
 *
 *   SH1106 SDA -> ESP32 GPIO 21
 *   SH1106 SCL -> ESP32 GPIO 22
 *
 * Libraries required (same as main firmware):
 *   - Adafruit Fingerprint Sensor Library
 *   - U8g2 by oliver
 */

#include <HardwareSerial.h>
#include <Adafruit_Fingerprint.h>
#include <U8g2lib.h>
#include <Wire.h>

#define FP_RX_PIN  16
#define FP_TX_PIN  17

HardwareSerial fpSerial(2);
Adafruit_Fingerprint finger = Adafruit_Fingerprint(&fpSerial);

U8G2_SH1106_128X64_NONAME_F_HW_I2C oled(U8G2_R0, U8X8_PIN_NONE);

void showOLED(const char* line1, const char* line2 = "", const char* line3 = "") {
    oled.clearBuffer();
    oled.setFont(u8g2_font_6x10_tf);
    oled.drawStr(10, 20, line1);
    if (strlen(line2) > 0) oled.drawStr(10, 35, line2);
    if (strlen(line3) > 0) oled.drawStr(10, 50, line3);
    oled.sendBuffer();
}

void setup() {
    Serial.begin(115200);
    oled.begin();
    showOLED("[FP Reset]", "Initializing...");

    fpSerial.begin(57600, SERIAL_8N1, FP_RX_PIN, FP_TX_PIN);
    finger.begin(57600);

    if (!finger.verifyPassword()) {
        Serial.println("ERROR: R307 not found. Check wiring and power (5V required).");
        showOLED("[FP Reset]", "R307 not found!", "Check wiring");
        while (true) delay(1000);
    }

    finger.getTemplateCount();
    Serial.println("R307 found.");
    Serial.print("Templates stored: ");
    Serial.println(finger.templateCount);
    Serial.println();
    Serial.println("Send 'Y' to erase ALL fingerprints, or any other key to cancel.");

    showOLED("[FP Reset]", "R307 ready.", "Send Y to erase");
}

void loop() {
    if (!Serial.available()) return;

    char c = Serial.read();
    if (c == 'Y' || c == 'y') {
        Serial.println("Erasing all fingerprint templates...");
        showOLED("[FP Reset]", "Erasing...", "Please wait");

        uint8_t result = finger.emptyDatabase();
        if (result == FINGERPRINT_OK) {
            Serial.println("SUCCESS: All fingerprint templates erased.");
            Serial.println("You can now re-upload esp32_dht11.ino.");
            showOLED("[FP Reset]", "Done!", "All cleared.");
        } else {
            Serial.print("FAILED: error code ");
            Serial.println(result);
            showOLED("[FP Reset]", "FAILED!", "Check sensor");
        }
    } else {
        Serial.println("Cancelled. No changes made.");
        showOLED("[FP Reset]", "Cancelled.");
    }
}
