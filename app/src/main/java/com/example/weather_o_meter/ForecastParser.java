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

/** Converts Open-Meteo's parallel hourly arrays into seven daily averages. */
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
            forecasts.add(new DailyForecast(entry.getKey(), samples.sum / samples.count));
            expectedDate.add(Calendar.DAY_OF_MONTH, 1);
        }
        return forecasts;
    }

    private static final class Samples {
        double sum;
        int count;
    }
}
