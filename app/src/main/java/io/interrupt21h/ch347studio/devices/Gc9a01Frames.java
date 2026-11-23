package io.interrupt21h.ch347studio.devices;

/** Pure Java round GC9A01 240x240 RGB565 frame generation. Local previews only. */
public final class Gc9a01Frames {
    public static final int WIDTH=240, HEIGHT=240, BYTES=WIDTH*HEIGHT*2;
    private Gc9a01Frames() {}
    public static int rgb565(int r,int g,int b) {
        return ((r&255)>>3)<<11 | ((g&255)>>2)<<5 | ((b&255)>>3);
    }
    public static int color(int x,int y,int pattern) {
        int p=Math.floorMod(pattern,4);
        if (p==0) return 0;
        if (p==1) {int[] c={0xF800,0x07E0,0x001F,0xFFE0,0xF81F,0x07FF};return c[Math.min(5,x/40)];}
        if (p==2) {int g=x*255/239;return rgb565(g,g,g);}
        int dx=x-120,dy=y-120;
        if (dx*dx+dy*dy < 90*90 && (Math.abs(dx)<3 || Math.abs(dy)<3))return 0xFFFF;
        return ((x/12+y/12)&1)==0?rgb565(30,75,145):rgb565(8,20,45);
    }
    public static byte[] make(int pattern) {
        byte[] b=new byte[BYTES];int k=0;
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            int pixel=color(x,y,pattern);
            b[k++]=(byte)(pixel>>>8);b[k++]=(byte)pixel;
        }
        return b;
    }
    public static String wiring() {
        return "GC9A01 240x240 round TFT (4-wire SPI)\n"
            +"VCC: verify breakout voltage; GND: common ground\n"
            +"SCL→SCK, SDA→MOSI, CS→CH347 CS0; MISO generally unused\n"
            +"DC (command/data), RES (reset), BLK (backlight) each need separate GPIO/control.\n"
            +"Command 0x2A: X window; 0x2B: Y window; 0x2C: RGB565 pixel write.\n"
            +"115,200 bytes per 240×240 frame, MSB first; initialization differs among modules.\n"
            +"No reliable SPI identity scan; previews do not write to the panel.";
    }
}
