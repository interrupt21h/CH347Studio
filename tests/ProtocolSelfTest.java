import io.interrupt21h.ch347studio.protocol.Ch347Packets;
import io.interrupt21h.ch347studio.protocol.Hex;
import java.util.Arrays;

public final class ProtocolSelfTest {
    private static int tests = 0;
    private static void eq(String expected, byte[] got) {
        String actual = Hex.format(got);
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " got " + actual);
        tests++;
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
        tests++;
    }
    private static void fails(Runnable r) {
        try { r.run(); throw new AssertionError("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { tests++; }
    }
    public static void main(String[] args) {
        eq("00 10 A5 FF", Hex.parse("0x00 : 10 A5, FF"));
        eq("0A 0B", Hex.parse("0a0b"));
        eq("", Hex.parse("  "));
        fails(() -> Hex.parse("AB C"));
        fails(() -> Hex.parse("FG"));
        eq("AA 61 00", Ch347Packets.i2cSpeed(1));
        eq("AA 74 81 20 75 00", Ch347Packets.i2cProbe(0x10));
        eq("AA 74 83 20 00 FF 75 00", Ch347Packets.i2cWrite(0x10, Hex.parse("00FF")));
        eq("AA 74 81 21 C3 C0 75 00", Ch347Packets.i2cRead(0x10, 4));
        eq("AA 74 82 20 05 74 81 21 C1 C0 75 00", Ch347Packets.i2cWriteRead(0x10, new byte[]{5}, 2));
        check(Ch347Packets.allAck(new byte[]{1,1,1}, 3));
        check(!Ch347Packets.allAck(new byte[]{1,0,1}, 3));
        fails(() -> Ch347Packets.i2cProbe(0x78));
        fails(() -> Ch347Packets.i2cRead(0x10,64));
        fails(() -> Ch347Packets.i2cWrite(0x10,new byte[63]));
        eq("CA 01 00 01", Ch347Packets.getSpiConfig());
        eq("C1 0A 00 80 00 00 00 00 00 00 00 00 00", Ch347Packets.spiCs(0,true));
        eq("C1 0A 00 00 00 00 00 00 C0 00 00 00 00", Ch347Packets.spiCs(1,false));
        eq("C2 03 00 9F 00 00", Ch347Packets.spiDuplex(Hex.parse("9F0000")));
        eq("9F 12 34", Ch347Packets.parseSpiReply(Hex.parse("C2 0300 9F1234"),3));
        fails(() -> Ch347Packets.spiDuplex(new byte[0]));
        byte[] cfg = new byte[26];
        byte[] set = Ch347Packets.spiConfig(cfg,3,6);
        check(set.length == 29 && (set[0]&0xff)==0xC0 && set[1] == 26);
        check(set[3+6]==1 && set[3+8]==1 && (set[3+12]&0xff)==48);
        check(Arrays.equals(cfg,new byte[26])); // Configuration builder must not mutate caller buffer.
        System.out.println("PASS " + tests + " protocol assertions");
    }
}
