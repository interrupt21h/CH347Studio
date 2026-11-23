package io.interrupt21h.ch347studio.devices;

import java.util.ArrayList;
import java.util.List;

/** An address map is NOT a chip identity map. Thread-safe snapshots for UI rendering. */
public final class BusMap {
    public static final int UNKNOWN = 0, EMPTY = 1, ACK = 2, ERROR = 3, RESERVED = 4;
    private final int[] state = new int[128];
    private int scanned = 0;
    public BusMap() { reset(); }
    public synchronized void reset() {
        for (int i = 0; i < 128; i++) state[i] = i < 0x08 || i > 0x77 ? RESERVED : UNKNOWN;
        scanned = 0;
    }
    public synchronized void record(int address, int result) {
        if (address < 0x08 || address > 0x77) throw new IllegalArgumentException("Reserved I2C address");
        if (result != ACK && result != EMPTY && result != ERROR) throw new IllegalArgumentException("Invalid scan result");
        if (state[address] == UNKNOWN) scanned++;
        state[address] = result;
    }
    public synchronized int get(int addr) { return state[addr]; }
    public synchronized int[] snapshot() { return state.clone(); }
    public synchronized int scanned() { return scanned; }
    public synchronized int count(int status) {
        int n = 0;
        for (int v : state) if (v == status) n++;
        return n;
    }
    public synchronized List<Integer> present() {
        List<Integer> found = new ArrayList<>();
        for (int i = 0x08; i <= 0x77; i++) if (state[i] == ACK) found.add(i);
        return found;
    }
}
