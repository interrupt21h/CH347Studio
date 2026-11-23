package io.interrupt21h.ch347studio.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import io.interrupt21h.ch347studio.devices.BusMap;
import java.util.Locale;

/** 8-column / 16-row interactive address matrix; 0x00-0x7F. */
public final class BusMatrixView extends View {
    public interface OnAddressSelected { void selected(int address); }
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BusMap map;
    private OnAddressSelected listener;
    private int selected=-1;
    private int cellHeight, gap, columns=8;
    public BusMatrixView(Context ctx,BusMap map) {
        super(ctx); this.map=map;
        float density=getResources().getDisplayMetrics().density;
        cellHeight=(int)(38*density+0.5f);gap=(int)(4*density+0.5f);
        setContentDescription("I2C address matrix: tap an address to select it");
        setFocusable(true);
    }
    public void setSelectedAddress(int address) { selected=address; invalidate(); }
    public void setOnAddressSelected(OnAddressSelected l) { listener=l; }
    @Override protected void onMeasure(int w,int h) {
        int wanted=16*cellHeight+gap*2;
        setMeasuredDimension(MeasureSpec.getSize(w),resolveSize(wanted,h));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int[] states=map.snapshot();
        float cw=(getWidth()-gap*(columns+1))/(float)columns;
        type.setTextAlign(Paint.Align.CENTER);
        type.setTypeface(android.graphics.Typeface.create("monospace",android.graphics.Typeface.BOLD));
        type.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);
        for(int index=0;index<128;index++) {
            int row=index/columns,col=index%columns;
            float x=gap+col*(cw+gap),y=gap+row*cellHeight;
            int v=states[index];
            int bg=v==BusMap.RESERVED?0xFF10192B:v==BusMap.ACK?0xFF185E55:
                   v==BusMap.ERROR?0xFF632F41:v==BusMap.EMPTY?0xFF223048:0xFF2C415D;
            if(index==selected) bg=0xFF4064A3;
            fill.setColor(bg);
            canvas.drawRoundRect(new RectF(x,y,x+cw,y+cellHeight-gap),8,8,fill);
            type.setColor(v==BusMap.ACK?0xFF69F5C9:v==BusMap.RESERVED?0xFF617187:
                         v==BusMap.ERROR?0xFFFFA7B2:0xFFCCD9EB);
            canvas.drawText(String.format(Locale.US,"%02X",index),x+cw/2,y+(cellHeight-gap)/2-(type.ascent()+type.descent())/2,type);
            if(v==BusMap.ACK) {
                fill.setColor(0xFF4FE3B5);
                canvas.drawCircle(x+cw-5,y+6,2.5f,fill);
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) return true;
        if(e.getActionMasked()==MotionEvent.ACTION_UP) {
            float cw=(getWidth()-gap*(columns+1))/(float)columns;
            int col=(int)((e.getX()-gap)/(cw+gap));
            int row=(int)((e.getY()-gap)/cellHeight);
            if(col>=0&&col<8&&row>=0&&row<16) {
                int address=row*8+col;
                selected=address;invalidate();performClick();
                if(listener!=null)listener.selected(address);
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick() { super.performClick();return true; }
}
