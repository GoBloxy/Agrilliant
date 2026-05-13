# R307 Fingerprint Sensor Integration

## Overview

The R307 optical fingerprint sensor is used for **worker attendance tracking**. Workers scan their finger on the sensor connected to the ESP32. The ESP32 matches the fingerprint locally and sends the result to the Java backend via TCP, which records check-in/check-out times in the database.

## Hardware

### R307 Specifications

| Spec               | Value                     |
|---------------------|---------------------------|
| Sensor Type         | Optical                   |
| Interface           | UART (TTL)                |
| Baud Rate           | 57600 bps (default)       |
| Operating Voltage   | 3.6V – 6.0V (5V typical) |
| Storage Capacity    | 1000 fingerprints         |
| False Accept Rate   | < 0.001%                  |
| False Reject Rate   | < 1.0%                    |
| Search Time         | < 1 second                |

### Wiring (ESP32 + R307)

| R307 Wire (Color) | R307 Pin | ESP32 Pin       | Notes                  |
|--------------------|----------|-----------------|------------------------|
| Red                | VCC      | 5V (VIN)        | Must use 5V, not 3.3V  |
| Black              | GND      | GND             | Common ground          |
| Yellow             | TX       | GPIO 16 (RX2)   | UART2 RX               |
| Green              | RX       | GPIO 17 (TX2)   | UART2 TX               |
| Blue               | Wakeup   | Not connected   | Optional touch detect  |
| White              | 3.3V     | Not connected   | Optional touch power   |

> **Note:** Blue (wakeup) and white (3.3V touch) are optional — only needed if you want touch-detect to wake the ESP32 from deep sleep.

### Full System Wiring (All Sensors)

```
ESP32 Dev Module
├── GPIO 4   ← DHT11 DATA
├── GPIO 34  ← FC-28 A0 (Soil Moisture, analog)
├── GPIO 21  ← SH1106 OLED SDA (I2C)
├── GPIO 22  ← SH1106 OLED SCL (I2C)
├── GPIO 16  ← R307 TX (UART2 RX)
├── GPIO 17  → R307 RX (UART2 TX)
├── 3.3V     → DHT11 VCC, FC-28 VCC, SH1106 VCC
├── 5V (VIN) → R307 VCC
└── GND      → All sensor GND (common)
```

No pin conflicts — each sensor uses a different peripheral:
- **DHT11**: single-wire digital (GPIO 4)
- **FC-28**: ADC input (GPIO 34)
- **SH1106**: I2C bus (GPIO 21/22)
- **R307**: UART2 (GPIO 16/17)

## Software

### Arduino Libraries Required

Install via Arduino Library Manager:
- **Adafruit Fingerprint Sensor Library** (by Adafruit)

### Firmware Flow

```
setup()
  ├── Initialize UART2 at 57600 baud (GPIO 16/17)
  ├── Call finger.verifyPassword()
  │   ├── Success → fingerprintDetected = true
  │   └── Failure → sensor skipped, app runs without attendance
  └── Print enrolled fingerprint count

loop()
  ├── Every 500ms → checkFingerprint()
  │   ├── finger.getImage()     → detect finger on sensor
  │   ├── finger.image2Tz()     → convert image to template
  │   ├── finger.fingerSearch() → match against enrolled prints
  │   ├── Match found:
  │   │   ├── Display "Fingerprint OK! ID: X" on OLED
  │   │   ├── Send "FINGERPRINT:<device_id>,ID:<fp_id>" via TCP
  │   │   └── 2-second debounce delay
  │   └── No match:
  │       └── Display "NOT RECOGNIZED" on OLED for 1.5s
  │
  └── Every 2000ms → read sensors + send DEVICE:... payload (unchanged)
```

### TCP Message Format

```
FINGERPRINT:plot1_sensor,ID:3
```

