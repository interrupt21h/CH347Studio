import io.interrupt21h.ch347studio.devices.*;
import java.io.IOException;
import java.util.*;

/** JVM-only regression cases for real bus transaction framing; no Android dependencies. */
public final class TopologySelfTest {
    private static int checks;
    private static void ok(boolean value,String why) {if(!value)throw new AssertionError(why);checks++;}
    private static class Mux implements DeviceScripts.I2c {
        int mask=0xA0,sets;
        public boolean probe(int address) {
            if(address==0x70)return true;
            return address==0x3C && (mask&4)!=0 || address==0x10 && (mask&32)!=0;
        }
        public byte[] read(int address,byte[] prefix,int count) throws IOException {
            if(address!=0x70||prefix.length!=0||count!=1)throw new IOException("invalid mux read");
            return new byte[]{(byte)mask};
        }
        public void write(int address,byte[] payload) throws IOException {
            if(address!=0x70||payload.length!=1)throw new IOException("mux write must be exactly one mask byte");
            mask=payload[0]&255;sets++;
        }
    }
    private static class McpI2c implements DeviceScripts.I2c {
        int[] regs=new int[0x16];
        public boolean probe(int address){return address==0x20;}
        public byte[] read(int a,byte[] prefix,int count)throws IOException {
            if(a!=0x20||count!=1||prefix.length!=1)throw new IOException("invalid MCP23017 read");
            return new byte[]{(byte)regs[prefix[0]&255]};
        }
        public void write(int addr,byte[] b)throws IOException {throw new IOException("configuration writes prohibited");}
    }
    private static class Nrf implements DeviceScripts.Spi {
        Map<Integer,byte[]> regs=new HashMap<>();
        Nrf(){
            regs.put(0x00,new byte[]{0x0F});regs.put(0x03,new byte[]{3});
            regs.put(0x05,new byte[]{42});regs.put(0x06,new byte[]{0x0F});
            regs.put(0x07,new byte[]{0x0E});regs.put(0x17,new byte[]{0x11});
            regs.put(0x1C,new byte[]{0});regs.put(0x1D,new byte[]{0});
            regs.put(0x08,new byte[]{0});regs.put(0x09,new byte[]{0});
            regs.put(0x0A,new byte[]{1,2,3,4,5});regs.put(0x0B,new byte[]{6,7,8,9,10});
            regs.put(0x10,new byte[]{11,12,13,14,15});
        }
        public byte[] transfer(byte[] tx)throws IOException {
            int r=tx[0]&255;
            if(r>0x1F)throw new IOException("nRF test must not write commands");
            byte[] out=new byte[tx.length];out[0]=0x0E;
            byte[] data=regs.getOrDefault(r,new byte[tx.length-1]);
            for(int i=1;i<out.length;i++)out[i]=data[Math.min(data.length-1,i-1)];
            return out;
        }
    }
    private static class McpSpi implements DeviceScripts.Spi {
        int opcode,lastReg;
        public byte[] transfer(byte[] tx)throws IOException {
            if(tx.length!=3 || (tx[0]&1)==0) throw new IOException("Only SPI register reads accepted");
            opcode=tx[0]&255;lastReg=tx[1]&255;
            return new byte[]{0,0,(byte)(lastReg==0x0A?0:(lastReg==0x00?0xFF:0xA5))};
        }
    }
    public static void main(String[] args) throws Exception {
        Mux mux=new Mux();
        ok(MuxTools.readMask(mux,0x70)==0xA0,"read TCA control byte");
        BusMap matrix=new BusMap();
        MuxTools.scanChannel(mux,0x70,2,matrix,(a,state)->{});
        ok(mux.mask==0xA0,"restore mask after downstream scan");
        ok(matrix.get(0x3C)==BusMap.ACK,"OLED behind CH2 found");
        ok(matrix.get(0x10)==BusMap.EMPTY,"CH5 receiver NOT shown on CH2");
        ok(matrix.scanned()==111,"mux own address excluded from child scan");
        BusMap other=new BusMap();MuxTools.scanChannel(mux,0x70,5,other,null);
        ok(other.get(0x10)==BusMap.ACK && other.get(0x3C)==BusMap.EMPTY,"channel maps remain distinct");
        try { MuxTools.withChannel(mux,0x70,0,()->{throw new IOException("simulated downstream failure");});
              throw new AssertionError("expected error"); }
        catch(IOException expected) {ok(expected.getMessage().contains("simulated"),"forward error to user");}
        ok(mux.mask==0xA0,"mask always restored on failure");
        String mask=DeviceScripts.runI2c("tca-mask",0x70,mux,x->{});
        ok(mask.contains("CH5") && mask.contains("CH7"),"human-readable mask");
        ok(DeviceScripts.runI2c("tca-onehot",0x70,mux,x->{}).contains("restored"),"all one-hot mux readbacks");
        ok(mux.mask==0xA0,"mux one-hot restores even multi-selected original mask");
        try{MuxTools.validate(0x20,0);throw new AssertionError("should reject nonmux");}
        catch(IllegalArgumentException e){checks++;}
        McpI2c gpio=new McpI2c();gpio.regs[0]=0xFF;gpio.regs[1]=0xF0;
        gpio.regs[0x12]=0xA5;gpio.regs[0x13]=0x5A;
        String pins=DeviceScripts.runI2c("mcp-gpio",0x20,gpio,x->{});
        ok(pins.contains("GPIOA=A5") && pins.contains("GPIOB=5A"),"MCP23017 reads inputs");
        ok(DeviceScripts.runI2c("mcp-iocon",0x20,gpio,x->{}).contains("IODIRA=FF"),"MCP23017 direction");
        ok(DeviceScripts.runI2c("mcp-olat",0x20,gpio,x->{}).contains("OLATA="),"output latch snapshot");
        ok(DeviceScripts.runI2c("mcp-pullup",0x20,gpio,x->{}).contains("GPPUA="),"MCP pull-up snapshot");
        Nrf nrf=new Nrf();
        String nrfStatus=DeviceScripts.runSpi("spi-nrf-status",nrf);
        ok(nrfStatus.contains("Plausible") && nrfStatus.contains("2.442 GHz"),"nRF24L01+ diagnostic fields");
        ok(DeviceScripts.runSpi("spi-nrf-fifo",nrf).contains("FIFO_STATUS=11"),"nRF FIFO snapshot");
        ok(DeviceScripts.runSpi("spi-nrf-addresses",nrf).contains("01 02 03 04 05"),"nRF pipe address framing");
        McpSpi spi=new McpSpi();
        ok(DeviceScripts.runSpi("spi-mcp-directions",spi,3).contains("A2:A0=3"),"MCP23S17 input direction on HW address 3");
        ok(spi.opcode==0x47,"MCP23S17 SPI read opcode 0x41|(3<<1)");
        ok(DeviceScripts.runSpi("spi-mcp-gpio",spi,7).contains("GPIOA="),"MCP23S17 read-only GPIO status");
        ok(spi.opcode==0x4F,"MCP23S17 HW address 7 opcode");
        ok(Gc9a01Frames.BYTES==115200,"GC9A01 frame 240x240x2");
        byte[] image=Gc9a01Frames.make(1);
        ok(image.length==115200,"RGB565 frame size");
        ok((image[0]&255)==0xF8 && image[1]==0,"first RGB red pixel 0xF800");
        ok(Gc9a01Frames.color(45,0,1)==0x07E0,"second color bar green");
        ok(Gc9a01Frames.wiring().contains("RESET")||Gc9a01Frames.wiring().contains("reset"),"GPIO prerequisite documented");
        ok(DeviceCatalog.candidates(0x70).stream().anyMatch(x->x.key.equals("tca9548a")),"mux catalog entries");
        ok(DeviceCatalog.candidates(0x20).stream().anyMatch(x->x.key.equals("mcp23017")),"MCP catalog entries");
        ok(DeviceScripts.forProfile("spi-nrf24").size()==3,"nRF24 scripts enumerated");
        ok(DeviceScripts.forProfile("spi-mcp23s17").size()==2,"MCP23S17 scripts enumerated");
        System.out.println("PASS "+checks+" topology / peripherals assertions");
    }
}
