package io.interrupt21h.ch347studio.devices;

import java.util.HashSet;
import java.util.Set;

/** Stored pin-to-signal mapping. A pin may only serve one signal. -1 means disconnected. */
public final class PinAssignments {
    public enum Role {
        EPAPER_RESET("E-paper RESET",true,true),
        EPAPER_DC("E-paper D/C",true,false),
        EPAPER_BUSY("E-paper BUSY",false,false),
        RADIO_CE("nRF24 CE",true,false),
        RADIO_IRQ("nRF24 IRQ",false,false),
        TFT_RESET("GC9A01 RESET",true,true),
        TFT_DC("GC9A01 D/C",true,false),
        TFT_BACKLIGHT("GC9A01 BLK",true,false);
        public final String label;
        public final boolean output, initialHigh;
        Role(String label,boolean output,boolean initialHigh){this.label=label;this.output=output;this.initialHigh=initialHigh;}
    }
    private final int[] pins;
    public PinAssignments(){pins=new int[Role.values().length];for(int i=0;i<pins.length;i++)pins[i]=i;}
    public PinAssignments(int[] input){
        if(input==null||input.length!=Role.values().length)throw new IllegalArgumentException("Invalid pin mapping size");
        pins=input.clone();validate();
    }
    public int get(Role role){return pins[role.ordinal()];}
    public int[] values(){return pins.clone();}
    public void set(Role role,int pin){int before=pins[role.ordinal()];pins[role.ordinal()]=pin;
        try{validate();}catch(IllegalArgumentException ex){pins[role.ordinal()]=before;throw ex;}}
    public void validate(){
        Set<Integer> seen=new HashSet<>();
        for(int p:pins){
            if(p< -1 ||p>15)throw new IllegalArgumentException("Pin must be unassigned or GPA0–GPB7");
            if(p>=0&&!seen.add(p))throw new IllegalArgumentException("Two signals cannot use the same GPIO pin");
        }
    }
    public static String pinLabel(int pin){return pin<0?"Disconnected":(pin<8?"GPA":"GPB")+(pin&7);}
}
