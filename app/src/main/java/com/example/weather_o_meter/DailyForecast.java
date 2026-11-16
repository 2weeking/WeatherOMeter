package com.example.weather_o_meter;

/** An Austin calendar day's mean temperature, precipitation chance, and maximum wind speed. */
public final class DailyForecast {
    private final String date;
    private final double averageFahrenheit;
    private final Double precipitationChancePercent;
    private final Double maxWindMph;

    public DailyForecast(String date, double averageFahrenheit, Double precipitationChancePercent, Double maxWindMph) {
        this.date = date;
        this.averageFahrenheit = averageFahrenheit;
        this.precipitationChancePercent = precipitationChancePercent;
        this.maxWindMph = maxWindMph;
    }

    public String getDate() {
        return date;
    }

    public double getAverageFahrenheit() {
        return averageFahrenheit;
    }

    /** Highest hourly precipitation probability for this day, from 0 to 100; null means unavailable. */
    public Double getPrecipitationChancePercent() {
        return precipitationChancePercent;
    }

    /** Maximum forecast wind speed, distinct from gust speed; null means unavailable. */
    public Double getMaxWindMph() {
        return maxWindMph;
    }
}
