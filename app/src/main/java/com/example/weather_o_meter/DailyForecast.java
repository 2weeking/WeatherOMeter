package com.example.weather_o_meter;

/** One calendar day's arithmetic mean of the available hourly forecasts, in Fahrenheit. */
public final class DailyForecast {
    private final String date;
    private final double averageFahrenheit;

    public DailyForecast(String date, double averageFahrenheit) {
        this.date = date;
        this.averageFahrenheit = averageFahrenheit;
    }

    public String getDate() {
        return date;
    }

    public double getAverageFahrenheit() {
        return averageFahrenheit;
    }
}
