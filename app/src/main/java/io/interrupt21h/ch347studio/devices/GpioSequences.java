package io.interrupt21h.ch347studio.devices;

import java.io.IOException;

/** Coordinated, bounded GPIO sequences; no RF transfer or e-paper refresh encoded here. */
public final class GpioSequences {
    public interface Sleeper { void sleep(long ms) throws InterruptedException; }
    private final GpioController gpio;
    private final PinAssignments pins;
    private final Sleeper sleeper;
    public GpioSequences(GpioController gpio,PinAssignments pins,Sleeper sleeper){
        this.gpio=gpio;this.pins=pins;this.sleeper=sleeper;
    }
    private int required(PinAssignments.Role role) throws IOException {
        int n=pins.get(role);
        if(n<0)throw new IOException(role.label+" is not assigned");
        return n;
    }
    public void arm() throws IOException {
        pins.validate();
        // Put the radio CE output LOW first: never accidentally enter TX or RX mode.
        if(pins.get(PinAssignments.Role.RADIO_CE)>=0)
            gpio.output(pins.get(PinAssignments.Role.RADIO_CE),false);
        // Establish all remaining output latches before setting their direction.
        for(PinAssignments.Role role:PinAssignments.Role.values()){
            if(role==PinAssignments.Role.RADIO_CE)continue;
            int pin=pins.get(role);if(pin<0)continue;
            if(role.output)gpio.output(pin,role.initialHigh);
            else gpio.input(pin);
        }
    }
    public void pulseReset(PinAssignments.Role role,long lowMs,long recoveryMs)
            throws IOException,InterruptedException {
        if(role!=PinAssignments.Role.TFT_RESET && role!=PinAssignments.Role.EPAPER_RESET)
            throw new IllegalArgumentException("Reset role required");
        if(lowMs<1 || lowMs>500 || recoveryMs<1 || recoveryMs>1000)
            throw new IllegalArgumentException("Reset timing out of range");
        int pin=required(role);
        gpio.write(pin,true);gpio.write(pin,false);
        try{sleeper.sleep(lowMs);}finally{gpio.write(pin,true);}
        sleeper.sleep(recoveryMs);
    }
    public void setData(boolean tft,boolean data) throws IOException {
        gpio.write(required(tft?PinAssignments.Role.TFT_DC:PinAssignments.Role.EPAPER_DC),data);
    }
    public void setBacklight(boolean on) throws IOException {
        gpio.write(required(PinAssignments.Role.TFT_BACKLIGHT),on);
    }
    public void radioCe(boolean high) throws IOException {
        gpio.write(required(PinAssignments.Role.RADIO_CE),high);
    }
    public boolean radioIrqHigh() throws IOException {return gpio.read(required(PinAssignments.Role.RADIO_IRQ));}
    public boolean epaperBusyHigh() throws IOException {return gpio.read(required(PinAssignments.Role.EPAPER_BUSY));}
    public int awaitEpaperBusy(boolean activeHigh,long timeoutMs) throws IOException,InterruptedException {
        if(timeoutMs<10||timeoutMs>15000)throw new IllegalArgumentException("Busy timeout must be 10–15000 ms");
        int elapsed=0;
        while(gpio.read(required(PinAssignments.Role.EPAPER_BUSY))==activeHigh){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("BUSY poll cancelled");
            if(elapsed>=timeoutMs)throw new IOException("E-paper BUSY stuck active for "+timeoutMs+" ms");
            sleeper.sleep(20);elapsed+=20;
        }
        return elapsed;
    }
}
