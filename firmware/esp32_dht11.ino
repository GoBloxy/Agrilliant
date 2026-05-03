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
 * TODO: Update WIFI_SSID, WIFI_PASSWORD, and SERVER_IP before uploading.
 */

// TODO: Implement firmware (see project spec Chapter 5.3)
