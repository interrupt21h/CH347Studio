# CH347 Studio v0.3.0 — Android USB/I²C/SPI Device Workbench

A native Java Android application for WCH CH347T-compatible USB Host/OTG adapters (including the Waveshare USB to UART/I²C/SPI/JTAG box). Offline, no root, no account, no Internet permission. The current transport is CH347 vendor BULK (M1 recommended), with CDC UART supported where exposed. M2 HID, CH343 UART and JTAG operations remain unimplemented.

## 0.3.0 — user-photographed peripherals

- TCA9548A / PCA9548A eight-way I²C bus mux: both NOYITO and GODIYMODULES breakouts, `0x70..0x77`, select a downstream channel and scan its dedicated 128-cell address matrix. Mux mask restored after every scan or routed transaction, including errors. Readback exercise requires deliberate confirmation.
- MCP23017 16-bit I²C GPIO expander: read directions, inputs, pullups, latches and interrupts (no automatic output writes).
- MCP23S17 16-bit SPI GPIO expander: separate read-only driver with 0–7 hardware address selection; `IOCON.HAEN`/`BANK` caveats.
- Aideepen nRF24L01+PA+LNA: SPI read-only STATUS/CONFIG/FIFO/features/pipe-address checks. CE is not controlled, RF transmission is not started. Stable 3.3 V power is critical.
- ShengYang GC9A01 1.28-inch round TFT: 240×240 RGB565 local preview with color bars, grayscale, checker/crosshair and annotated SPI wiring. Physical update requires D/C + reset + backlight GPIO; it remains disabled.
- LAFVIN 2.13-inch e-paper: existing local frame previews; confirm pictured HAT's 3/4-wire SPI setting and version before pursuing GPIO-controlled refresh.
- Higher confidence discovery: I²C ACK is not a chip identity, and SPI has no generic device scan. Profiles are candidate matches only.

See [HARDWARE.md](HARDWARE.md) for schematics guidance, wiring limitations, safe tests and reference datasheets.

## v0.2.0 changes

- Visual **128-address I²C bus map**, showing reserved / unknown / NACK / ACK / error separately; tap an address to select it. Retains results while switching tabs. Full scan probes `0x08`–`0x77` and does *not* perform device writes beyond the CH347 bus address probe.
- Device profiles for **SSD1306, SH1106, PCF8574/HD44780 LCD, Si4703, Si4713, RDA5807M**, plus BME280/BMP280, SHT31, DS3231, ADS1115, BH1750 and 24xx EEPROM devices.
- A **30-script diagnostics library**, including SSD1306 / SH1106 patterns and clearing, PCF8574 character-LCD test, Si4703 16-register / RSSI / RDS snapshots, RDA5807M signal / RDS / optional ID, Si4713 CTS, POWER_UP (no frequency/power command), revision, TX status and audio status, and environmental sensor ID / sampling scripts.
- SPI test scripts for **JEDEC ID, flash status, SFDP and isolated MOSI↔MISO loopback**.
- **LAFVIN 2.13-inch e-paper HAT V4**: local preview for 122×250 1-bit frames; generate four 4,000-byte test frames and copy the packed bitmap to clipboard. Includes verified command-plan notes and an explicit hardware-control warning.
- Larger touch controls, distinct per-category screens (USB / Bus / Displays / Radios / Sensors / SPI / UART / JTAG), retained horizontal tab position, USB trace, live scan status and proper Android 15+ system bar insets.

### Important distinction: candidate vs. identification

An I²C address ACK is not proof of identity. **Si4703 and RDA5807M can both use `0x10`; SSD1306 and SH1106 often share `0x3C` or `0x3D`**. The app shows candidates, labels actual chip-ID checks separately, and never tries invasive identification by default. **Si4713 defaults to `0x63`, with `0x11` alternate**. Its `GET_REV` and other command tests require a valid powered-up interface and may require RESET wiring. For ambiguous addresses, choose the known hardware before invoking a script.

### Safety and limitations

