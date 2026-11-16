package com.example.weather_o_meter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/** Calculates seven daily temperature averages and joins precipitation/wind summaries by local date. */
public final class ForecastParser {
    public static final int FORECAST_DAYS = 7;

    private ForecastParser() {}

    public static List<DailyForecast> parse(String json) throws JSONException {
        JSONObject response = new JSONObject(json);
        if (response.optBoolean("error", false)) {
            throw new JSONException("The weather service returned an error.");
        }
        JSONObject hourly = response.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temperatures = hourly.getJSONArray("temperature_2m");
        if (times.length() == 0 || times.length() != temperatures.length()) {
            throw new JSONException("Hourly times and temperatures must match.");
        }

        // The requested timezone makes the date prefix an Austin calendar date.
        // Group by those dates rather than assuming every day contains 24 samples.
        Map<String, Samples> days = new TreeMap<>();
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        timestampFormat.setLenient(false);
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < times.length(); i++) {
            String timestamp = times.getString(i);
            ParsePosition position = new ParsePosition(0);
            if (!timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:00")
                    || timestampFormat.parse(timestamp, position) == null
                    || position.getIndex() != timestamp.length()) {
                throw new JSONException("Invalid hourly timestamp.");
            }
            String date = timestamp.substring(0, 10);
            Samples samples = days.get(date);
            if (samples == null) {
                samples = new Samples();
                days.put(date, samples);
            }
            // Missing values are not zero degrees and must not enter the denominator.
            if (temperatures.isNull(i)) {
                continue;
            }
            Object value = temperatures.get(i);
            if (!(value instanceof Number)) {
                throw new JSONException("Invalid hourly temperature.");
            }
            double temperature = ((Number) value).doubleValue();
            if (Double.isNaN(temperature) || Double.isInfinite(temperature)) {
                throw new JSONException("Non-finite hourly temperature.");
            }
            samples.sum += temperature;
            samples.count++;
        }

        if (days.size() != FORECAST_DAYS) {
            throw new JSONException("Expected seven forecast dates.");
        }
        Map<String, DailyDetails> dailyDetails = parseDailyDetails(response);
        List<DailyForecast> forecasts = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar expectedDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        for (Map.Entry<String, Samples> entry : days.entrySet()) {
            if (forecasts.isEmpty()) {
                Date first = dateFormat.parse(entry.getKey(), new ParsePosition(0));
                if (first == null) {
                    throw new JSONException("Invalid forecast date.");
                }
                expectedDate.setTime(first);
            }
            if (!dateFormat.format(expectedDate.getTime()).equals(entry.getKey())) {
                throw new JSONException("Forecast dates must be consecutive.");
            }
            Samples samples = entry.getValue();
            if (samples.count == 0 || Double.isInfinite(samples.sum)) {
                throw new JSONException("No usable temperatures for a forecast date.");
            }
            DailyDetails details = dailyDetails.get(entry.getKey());
            if (details == null) {
                throw new JSONException("Daily precipitation/wind dates must match the temperature dates.");
            }
            forecasts.add(new DailyForecast(entry.getKey(), samples.sum / samples.count,
                    details.precipitationChancePercent, details.maxWindMph));
            expectedDate.add(Calendar.DAY_OF_MONTH, 1);
        }
        return forecasts;
    }

    private static Map<String, DailyDetails> parseDailyDetails(JSONObject response) throws JSONException {
        // Requiring the probability field also refreshes cached rainfall-total responses.
        JSONObject daily = response.getJSONObject("daily");
        JSONObject units = response.getJSONObject("daily_units");
        String windUnit = units.getString("wind_speed_10m_max");
        if (!"%".equals(units.getString("precipitation_probability_max"))
                || !("mp/h".equals(windUnit) || "mph".equals(windUnit))) {
            throw new JSONException("Expected precipitation probability in percent and wind speed in mph.");
        }
        JSONArray dates = daily.getJSONArray("time");
        JSONArray chances = daily.getJSONArray("precipitation_probability_max");
        JSONArray wind = daily.getJSONArray("wind_speed_10m_max");
        if (dates.length() != FORECAST_DAYS || chances.length() != dates.length()
                || wind.length() != dates.length()) {
            throw new JSONException("Daily dates, precipitation probability, and wind arrays must match.");
        }
        Map<String, DailyDetails> result = new TreeMap<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < dates.length(); i++) {
            String date = dates.getString(i);
            ParsePosition position = new ParsePosition(0);
            if (!date.matches("\\d{4}-\\d{2}-\\d{2}")
                    || format.parse(date, position) == null || position.getIndex() != date.length()
                    || result.containsKey(date)) {
                throw new JSONException("Invalid or duplicate daily date.");
            }
            // The API already returns a 0-100 percentage: do not multiply by 100
            // or derive probability from rainfall amounts.
            Double chance = optionalNonnegativeNumber(chances, i);
            if (chance != null && chance > 100) {
                throw new JSONException("Precipitation probability must be between 0 and 100.");
            }
            result.put(date, new DailyDetails(chance, optionalNonnegativeNumber(wind, i)));
        }
        return result;
    }

    private static Double optionalNonnegativeNumber(JSONArray values, int index) throws JSONException {
        if (values.isNull(index)) {
            return null;
        }
        Object value = values.get(index);
        if (!(value instanceof Number)) {
            throw new JSONException("Invalid daily measurement.");
        }
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number) || number < 0) {
            throw new JSONException("Daily precipitation and wind measurements must be finite and nonnegative.");
        }
        return number;
    }

    private static final class DailyDetails {
        final Double precipitationChancePercent;
        final Double maxWindMph;

        DailyDetails(Double precipitationChancePercent, Double maxWindMph) {
            this.precipitationChancePercent = precipitationChancePercent;
            this.maxWindMph = maxWindMph;
        }
    }

    private static final class Samples {
        double sum;
        int count;
    }
}
