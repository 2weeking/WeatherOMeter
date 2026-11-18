package com.example.weather_o_meter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;

/** A probability-filled droplet or a compass for the day's dominant wind direction. */
public final class MetricArtView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path wave = new Path();
    private final RectF highlight = new RectF(29,45,71,85);
    private Shader waterShader;
    private final Rect visible = new Rect();
    private Double value;
    private boolean compass, motion = true;
    public MetricArtView(Context c, AttributeSet a) { super(c,a); }
    public void setProbability(Double probability) {
        compass=false; value=probability;
        float water=probability==null?100:100-(float)(probability*.94);
        waterShader=new LinearGradient(0,water,0,96,0xFFC5EFFF,0xFF52AEDB,Shader.TileMode.CLAMP);
        invalidate();
    }
    public void setDirection(Double direction) { compass=true; value=direction; invalidate(); }
    public void setMotionEnabled(boolean enabled) { motion=enabled; invalidate(); }
    @Override protected void onDraw(@NonNull Canvas c) {
        super.onDraw(c);
        float size=Math.min(getWidth(),getHeight());
        c.save(); c.translate((getWidth()-size)/2,(getHeight()-size)/2); c.scale(size/100,size/100);
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f);
        paint.setColor(0x44D6E8F8);
        if(compass) {
            c.drawCircle(50,50,37,paint);
            for(int i=0;i<24;i++) {
                double a=i*Math.PI/12;
                float inner=i%6==0?30:34;
                c.drawLine(50+(float)Math.sin(a)*inner,50-(float)Math.cos(a)*inner,
                        50+(float)Math.sin(a)*37,50-(float)Math.cos(a)*37,paint);
            }
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(10);
            paint.setColor(0xFFD8E7F6); c.drawText("N",50,10,paint); c.drawText("S",50,98,paint);
            c.drawText("W",5,54,paint); c.drawText("E",95,54,paint);
            if(value!=null) {
                c.save(); c.rotate(value.floatValue(),50,50);
                path.reset(); path.moveTo(50,20); path.lineTo(40,56); path.lineTo(50,50); path.lineTo(60,56); path.close();
                paint.setColor(0xFFFFCEAB); c.drawPath(path,paint);
                paint.setColor(0x77FFE9D8); c.drawLine(50,50,50,77,paint); c.restore();
            }
            paint.setColor(0xFFEAF2FA); c.drawCircle(50,50,3,paint);
        } else {
            path.reset(); path.moveTo(50,6); path.cubicTo(42,24,21,44,21,62);
            path.cubicTo(21,101,79,101,79,62); path.cubicTo(79,44,58,24,50,6); path.close();
            paint.setStyle(Paint.Style.FILL); paint.setColor(0x0FD5EEFF); c.drawPath(path,paint);
            if(value!=null) {
                c.save(); c.clipPath(path);
                float water=100-(float)(value*.94);
                float phase=motion?(SystemClock.uptimeMillis()%4000)/4000f*6.283f:0;
                wave.reset(); wave.moveTo(12,100); wave.lineTo(12,water);
                for(int i=12;i<=88;i+=2) wave.lineTo(i,water+(float)Math.sin(i*.09f+phase)*(value==0 || value==100?0:2.4f));
                wave.lineTo(88,100); wave.close();
                paint.setColor(0xFFFFFFFF); paint.setShader(waterShader);
                c.drawPath(wave,paint); paint.setShader(null); c.restore();
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f); paint.setColor(0xFFBDEAFF); c.drawPath(path,paint);
            paint.setColor(0x88E0F6FF); c.drawArc(highlight,110,65,false,paint);
        }
        c.restore();
        if(!compass && motion && value!=null && value>0 && value<100
                && getWindowVisibility()==VISIBLE && getGlobalVisibleRect(visible)) postInvalidateDelayed(80);
    }
}
