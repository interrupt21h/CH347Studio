# CH347 Studio — Android USB Hardware Workbench

A native, offline Android USB-host app for the **Waveshare USB TO UART/I2C/SPI/JTAG** adapter based on the **WCH CH347T**. Written in Java, using Android's built-in `android.hardware.usb` APIs. No root, backend service, analytics, network permission, or binary WCH DLL is needed.

> **Release status: protocol-tested prototype, NOT yet tested with a physical CH347 on Android.** Source packet builders pass 24 standalone JVM assertions. Android APK compilation and device behavior must still be confirmed in Android Studio / CI and against real hardware. The app is intentionally honest about incomplete transports.

## Supported modes / features

| Hardware mode | VID:PID | Function in this app |
|---|---|---|
| M0 dual UART | `1A86:55DA` | USB descriptor inspection; CDC-ACM UART if enumerated (vendor CH343 UART unimplemented) |
| M1 UART / I²C / SPI | `1A86:55DB` | **Vendor BULK I²C + SPI** with transaction logging; CDC UART where available |
| M2 HID UART / I²C / SPI | `1A86:55DC` | USB descriptor inspection only; HID reporting is a distinct transport and not implemented |
| M3 UART / JTAG / I²C | `1A86:55DD` | **Vendor BULK I²C**; JTAG control not implemented |
| CH347F multi-interface | `1A86:55DE` | Vendor BULK I²C/SPI **experimental**; individual interfaces must be validated |

### Implemented I²C

- 20/100/400/750 kHz selectable CH347 clock.
- Address-only ACK probe and range scan (`0x08` through `0x77`).
- 7-bit addressed write (up to 62 payload bytes); NACK reporting.
- Read (1–63 bytes) and combined register-prefix write / repeated START / read / STOP.
- Common address **hints**, *not identification*, and command / response hex logging.

### Implemented SPI

- Clock polarity/phase (modes 0–3), CS0 or CS1; hardware prescaler 0–7.
- Read current 26-byte hardware configuration; preserve unrelated settings while adjusting mode and clock.
- Explicit CS assertion/release and up to 507 full-duplex bytes; validate replies.
- Shared command-level diagnostic log.

### UART

- A minimal CDC-ACM 8N1 terminal when the adapter actually exposes compatible CDC data/control interfaces.
- Configurable baud rate, UTF-8 text+newline, arbitrary hex TX, live RX in hex and text.
- This does *not* implement the proprietary WCH CH343 serial interface, so UART may not work on the supplied hardware despite its physical UART support.

### JTAG

- M3 interface inspection and hardware information only. Actual JTAG shifts, TAP-state machinery, target identification and programming are not implemented.

## Build

1. Open this directory as a project in Android Studio with **JDK 17**, **Android SDK 35** and internet for initial Gradle dependency retrieval.
2. Use **Android Gradle Plugin 8.9.2** and **Gradle 8.11.1**.
3. Run `:app:assembleDebug` or select **Build APK(s)** in Android Studio.
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

The ZIP does not contain a downloaded Gradle distribution or official `gradle-wrapper.jar`; `gradlew` is a deliberately explicit local-Gradle shim. An installed Gradle 8.11.1 works with `./gradlew`; Android Studio can also set a local Gradle installation. GitHub Actions uses `gradle/actions/setup-gradle@v4` to provision the known compatible version.

To produce a downloadable APK on GitHub, push this directory into a repository and run **Build CH347 Studio** in Actions. The successful workflow uploads a uniquely named artifact containing `app-debug.apk`.

Protocol-only tests (require just Java 17):

```bash
bash scripts/check-protocol.sh
```

## Connection and safety

1. **Unplug the USB-B cable** before changing the red two-position DIP switch. Select **M1** per the mode diagram on page 1 of the provided PDF. Reconnect with a USB-C OTG adapter (or powered hub) to your phone.
2. Configure the adjacent physical 3.3 V / 5 V level selector to match the target. Voltage selection is **hardware-controlled**; the app cannot set it. Share GND with the target.
3. Open CH347 Studio → Device → **Connect / Authorize USB**. Grant the Android USB permission prompt.
4. Open I²C → Start at **100 kHz**. Scan addresses; confirm any ACKs before reading or writing.
5. For SPI, start at <= 937.5 kHz if you have flying leads. SPI transfers are not harmless: MOSI commands can write/erase target memory. Never connect incompatible voltage levels.
6. Open Device → **Copy USB descriptors** and **Copy event log** for debugging if transfer errors appear.

The USB-B connector powers the board (5 V USB). The 3.3/5 V switch controls I/O level; it does **not** mean every attached target is safe at 5 V. The app intentionally does not attempt to change the red DIP configuration in software.

## Current limitations / testing checklist

- [x] Protocol packet-builder test vectors (24 JVM assertions).
- [x] USB VID/PID mode matching and interface/endpoint display.
- [x] Android USB permission and attach/detach lifecycle logic in source.
- [ ] Compile APK in Android Studio or run the included GitHub Actions workflow.
- [ ] Verify M1 I²C scan and repeated-START transactions with real device (e.g. known EEPROM).
- [ ] Verify M1 SPI JEDEC ID read using suitable flash chip, correct voltage and CS wiring.
- [ ] Validate CH347F interface discovery.
- [ ] Add a WCH CH343 UART backend / M2 HID UART and I²C/SPI reports.
- [ ] Implement JTAG TAP operations for M3.
- [ ] Bench-test unplug/replug, Android sleep, and USB power constraints.

## Files and design

- `app/src/main/java/.../MainActivity.java`: native dark UI, tabs, USB authorization, event log.
- `.../usb/Ch347Usb.java`: CH347 vendor BULK transport and I²C/SPI operations.
- `.../usb/CdcUart.java`: opt-in CDC-ACM serial transport only.
- `.../protocol/Ch347Packets.java`: pure-Java packet builders and reply checks.
- `.../protocol/Hex.java`: strict hex parser and formatter.
- `tests/ProtocolSelfTest.java`: dependency-free JVM protocol regression tests.
- `.github/workflows/android.yml`: repeatable APK build + lint + protocol tests.

## Sources used to derive protocol and platform behavior

- Device PDF supplied by the user, pages 1–2 (DIP modes, level selector, connectors).
- WCH CH347 application development manual: https://files.waveshare.com/upload/2/2b/CH347-applicationa-development-manual-en.pdf
- CH347 vendor BULK stream opcodes and expected ACK semantics (independently documented by Linux driver): https://github.com/aystarik/ch347_vcp
- CH347 SPI command/config framing analysis: https://github.com/nic3-14159/CH347-Research
- Android USB host permissions and bulk transfers: https://developer.android.com/develop/connectivity/usb/host
- Android Gradle plugin compatibility: https://developer.android.com/build/releases/agp-8-9-0-release-notes

Original app code; licensed under the included MIT License. Protocol facts are not copied implementation code from the referenced GPL kernel driver.
