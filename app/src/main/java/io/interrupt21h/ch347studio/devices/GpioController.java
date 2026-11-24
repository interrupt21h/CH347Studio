package io.interrupt21h.ch347studio.devices;

import java.io.IOException;

/** Platform-independent GPIO backend: pin 0..7 = GPA0..7, 8..15 = GPB0..7. */
public interface GpioController {
    void output(int pin, boolean initialHigh) throws IOException;
    void input(int pin) throws IOException;
    void write(int pin, boolean high) throws IOException;
    boolean read(int pin) throws IOException;
    Snapshot snapshot() throws IOException;

    final class Snapshot {
        public final int[] directions, levels, latches;
        public Snapshot(int[] directions, int[] levels, int[] latches) {
            this.directions=directions.clone(); this.levels=levels.clone(); this.latches=latches.clone();
        }
        public boolean isInput(int pin) { return (directions[pin>>>3] & (1 << (pin & 7))) != 0; }
        public boolean isHigh(int pin) { return (levels[pin>>>3] & (1 << (pin & 7))) != 0; }
        public boolean outputHigh(int pin) { return (latches[pin>>>3] & (1 << (pin & 7))) != 0; }
    }
}
