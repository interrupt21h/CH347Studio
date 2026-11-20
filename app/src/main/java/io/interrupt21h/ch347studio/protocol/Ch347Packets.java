package io.interrupt21h.ch347studio.protocol;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** CH347T vendor BULK mode stream commands; separate from CH347 HID report framing. */
public final class Ch347Packets {
    private Ch347Packets() {}
    private static final int STREAM = 0xAA, START = 0x74, STOP = 0x75, END = 0x00;

    public static byte[] i2cSpeed(int selection) {
        if (selection < 0 || selection > 3) throw new IllegalArgumentException("I2C speed 0..3");
        return bytes(STREAM, 0x60 | selection, END);
    }

    public static byte[] i2cProbe(int address) {
        address(address);
        return bytes(STREAM, START, 0x81, address << 1, STOP, END);
    }

    public static byte[] i2cWrite(int address, byte[] data) {
        address(address);
        if (data.length > 62) throw new IllegalArgumentException("I2C write maximum is 62 bytes");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(STREAM); b.write(START); b.write(0x81 + data.length); b.write(address << 1);
        b.write(data, 0, data.length); b.write(STOP); b.write(END);
        return b.toByteArray();
    }

    public static byte[] i2cRead(int address, int count) {
        return i2cWriteRead(address, new byte[0], count);
    }

    /** Combined transfer: the address/data phase and read phase use a repeated START. */
    public static byte[] i2cWriteRead(int address, byte[] prefix, int count) {
        address(address);
        if (count < 1 || count > 63) throw new IllegalArgumentException("I2C read length 1..63");
        if (prefix.length > 62) throw new IllegalArgumentException("I2C prefix maximum is 62 bytes");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(STREAM);
        if (prefix.length > 0) {
            b.write(START); b.write(0x81 + prefix.length); b.write(address << 1);
            b.write(prefix, 0, prefix.length);
        }
        b.write(START); b.write(0x81); b.write((address << 1) | 1);
        if (count > 1) b.write(0xC0 | (count - 1));
        b.write(0xC0); // Final single-byte read NACKs instead of ACKing.
        b.write(STOP); b.write(END);
        return b.toByteArray();
    }

    public static boolean allAck(byte[] status, int count) {
        if (status.length != count) throw new IllegalArgumentException("Incorrect status length");
        for (byte b : status) if (b != 1) return false;
        return true;
    }

    public static byte[] getSpiConfig() { return bytes(0xCA, 0x01, 0x00, 0x01); }

    public static byte[] spiConfig(byte[] previous, int mode, int prescaler) {
        if (previous.length != 26) throw new IllegalArgumentException("SPI config must be 26 bytes");
        if (mode < 0 || mode > 3 || prescaler < 0 || prescaler > 7)
            throw new IllegalArgumentException("Invalid SPI mode/prescaler");
        byte[] cfg = previous.clone();
        cfg[6] = (byte) (mode >> 1); cfg[7] = 0; // polarity
        cfg[8] = (byte) (mode & 1); cfg[9] = 0;  // phase
        cfg[12] = (byte) (prescaler << 3); cfg[13] = 0;
        byte[] cmd = new byte[29]; cmd[0] = (byte) 0xC0; cmd[1] = 26;
        System.arraycopy(cfg, 0, cmd, 3, 26);
        return cmd;
    }

    public static byte[] spiCs(int channel, boolean assertCs) {
        if (channel != 0 && channel != 1) throw new IllegalArgumentException("SPI CS 0 or 1");
        byte[] packet = new byte[13]; packet[0] = (byte) 0xC1; packet[1] = 10;
        packet[channel == 0 ? 3 : 8] = (byte) (assertCs ? 0x80 : 0xC0);
        return packet;
    }

    public static byte[] spiDuplex(byte[] tx) {
        if (tx.length < 1 || tx.length > 507) throw new IllegalArgumentException("SPI transfer 1..507 bytes");
        byte[] p = new byte[3 + tx.length]; p[0] = (byte) 0xC2;
        p[1] = (byte) tx.length; p[2] = (byte) (tx.length >>> 8);
        System.arraycopy(tx, 0, p, 3, tx.length);
        return p;
    }

    public static byte[] parseSpiReply(byte[] reply, int length) {
        if (reply.length != length + 3 || (reply[0] & 0xff) != 0xC2 ||
                (reply[1] & 0xff) != (length & 0xff) || (reply[2] & 0xff) != (length >>> 8))
            throw new IllegalStateException("Unexpected SPI response framing: " + Hex.format(reply));
        return Arrays.copyOfRange(reply, 3, reply.length);
    }

    private static void address(int a) {
        if (a < 0x08 || a > 0x77) throw new IllegalArgumentException("7-bit I2C address 0x08..0x77");
    }
    private static byte[] bytes(int... ints) {
        byte[] result = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) result[i] = (byte) ints[i];
        return result;
    }
}
