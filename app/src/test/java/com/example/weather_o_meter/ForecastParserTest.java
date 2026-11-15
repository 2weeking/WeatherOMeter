package com.example.weather_o_meter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ForecastParserTest {
    private JSONObject week() throws JSONException {
        JSONArray times = new JSONArray();
        JSONArray temperatures = new JSONArray();
        for (int day = 15; day <= 21; day++) {
            for (int hour = 0; hour < 24; hour++) {
                times.put(String.format(java.util.Locale.US, "2026-11-%02dT%02d:00", day, hour));
                temperatures.put(day + hour * 0.5);
            }
        }
        return new JSONObject().put("hourly", new JSONObject()
                .put("time", times).put("temperature_2m", temperatures));
    }

    @Test
    public void calculatesSevenDailyMeansFromAllHourlyValues() throws Exception {
        List<DailyForecast> result = ForecastParser.parse(week().toString());
        assertEquals(7, result.size());
        for (int day = 0; day < 7; day++) {
            assertEquals("2026-11-" + (15 + day), result.get(day).getDate());
            assertEquals(20.75 + day, result.get(day).getAverageFahrenheit(), 0.000001);
        }
    }

    @Test
    public void usesArithmeticMeanRatherThanMidpointOfHighAndLow() throws Exception {
        JSONObject data = week();
        JSONArray temperatures = data.getJSONObject("hourly").getJSONArray("temperature_2m");
        for (int i = 0; i < 24; i++) {
            temperatures.put(i, i == 23 ? 100 : 0);
        }
        assertEquals(100.0 / 24, ForecastParser.parse(data.toString()).get(0)
                .getAverageFahrenheit(), 0.000001);
    }

    @Test
    public void ignoresNullTemperaturesRatherThanCountingThemAsZero() throws Exception {
        JSONObject data = week();
        JSONArray values = data.getJSONObject("hourly").getJSONArray("temperature_2m");
        for (int i = 0; i < 24; i++) {
            values.put(i, i == 0 ? 80 : JSONObject.NULL);
        }
        assertEquals(80, ForecastParser.parse(data.toString()).get(0)
                .getAverageFahrenheit(), 0.000001);
    }

    @Test
    public void groupsByDateEvenWithUnequalSampleCountsAndUnsortedInput() throws Exception {
        JSONArray times = new JSONArray();
        JSONArray values = new JSONArray();
        for (int day = 21; day >= 15; day--) {
            int hours = day == 15 ? 23 : 24;
            for (int hour = 0; hour < hours; hour++) {
                times.put(String.format(java.util.Locale.US, "2026-11-%02dT%02d:00", day, hour));
                values.put(day == 15 ? -10 : 50);
            }
        }
        JSONObject data = new JSONObject().put("hourly", new JSONObject()
                .put("time", times).put("temperature_2m", values));
        List<DailyForecast> result = ForecastParser.parse(data.toString());
        assertEquals("2026-11-15", result.get(0).getDate());
        assertEquals(-10, result.get(0).getAverageFahrenheit(), 0.000001);
        assertEquals(50, result.get(1).getAverageFahrenheit(), 0.000001);
    }

    @Test
    public void supportsWeeksCrossingYearBoundary() throws Exception {
        JSONArray times = new JSONArray();
        JSONArray values = new JSONArray();
        String[] dates = {"2026-12-29", "2026-12-30", "2026-12-31", "2027-01-01",
                "2027-01-02", "2027-01-03", "2027-01-04"};
        for (String date : dates) {
            times.put(date + "T00:00");
            values.put(42);
        }
        JSONObject data = new JSONObject().put("hourly", new JSONObject()
                .put("time", times).put("temperature_2m", values));
        assertEquals("2027-01-04", ForecastParser.parse(data.toString()).get(6).getDate());
    }

    @Test
    public void rejectsMismatchedArrays() throws Exception {
        JSONObject data = week();
        data.getJSONObject("hourly").getJSONArray("temperature_2m").remove(0);
        assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
    }

    @Test
    public void rejectsDayWithoutAnyUsableTemperatures() throws Exception {
        JSONObject data = week();
        JSONArray values = data.getJSONObject("hourly").getJSONArray("temperature_2m");
        for (int i = 0; i < 24; i++) {
            values.put(i, JSONObject.NULL);
        }
        assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
    }

    @Test
    public void rejectsMissingOrNonconsecutiveDates() throws Exception {
        JSONObject data = week();
        JSONArray times = data.getJSONObject("hourly").getJSONArray("time");
        for (int i = 0; i < 24; i++) {
            times.put(i, String.format(java.util.Locale.US, "2026-11-23T%02d:00", i));
        }
        assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
    }

    @Test
    public void rejectsFewerThanSevenDays() throws Exception {
        JSONObject data = week();
        JSONArray times = data.getJSONObject("hourly").getJSONArray("time");
        JSONArray values = data.getJSONObject("hourly").getJSONArray("temperature_2m");
        for (int i = 0; i < 24; i++) {
            times.remove(0);
            values.remove(0);
        }
        assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
    }

    @Test
    public void rejectsInvalidDatesAndInvalidTemperatureTypes() throws Exception {
        JSONObject invalidDate = week();
        invalidDate.getJSONObject("hourly").getJSONArray("time").put(0, "2026-02-30T00:00");
        assertThrows(JSONException.class, () -> ForecastParser.parse(invalidDate.toString()));
        JSONObject invalidValue = week();
        invalidValue.getJSONObject("hourly").getJSONArray("temperature_2m").put(0, "warm");
        assertThrows(JSONException.class, () -> ForecastParser.parse(invalidValue.toString()));
    }

    @Test
    public void rejectsMalformedJsonAndApiErrors() {
        assertThrows(JSONException.class, () -> ForecastParser.parse("not JSON"));
        assertThrows(JSONException.class, () -> ForecastParser.parse("{\"error\":true}"));
        assertThrows(JSONException.class, () -> ForecastParser.parse("{}"));
        assertThrows(JSONException.class, () -> ForecastParser.parse(
                "{\"hourly\":{\"time\":[],\"temperature_2m\":[]}}"));
    }
}
