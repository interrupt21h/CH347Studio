package io.interrupt21h.ch347studio.devices;

import java.io.IOException;
import java.util.Locale;

/** TCA9548A channel selection: a ONE-BYTE control register, NOT a register-indexed write. */
public final class MuxTools {
    private MuxTools() {}
    public interface Operation<T> { T execute() throws Exception; }
    public interface Progress { void scanned(int address, int state); }
    public static void validate(int address, int channel) {
        if (address < 0x70 || address > 0x77) throw new IllegalArgumentException("TCA9548A address must be 0x70–0x77");
        if (channel < 0 || channel > 7) throw new IllegalArgumentException("TCA9548A channel must be 0–7");
    }
    public static int readMask(DeviceScripts.I2c bus, int address) throws IOException {
        validate(address,0);
        byte[] b=bus.read(address,new byte[0],1);
        if (b.length != 1) throw new IOException("TCA9548A control register response length " + b.length);
        return b[0]&255;
    }
    public static void setMask(DeviceScripts.I2c bus,int address,int mask) throws IOException {
        validate(address,0);
        if ((mask&~255)!=0) throw new IllegalArgumentException("Mux mask must be 0..255");
        bus.write(address,new byte[]{(byte)mask});
    }
    /** Always restores the original mask even if downstream probing throws. */
    public static <T> T withChannel(DeviceScripts.I2c bus,int address,int channel,Operation<T> body) throws Exception {
        validate(address,channel);
        int old=readMask(bus,address);
        Exception failure=null;
        try {
            setMask(bus,address,1<<channel);
            return body.execute();
        } catch (Exception e) {
            failure=e; throw e;
        } finally {
            try { setMask(bus,address,old); }
            catch (IOException restore) {
                if (failure != null) failure.addSuppressed(restore);
                else throw new IOException(String.format(Locale.US,
                     "Mux 0x%02X CH%d could not restore mask 0x%02X: %s",address,channel,old,restore.getMessage()),restore);
            }
        }
    }
    /** Explicit scan only; no automatic probing of downstream branches. */
    public static void scanChannel(DeviceScripts.I2c bus,int address,int channel,BusMap result,Progress progress) throws Exception {
        result.reset();
        withChannel(bus,address,channel,() -> {
            for (int a=0x08;a<=0x77;a++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Scan interrupted");
                if (a==address) continue; // mux's own upstream address is not a downstream endpoint
                int state;
                try { state=bus.probe(a)?BusMap.ACK:BusMap.EMPTY; }
                catch (IOException e) {state=BusMap.ERROR;}
                result.record(a,state);
                if(progress!=null) progress.scanned(a,state);
            }
            return null;
        });
    }
}
