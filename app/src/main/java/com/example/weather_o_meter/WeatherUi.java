package com.example.weather_o_meter;

import android.content.Context;
import android.graphics.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class WeatherUi {
    private WeatherUi() {}
    static String hour(int hour) {
        SimpleDateFormat format = new SimpleDateFormat("h a", Locale.getDefault());
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(hour * 3600000L));
    }
    static String condition(Context context, Integer code, Boolean daylight) {
        int id;
        switch (WeatherCondition.fromCode(code)) {
            case CLEAR: id = Boolean.FALSE.equals(daylight) ? R.string.condition_clear_night : R.string.condition_clear; break;
            case PARTLY_CLOUDY: id = R.string.condition_partly; break;
            case CLOUDY: id = R.string.condition_cloudy; break;
            case FOG: id = R.string.condition_fog; break;
            case RAIN: id = R.string.condition_rain; break;
            case SNOW: id = R.string.condition_snow; break;
            case STORM: id = R.string.condition_storm; break;
            default: id = R.string.condition_unknown;
        }
        return context.getString(id);
    }
    static String temperature(Context c, Double value) {
        return value == null ? c.getString(R.string.measurement_unavailable)
                : c.getString(R.string.hour_temperature, value);
    }
    static String chance(Context c, Double value) {
        return value == null ? c.getString(R.string.measurement_unavailable)
                : c.getString(R.string.precipitation_value, value);
    }
    static String wind(Context c, Double value) {
        return value == null ? c.getString(R.string.measurement_unavailable)
                : c.getString(R.string.wind_value, value);
    }
    static int temperatureColor(double value) {
        float t = (float) Math.max(0, Math.min(1, (value - 25) / 75));
        int cold = 0xFF8AD7F5, warm = 0xFFFFAF88;
        return Color.rgb((int)(Color.red(cold)*(1-t)+Color.red(warm)*t),
                (int)(Color.green(cold)*(1-t)+Color.green(warm)*t),
                (int)(Color.blue(cold)*(1-t)+Color.blue(warm)*t));
    }
}
