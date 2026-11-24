package io.interrupt21h.ch347studio.devices;

import java.io.IOException;
import java.util.Arrays;

/** Experimental GC9A01A 4-wire SPI command/pixel writer with GPIO D/C. */
public final class Gc9a01Hardware {
    public interface Spi { void write(byte[] bytes) throws IOException; }
    private final Spi spi;
    private final GpioSequences gpio;
    public Gc9a01Hardware(Spi spi,GpioSequences gpio){this.spi=spi;this.gpio=gpio;}
    public void command(int cmd, byte... args) throws IOException {
        gpio.setData(true,false);spi.write(new byte[]{(byte)cmd});
        if(args.length>0){gpio.setData(true,true);spi.write(args);}
    }
    /** Standard DCS sequence, not a vendor-specific LCD power/gamma initialization table. */
    public void basicWake() throws IOException,InterruptedException {
        command(0x01);Thread.sleep(150); // SWRESET
        command(0x3A,(byte)0x55); // RGB565
        command(0x36,(byte)0x08); // BGR orientation common to breakout
        command(0x11);Thread.sleep(150); // SLEEP OUT
        command(0x21); // INVERSION ON (common IPS modules)
        command(0x29);Thread.sleep(20); // DISPLAY ON
    }
    public void frame(byte[] rgb565) throws IOException {
        if(rgb565.length!=Gc9a01Frames.BYTES)throw new IllegalArgumentException("Expected 240x240 RGB565 frame");
        command(0x2A,(byte)0,(byte)0,(byte)0,(byte)239);
        command(0x2B,(byte)0,(byte)0,(byte)0,(byte)239);
        gpio.setData(true,false);spi.write(new byte[]{(byte)0x2C});
        gpio.setData(true,true);
        // CH347 protocol max 507 bytes per SPI transaction; align RGB565 pairs.
        for(int offset=0;offset<rgb565.length;offset+=506){
            if(Thread.currentThread().isInterrupted())
                throw new IOException("Frame transfer interrupted");
            spi.write(Arrays.copyOfRange(rgb565,offset,Math.min(offset+506,rgb565.length)));
        }
    }
}
