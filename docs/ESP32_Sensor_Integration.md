# ESP32 DHT11 Sensor Integration — Technical Documentation

## 1. Overview

The Smart Farm Management System uses an **ESP32 microcontroller** with a **DHT11 sensor** to collect real-time temperature and humidity data from agricultural plots. The data is transmitted over **Wi-Fi via TCP** to a Java server running alongside the JavaFX dashboard, where it is displayed live and persisted to a MySQL database.

---

## 2. Hardware Setup

### Components

| Component       | Purpose                          |
|-----------------|----------------------------------|
| ESP32 Dev Module | Wi-Fi-enabled microcontroller   |
| DHT11 Sensor     | Measures temperature & humidity |
| Jumper Wires     | Connections between components  |

### Wiring Diagram

```
DHT11 Pin    →    ESP32 Pin
─────────────────────────────
VCC          →    3.3V
DATA         →    GPIO 4
GND          →    GND
```

> **Note:** The DHT11 DATA pin connects directly to GPIO 4. No pull-up resistor is needed as the Adafruit DHT library enables the internal pull-up.

---

## 3. Firmware (ESP32 Side)

**File:** `firmware/esp32_dht11/esp32_dht11.ino`

### Dependencies

Install via Arduino IDE → **Library Manager**:

- **DHT sensor library** by Adafruit
- **Adafruit Unified Sensor**

Board: **ESP32 Dev Module** (Tools → Board → ESP32 Arduino → ESP32 Dev Module)

### Configuration Constants

```cpp
#define WIFI_SSID     "your_ssid"        // Wi-Fi network name
#define WIFI_PASSWORD "your_password"     // Wi-Fi password
#define SERVER_IP     "192.168.x.x"      // Local IP of the PC running the Java app
#define SERVER_PORT   8080               // Must match FarmServer port
#define DHT_PIN       4                  // GPIO pin for DHT11 data line
#define DEVICE_ID     "plot1_sensor"     // Unique identifier for this sensor node
#define READ_INTERVAL 2000              // Milliseconds between readings
```

### Execution Flow

1. **`setup()`** — Runs once on boot:
   - Initializes serial monitor at 115200 baud
   - Initializes the DHT11 sensor
   - Connects to Wi-Fi and waits until connected
   - Prints the assigned local IP address

2. **`loop()`** — Runs repeatedly every 2 seconds:
   - Reads temperature (°C) and humidity (%) from the DHT11
   - Validates the reading (discards `NaN` values from failed reads)
   - Checks if the TCP connection to the server is alive; reconnects if not
   - Formats and sends the data as a single line string
   - Calls `client.flush()` to force immediate TCP transmission (no buffering delay)

### Data Format

Each reading is sent as a single newline-terminated string:

```
DEVICE:plot1_sensor,TEMP:27.50,HUM:63.20\n
```

| Field    | Description                        | Example        |
|----------|------------------------------------|----------------|
| DEVICE   | Unique sensor/node identifier      | `plot1_sensor`  |
| TEMP     | Temperature in Celsius (2 decimal) | `27.50`         |
| HUM      | Relative humidity percentage       | `63.20`         |

### DHT11 Sensor Limitations

- **Sampling rate:** Maximum 1 reading per second (firmware uses 2s for reliability)
- **Temperature range:** 0–50 °C, ±2 °C accuracy
- **Humidity range:** 20–80%, ±5% accuracy
- For higher precision, upgrade to a **DHT22** (same wiring and code, change `DHT_TYPE` to `DHT22`)

---

## 4. Server Side (Java)

### 4.1 TCP Server — `FarmServer.java`

```
Package: smartfarm.server
Port:    8080
```

- Started on a **daemon thread** from `Main.java` at application launch
- Uses `ServerSocket` to listen for incoming TCP connections
- Each new device connection spawns a `SensorHandler` thread via a cached thread pool
- Being a daemon thread, it shuts down automatically when the JavaFX application closes

### 4.2 Connection Handler — `SensorHandler.java`

```
Package: smartfarm.server
```

- Wraps the device socket in a `BufferedReader`
- Reads lines continuously in a `while` loop (blocking I/O)
- Parses each line using `parseReading()`:
  - Splits by `,` to get the three key-value pairs
  - Splits each pair by `:` to extract the value
  - Constructs a `SensorReading` object with the current timestamp
- Passes valid readings to `SensorService.processReading()`
- On disconnect, calls `LiveSensorData.removeDevice()` to update the active sensor count

### 4.3 Processing — `SensorService.java`

```
Package: smartfarm.service
```

Receives each `SensorReading` and does two things **in order**:

1. **Live UI update (always runs first):**
   - Calls `LiveSensorData.getInstance().update(reading)`
   - This ensures the dashboard updates even if the database is down

2. **Database persistence (may fail independently):**
   - Calls `SensorDAO.save(reading)` to INSERT into `sensor_readings` table
   - Triggers `AlertService.checkAndAlert()` for threshold-based alerts
   - Any DB/alert errors are caught and logged without affecting the live UI

### 4.4 Live Data Bridge — `LiveSensorData.java`

