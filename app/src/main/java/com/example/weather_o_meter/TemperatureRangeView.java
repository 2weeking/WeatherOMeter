package com.example.weather_o_meter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;

public final class TemperatureRangeView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private DailyForecast day;
    private double min, max;
    private Shader rangeShader;
    public TemperatureRangeView(Context context) { super(context); }
    public TemperatureRangeView(Context context, AttributeSet attrs) { super(context,attrs); }
    public void setRange(DailyForecast value,double minimum,double maximum) {
        day=value; min=minimum; max=Math.max(minimum+1,maximum); updateShader(); invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { updateShader(); }
    private void updateShader() {
        if(day==null || day.getLowFahrenheit()==null || getWidth()==0) return;
        float d=getResources().getDisplayMetrics().density,left=28*d,right=getWidth()-28*d;
        float x1=left+(float)((day.getLowFahrenheit()-min)/(max-min))*(right-left);
        float x2=left+(float)((day.getHighFahrenheit()-min)/(max-min))*(right-left);
        rangeShader=new LinearGradient(x1,0,Math.max(x1+1,x2),0,
                WeatherUi.temperatureColor(day.getLowFahrenheit()),WeatherUi.temperatureColor(day.getHighFahrenheit()),Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(@NonNull Canvas c) {
        super.onDraw(c);
        if(day==null || day.getLowFahrenheit()==null) return;
        float d=getResources().getDisplayMetrics().density, left=28*d, right=getWidth()-28*d, y=getHeight()*.58f;
        double low=day.getLowFahrenheit(), high=day.getHighFahrenheit();
        float x1=left+(float)((low-min)/(max-min))*(right-left), x2=left+(float)((high-min)/(max-min))*(right-left);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(5*d); paint.setColor(0x20FFFFFF);
        c.drawLine(left,y,right,y,paint);
        paint.setColor(0xFFFFFFFF); paint.setShader(rangeShader);
        c.drawLine(x1,y,Math.max(x1+2*d,x2),y,paint); paint.setShader(null);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(left+(float)((day.getAverageFahrenheit()-min)/(max-min))*(right-left),y,3.5f*d,paint);
        paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(0xFFCCDFED); paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(WeatherUi.temperature(getContext(),low),0,y+4*d,paint);
        paint.setTextAlign(Paint.Align.RIGHT); c.drawText(WeatherUi.temperature(getContext(),high),getWidth(),y+4*d,paint);
    }
}
