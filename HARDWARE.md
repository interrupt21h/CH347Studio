# CH347 Studio — module wiring and validation

The user-supplied module photos show two TCA9548A eight-channel I²C switch boards, a LAFVIN 2.13-inch e-paper HAT, Aideepen nRF24L01+PA+LNA boards with power adapter, a MCP23017/MCP23S17 I/O expander PCB, and a ShengYang GC9A01 round TFT.

**Important:** Images and seller listings are not hardware identification proof. Check markings and voltages on the actual board before connecting. CH347's onboard 3.3/5 V switch controls interface levels; set it for the *target interface*, not automatically to USB's 5 V. GND must be common. Never connect 5 V signalling to a 3.3 V-only part.

## TCA9548A / PCA9548A (two breakout styles)

* **Interface:** I²C upstream SDA/SCL/GND/VCC, with SDA0/SCL0 through SDA7/SCL7 downstream.
* **Address:** `0x70`–`0x77` (A0–A2); RESET must not be held low. Same controller on both photographed brands.
* **Control:** send exactly one mask byte to mux address, with bit *n* selecting downstream channel *n*. Read a single byte to get the current mask. **No register-pointer byte is used**.
* **App:** root address scan, selected-channel scan, per-channel matrix caching, read mask and opt-in one-hot readback test. Channel scans always restore the original mask. Read/write/register diagnostics route through selected channel and restore the mask after each bus operation.
* **Electrical:** each channel is a switch, not a supply rail. Connect target power separately. Check pull-ups and downstream voltage-domain support; bus speeds and voltage choices must respect all attached modules.

## MCP23017 / MCP23S17 16-bit expanders

* **MCP23017:** I²C, `0x20`–`0x27` (A0–A2); read-only IOCON, IODIR, GPIO, GPPU, OLAT and INTF scripts.
* **MCP23S17:** SPI, chip select + SCK/MOSI/MISO, opcode `0x41 | (A2:A0 << 1)` for read, then register address and dummy. Read-only direction, GPIO and pullup scripts. Hardware addressing requires `IOCON.HAEN`; the app does **not** enable it.
* **BANK caveat:** snapshots use power-up `BANK=0` mapping (IODIR A/B `0x00/0x01`, GPIO `0x12/0x13`, GPPU `0x0C/0x0D`, OLAT `0x14/0x15`). An already configured chip with `IOCON.BANK=1` needs a different map; scripts do not alter register state to force BANK=0.
* **No unique identification register** in this family; plausible reads are not chip verification. No automatic GPIO changes or toggles.

## Aideepen nRF24L01+PA+LNA with socket adapter

* **Interface:** SPI, CSN/SCK/MOSI/MISO; CE separate, IRQ optional, 3.3 V logic. Some socket adapters have regulators; that does **not** establish that their IO pins tolerate 5 V.
* **App:** read-only nRF24 CONFIG, STATUS, SETUP_AW, RF_CH, RF_SETUP; FIFO_STATUS, DYNPD, FEATURE, OBSERVE_TX, RPD; RX_ADDR_P0, RX_ADDR_P1, TX_ADDR. No transmit, receive, auto-ack, channel changes or CE toggles.
* **Power:** PA+LNA variants need a stable and adequately rated 3.3 V supply; decouple near module. Hold CE LOW for passive SPI diagnostics. Select SPI Mode 0 and a conservative clock.
* **Detection:** values in legal register ranges are *plausible*, not a guaranteed ID; SPI has no bus-address discovery.

## ShengYang GC9A01 1.28-inch 240×240 round TFT

* **Interface:** 4-wire SPI: printed `SCL` means SPI SCK, `SDA` means MOSI; `CS` to SPI CS. `DC`, `RES` and `BLK` require additional controllable pins. `VCC` and GND as specified by actual breakout.
* **App:** local 240×240 RGB565 preview (black, color bars, grayscale and crosshair), documented window/pixel command plan and pinout.
* **Physical refresh:** not implemented: CH347 SPI-only interface is insufficient to sequence DC/RES/BLK. An external verified GPIO expander could eventually supply those signals. No write-only display controller can be identified by an SPI JEDEC or address scan.
* **Framebuffer:** 240×240×2 bytes = **115,200 bytes**. Tested pure-Java encoder, but preview is intentionally local.

## LAFVIN 2.13-inch e-paper HAT

* The supplied photos show an e-paper breakout with a **3-line vs 4-line SPI selector**; confirm the hardware setting and controller revision. Pins include SCK/DIN/CS/D/C/RST/BUSY alongside power and ground.
* **App:** existing local 122×250 1-bit framebuffer/pattern preview and command plan. Physical refresh not implemented; controller initialization, D/C and reset sequencing, and BUSY polling must be validated first.

## Hardware regression protocol

1. With the CH347 unplugged, choose **M1**, ensure 3.3 V I/O as appropriate, and use USB-OTG on Android.
2. Inspect USB VID/PID and interface descriptors. Run upstream scan and note any 0x70–0x77 mux ACK.
3. With exactly one TCA9548A selected, choose CH0–CH7 in the topology card, run *Scan selected route*, and confirm that devices appear only on their wired channels. Check mux mask was restored.
4. Connect MCP23017 on I²C; read directions and GPIO values **without writes**. If using the MCP23S17 variant instead, use SPI and choose the hardware address.
5. For nRF24, verify a stable 3.3 V rail, hold CE LOW, use mode 0, conservative SPI clock, and run read-only status tests. Never try JEDEC ID on it.
6. For GC9A01/e-paper, view local patterns but do not attempt physical display refresh until external GPIO support and module-specific sequences are implemented.

## Reference specifications

- TI TCA9548A (control mask, address selection): https://www.ti.com/lit/ds/symlink/tca9548a.pdf
- Microchip MCP23017/MCP23S17 (register map and SPI opcode): https://ww1.microchip.com/downloads/en/DeviceDoc/20001952C.pdf
- Nordic nRF24L01+ register definitions (community RF24 source): https://github.com/nRF24/RF24/blob/master/nRF24L01.h
- Adafruit GC9A01 round TFT (four-wire control): https://learn.adafruit.com/adafruit-1-28-240x240-round-tft-lcd?view=all
