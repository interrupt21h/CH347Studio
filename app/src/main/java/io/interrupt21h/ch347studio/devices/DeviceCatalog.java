package io.interrupt21h.ch347studio.devices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Address hints only. Identical addresses are shared by unrelated chip families. */
public final class DeviceCatalog {
    private DeviceCatalog() {}
    public static final class Profile {
        public final String key, name, category, notes;
        public final int[] addresses;
        public Profile(String key, String name, String category, String notes, int... addresses) {
            this.key = key; this.name = name; this.category = category; this.notes = notes;
            this.addresses = addresses.clone();
        }
        public boolean matches(int address) {
            for (int a : addresses) if (a == address) return true;
            return false;
        }
    }
    public static final List<Profile> I2C = Collections.unmodifiableList(Arrays.asList(
        new Profile("tca9548a", "TCA9548A / PCA9548A I²C 8-way mux", "Peripheral", "NOYITO or GODIYMODULES breakout; 0x70–0x77 by A0/A1/A2; bus matrix can scan each branch separately",0x70,0x71,0x72,0x73,0x74,0x75,0x76,0x77),
        new Profile("mcp23017", "MCP23017 16-bit I²C I/O expander", "Peripheral", "MCP23017, not MCP23S17 (SPI variant); 0x20–0x27; register snapshot assumes BANK=0",0x20,0x21,0x22,0x23,0x24,0x25,0x26,0x27),
        new Profile("ssd1306", "SSD1306 OLED", "Display", "128×32 / 128×64 candidates; ACK cannot distinguish from SH1106", 0x3C, 0x3D),
        new Profile("sh1106", "SH1106 OLED", "Display", "132-column RAM, usually visible 128 columns; requires explicit selection", 0x3C, 0x3D),
        new Profile("pcf8574", "PCF8574 / HD44780 LCD", "Display", "PCF8574 I/O expanders can drive 16×2 LCDs; pin mapping varies", 0x20,0x21,0x22,0x23,0x24,0x25,0x26,0x27,0x38,0x39,0x3A,0x3B,0x3C,0x3D,0x3E,0x3F),
        new Profile("si4703", "Si4703 FM receiver", "Radio", "Default 0x10, read register stream begins at 0x0A; reset/SEN wiring matters", 0x10),
        new Profile("rda5807m", "RDA5807M FM receiver", "Radio", "0x10 sequential status; 0x11 random register access (chip ID)", 0x10),
        new Profile("si4713", "Si4713 FM transmitter", "Radio", "0x63 usual; 0x11 alternative. GET_REV requires powered-up command interface", 0x63,0x11),
        new Profile("bme280", "BME280 / BMP280", "Sensor", "ID register at 0xD0; 0x60=BME280, 0x58=BMP280", 0x76,0x77),
        new Profile("sht31", "SHT3x environmental sensor", "Sensor", "Single-shot measurement returns T/RH with CRC8", 0x44,0x45),
        new Profile("ds3231", "DS3231 RTC", "Clock", "Calendar at register 0x00; address also shared by other RTCs", 0x68),
        new Profile("ads1115", "ADS1115 ADC", "Sensor", "Config register at 0x01; multiple ADS1x15 members share address", 0x48,0x49,0x4A,0x4B),
        new Profile("bh1750", "BH1750 light sensor", "Sensor", "Typically 0x23 or 0x5C; read may require explicit measuring mode", 0x23,0x5C),
        new Profile("eeprom", "24xx EEPROM", "Memory", "Address range depends on chip size and wiring; read uses an 8-bit pointer here", 0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57)
    ));
    public static List<Profile> candidates(int address) {
        List<Profile> result = new ArrayList<>();
        for (Profile p : I2C) if (p.matches(address)) result.add(p);
        return result;
    }
    public static String hint(int address) {
        List<Profile> profiles = candidates(address);
        if (profiles.isEmpty()) return "Unknown / custom I²C device";
        StringBuilder b = new StringBuilder();
        for (Profile p : profiles) {
            if (b.length() > 0) b.append(" · ");
            b.append(p.name);
        }
        return b.toString();
    }
}
