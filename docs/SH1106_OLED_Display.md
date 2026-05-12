# SH1106 1.3" OLED Display — Setup Guide

## What is it?

A tiny 128x64 pixel screen that connects to the ESP32 over I2C (just 2 data wires). It shows the live sensor readings right on the device — no need to check Serial Monitor or the dashboard to see what's happening in the field.

---

## What it Shows

Every 2 seconds the screen refreshes with:

```
-- Agrilliant --
────────────────
Temp:  27.5 C
Hum:   63 %
Soil:  54 %
[Server OK]
```

On startup it also shows the WiFi IP address briefly, which is handy for debugging.

---

## Wiring to ESP32

4 wires:

```
SH1106 Pin     ESP32 Pin
─────────────────────────
VCC        →   3.3V
GND        →   GND
SDA        →   GPIO 21
SCL        →   GPIO 22
```

> GPIO 21 and 22 are the **default I2C pins** on the ESP32. No configuration needed.

### Full Wiring Diagram (all components)

```
                    ┌───────────────┐
                    │   ESP32       │
                    │               │
  DHT11 DATA ──────┤ GPIO 4        │
  FC-28 A0   ──────┤ GPIO 34       │
  OLED SDA   ──────┤ GPIO 21       │
  OLED SCL   ──────┤ GPIO 22       │
                    │               │
  All VCC ─────────┤ 3.3V          │
  All GND ─────────┤ GND           │
                    └───────────────┘
```

All three components (DHT11, FC-28, SH1106) share the same 3.3V and GND pins. Use a breadboard to split them.

---

## Arduino Library

Install **one** library via Arduino IDE → Library Manager:

- **U8g2** by oliver (search "U8g2")

That's it. The U8g2 library has built-in support for the SH1106 chip.

---

## How It Works in the Firmware

The OLED is initialized in `setup()` with a splash screen:

```cpp
U8G2_SH1106_128X64_NONAME_F_HW_I2C oled(U8G2_R0, U8X8_PIN_NONE);

void setup() {
    oled.begin();
    oled.clearBuffer();
    oled.setFont(u8g2_font_6x10_tf);
    oled.drawStr(10, 30, "Agrilliant");
    oled.drawStr(10, 45, "Starting...");
    oled.sendBuffer();
    // ... WiFi connect ...
}
```

Then `updateOLED()` is called every loop cycle after reading the sensors:

```cpp
void updateOLED(float temp, float hum, float soil) {
    char buf[32];
    oled.clearBuffer();
    oled.setFont(u8g2_font_6x10_tf);

    oled.drawStr(0, 10, "-- Agrilliant --");
    oled.drawHLine(0, 13, 128);

    snprintf(buf, sizeof(buf), "Temp:  %.1f C", temp);
    oled.drawStr(0, 28, buf);

    snprintf(buf, sizeof(buf), "Hum:   %.0f %%", hum);
    oled.drawStr(0, 40, buf);

    snprintf(buf, sizeof(buf), "Soil:  %.0f %%", soil);
    oled.drawStr(0, 52, buf);

    oled.drawStr(0, 64, client.connected() ? "[Server OK]" : "[No Server]");
    oled.sendBuffer();
}
```

The bottom line shows `[Server OK]` or `[No Server]` so you can tell at a glance if the ESP32 is connected to the Java app.

---

## Common Issues

| Problem | Cause | Fix |
|---------|-------|-----|
| Screen stays blank | Wrong I2C address | Most SH1106 modules use address `0x3C` (U8g2 default). Some use `0x3D` — check the back of your module. |
| Screen shows garbage | Wrong driver | Make sure you're using `U8G2_SH1106_128X64_NONAME_F_HW_I2C`, not the SSD1306 version. They look the same but the chip is different. |
| Very dim display | Low voltage | Use 3.3V directly from the ESP32 pin, not from a breadboard rail that might have voltage drop. |
| Nothing on screen, no errors | Bad wiring | Double check SDA → 21, SCL → 22. Swapping them is an easy mistake. |

### How to Check the I2C Address

If the screen stays blank, upload this quick I2C scanner sketch to find the address:

```cpp
#include <Wire.h>

void setup() {
    Serial.begin(115200);
    Wire.begin();
    for (byte addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0) {
            Serial.printf("Found device at 0x%02X\n", addr);
        }
    }
}
void loop() {}
```

---

## Quick Checklist

1. Wire: VCC → 3.3V, GND → GND, SDA → GPIO 21, SCL → GPIO 22
2. Install **U8g2** library in Arduino IDE
3. Upload the firmware
4. Screen should show "Agrilliant / Starting..." then the WiFi IP, then live readings

Done.
