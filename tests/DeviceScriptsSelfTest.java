import io.interrupt21h.ch347studio.devices.BusMap;
import io.interrupt21h.ch347studio.devices.DeviceCatalog;
import io.interrupt21h.ch347studio.devices.DeviceScripts;
import io.interrupt21h.ch347studio.devices.EpaperFrames;
import io.interrupt21h.ch347studio.protocol.Hex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public final class DeviceScriptsSelfTest {
    private static int checks;
    private static void ok(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
        checks++;
    }
    private static class Bus implements DeviceScripts.I2c {
        final List<byte[]> writes=new ArrayList<>();
        int readCount, maxWrite;
        byte[] response;
        @Override public boolean probe(int addr){return addr==0x3c;}
        @Override public byte[] read(int addr,byte[] prefix,int count) throws IOException {
            readCount++;
            if(response==null)throw new IOException("Missing response");
            return Arrays.copyOf(response,count);
        }
        @Override public void write(int addr,byte[] bytes) {
            if(bytes.length>62)throw new AssertionError("Illegal CH347 I2C packet size");
            maxWrite=Math.max(maxWrite,bytes.length);writes.add(bytes.clone());
        }
    }
    public static void main(String[] args) throws Exception {
        BusMap map=new BusMap();
        ok(map.scanned()==0,"new map empty");
        ok(map.get(0)==BusMap.RESERVED,"reserved address");
        ok(map.get(0x77)==BusMap.UNKNOWN,"last probed address");
        map.record(0x10,BusMap.ACK); map.record(0x11,BusMap.ERROR); map.record(0x3c,BusMap.EMPTY);
        ok(map.count(BusMap.ACK)==1,"ACK tally");
        ok(map.scanned()==3,"scanned tally");
        map.record(0x10,BusMap.EMPTY);ok(map.scanned()==3,"rescan not double counted");
        ok(map.present().isEmpty(),"ACK cleared");
        map.reset();ok(map.scanned()==0,"reset");
        ok(DeviceCatalog.candidates(0x10).size()==2,"Si4703 and RDA5807 share address 0x10");
        ok(DeviceCatalog.candidates(0x3c).size()>=3,"OLED / LCD overlapping address");
        ok(DeviceCatalog.candidates(0x63).stream().anyMatch(p->p.key.equals("si4713")),"Si4713 address");
        ok(DeviceScripts.forProfile("si4703").size()==3,"receiver scripts");
        ok(DeviceScripts.forProfile("ssd1306").size()>=4,"oled scripts");
        ok(DeviceScripts.ALL.size()>=25,"script library coverage");
        for(int p=0;p<4;p++) {
            byte[] data=EpaperFrames.make(p);
            ok(data.length==4000,"frame length = 16 * 250");
            for(int y=0;y<EpaperFrames.HEIGHT;y++){
                ok((data[y*16+15]&0x3f)==0x3f,"unused padded pixels must be white");
            }
        }
        ok(EpaperFrames.pixel(EpaperFrames.make(0),0,0),"white frame");
        ok(!EpaperFrames.pixel(EpaperFrames.make(1),10,0),"checkerboard black");
        Bus bus=new Bus();
        ok(DeviceScripts.runI2c("ack",0x3c,bus,System.out::println).contains("ACK"),"ACK probe");
        DeviceScripts.runI2c("ssd1306-checker",0x3c,bus,System.out::println);
        ok(bus.writes.size()>15,"screen writes segmented");
        ok(bus.maxWrite<=62,"CH347 transaction budget");
        ok(bus.writes.stream().anyMatch(w->w[0]==0x40),"OLED data bytes");
        bus.writes.clear();
        DeviceScripts.runI2c("sh1106-checker",0x3c,bus,System.out::println);
        ok(bus.writes.stream().anyMatch(w->w.length>=2&&(w[1]&0xff)==0xB0),"SH1106 page addressing");
        bus.writes.clear();
        DeviceScripts.runI2c("lcd-hello",0x27,bus,System.out::println);
        ok(bus.writes.size()>30,"PCF8574 LCD pulses");
        bus.response=Hex.parse("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
        String reg=DeviceScripts.runI2c("si4703-registers",0x10,bus,System.out::println);
        ok(reg.contains("R0A = 0000"),"Si4703 circular register mapping");
        bus.response=Hex.parse("80 0D 32 30 00 00 32 30 41");
        String rev=DeviceScripts.runI2c("si4713-revision",0x63,bus,System.out::println);
        ok(rev.contains("Si4713 signature"),"Si4713 part number identification");
        ok(bus.writes.stream().anyMatch(w->Arrays.equals(w,Hex.parse("10 00"))),"Si4713 GET_REV framing");
        bus.response=Hex.parse("60");
        ok(DeviceScripts.runI2c("bme280-id",0x76,bus,System.out::println).contains("BME280 confirmed"),"BME280 identity");
        bus.response=Hex.parse("BE EF");
        ok(DeviceScripts.runI2c("rda5807-id",0x10,bus,System.out::println).contains("Not a recognized"),"RDA reject unexpected chipid");
        byte[] sf=Hex.parse("00 00 00 00 00 53 46 44 50 00 00 00 00 00 00 00 00 00 00 00 00");
        ok(DeviceScripts.runSpi("spi-sfdp", tx -> sf).contains("valid"),"SFDP signature");
        ok(DeviceScripts.runSpi("spi-loopback", tx -> tx).contains("PASS"),"SPI isolated loopback");
        ok(DeviceScripts.runSpi("spi-jedec", tx -> Hex.parse("00 EF 40 18")).contains("Winbond"),"SPI flash ID");
        System.out.println("PASS " + checks + " device diagnostics assertions");
    }
}
