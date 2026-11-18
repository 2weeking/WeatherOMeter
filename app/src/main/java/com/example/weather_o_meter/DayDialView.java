package com.example.weather_o_meter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/** Touch an hour on the clock; a native slider provides the accessible equivalent. */
public final class DayDialView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<HourlyForecast> hours = Collections.emptyList();
    private int selected;
    private IntConsumer listener;
    public DayDialView(Context c, AttributeSet a) { super(c,a); }
    public void setHours(List<HourlyForecast> values, int index, IntConsumer onSelect) {
        hours=values; selected=index; listener=onSelect; invalidate();
    }
    public void setSelectedHour(int index) { selected=index; invalidate(); }
    @Override protected void onDraw(@NonNull Canvas c) {
        super.onDraw(c);
        float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(getWidth(),getHeight())*.38f;
        float density=getResources().getDisplayMetrics().density;
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density);
        paint.setColor(0x26FFFFFF); c.drawCircle(cx,cy,r,paint);
        for(int i=0;i<hours.size();i++) {
            HourlyForecast hour=hours.get(i); double a=hour.getHour()*Math.PI/12-Math.PI/2;
            float x=cx+(float)Math.cos(a)*r, y=cy+(float)Math.sin(a)*r;
            paint.setStrokeWidth(i==selected?4*density:2.5f*density);
            paint.setColor(i==selected?0xFFFFFFFF:Boolean.TRUE.equals(hour.getDaylight())?0xA6FFDCAB:0xA67AA6D6);
            float inner=r-9*density;
            c.drawLine(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,x,y,paint);
            if(hour.getTemperatureFahrenheit()!=null) {
                paint.setStyle(Paint.Style.FILL); paint.setColor(WeatherUi.temperatureColor(hour.getTemperatureFahrenheit()));
                c.drawCircle(cx+(float)Math.cos(a)*(r+5*density),cy+(float)Math.sin(a)*(r+5*density),
                        (i==selected?3:1.8f)*density,paint); paint.setStyle(Paint.Style.STROKE);
            }
        }
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10*getResources().getDisplayMetrics().scaledDensity); paint.setColor(0xBBD4E2F4);
        c.drawText("00",cx,cy-r-11*density,paint); c.drawText("12",cx,cy+r+20*density,paint);
        c.drawText("06",cx+r+18*density,cy+4*density,paint); c.drawText("18",cx-r-18*density,cy+4*density,paint);
        if(!hours.isEmpty() && selected<hours.size()) {
            HourlyForecast hour=hours.get(selected);
            WeatherGlyphView.drawSymbol(c,paint,hour.getWeatherCode(),hour.getDaylight(),cx,cy-15*density,64*density);
            paint.setColor(0xFFFFFFFF); paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(26*getResources().getDisplayMetrics().scaledDensity);
            c.drawText(WeatherUi.temperature(getContext(),hour.getTemperatureFahrenheit()),cx,cy+43*density,paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(hours.isEmpty()) return false;
        float x=event.getX()-getWidth()/2f, y=event.getY()-getHeight()/2f;
        if(event.getAction()==MotionEvent.ACTION_DOWN && Math.hypot(x,y)<Math.min(getWidth(),getHeight())*.2) return false;
        if(event.getAction()==MotionEvent.ACTION_DOWN || event.getAction()==MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            double a=(Math.atan2(y,x)+Math.PI/2+Math.PI*2)%(Math.PI*2);
            int target=(int)Math.round(a/(Math.PI*2)*24)%24, nearest=0, distance=25;
            for(int i=0;i<hours.size();i++) {
                int d=Math.abs(hours.get(i).getHour()-target); d=Math.min(d,24-d);
                if(d<distance) { distance=d; nearest=i; }
            }
            if(nearest!=selected) { selected=nearest; invalidate(); if(listener!=null) listener.accept(selected); }
            return true;
        }
        if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false); performClick(); return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