- **Disconnect USB power before moving the DIP mode or voltage selector.** Match the I/O voltage to your external module; avoid powering a 3.3 V bare sensor from 5 V signals.
- OLED and PCF8574 LCD tests intentionally change device state. They require explicit confirmation; common LCD pin mapping is assumed and may not match your backpack.
- Si4713 POWER_UP does not execute TX_TUNE_FREQ or TX_TUNE_POWER; **this app does not intentionally activate RF transmission**. TX status and audio status are interrogations, not RF proof.
- Si4703 and RDA5807M FM status and RDS scripts are read-only. Some modules require explicit reset / SEN selection before they answer over I²C; not seeing an ACK does not prove the chip is absent.
- SPI peripherals do not have enumerable addresses. Flash JEDEC ID is only meaningful for suitable flash chips, **not e-paper**. SPI probing is never automatic.
- The LAFVIN e-paper requires **D/C, RESET and BUSY** control in addition to CLK, MOSI and CS. The CH347 M1 SPI channel alone cannot execute a reliable full-panel refresh without validated external GPIO wiring and transport support. Frame previews are local-only and are not transmitted to the panel. The V4-specific control sequence is a reference, not a hardware-tested renderer.
- USB transports, tests and validation code are based on published device documentation and packet framing, **not physical-device-tested** in this release. Please report USB descriptors and trace if hardware differs.

## Build and tests

Android JDK 17 + SDK 35, Gradle 8.11.1, Android Gradle Plugin 8.9.2:

```bash
bash scripts/check-protocol.sh
# In Android Studio or GitHub Actions:
gradle :app:assembleDebug :app:lintDebug
```

The CI workflow runs protocol and device diagnostics unit tests, builds and lints the Android APK, checks its signature and publishes uniquely named APK artifacts with SHA-256 checksums. No Android SDK is needed for the pure-Java tests.

## Repository layout

- `app/src/main/java/io/interrupt21h/ch347studio/MainActivity.java`: screen navigation, bus scan, devices/scripts and user confirmation.
- `.../ui/BusMatrixView.java`: tactile bus matrix; `.../ui/EpaperPreviewView.java`: offline frame preview.
- `.../devices/DeviceCatalog.java`: address candidates, explanatory labels.
- `.../devices/BusMap.java`: thread-safe scan state.
- `.../devices/DeviceScripts.java`: script registry and functional test routines.
- `.../devices/EpaperFrames.java`: packed 1-bit framebuffer generation.
- `.../usb/Ch347Usb.java`: native M1/M3 USB BULK transactions.
- `.../protocol/Ch347Packets.java`: CH347 packet framing.
- `tests/ProtocolSelfTest.java` and `tests/DeviceScriptsSelfTest.java`: test suite executed by `scripts/check-protocol.sh`.

## Technical references

- Supplied Waveshare USB UART/I²C/SPI/JTAG adapter PDF (modes and DIP diagram).
- [CH347 application development manual](https://files.waveshare.com/upload/2/2b/CH347-applicationa-development-manual-en.pdf).
- [Si4703 register map and read ordering](https://github.com/sparkfun/FM_Tuner_Basic_Breakout-Si4703/blob/master/Libraries/Arduino/src/SparkFunSi4703.cpp).
- [Adafruit Si4713 command descriptions](https://docs.circuitpython.org/projects/si4713/en/latest/_modules/adafruit_si4713.html).
- [RDA5807M fixed-pointer register behavior](https://github.com/mathertel/Radio/blob/master/src/RDA5807M.cpp).
- [SSD1306 command / framebuffer protocol](https://github.com/raspberrypi/pico-examples/blob/master/i2c/ssd1306_i2c/ssd1306_i2c.c).
- [LAFVIN 2.13-inch HAT V4 driver](https://github.com/lafvintech/LAFVIN-2.13inch-ePaper-HAT/blob/main/Arduino/epd2in13_V4/epd2in13_V4.cpp).
- [Android USB Host reference](https://developer.android.com/develop/connectivity/usb/host).

MIT-licensed original Android application. No bundled third-party code from GPL projects.
