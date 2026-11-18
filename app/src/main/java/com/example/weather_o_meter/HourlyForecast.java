package com.example.weather_o_meter;

/** A forecast for one Austin-local hour. Missing measurements remain null. */
public final class HourlyForecast {
    private final String time;
    private final Double temperature, precipitationChance, windSpeed, windDirection;
    private final Integer weatherCode;
    private final Boolean daylight;

    public HourlyForecast(String time, Double temperature, Double precipitationChance,
            Double windSpeed, Double windDirection, Integer weatherCode, Boolean daylight) {
        this.time = time;
        this.temperature = temperature;
        this.precipitationChance = precipitationChance;
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.weatherCode = weatherCode;
        this.daylight = daylight;
    }
    public String getTime() { return time; }
    public int getHour() { return Integer.parseInt(time.substring(11, 13)); }
    public Double getTemperatureFahrenheit() { return temperature; }
    public Double getPrecipitationChancePercent() { return precipitationChance; }
    public Double getWindMph() { return windSpeed; }
    public Double getWindDirectionDegrees() { return windDirection; }
    public Integer getWeatherCode() { return weatherCode; }
    public Boolean getDaylight() { return daylight; }
}
