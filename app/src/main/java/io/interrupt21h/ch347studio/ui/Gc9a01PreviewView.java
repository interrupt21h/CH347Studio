package io.interrupt21h.ch347studio.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import io.interrupt21h.ch347studio.devices.Gc9a01Frames;

/** Local 240x240 round display preview; deliberately never sends SPI commands. */
public final class Gc9a01PreviewView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap image;
    private int pattern=1;
    public Gc9a01PreviewView(Context context){ super(context);refresh();setContentDescription("GC9A01 local RGB565 preview"); }
    public void setPattern(int pattern){this.pattern=pattern;refresh();invalidate();}
    private void refresh() {
        int[] pixels=new int[240*240];
        for(int y=0;y<240;y++)for(int x=0;x<240;x++) {
            int value=Gc9a01Frames.color(x,y,pattern);
            int r=((value>>>11)&31)*255/31,g=((value>>>5)&63)*255/63,b=(value&31)*255/31;
            pixels[y*240+x]=0xFF000000|(r<<16)|(g<<8)|b;
        }
        image=Bitmap.createBitmap(pixels,240,240,Bitmap.Config.ARGB_8888);
    }
    @Override protected void onMeasure(int w,int h) {
        int d=(int)(280*getResources().getDisplayMetrics().density+0.5f);
        setMeasuredDimension(MeasureSpec.getSize(w),resolveSize(d,h));
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float side=Math.min(getWidth()-20,getHeight()-18);
        float left=(getWidth()-side)/2,top=(getHeight()-side)/2;
        paint.setColor(0xFF050B16);c.drawCircle(getWidth()/2f,getHeight()/2f,side/2+6,paint);
        Path circle=new Path();circle.addCircle(getWidth()/2f,getHeight()/2f,side/2,Path.Direction.CW);
        c.save();c.clipPath(circle);
        c.drawBitmap(image,null,new RectF(left,top,left+side,top+side),paint);
        c.restore();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
        paint.setColor(0xFF3E526D);c.drawCircle(getWidth()/2f,getHeight()/2f,side/2+3,paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