| Field          | Description                                    |
|----------------|------------------------------------------------|
| `FINGERPRINT`  | Message type identifier                        |
| `plot1_sensor` | Device code (same as sensor node ID)           |
| `ID`           | Fingerprint template ID stored in R307 memory  |

### Java Backend Flow

```
SensorHandler.run()
  └── line.startsWith("FINGERPRINT:") ?
      ├── Yes → handleFingerprint(line)
      │         ├── Parse device code + fingerprint ID
      │         └── AttendanceService.handleFingerprintScan(fpId, deviceCode)
      │             ├── Look up worker by fingerprint_id in worker table
      │             ├── Check for open session (check_out IS NULL)
      │             │   ├── Open session exists → CHECK OUT (set check_out timestamp)
      │             │   └── No open session     → CHECK IN  (insert new attendance row)
      │             └── Return "CHECK_IN" / "CHECK_OUT" / "UNKNOWN" / "ERROR"
      └── No  → parseLine() → sensor data pipeline (unchanged)
```

## Database

### Worker Table Change

```sql
ALTER TABLE worker ADD COLUMN fingerprint_id INT NULL AFTER on_duty;
```

Each worker can have one enrolled fingerprint ID. Set this after enrolling the worker's fingerprint on the R307.

### Attendance Table

```sql
CREATE TABLE attendance (
    attendance_id INT AUTO_INCREMENT PRIMARY KEY,
    worker_id     INT          NOT NULL,
    check_in      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    check_out     TIMESTAMP    NULL,
    device_code   VARCHAR(50),
    FOREIGN KEY (worker_id) REFERENCES worker(worker_id) ON DELETE CASCADE
);
```

### Toggle Logic

The system uses **toggle-based attendance**:
1. **First scan** → No open session found → **CHECK IN** (new row, `check_out = NULL`)
2. **Second scan** → Open session found → **CHECK OUT** (update `check_out` to now)
3. **Third scan** → No open session → **CHECK IN** again (new row)

## Fingerprint Enrollment

Enrollment must be done **separately** using the Adafruit example sketch before deploying the main firmware.

### Steps

1. Open Arduino IDE
2. Go to **File → Examples → Adafruit Fingerprint Sensor Library → enroll**
3. Upload to ESP32
4. Open Serial Monitor (57600 baud)
5. Follow prompts: place finger twice to enroll
6. Note the **template ID** assigned (e.g., `1`, `2`, `3`...)
7. In the database, update the worker:
   ```sql
   UPDATE worker SET fingerprint_id = 1 WHERE worker_id = 5;
   ```
8. Re-upload the main `esp32_dht11.ino` firmware

### Important Notes

- The R307 stores fingerprints **locally in its own flash memory** (up to 1000)
- Template IDs persist across power cycles
- The ESP32 only sends the matched ID — it does **not** send raw fingerprint data
- If the R307 is not connected, the system auto-detects this at boot and runs without attendance (no errors)

## Files Modified/Created

| File | Change |
|------|--------|
| `firmware/esp32_dht11/esp32_dht11.ino` | Added R307 init, `checkFingerprint()`, OLED indicators |
| `sql/schema.sql` | Added `fingerprint_id` to worker, new `attendance` table |
| `src/.../model/Attendance.java` | New model class |
| `src/.../model/Worker.java` | Added `fingerprintId` field |
| `src/.../dao/AttendanceDAO.java` | New DAO for attendance CRUD |
| `src/.../dao/WorkerDAO.java` | Updated save/update/mapRow for fingerprint_id |
| `src/.../service/AttendanceService.java` | New service with toggle check-in/out logic |
| `src/.../server/SensorHandler.java` | Routes `FINGERPRINT:` messages to AttendanceService |
| `src/.../ui/AttendancePage.java` | New JavaFX page with attendance table |
| `src/.../ui/DashboardController.java` | Added `btnAttendance` + `onNavAttendance()` |
| `src/.../resources/fxml/dashboard.fxml` | Added Attendance sidebar button |
