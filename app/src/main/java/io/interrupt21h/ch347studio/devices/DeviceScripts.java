package io.interrupt21h.ch347studio.devices;

import io.interrupt21h.ch347studio.protocol.Hex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Deterministic, opt-in per-device tests. No implicit tuning, GPIO or RF activation. */
public final class DeviceScripts {
    private DeviceScripts() {}
    public interface I2c {
        boolean probe(int address) throws IOException;
        byte[] read(int address, byte[] prefix, int length) throws IOException;
        void write(int address, byte[] bytes) throws IOException;
    }
    public interface Spi {
        byte[] transfer(byte[] bytes) throws IOException;
    }
    public interface Reporter { void log(String message); }
    public enum Risk { READ_ONLY, DISPLAY_CHANGE, CONFIG_CHANGE, EXTERNAL_DEVICE }
    public static final class Script {
        public final String id, profile, title, detail;
        public final Risk risk;
        public Script(String id, String profile, String title, String detail, Risk risk) {
            this.id=id; this.profile=profile; this.title=title; this.detail=detail; this.risk=risk;
        }
    }
    private static Script s(String id,String profile,String title,String detail,Risk risk) {
        return new Script(id,profile,title,detail,risk);
    }
    public static final List<Script> ALL = Collections.unmodifiableList(Arrays.asList(
        s("ack","generic","Address ACK", "Checks whether the target still acknowledges (no ID claim)",Risk.READ_ONLY),
        s("tca-mask","tca9548a","Read channel mask", "Read 8-channel select mask; does not alter routing",Risk.READ_ONLY),
        s("tca-onehot","tca9548a","Channel select/readback exercise", "Temporarily select CH0–CH7, verify each control register readback, then restore original mask",Risk.CONFIG_CHANGE),
        s("mcp-iocon","mcp23017","IOCON / configuration", "Read IOCON, IODIRA and IODIRB (BANK=0 assumed); no GPIO changes",Risk.READ_ONLY),
        s("mcp-gpio","mcp23017","Read GPIO A + B", "Read 16 GPIO levels, input vs output direction and pull-ups",Risk.READ_ONLY),
        s("mcp-olat","mcp23017","Read output latches", "Read OLATA / OLATB and interrupt flag registers",Risk.READ_ONLY),
        s("mcp-pullup","mcp23017","Read pull-up configuration", "Read GPPUA and GPPUB; no state changes",Risk.READ_ONLY),
        s("ssd1306-checker","ssd1306","Checkerboard test", "Initialize 128×64 SSD1306 then draw checkerboard",Risk.DISPLAY_CHANGE),
        s("ssd1306-bars","ssd1306","Vertical bars", "Initialize SSD1306 then draw contrasting columns",Risk.DISPLAY_CHANGE),
        s("ssd1306-clear","ssd1306","Clear display", "Clear SSD1306 128×64 screen RAM",Risk.DISPLAY_CHANGE),
        s("ssd1306-invert","ssd1306","Invert briefly", "Invert pixels for 750 ms; restore normal mode",Risk.DISPLAY_CHANGE),
        s("sh1106-checker","sh1106","SH1106 checkerboard", "Use page addressing and 2-column offset",Risk.DISPLAY_CHANGE),
        s("sh1106-clear","sh1106","SH1106 clear", "Clear 8 pages; 128 visible columns",Risk.DISPLAY_CHANGE),
        s("lcd-hello","pcf8574","LCD HELLO", "HD44780 4-bit initialization, common P0/P2/P3 mapping",Risk.DISPLAY_CHANGE),
        s("lcd-backlight","pcf8574","Backlight pulse", "Toggle PCF8574 backlight output and restore",Risk.CONFIG_CHANGE),
        s("si4703-registers","si4703","Register snapshot", "Read 32-byte circular register map without a pointer write",Risk.READ_ONLY),
        s("si4703-signal","si4703","Signal / station status", "RSSI, tuned channel and stereo indicators",Risk.READ_ONLY),
        s("si4703-rds","si4703","RDS block snapshot", "Read raw PI and RDS blocks without seeking",Risk.READ_ONLY),
        s("rda5807-status","rda5807m","FM status / RSSI", "Read 0x0A and 0x0B via sequential access",Risk.READ_ONLY),
        s("rda5807-rds","rda5807m","RDS block snapshot", "Read 0x0A–0x0F sequentially",Risk.READ_ONLY),
        s("rda5807-id","rda5807m","Identify at 0x11", "Random-register chip ID probe; uses write-prefix protocol",Risk.CONFIG_CHANGE),
        s("si4713-cts","si4713","Command status", "Read CTS/ERR response; safe even before POWER_UP",Risk.READ_ONLY),
        s("si4713-power","si4713","Power up (no RF tune)", "Initialize FM transmitter analog input; does not tune frequency or enable TX power",Risk.CONFIG_CHANGE),
        s("si4713-revision","si4713","GET_REV identification", "Only after the transmitter has been powered up",Risk.CONFIG_CHANGE),
        s("si4713-txstatus","si4713","TX tuning status", "Query current tuning; does not start transmission",Risk.CONFIG_CHANGE),
        s("si4713-asq","si4713","Audio level / ASQ", "Query transmitter audio status; no RF activation",Risk.CONFIG_CHANGE),
        s("bme280-id","bme280","Read chip ID", "Read register 0xD0 and distinguish BMP280/BME280",Risk.READ_ONLY),
        s("sht31-measure","sht31","Measure temperature/RH", "Single shot; validate both CRC-8 checksums",Risk.CONFIG_CHANGE),
        s("ds3231-clock","ds3231","Read RTC clock", "Read 7 calendar BCD bytes",Risk.READ_ONLY),
        s("ads1115-config","ads1115","Read ADC config", "Read 0x01 register without starting conversions",Risk.READ_ONLY),
        s("bh1750-light","bh1750","Read ambient light", "Read last measurement if module is already measuring",Risk.READ_ONLY),
        s("eeprom-bytes","eeprom","Read 16 EEPROM bytes", "Uses 8-bit address pointer; read-only memory access",Risk.READ_ONLY),
        s("spi-nrf-status","spi-nrf24","nRF24L01+ status + configuration", "Read STATUS / CONFIG / SETUP_AW / RF_CH / RF_SETUP; CE remains untouched and no RF TX",Risk.READ_ONLY),
        s("spi-nrf-fifo","spi-nrf24","nRF24 FIFO + feature registers", "Read FIFO_STATUS / DYNPD / FEATURE / OBSERVE_TX and carrier detect; no CE toggles",Risk.READ_ONLY),
        s("spi-nrf-addresses","spi-nrf24","nRF24 pipe addresses", "Read RX_ADDR_P0, RX_ADDR_P1 and TX_ADDR, without changing them",Risk.READ_ONLY),
        s("spi-mcp-directions","spi-mcp23s17","MCP23S17 direction / IOCON", "Read opcode 0x41, IOCON and both IODIR registers (BANK=0 assumed)",Risk.READ_ONLY),
        s("spi-mcp-gpio","spi-mcp23s17","MCP23S17 GPIO / pull-ups", "Read GPIOA/B, GPPUA/B and OLATA/B; outputs are NOT toggled",Risk.READ_ONLY),
        s("spi-jedec","spi-flash","Read JEDEC ID", "0x9F manufacturer/device identification",Risk.READ_ONLY),
        s("spi-status","spi-flash","Read status register", "0x05 read status; no write-enable",Risk.READ_ONLY),
        s("spi-sfdp","spi-flash","Read SFDP header", "0x5A read Serial Flash Discoverable Parameters",Risk.READ_ONLY),
        s("spi-loopback","spi-tools","MOSI↔MISO loopback", "Requires disconnecting target and wiring MOSI to MISO",Risk.EXTERNAL_DEVICE)
    ));
    public static List<Script> forProfile(String profile) {
        List<Script> scripts = new ArrayList<>();
        for (Script script : ALL) if (script.profile.equals(profile)) scripts.add(script);
        return scripts;
    }
    public static Script byId(String id) {
        for (Script script : ALL) if (script.id.equals(id)) return script;
        throw new IllegalArgumentException("Unknown script: " + id);
    }
    private static int word(byte[] b,int offset) { return (u(b[offset]) << 8) | u(b[offset+1]); }
    private static int u(byte b) { return b & 255; }
    private static void length(byte[] data,int len) throws IOException {
        if (data.length != len) throw new IOException("Response length " + data.length + " expected " + len);
    }
    public static String runI2c(String id,int address,I2c bus,Reporter log) throws IOException, InterruptedException {
        switch (id) {
            case "ack": return bus.probe(address) ? "ACK at " + hex(address) : "NACK at " + hex(address);
            case "tca-mask": {
                int mask=MuxTools.readMask(bus,address);
                return String.format(Locale.US,"TCA9548A-compatible control register mask 0x%02X (%s). ACK/control read is not a unique chip ID.",
                        mask,channelList(mask));
            }
            case "tca-onehot": {
                StringBuilder result=new StringBuilder();
                // Preserve whatever combination of channels the application found.
                int old=MuxTools.readMask(bus,address);
                IOException failure=null;
                try {
                    for(int ch=0;ch<8;ch++) {
                        int mask=1<<ch;
                        MuxTools.setMask(bus,address,mask);
                        int returned=MuxTools.readMask(bus,address);
                        if(returned!=mask) throw new IOException("Mux CH"+ch+" readback mismatch: "+hex(returned));
                        result.append("CH").append(ch).append(" verified; ");
                    }
                } catch(IOException e) { failure=e;throw e; }
                finally {
                    try {MuxTools.setMask(bus,address,old);}
                    catch(IOException e) {if(failure!=null)failure.addSuppressed(e);else throw new IOException("Could not restore mux mask",e);}
                }
                return result+String.format(Locale.US,"original mask 0x%02X restored",old);
            }
            case "mcp-iocon": case "mcp-gpio": case "mcp-olat": case "mcp-pullup": {
                // BANK=0 power-up map only; reading individual registers avoids SEQOP assumptions.
                int iocon=readMcpI2c(bus,address,0x0A);
                if ((iocon&0x80)!=0) return "IOCON.BANK=1 reported; change to BANK=0 before using this register map. IOCON=0x"+hexByte(iocon);
                if(id.equals("mcp-iocon")) return String.format(Locale.US,
                    "MCP23017-compatible map, no unique ID. IOCON=%02X IODIRA=%02X IODIRB=%02X",
                    iocon,readMcpI2c(bus,address,0x00),readMcpI2c(bus,address,0x01));
                if(id.equals("mcp-gpio"))return String.format(Locale.US,
                    "GPIOA=%02X GPIOB=%02X  IODIRA=%02X IODIRB=%02X (1=input,0=output)",
                    readMcpI2c(bus,address,0x12),readMcpI2c(bus,address,0x13),
                    readMcpI2c(bus,address,0x00),readMcpI2c(bus,address,0x01));
                if(id.equals("mcp-pullup"))return String.format(Locale.US,
                    "GPPUA=%02X GPPUB=%02X (1=pull-up enabled)",
                    readMcpI2c(bus,address,0x0C),readMcpI2c(bus,address,0x0D));
                return String.format(Locale.US,"OLATA=%02X OLATB=%02X INTFA=%02X INTFB=%02X",
                    readMcpI2c(bus,address,0x14),readMcpI2c(bus,address,0x15),
                    readMcpI2c(bus,address,0x0E),readMcpI2c(bus,address,0x0F));
            }
            case "ssd1306-checker": oled(bus,address,false,"checker"); return "SSD1306 checkerboard written (128×64; profile not verified)";
            case "ssd1306-bars": oled(bus,address,false,"bars"); return "SSD1306 vertical bar pattern written";
            case "ssd1306-clear": oled(bus,address,false,"clear"); return "SSD1306 display RAM cleared";
            case "ssd1306-invert": {
                bus.write(address,new byte[]{0, (byte)0xA7});
                try { Thread.sleep(750); } finally { bus.write(address,new byte[]{0,(byte)0xA6}); }
                return "Normal OLED mode restored";
            }
            case "sh1106-checker": oled(bus,address,true,"checker"); return "SH1106 checkerboard written (128×64)";
            case "sh1106-clear": oled(bus,address,true,"clear"); return "SH1106 display cleared";
            case "lcd-hello": lcdHello(bus,address); return "LCD initialized / HELLO CH347 displayed (common backpack mapping)";
            case "lcd-backlight": {
                bus.write(address,new byte[]{0x00});
                try { Thread.sleep(250); } finally { bus.write(address,new byte[]{0x08}); }
                return "Backlight restored ON (standard P3 mapping)";
            }
            case "si4703-registers": case "si4703-signal": case "si4703-rds": {
                byte[] registers = bus.read(address,new byte[0],32);
                length(registers,32);
                int[] r = new int[16];
                for (int n=0;n<16;n++) r[(0x0A+n)&15] = word(registers,n*2);
                if (id.equals("si4703-registers")) {
                    StringBuilder sb = new StringBuilder("Si4703-compatible register stream (not identity verified)\n");
                    for (int n=0;n<16;n++) sb.append(String.format(Locale.US,"R%02X = %04X%s",n,r[n],n%4==3?"\n":"    "));
                    return sb.toString().trim();
                }
                int raw = r[0x0A], channel = r[0x0B]&0x3FF;
                String status = String.format(Locale.US,"RSSI %d  Stereo %s  RDS ready %s  Tune complete %s  Channel %d",
                        raw&0xFF,(raw&0x100)!=0?"yes":"no",(raw&0x8000)!=0?"yes":"no",(raw&0x4000)!=0?"yes":"no",channel);
                return id.equals("si4703-signal") ? status : status +
                        String.format(Locale.US,"\nRDS A/B/C/D = %04X %04X %04X %04X\nGroup %d%s",
                                r[12],r[13],r[14],r[15],(r[13]>>12)&15,((r[13]&0x0800)!=0)?"B":"A");
            }
            case "rda5807-status": case "rda5807-rds": {
                byte[] status = bus.read(address,new byte[0],id.equals("rda5807-rds")?12:4);
                int a=word(status,0), b=word(status,2);
                String info=String.format(Locale.US,"STC %s  Channel %d  RSSI %d  Stereo %s  RDS ready %s",
                    (a&0x4000)!=0?"yes":"no",a&0x3FF,(b>>9)&0x7F,(b&0x0400)!=0?"yes":"no",(a&0x8000)!=0?"yes":"no");
                return status.length==4?info:info+String.format(Locale.US,"\nRDS A/B/C/D = %04X %04X %04X %04X",word(status,4),word(status,6),word(status,8),word(status,10));
            }
            case "rda5807-id": {
                if (address != 0x10 && address != 0x11) throw new IOException("RDA5807M profile only at 0x10/0x11");
                byte[] raw=bus.read(0x11,new byte[]{0x00},2);
                int chip=word(raw,0);
                return String.format(Locale.US,"Random register 0x00 = 0x%04X (%s)",chip,(chip&0xFF00)==0x5800?"RDA5807 family signature":"Not a recognized RDA5807 signature");
            }
            case "si4713-cts": {
                byte[] st=bus.read(address,new byte[0],1);
                return String.format(Locale.US,"Status 0x%02X • CTS %s • ERR %s",u(st[0]),(st[0]&0x80)!=0?"ready":"busy",(st[0]&0x40)!=0?"yes":"no");
            }
            case "si4713-power": {
                si4713Command(bus,address,new byte[]{0x01,0x12,0x50},log);
                // REFCLK_FREQ = 32768 Hz for common crystal-based breakouts. Does NOT set TX_TUNE_POWER.
                si4713Command(bus,address,new byte[]{0x12,0x00,0x02,0x01,(byte)0x80,0x00},log);
                return "Si4713 command core powered up; REFCLK=32768 Hz. RF TX not tuned/enabled.";
            }
            case "si4713-revision": {
                si4713Command(bus,address,new byte[]{0x10,0},log);
                byte[] rev=bus.read(address,new byte[0],9);
                int part=u(rev[1]);
                return String.format(Locale.US,"GET_REV part %d (0x%02X) %s, FW %c%c, chip %c | raw %s",part,part,
                        part==13?"Si4713 signature":part==21?"Si4721 signature":"unrecognized – do not assume Si4713",
                        cleanChar(rev[2]),cleanChar(rev[3]),cleanChar(rev[8]),Hex.format(rev));
            }
            case "si4713-txstatus": {
                si4713Command(bus,address,new byte[]{0x33,0x01},log);
                byte[] tx=bus.read(address,new byte[0],8);
                return String.format(Locale.US,"TX status 0x%02X  Frequency %.2f MHz  Power %d dBuV  Antcap %d | %s",u(tx[0]),word(tx,2)/100.0,u(tx[5]),u(tx[6]),Hex.format(tx));
            }
            case "si4713-asq": {
                si4713Command(bus,address,new byte[]{0x34,0x01},log);
                return "TX audio quality status: " + Hex.format(bus.read(address,new byte[0],5));
            }
            case "bme280-id": {
                byte[] idByte=bus.read(address,new byte[]{(byte)0xD0},1);
                int chip=u(idByte[0]);
                return String.format(Locale.US,"Chip ID 0x%02X • %s",chip,chip==0x60?"BME280 confirmed":chip==0x58?"BMP280 confirmed":"unknown sensor");
            }
            case "sht31-measure": {
                bus.write(address,new byte[]{0x24,0x00});
                Thread.sleep(18);
                byte[] b=bus.read(address,new byte[0],6);
                if (crc8(b,0)!=u(b[2]) || crc8(b,3)!=u(b[5])) throw new IOException("SHT3x CRC mismatch: " + Hex.format(b));
                double t=-45.0+175.0*word(b,0)/65535.0;
                double h=100.0*word(b,3)/65535.0;
                return String.format(Locale.US,"Temperature %.2f °C  Humidity %.1f %% (both CRCs passed)",t,h);
            }
            case "ds3231-clock": {
                byte[] b=bus.read(address,new byte[]{0},7);
                return String.format(Locale.US,"RTC %02d:%02d:%02d  Date %04d-%02d-%02d (BCD; verify oscillator)",
                   bcd(b[2]&0x3F),bcd(b[1]&0x7F),bcd(b[0]&0x7F),2000+bcd(b[6]&0xFF),bcd(b[5]&0x1F),bcd(b[4]&0x3F));
            }
            case "ads1115-config": {
                byte[] b=bus.read(address,new byte[]{0x01},2);
                return String.format(Locale.US,"ADS1x15 config register 0x%04X (chip family unverified)",word(b,0));
            }
            case "bh1750-light": {
                byte[] b=bus.read(address,new byte[0],2);
                return String.format(Locale.US,"Last sensor value: %d raw / estimated %.1f lux at default resolution (if active)",word(b,0),word(b,0)/1.2);
            }
            case "eeprom-bytes": return "16-byte EEPROM window @ offset 0x00: " + Hex.format(bus.read(address,new byte[]{0},16));
            default: throw new IllegalArgumentException("Not an I2C script: "+id);
        }
    }
    public static String runSpi(String id,Spi bus) throws IOException { return runSpi(id,bus,0); }
    /** MCP23S17 address pins A2..A0 when IOCON.HAEN is enabled. */
    public static String runSpi(String id,Spi bus,int hwAddress) throws IOException {
        if(hwAddress<0||hwAddress>7)throw new IllegalArgumentException("SPI expander address 0..7");
        switch (id) {
            case "spi-nrf-status": {
                int[] regs={0x00,0x03,0x05,0x06,0x07};
                int[] v=new int[regs.length];
                for(int i=0;i<regs.length;i++)v[i]=nrfRead(bus,regs[i],1)[0]&255;
                String verdict=(v[1]>=1&&v[1]<=3&&v[2]<=125&&v[0]!=255&&v[3]!=255)?
                    "Plausible nRF24L01+ register values (NOT definitive identification)":"Unrecognized response; check 3.3 V, MISO, CE, CSN";
                return String.format(Locale.US,
                    "%s\nCONFIG=%02X  SETUP_AW=%02X  RF_CH=%02X (%.3f GHz)\nRF_SETUP=%02X  STATUS=%02X",
                    verdict,v[0],v[1],v[2],2.400+v[2]/1000.0,v[3],v[4]);
            }
            case "spi-nrf-fifo": return String.format(Locale.US,
                "FIFO_STATUS=%02X  DYNPD=%02X FEATURE=%02X OBSERVE_TX=%02X RPD=%02X",
                nrfRead(bus,0x17,1)[0]&255,nrfRead(bus,0x1C,1)[0]&255,
                nrfRead(bus,0x1D,1)[0]&255,nrfRead(bus,0x08,1)[0]&255,nrfRead(bus,0x09,1)[0]&255);
            case "spi-nrf-addresses": return "RX_ADDR_P0="+Hex.format(nrfRead(bus,0x0A,5))+
                    "\nRX_ADDR_P1="+Hex.format(nrfRead(bus,0x0B,5))+
                    "\nTX_ADDR="+Hex.format(nrfRead(bus,0x10,5))+
                    "\nNote: pipe addresses may be sensitive; copy logs deliberately.";
            case "spi-mcp-directions": {
                int control=readMcpSpi(bus,hwAddress,0x0A);
                if ((control&0x80)!=0)return "IOCON.BANK=1 possibly enabled; alternate map not decoded. Readback="+hexByte(control);
                return String.format(Locale.US,"MCP23S17-compatible register reads (no unique ID). A2:A0=%d; IOCON=%02X IODIRA=%02X IODIRB=%02X",
                    hwAddress,control,readMcpSpi(bus,hwAddress,0x00),readMcpSpi(bus,hwAddress,0x01));
            }
            case "spi-mcp-gpio": {
                int control=readMcpSpi(bus,hwAddress,0x0A);
                if((control&0x80)!=0)return "BANK=1 possibly enabled; this profile assumes BANK=0";
                return String.format(Locale.US,"SPI MCP23S17 A2:A0=%d: GPIOA=%02X GPIOB=%02X GPPUA=%02X GPPUB=%02X OLATA=%02X OLATB=%02X",
                    hwAddress,readMcpSpi(bus,hwAddress,0x12),readMcpSpi(bus,hwAddress,0x13),
                    readMcpSpi(bus,hwAddress,0x0C),readMcpSpi(bus,hwAddress,0x0D),
                    readMcpSpi(bus,hwAddress,0x14),readMcpSpi(bus,hwAddress,0x15));
            }

            case "spi-jedec": {
                byte[] b=bus.transfer(Hex.parse("9F 00 00 00"));
                length(b,4);
                int mfg=u(b[1]);
                String manufacturer=mfg==0xEF?"Winbond":mfg==0xC2?"Macronix":mfg==0x20?"Micron/ST":mfg==0x1F?"Adesto/Atmel":"unknown";
                return String.format(Locale.US,"JEDEC manufacturer 0x%02X (%s), type 0x%02X, capacity 0x%02X. All-FF/00 = no meaningful response.",mfg,manufacturer,u(b[2]),u(b[3]));
            }
            case "spi-status": {
                byte[] b=bus.transfer(Hex.parse("05 00")); length(b,2);
                return String.format(Locale.US,"SR1 = 0x%02X | WIP %s | WEL %s",u(b[1]),(b[1]&1)!=0?"1":"0",(b[1]&2)!=0?"1":"0");
            }
            case "spi-sfdp": {
                byte[] b=bus.transfer(Hex.parse("5A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"));
                String h=new String(b,5,Math.min(4,b.length-5),java.nio.charset.StandardCharsets.US_ASCII);
                return "SFDP signature "+h+("SFDP".equals(h)?" (valid)":" (not recognized)")+" • "+Hex.format(b);
            }
            case "spi-loopback": {
                byte[] b=bus.transfer(Hex.parse("A5 5A AA 55 3C C3"));
                return "Loopback " + (Arrays.equals(b,Hex.parse("A5 5A AA 55 3C C3"))?"PASS":"MISMATCH") + " • RX "+Hex.format(b);
            }
            default: throw new IllegalArgumentException("Not an SPI script: " + id);
        }
    }
    private static int readMcpI2c(I2c bus,int addr,int reg) throws IOException {
        byte[] b=bus.read(addr,new byte[]{(byte)reg},1);
        length(b,1);return u(b[0]);
    }
    private static int readMcpSpi(Spi bus,int hardwareAddress,int reg) throws IOException {
        int opcode=0x41 | (hardwareAddress<<1);
        byte[] b=bus.transfer(new byte[]{(byte)opcode,(byte)reg,0});
        length(b,3);return u(b[2]);
    }
    private static byte[] nrfRead(Spi bus,int reg,int count) throws IOException {
        if(reg<0 || reg>0x1F || count<1 || count>5)throw new IllegalArgumentException("Invalid nRF24 read");
        byte[] command=new byte[1+count];command[0]=(byte)reg; // R_REGISTER=0x00 | register
        byte[] reply=bus.transfer(command);
        length(reply,command.length);
        return Arrays.copyOfRange(reply,1,reply.length);
    }
    private static String hexByte(int value) {return String.format(Locale.US,"%02X",value);}
    private static String channelList(int mask) {
        if(mask==0)return "all channels disconnected";
        StringBuilder s=new StringBuilder();
        for(int n=0;n<8;n++)if((mask&(1<<n))!=0) {
            if(s.length()>0)s.append(", ");s.append("CH").append(n);
        }
        return s.toString();
    }
    private static void si4713Command(I2c bus,int address,byte[] cmd,Reporter reporter) throws IOException, InterruptedException {
        bus.write(address,cmd);
        for (int attempt=0;attempt<20;attempt++) {
            byte[] status=bus.read(address,new byte[0],1);
            if ((status[0]&0x80)!=0) {
                if ((status[0]&0x40)!=0) throw new IOException("Si47xx command rejected, ERR bit set");
                return;
            }
            Thread.sleep(10);
        }
        throw new IOException("Si47xx CTS timeout (requires POWER_UP and reset/SEN setup)");
    }
    private static int crc8(byte[] b,int offset) {
        int crc=0xFF;
        for(int i=offset;i<offset+2;i++) {
            crc^=u(b[i]);
            for(int bit=0;bit<8;bit++) crc=((crc&0x80)!=0 ? (crc<<1)^0x31 : (crc<<1))&0xFF;
        }
        return crc;
    }
    private static int bcd(int b) { return ((b>>4)&15)*10+(b&15); }
    private static char cleanChar(byte b) { int v=u(b); return v>=32&&v<127?(char)v:'?'; }
    private static String hex(int a) { return String.format(Locale.US,"0x%02X",a); }
    private static void oled(I2c bus,int address,boolean sh1106,String pattern) throws IOException {
        if (sh1106) {
            bus.write(address,new byte[]{0, (byte)0xAE, (byte)0xA1, (byte)0xC8, (byte)0xAD, (byte)0x8B});
        } else {
            bus.write(address,new byte[]{0,(byte)0xAE,(byte)0xD5,(byte)0x80,(byte)0xA8,0x3F,(byte)0xD3,0,0x40});
            bus.write(address,new byte[]{0,(byte)0x8D,0x14,0x20,0, (byte)0xA1,(byte)0xC8,(byte)0xDA,0x12});
            bus.write(address,new byte[]{0,(byte)0x81,(byte)0xCF,(byte)0xD9,(byte)0xF1,(byte)0xDB,0x40,(byte)0xA4,(byte)0xA6});
            bus.write(address,new byte[]{0,0x21,0,127,0x22,0,7});
        }
        for (int page=0; page<8; page++) {
            if (sh1106) bus.write(address,new byte[]{0, (byte)(0xB0+page),0x02,0x10});
            byte[] row=new byte[128];
            for (int col=0;col<128;col++) {
                if (pattern.equals("checker")) row[col] = (byte)(((col/8+page)%2==0)?0xAA:0x55);
                else if(pattern.equals("bars")) row[col]=(byte)(((col/8)%2==0)?0xFF:0);
                else row[col]=0;
            }
            for (int offset=0;offset<row.length;offset+=61) {
                int len=Math.min(61,row.length-offset);
                byte[] packet=new byte[len+1];packet[0]=0x40;
                System.arraycopy(row,offset,packet,1,len);
                bus.write(address,packet);
            }
        }
        bus.write(address,new byte[]{0,(byte)0xAF});
    }
    private static void lcdHello(I2c bus,int address) throws IOException, InterruptedException {
        // Common PCF8574 backpack wiring: P0=RS, P2=E, P3=LED, P4-7=D4-7.
        Thread.sleep(45);
        lcdNibble(bus,address,0x30,false); Thread.sleep(5);
        lcdNibble(bus,address,0x30,false); Thread.sleep(1);
        lcdNibble(bus,address,0x30,false); lcdNibble(bus,address,0x20,false);
        for (int cmd : new int[]{0x28,0x08,0x01,0x06,0x0C}) { lcdByte(bus,address,cmd,false); if(cmd==1) Thread.sleep(3); }
        for (char c : "HELLO CH347".toCharArray()) lcdByte(bus,address,c,true);
    }
    private static void lcdByte(I2c bus,int address,int value,boolean data) throws IOException {
        lcdNibble(bus,address,value&0xF0,data);
        lcdNibble(bus,address,(value<<4)&0xF0,data);
    }
    private static void lcdNibble(I2c bus,int address,int high,boolean data) throws IOException {
        int val=(high&0xF0)|0x08|(data?1:0);
        bus.write(address,new byte[]{(byte)(val|0x04)});
        bus.write(address,new byte[]{(byte)(val&~0x04)});
    }
}
