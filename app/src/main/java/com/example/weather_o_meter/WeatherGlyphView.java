package com.example.weather_o_meter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;

public final class WeatherGlyphView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Integer code;
    private Boolean daylight;
    public WeatherGlyphView(Context context, AttributeSet attrs) { super(context, attrs); }
    public WeatherGlyphView(Context context) { super(context); }
    public void setForecast(HourlyForecast hour) {
        code = hour == null ? null : hour.getWeatherCode();
        daylight = hour == null ? null : hour.getDaylight();
        setContentDescription(WeatherUi.condition(getContext(), code, daylight));
        invalidate();
    }
    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        drawSymbol(canvas, paint, code, daylight, getWidth()/2f, getHeight()/2f,
                Math.min(getWidth(), getHeight())*.9f);
    }
    static void drawSymbol(Canvas c, Paint p, Integer code, Boolean daylight, float x, float y, float size) {
        c.save(); c.translate(x-size/2, y-size/2); c.scale(size/100, size/100);
        WeatherCondition kind = WeatherCondition.fromCode(code);
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.ROUND);
        if (kind == WeatherCondition.UNKNOWN) {
            p.setColor(0x99DCE8F8); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            c.drawCircle(50,50,25,p); p.setStyle(Paint.Style.FILL); p.setTextSize(29);
            p.setTextAlign(Paint.Align.CENTER); c.drawText("?",50,60,p); c.restore(); return;
        }
        if (kind == WeatherCondition.CLEAR || kind == WeatherCondition.PARTLY_CLOUDY) {
            float sx = kind == WeatherCondition.CLEAR ? 50 : 38;
            if (Boolean.FALSE.equals(daylight)) {
                p.setColor(0xFFFFE8BB);
                Path moon = new Path(); moon.moveTo(sx+12,20);
                moon.cubicTo(sx-25,18,sx-30,63,sx+7,65);
                moon.cubicTo(sx-9,51,sx-5,31,sx+12,20); c.drawPath(moon,p);
                c.drawCircle(sx+24,27,2,p); c.drawCircle(sx+30,45,1.5f,p);
            } else {
                p.setColor(0xFFFFD79B); c.drawCircle(sx,43,17,p);
                p.setStrokeWidth(3);
                for(int i=0;i<8;i++) {
                    double a=i*Math.PI/4;
                    c.drawLine(sx+(float)Math.cos(a)*24,43+(float)Math.sin(a)*24,
                            sx+(float)Math.cos(a)*29,43+(float)Math.sin(a)*29,p);
                }
            }
        }
        if (kind != WeatherCondition.CLEAR) {
            p.setColor(Boolean.FALSE.equals(daylight) ? 0xFFB8C9E1 : 0xFFE3EDF6);
            c.drawCircle(39,49,18,p); c.drawCircle(57,42,22,p); c.drawCircle(75,53,16,p);
            c.drawRoundRect(new RectF(23,49,88,68),9,9,p);
            if (kind == WeatherCondition.RAIN || kind == WeatherCondition.STORM) {
                p.setColor(0xFF8CD8FC); p.setStrokeWidth(3);
                for(int i=0;i<3;i++) c.drawLine(36+i*19,76,32+i*19,85,p);
            }
            if (kind == WeatherCondition.SNOW) {
                p.setColor(0xFFE2F7FF); p.setStrokeWidth(2);
                for(int i=0;i<3;i++) {
                    float sx=35+i*20;
                    c.drawLine(sx-3,80,sx+3,80,p); c.drawLine(sx,77,sx,83,p);
                    c.drawLine(sx-2,78,sx+2,82,p);
                }
            }
            if(kind == WeatherCondition.STORM) {
                p.setColor(0xFFFFD689); Path bolt=new Path();
                bolt.moveTo(58,62); bolt.lineTo(47,78); bolt.lineTo(57,78);
                bolt.lineTo(51,94); bolt.lineTo(70,72); bolt.lineTo(59,72); bolt.close(); c.drawPath(bolt,p);
            }
            if(kind == WeatherCondition.FOG) {
                p.setColor(0xBBD2E2ED); p.setStrokeWidth(3);
                c.drawLine(20,76,77,76,p); c.drawLine(32,85,89,85,p);
            }
        }
        c.restore();
    }
}
