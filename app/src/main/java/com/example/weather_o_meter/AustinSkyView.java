package com.example.weather_o_meter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.os.SystemClock;
import androidx.core.graphics.ColorUtils;

import androidx.annotation.NonNull;

/** Austin scenery responds to actual hourly WMO conditions; precipitation chance never implies rain. */
public final class AustinSkyView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Shader sky;
    private Shader glow;
    private Shader sun;
    private Shader shade;
    private float sunCenterX;
    private float scrollPosition;
    private WeatherCondition condition = WeatherCondition.UNKNOWN;
    private Boolean daylight;
    private boolean motionEnabled = true, active = true;
    private long paletteChanged, frozenAt;
    private int[] targetPalette = {0xFF081C37,0xFF244A79,0xFF70618F,0xFFC3899A,0xFFF0B397,0xFF203C59};
    private int[] fromPalette = targetPalette.clone();
    private final int[] blendedPalette = targetPalette.clone();

    public AustinSkyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setScrollPosition(float position) {
        scrollPosition = position;
        invalidate();
    }

    public void setMotionEnabled(boolean enabled) {
        motionEnabled = enabled;
        if (!enabled) frozenAt = SystemClock.uptimeMillis();
        invalidate();
    }
    public void setActive(boolean enabled) {
        active = enabled;
        if (!enabled) frozenAt = SystemClock.uptimeMillis();
        invalidate();
    }
    public void setForecast(HourlyForecast hour) {
        WeatherCondition next = hour == null ? WeatherCondition.UNKNOWN : WeatherCondition.fromCode(hour.getWeatherCode());
        Boolean day = hour == null ? null : hour.getDaylight();
        if (next == condition && java.util.Objects.equals(day, daylight)) return;
        condition = next; daylight = day; fromPalette = blendedPalette.clone();
        if (Boolean.FALSE.equals(daylight)) {
            targetPalette = new int[]{0xFF050E25,0xFF0A1C3F,0xFF1B3155,0xFF354363,0xFF5D5C79,0xFF102740};
        } else if (condition == WeatherCondition.RAIN || condition == WeatherCondition.STORM) {
            targetPalette = new int[]{0xFF071C31,0xFF163754,0xFF314B68,0xFF576981,0xFF7890A4,0xFF102E47};
        } else if (condition == WeatherCondition.CLOUDY || condition == WeatherCondition.FOG || condition == WeatherCondition.SNOW) {
            targetPalette = new int[]{0xFF0B233D,0xFF284A68,0xFF53677D,0xFF8C849A,0xFFC39EA8,0xFF1B364E};
        } else if (condition == WeatherCondition.CLEAR || condition == WeatherCondition.PARTLY_CLOUDY) {
            targetPalette = new int[]{0xFF103355,0xFF2C668E,0xFF5791AC,0xFFA3ACBE,0xFFF4CAAA,0xFF204255};
        } else {
            targetPalette = new int[]{0xFF081C37,0xFF244A79,0xFF70618F,0xFFC3899A,0xFFF0B397,0xFF203C59};
        }
        paletteChanged = SystemClock.uptimeMillis(); invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        rebuildShaders(width,height);
    }

    private void rebuildShaders(int width,int height) {
        if (width <= 0 || height <= 0) return;
        float progress = motionEnabled && active ? Math.min(1,(SystemClock.uptimeMillis()-paletteChanged)/600f) : 1;
        progress = progress*progress*(3-2*progress);
        for (int i=0;i<blendedPalette.length;i++) blendedPalette[i]=ColorUtils.blendARGB(fromPalette[i],targetPalette[i],progress);
        sky = new LinearGradient(0, 0, 0, height,
                blendedPalette,
                new float[]{0, 0.3f, 0.54f, 0.7f, 0.83f, 1}, Shader.TileMode.CLAMP);
        float radius = Math.min(width * 0.13f, height * 0.1f);
        // Keep the bright disc beyond the text column in both orientations.
        sunCenterX = width * (width > height ? 0.95f : 0.88f);
        glow = new RadialGradient(sunCenterX, height * 0.71f, radius * 3,
                Boolean.FALSE.equals(daylight) ? new int[]{0x448EAED8,0x185F89BC,Color.TRANSPARENT}
                        : new int[]{0x55FFD9A8, 0x18F4C3AE, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        sun = new LinearGradient(0, height * 0.61f, 0, height * 0.78f,
                0xFFFFE4B8, 0xFFE8A496, Shader.TileMode.CLAMP);
        shade = new LinearGradient(0, 0, 0, height,
                new int[]{0x55051429, 0x17091631, 0x5509152B, 0xB307192C},
                new float[]{0, 0.35f, 0.65f, 1}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (sky == null || SystemClock.uptimeMillis()-paletteChanged < 650) rebuildShaders(getWidth(),getHeight());
        float time = (motionEnabled && active ? SystemClock.uptimeMillis() : frozenAt)/1000f;
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(sky);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(glow);
        canvas.drawRect(0, 0, w, h, paint);
        canvas.save();
        // A gentle parallax offset follows the swipe; the scenery never swaps or flashes.
        canvas.translate(-scrollPosition * w * 0.009f, 0);
        float orb = Math.min(w * 0.13f, h * 0.1f);
        paint.setShader(sun);
        if (Boolean.FALSE.equals(daylight)) {
            paint.setShader(null); paint.setColor(0xFFC5D6ED);
            path.reset(); path.moveTo(sunCenterX+orb*.45f,h*.71f-orb);
            path.cubicTo(sunCenterX-orb*1.2f,h*.71f-orb,sunCenterX-orb*1.4f,h*.71f+orb,sunCenterX+orb*.25f,h*.71f+orb);
            path.cubicTo(sunCenterX-orb*.5f,h*.71f+orb*.4f,sunCenterX-orb*.4f,h*.71f-orb*.4f,sunCenterX+orb*.45f,h*.71f-orb);
            canvas.drawPath(path,paint);
            if (condition == WeatherCondition.CLEAR || condition == WeatherCondition.PARTLY_CLOUDY) {
                for (int i=0;i<35;i++) {
                    paint.setColor(Color.argb((int)(65+25*Math.sin(time*.7+i)),205,224,248));
                    canvas.drawCircle((i*137.3f+w*.08f)%w,(i*61.7f)%(h*.56f),1+(i%3)*.45f,paint);
                }
            }
        } else if (condition != WeatherCondition.RAIN && condition != WeatherCondition.STORM && condition != WeatherCondition.FOG) {
            canvas.drawCircle(sunCenterX, h * 0.71f, orb, paint);
        }
        paint.setShader(null);

        canvas.save();
        canvas.translate((float)Math.sin(time*.035)*w*.04f,0);

        paint.setColor(0x20D5C6EF);
        path.reset();
        path.moveTo(-w * 0.1f, h * 0.29f);
        path.cubicTo(w * 0.15f, h * 0.24f, w * 0.22f, h * 0.32f, w * 0.47f, h * 0.28f);
        path.cubicTo(w * 0.25f, h * 0.35f, w * 0.1f, h * 0.31f, -w * 0.1f, h * 0.34f);
        path.close();
        canvas.drawPath(path, paint);

        canvas.restore();
        paint.setColor(0x15FFE2D0);
        path.reset();
        path.moveTo(w * 0.64f, h * 0.43f);
        path.cubicTo(w * 0.8f, h * 0.39f, w * 0.94f, h * 0.45f, w * 1.2f, h * 0.4f);
        path.lineTo(w * 1.2f, h * 0.44f);
        path.cubicTo(w * 0.99f, h * 0.48f, w * 0.78f, h * 0.43f, w * 0.64f, h * 0.43f);
        canvas.drawPath(path, paint);

        // Hill Country layers behind the city.
        paint.setColor(0xFF535572);
        path.reset();
        path.moveTo(-w * 0.2f, h * 0.79f);
        path.cubicTo(w * 0.1f, h * 0.69f, w * 0.25f, h * 0.83f, w * 0.52f, h * 0.77f);
        path.cubicTo(w * 0.8f, h * 0.71f, w, h * 0.78f, w * 1.2f, h * 0.72f);
        path.lineTo(w * 1.2f, h);
        path.lineTo(-w * 0.2f, h);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFF243C57);
        canvas.drawRect(-w * 0.2f, h * 0.86f, w * 1.2f, h, paint);
        building(canvas, w * 0.16f, h * 0.81f, w * 0.065f, h * 0.06f);
        building(canvas, w * 0.25f, h * 0.77f, w * 0.045f, h * 0.1f);
        building(canvas, w * 0.46f, h * 0.79f, w * 0.07f, h * 0.08f);
        building(canvas, w * 0.55f, h * 0.74f, w * 0.055f, h * 0.13f);
        building(canvas, w * 0.64f, h * 0.8f, w * 0.08f, h * 0.07f);
        building(canvas, w * 0.83f, h * 0.79f, w * 0.045f, h * 0.08f);

        // The angular crown of the Frost Bank Tower gives the skyline a local signature.
        path.reset();
        path.moveTo(w * 0.32f, h * 0.87f);
        path.lineTo(w * 0.32f, h * 0.748f);
        path.lineTo(w * 0.336f, h * 0.748f);
        path.lineTo(w * 0.326f, h * 0.716f);
        path.lineTo(w * 0.357f, h * 0.733f);
        path.lineTo(w * 0.38f, h * 0.705f);
        path.lineTo(w * 0.403f, h * 0.733f);
        path.lineTo(w * 0.434f, h * 0.716f);
        path.lineTo(w * 0.424f, h * 0.748f);
        path.lineTo(w * 0.44f, h * 0.748f);
        path.lineTo(w * 0.44f, h * 0.87f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0x65BDCEE7);
        paint.setStrokeWidth(Math.max(1, w * 0.002f));
        for (int i = 0; i < 4; i++) {
            float x = w * (0.344f + i * 0.023f);
            canvas.drawLine(x, h * 0.764f, x, h * 0.85f, paint);
        }

        // Lady Bird Lake and a quiet bridge silhouette.
        paint.setColor(0xFF142E48);
        canvas.drawRect(-w * 0.2f, h * 0.88f, w * 1.2f, h, paint);
        paint.setColor(0xFF11283D);
        canvas.drawRect(-w * 0.2f, h * 0.875f, w * 1.2f, h * 0.884f, paint);
        for (int i = 0; i < 6; i++) {
            canvas.drawRect(w * (i * 0.23f), h * 0.88f,
                    w * (i * 0.23f + 0.015f), h * 0.925f, paint);
        }
        paint.setColor(0x207DABCA);
        paint.setStrokeWidth(Math.max(1, h * 0.0006f));
        for (int i = 0; i < 7; i++) {
            float y = h * (0.9f + i * 0.014f);
            float left = w * (i % 2 == 0 ? 0.11f : 0.57f);
            canvas.drawLine(left, y, left + w * 0.24f, y, paint);
        }
        canvas.restore();
        if (condition == WeatherCondition.RAIN || condition == WeatherCondition.STORM || condition == WeatherCondition.SNOW) {
            paint.setStyle(Paint.Style.FILL); paint.setShader(null);
            paint.setStrokeWidth(Math.max(1,w*.0014f));
            for(int i=0;i<46;i++) {
                float x=(i*137.3f+w*.03f)%w;
                float y=(i*79.4f+time*(condition==WeatherCondition.SNOW?22:140))%h;
                if(condition==WeatherCondition.SNOW) {
                    paint.setColor(0x55DEEFF9);
                    canvas.drawCircle(x+(float)Math.sin(time*.3+i)*14,y,1.5f+(i%3),paint);
                } else {
                    paint.setColor(0x448CD2F3); canvas.drawLine(x,y,x-w*.012f,y+h*.018f,paint);
                }
            }
        }
        paint.setAlpha(255);
        paint.setShader(shade);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        if (active && motionEnabled && getWindowVisibility()==VISIBLE && isShown()) postInvalidateDelayed(50);
    }

    private void building(Canvas canvas, float left, float top, float width, float height) {
        canvas.drawRect(left, top, left + width, top + height, paint);
    }
}
