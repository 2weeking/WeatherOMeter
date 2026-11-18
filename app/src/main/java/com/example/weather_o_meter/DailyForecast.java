package com.example.weather_o_meter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An Austin calendar day's mean temperature, precipitation chance, and maximum wind speed. */
public final class DailyForecast {
    private final String date;
    private final double averageFahrenheit;
    private final Double precipitationChancePercent;
    private final Double maxWindMph;
    private final Double dominantWindDegrees;
    private final List<HourlyForecast> hours;

    public DailyForecast(String date, double averageFahrenheit, Double precipitationChancePercent, Double maxWindMph) {
        this(date, averageFahrenheit, precipitationChancePercent, maxWindMph, null, Collections.emptyList());
    }

    public DailyForecast(String date, double averageFahrenheit, Double precipitationChancePercent,
            Double maxWindMph, Double dominantWindDegrees, List<HourlyForecast> hours) {
        this.date = date;
        this.averageFahrenheit = averageFahrenheit;
        this.precipitationChancePercent = precipitationChancePercent;
        this.maxWindMph = maxWindMph;
        this.dominantWindDegrees = dominantWindDegrees;
        this.hours = Collections.unmodifiableList(new ArrayList<>(hours));
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

    public List<HourlyForecast> getHours() { return hours; }
    public Double getDominantWindDegrees() { return dominantWindDegrees; }
    public Double getLowFahrenheit() { return extreme(false); }
    public Double getHighFahrenheit() { return extreme(true); }
    private Double extreme(boolean high) {
        Double result = null;
        for (HourlyForecast hour : hours) {
            Double value = hour.getTemperatureFahrenheit();
            if (value != null && (result == null || (high ? value > result : value < result))) result = value;
        }
        return result;
    }
    public int getWarmestHourIndex() {
        int index = -1;
        for (int i = 0; i < hours.size(); i++) {
            Double value = hours.get(i).getTemperatureFahrenheit();
            if (value != null && (index < 0 || value > hours.get(index).getTemperatureFahrenheit())) index = i;
        }
        return index;
    }
}
