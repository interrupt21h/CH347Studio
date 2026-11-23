package io.interrupt21h.ch347studio.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import io.interrupt21h.ch347studio.devices.EpaperFrames;

/** Local-only 122 × 250 preview (does not claim the physical panel was updated). */
public final class EpaperPreviewView extends View {
    private Bitmap preview;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    public EpaperPreviewView(Context context) {super(context);setPattern(1);}
    public void setPattern(int pattern) {
        byte[] frame=EpaperFrames.make(pattern);
        int[] pixels=new int[EpaperFrames.WIDTH*EpaperFrames.HEIGHT];
        for(int y=0;y<EpaperFrames.HEIGHT;y++)for(int x=0;x<EpaperFrames.WIDTH;x++)
            pixels[y*EpaperFrames.WIDTH+x]=EpaperFrames.pixel(frame,x,y)?Color.WHITE:Color.BLACK;
        if(preview!=null)preview.recycle();
        preview=Bitmap.createBitmap(pixels,EpaperFrames.WIDTH,EpaperFrames.HEIGHT,Bitmap.Config.ARGB_8888);
        setContentDescription("E-paper frame preview pattern "+pattern+", simulated only");
        invalidate();
    }
    @Override protected void onMeasure(int w,int h) {
        float d=getResources().getDisplayMetrics().density;
        setMeasuredDimension(MeasureSpec.getSize(w),resolveSize((int)(340*d),h));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF223047);
        float scale=Math.min((getWidth()-32f)/EpaperFrames.WIDTH,(getHeight()-24f)/EpaperFrames.HEIGHT);
        float width=scale*EpaperFrames.WIDTH,height=scale*EpaperFrames.HEIGHT;
        float x=(getWidth()-width)/2,y=(getHeight()-height)/2;
        paint.setFilterBitmap(false);
        canvas.drawBitmap(preview,null,new RectF(x,y,x+width,y+height),paint);
    }
}
