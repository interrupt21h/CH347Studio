package io.interrupt21h.ch347studio.devices;

import java.io.IOException;
import java.util.Locale;

/**
 * MCP23017 GPIO using the factory IOCON.BANK=0 map.
 * Explicit configuration only; all register updates are read/modify/write.
 * This is NOT the SPI MCP23S17 driver.
 */
public final class Mcp23017Gpio implements GpioController {
    private static final int[] IODIR={0x00,0x01}, GPIO={0x12,0x13}, OLAT={0x14,0x15};
    private final DeviceScripts.I2c bus;
    private final int address;

    public Mcp23017Gpio(DeviceScripts.I2c bus, int address) {
        if(bus==null)throw new IllegalArgumentException("I2C bus missing");
        if(address<0x20 || address>0x27)throw new IllegalArgumentException("MCP23017 7-bit address must be 0x20..0x27");
        this.bus=bus;this.address=address;
    }
    public int address(){return address;}
    private static void validate(int pin){if(pin<0 || pin>=16)throw new IllegalArgumentException("GPIO pin index must be 0..15");}
    private int reg(int address) throws IOException {
        byte[] b=bus.read(this.address,new byte[]{(byte)address},1);
        if(b.length!=1)throw new IOException("MCP23017 short read from register "+address);
        return b[0]&255;
    }
    private void set(int reg,int value) throws IOException {bus.write(address,new byte[]{(byte)reg,(byte)value});}
    /** Refuse to drive when BANK=1 could change the address mapping. */
    public void verifyLayout() throws IOException {
        int a=reg(0x0A), b=reg(0x0B);
        if ((a & 0x81)!=0 || a!=b)
            throw new IOException(String.format(Locale.US,"Unexpected IOCON 0x0A=%02X,0x0B=%02X: BANK=0 map required",a,b));
    }
    @Override public synchronized void output(int pin,boolean initialHigh) throws IOException {
        validate(pin);verifyLayout();
        int port=pin>>>3,bit=1<<(pin&7);
        int desired=reg(OLAT[port]);
        desired=initialHigh?desired|bit:desired&~bit;
        set(OLAT[port],desired); // establish correct level before changing direction
        set(IODIR[port],reg(IODIR[port])&~bit);
    }
    @Override public synchronized void input(int pin) throws IOException {
        validate(pin);verifyLayout();int port=pin>>>3;
        set(IODIR[port],reg(IODIR[port]) | (1<<(pin&7)));
    }
    @Override public synchronized void write(int pin,boolean high) throws IOException {
        validate(pin);verifyLayout();int port=pin>>>3,bit=1<<(pin&7);
        if((reg(IODIR[port])&bit)!=0)throw new IOException("Refusing to drive input pin "+pin);
        int value=reg(OLAT[port]);
        set(OLAT[port],high?value|bit:value&~bit);
    }
    @Override public synchronized boolean read(int pin) throws IOException {
        validate(pin);verifyLayout();
        return (reg(GPIO[pin>>>3]) & (1<<(pin&7)))!=0;
    }
    @Override public synchronized Snapshot snapshot() throws IOException {
        verifyLayout();
        return new Snapshot(new int[]{reg(0x00),reg(0x01)},
                new int[]{reg(0x12),reg(0x13)},new int[]{reg(0x14),reg(0x15)});
    }
}
