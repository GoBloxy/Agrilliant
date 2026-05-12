# FC-28 Soil Moisture Sensor — Setup Guide

## What is the FC-28?

The FC-28 is a simple soil moisture sensor. It has two metal probes that you stick into the soil. It measures how well the soil conducts electricity — wet soil conducts better, dry soil conducts worse. The sensor gives you an analog voltage that represents how wet or dry the soil is.

---

## What's in the Box

The FC-28 comes with **two parts**:

1. **The probe** — two metal forks that go into the soil
2. **The module board** — a small PCB with a comparator chip, a potentiometer, and pins

The module board has **4 pins**:

| Pin | What it does |
|-----|-------------|
| VCC | Power (3.3V from ESP32) |
| GND | Ground |
| A0  | Analog output (the one we use) |
| D0  | Digital output (HIGH/LOW only — we don't use this) |

---

## Wiring to ESP32

Only 3 wires needed:

```
FC-28 Module     ESP32
────────────────────────
VCC          →   3.3V
GND          →   GND
A0           →   GPIO 34
```

**That's it.** No resistors, no capacitors, no extra components.

> **Why GPIO 34?** It's an ADC (analog-to-digital) input pin on the ESP32. Pins 34, 35, 36, 39 are input-only and work well for analog sensors.

### Quick Wiring Diagram

```
                    ┌───────────────┐
                    │   ESP32       │
                    │               │
  FC-28 VCC ───────┤ 3.3V          │
  FC-28 GND ───────┤ GND           │
  FC-28 A0  ───────┤ GPIO 34       │
                    │               │
  DHT11 DATA ──────┤ GPIO 4        │
  DHT11 VCC  ──────┤ 3.3V          │
  DHT11 GND  ──────┤ GND           │
                    └───────────────┘
```

Both sensors share the same 3.3V and GND — no problem.

---

## How the Sensor Works

1. You stick the probe into the soil
2. The module reads the resistance between the two forks
3. It outputs an **analog voltage** on the A0 pin
4. The ESP32 reads this voltage using `analogRead()` and gets a number between **0 and 4095** (12-bit ADC)

| Condition | ADC Value | Meaning |
|-----------|-----------|---------|
| Probe in water | ~1200 | Very wet |
| Probe in moist soil | ~2000–3000 | Normal |
| Probe in dry soil | ~3500–4000 | Dry |
| Probe in air | ~4095 | Completely dry |

> **Note:** The raw numbers are **inverted** — lower number = wetter. The firmware converts this to a percentage where **100% = wet** and **0% = dry**.

---

## Firmware

The sensor is read in `firmware/esp32_dht11/esp32_dht11.ino` alongside the DHT11.

### Key lines:

```cpp
#define SOIL_PIN  34        // GPIO pin for FC-28 A0
#define SOIL_DRY  4095      // ADC reading in dry air
#define SOIL_WET  1200      // ADC reading in water
```

### Reading and converting:

```cpp
int rawSoil = analogRead(SOIL_PIN);
float soilPercent = map(constrain(rawSoil, SOIL_WET, SOIL_DRY), SOIL_DRY, SOIL_WET, 0, 100);
```

This converts the raw ADC value to a 0–100% scale.

### Data sent to server:

```
DEVICE:plot1_sensor,TEMP:27.50,HUM:63.20,SOIL:54.30
```

The `SOIL` field is the new addition — it carries the moisture percentage.

---

## Calibration

The default values (`SOIL_DRY = 4095`, `SOIL_WET = 1200`) are good starting points, but you should calibrate for your sensor:

1. **Dry calibration:** Hold the probe in air, open Serial Monitor, note the ADC value → set as `SOIL_DRY`
2. **Wet calibration:** Dip the probe in a glass of water (just the forks, not the wires!), note the ADC value → set as `SOIL_WET`

---

## What Happens on the Java Side

The data flows through the same pipeline as temperature and humidity:

```
ESP32 sends "...,SOIL:54.30"
        ↓
SensorHandler.parseLine()  — extracts the SOIL value
        ↓
SensorReading object       — now has a soilMoisture field
        ↓
LiveSensorData.update()    — pushes to dashboard UI
SensorDAO.save()           — saves to MySQL (soil_moisture column)
AlertService.checkAndAlert() — checks thresholds
```

### Alert Thresholds

| Condition | Threshold | Severity | Action |
|-----------|-----------|----------|--------|
| Critically dry | ≤ 20% | CRITICAL | Alert + auto-creates irrigation task |
| Low moisture | ≤ 30% | WARNING | Alert only |
| Too wet | ≥ 85% | WARNING | Alert only |

These are configurable in `src/main/java/smartfarm/util/ThresholdConfig.java`.

---

## Database

The `sensor_readings` table has a `soil_moisture` column:

```sql
CREATE TABLE sensor_readings (
    reading_id    INT AUTO_INCREMENT PRIMARY KEY,
    device_id     INT NOT NULL,
    temperature   FLOAT,
    humidity      FLOAT,
    soil_moisture FLOAT,        -- ← FC-28 data goes here
    timestamp     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
);
```

If your database already exists, run this to add the column:

```sql
ALTER TABLE sensor_readings ADD COLUMN soil_moisture FLOAT AFTER humidity;
```

---

## Sensor Limitations

- **Corrosion:** The metal probes corrode over time when powered constantly. For a long-term setup, only power the sensor when reading (use a GPIO pin for VCC instead of 3.3V).
- **Accuracy:** The FC-28 is not lab-grade. It's good enough for "dry / ok / wet" but not for precise measurements.
- **Range:** Works best in the top 5–10 cm of soil.

---

## Quick Checklist

1. Wire FC-28: VCC → 3.3V, GND → GND, A0 → GPIO 34
2. Upload the updated firmware
3. Open Serial Monitor — you should see `Soil: XX.X %` in the output
4. If the database already exists, run the `ALTER TABLE` command above
5. Run the Java app — soil moisture shows on the dashboard

Done.
