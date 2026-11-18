package com.example.weather_o_meter;

/** WMO condition families; never infer actual rain/snow from precipitation probability. */
public enum WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, STORM, UNKNOWN;

    public static WeatherCondition fromCode(Integer code) {
        if (code == null) return UNKNOWN;
        switch (code) {
            case 0: return CLEAR;
            case 1: case 2: return PARTLY_CLOUDY;
            case 3: return CLOUDY;
            case 45: case 48: return FOG;
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82: return RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return SNOW;
            case 95: case 96: case 99: return STORM;
            default: return UNKNOWN;
        }
    }

    public static String compassPoint(Double degrees) {
        if (degrees == null) return "—";
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return points[((int) Math.floor((degrees % 360 + 22.5) / 45)) % 8];
    }
}