```
Package: smartfarm.service
```

This is the **critical bridge** between the TCP thread and the JavaFX UI thread.

**Problem:** JavaFX UI components can only be modified from the JavaFX Application Thread. Sensor data arrives on a background TCP thread.

**Solution:** `LiveSensorData` is a singleton that:
- Exposes JavaFX `Property` objects (`SimpleFloatProperty`, `SimpleStringProperty`, `SimpleIntegerProperty`)
- Wraps all property updates in `Platform.runLater()` to safely marshal them to the UI thread
- Tracks connected devices using a `ConcurrentHashMap`-backed set for thread safety

**Properties exposed:**

| Property           | Type                  | Description                     |
|--------------------|-----------------------|---------------------------------|
| `temperature`      | `SimpleFloatProperty` | Latest temperature reading      |
| `humidity`         | `SimpleFloatProperty` | Latest humidity reading         |
| `deviceId`         | `SimpleStringProperty`| ID of the device that sent data |
| `activeSensors`    | `SimpleIntegerProperty`| Count of connected sensor nodes|

---

## 5. Dashboard Integration (JavaFX)

### `DashboardController.java`

On initialization, calls `subscribeLiveSensor()` which attaches **change listeners** to the `LiveSensorData` properties:

- **Temperature listener:** Updates `lblTemperature` text and sets the status badge to "Normal", "High" (>35°C), or "Low" (<10°C)
- **Humidity listener:** Updates `lblHumidity` text and sets the status badge to "Normal", "High" (>80%), or "Low" (<30%)
- **Device ID listener:** Updates the plot label on sensor cards

### Sidebar Status Indicators

| Indicator     | Source                        | Logic                                      |
|---------------|-------------------------------|---------------------------------------------|
| System Status | Application lifecycle         | Always "Online" (green) if app is running   |
| Database      | `DBConnection.getInstance()`  | Checks if JDBC connection is open           |
| IoT Sensors   | `LiveSensorData.activeSensorsProperty()` | Shows count, green if >0, red if 0 |

---

## 6. Complete Data Flow

```
┌──────────────────────┐
│   ESP32 + DHT11      │
│   (reads every 2s)   │
└──────┬───────────────┘
       │  TCP packet: "DEVICE:plot1_sensor,TEMP:27.50,HUM:63.20\n"
       │  (Wi-Fi → LAN)
       ▼
┌──────────────────────┐
│   FarmServer         │
│   (port 8080)        │
│   accepts connection │
└──────┬───────────────┘
       │  spawns thread
       ▼
┌──────────────────────┐
│   SensorHandler      │
│   (reads lines,      │
│    parses fields)     │
└──────┬───────────────┘
       │  SensorReading object
       ▼
┌──────────────────────┐
│   SensorService      │
│   .processReading()  │
└──────┬──────┬────────┘
       │      │
       ▼      ▼
┌──────────┐ ┌──────────────┐
│ LiveSensor│ │  SensorDAO   │
│ Data      │ │  .save()     │
│ .update() │ │  (MySQL)     │
└──────┬────┘ └──────────────┘
       │  Platform.runLater()
       ▼
┌──────────────────────┐
│   DashboardController│
│   (JavaFX UI thread) │
│   updates labels     │
└──────────────────────┘
```

---

## 7. Setup Checklist

1. **Wire** the DHT11 to the ESP32 (VCC → 3.3V, DATA → GPIO 4, GND → GND)
2. **Install** Arduino libraries: DHT sensor library, Adafruit Unified Sensor
3. **Configure** the firmware: set `WIFI_SSID`, `WIFI_PASSWORD`, and `SERVER_IP`
4. **Find your PC's IP:** Run `ipconfig` (Windows) or `ifconfig` (Mac/Linux) — use the local network IP (e.g. `192.168.x.x`)
5. **Ensure** both ESP32 and PC are on the **same Wi-Fi network**
6. **Upload** the firmware to ESP32 via Arduino IDE
7. **Run** the Java application — FarmServer starts automatically on port 8080
8. **Verify** in Serial Monitor: you should see "Connected to server." and "Sent:" messages
9. **Verify** on dashboard: temperature and humidity cards update live, sidebar shows "1 Active"

---

## 8. Troubleshooting

| Symptom                              | Likely Cause                          | Fix                                         |
|--------------------------------------|---------------------------------------|---------------------------------------------|
| ESP32 prints "Connection failed!"    | Wrong SERVER_IP or firewall blocking  | Check IP with `ipconfig`, allow port 8080   |
| Server shows no "Device connected"   | ESP32 can't reach PC                  | Verify same Wi-Fi network, check firewall   |
| Dashboard stays at "--.- °C"         | DB error blocking LiveSensorData      | Already fixed — UI update runs before DB    |
| "DHT read failed, retrying..."       | Loose wiring or bad sensor            | Check connections, try a different GPIO pin |
| "Bad data format" in server console  | Corrupted TCP data                    | Check baud rate, reduce READ_INTERVAL       |
| IoT Sensors shows "0 Active"        | No device connected or app restarted  | Reconnect ESP32 or restart both             |
