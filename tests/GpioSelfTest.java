import io.interrupt21h.ch347studio.devices.*;
import java.io.IOException;
import java.util.*;

public final class GpioSelfTest {
    private static int checks;
    private static void ok(boolean condition,String msg){if(!condition)throw new AssertionError(msg);checks++;}
    static final class Bus implements DeviceScripts.I2c {
        final byte[] registers=new byte[0x16];
        final ArrayList<String> operations=new ArrayList<>();
        boolean fail;
        Bus(){registers[0]=(byte)255;registers[1]=(byte)255;registers[0x14]=(byte)0x80;
            registers[0x0A]=0;registers[0x0B]=0;}
        @Override public boolean probe(int address){return address==0x20;}
        @Override public byte[] read(int address,byte[] prefix,int len)throws IOException{
            ok(address==0x20,"address");
            if(fail)throw new IOException("deliberate bus failure");
            int reg=prefix[0]&255;
            operations.add("read:"+reg);
            if(reg==0x12)return new byte[]{registers[0x14]};
            if(reg==0x13)return new byte[]{registers[0x15]};
            return new byte[]{registers[reg]};
        }
        @Override public void write(int address,byte[] payload)throws IOException{
            ok(address==0x20 && payload.length==2,"register write packet");
            if(fail)throw new IOException("deliberate bus failure");
            int reg=payload[0]&255;
            registers[reg]=payload[1];
            operations.add("write:"+reg+":"+(payload[1]&255));
        }
    }
    static final class FakeGpio implements GpioController {
        final int[] directions={255,255},latches={0,0};
        final ArrayList<String> events=new ArrayList<>();
        boolean stuckBusy,throwSleep;
        public void output(int pin,boolean high){
            events.add("output:"+pin+":"+high);
            int port=pin>>>3,bit=1<<(pin&7);
            directions[port]&=~bit;
            if(high)latches[port]|=bit;else latches[port]&=~bit;
        }
        public void input(int pin){events.add("input:"+pin);directions[pin>>>3]|=1<<(pin&7);}
        public void write(int pin,boolean high){
            events.add("write:"+pin+":"+high);
            if(high)latches[pin>>>3]|=1<<(pin&7);else latches[pin>>>3]&=~(1<<(pin&7));
        }
        public boolean read(int pin){return pin==2?stuckBusy:(pin==4);}
        public Snapshot snapshot(){return new Snapshot(directions,new int[]{latches[0],latches[1]},latches);}
    }
    private static void failure(Runnable action,String msg){try{action.run();throw new AssertionError(msg);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Bus bus=new Bus();Mcp23017Gpio chip=new Mcp23017Gpio(bus,0x20);
        GpioController.Snapshot before=chip.snapshot();
        ok(before.isInput(0)&&before.isInput(15),"input default");
        chip.output(0,true);
        ok((bus.registers[0x14]&255)==0x81,"other latches preserved");
        ok((bus.registers[0]&255)==0xFE,"direction single bit");
        int latchWrite=bus.operations.indexOf("write:20:129");
        int directionWrite=bus.operations.indexOf("write:0:254");
        ok(latchWrite>=0&&directionWrite>latchWrite,"output latch before direction");
        chip.output(8,false);
        ok((bus.registers[1]&255)==0xFE,"port B output");
        chip.write(0,false);ok((bus.registers[0x14]&255)==0x80,"write bit preserves others");
        chip.input(0);ok((bus.registers[0]&255)==255,"input high impedance");
        try{chip.write(0,true);throw new AssertionError("input write");}catch(IOException expected){checks++;}
        bus.registers[0x0A]=(byte)0x80;
        try{chip.snapshot();throw new AssertionError("BANK=1");}catch(IOException expected){checks++;}
        bus.registers[0x0A]=0;
        failure(()->new Mcp23017Gpio(bus,0x28),"address validation");
        failure(()->new PinAssignments(new int[]{0,0,2,3,4,5,6,7}),"pin reuse");
        PinAssignments map=new PinAssignments();
        map.set(PinAssignments.Role.TFT_BACKLIGHT,15);
        ok(map.get(PinAssignments.Role.TFT_BACKLIGHT)==15,"remap 16th pin");
        FakeGpio fake=new FakeGpio();
        GpioSequences seq=new GpioSequences(fake,map,ms->{ });seq.arm();
        ok((fake.directions[0]&0xFF)==0x94,"input direction on busy/irq");
        ok((fake.latches[0]&0xFF)==0x21,"safe reset high and radio CE low");
        ok(fake.events.get(0).equals("output:3:false"),"CE is the first output armed");
        int n=fake.events.size();seq.pulseReset(PinAssignments.Role.TFT_RESET,20,100);
        ok(fake.events.size()==n+3,"reset assert and release");
        ok(fake.events.get(n+2).equals("write:5:true"),"reset released high");
        seq.setData(true,true);ok(fake.events.get(fake.events.size()-1).equals("write:6:true"),"TFT D/C");
        seq.radioCe(false);ok(fake.events.get(fake.events.size()-1).equals("write:3:false"),"CE low");
        fake.stuckBusy=true;
        try{seq.awaitEpaperBusy(true,60);throw new AssertionError("busy timeout");}catch(IOException expected){checks++;}
        fake.stuckBusy=false;ok(seq.awaitEpaperBusy(true,60)==0,"idle not busy");
        final int[] chunks={0},commands={0},pixels={0};
        Gc9a01Hardware lcd=new Gc9a01Hardware(bytes->{
            ok(bytes.length>0 && bytes.length<=507,"CH347 packet limit");
            chunks[0]++;
            if(bytes.length==506)pixels[0]+=bytes.length;
            if(bytes.length==1)commands[0]++;
        },seq);
        lcd.command(0x2A,(byte)0,(byte)0,(byte)0,(byte)239);
        lcd.frame(Gc9a01Frames.make(1));
        ok(chunks[0]>200 && pixels[0]>100000,"240x240 frame transport");
        ok(commands[0]>=4,"display commands");
        try{lcd.frame(new byte[10]);throw new AssertionError("short frame");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("GPIO sequence, MCP23017 registers, and RGB565 transfer: "+checks+" assertions passed");
    }
}
