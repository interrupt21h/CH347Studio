package io.interrupt21h.ch347studio.devices;

/** LAFVIN 2.13-inch V4 e-paper: 122 × 250, row-major 1-bit frame, high bit first. */
public final class EpaperFrames {
    public static final int WIDTH=122, HEIGHT=250, STRIDE=(WIDTH+7)/8;
    public static final int SIZE=STRIDE*HEIGHT;
    private EpaperFrames() {}
    public static byte[] make(int pattern) {
        if (pattern < 0 || pattern > 3) throw new IllegalArgumentException("Pattern 0..3");
        byte[] out=new byte[SIZE];
        for (int y=0;y<HEIGHT;y++) for(int x=0;x<STRIDE*8;x++) {
            boolean white = x>=WIDTH;
            if (!white) {
                switch (pattern) {
                    case 0: white = true; break;
                    case 1: white = ((x/8+y/8)&1)==0; break;
                    case 2: white = ((x/12)&1)==0; break;
                    default: white = x>3&&y>3&&x<WIDTH-4&&y<HEIGHT-4&&
                        (Math.abs(x-WIDTH/2)>3||Math.abs(y-HEIGHT/2)>3); break;
                }
            }
            if(white) out[y*STRIDE+x/8] |= (byte)(0x80>>(x&7));
        }
        return out;
    }
    public static boolean pixel(byte[] frame,int x,int y) {
        if (frame.length!=SIZE || x<0 || x>=WIDTH || y<0 || y>=HEIGHT) throw new IllegalArgumentException("Bad e-paper coordinate or frame");
        return (frame[y*STRIDE+x/8] & (0x80>>(x&7)))!=0;
    }
}
