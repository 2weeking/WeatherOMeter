package com.example.weather_o_meter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;

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
        return withDailyData(new JSONObject().put("hourly", new JSONObject()
                .put("time", times).put("temperature_2m", temperatures)));
    }

    private JSONObject withDailyData(JSONObject response) throws JSONException {
        Set<String> uniqueDates = new TreeSet<>();
        JSONArray hourlyTimes = response.getJSONObject("hourly").getJSONArray("time");
        for (int i = 0; i < hourlyTimes.length(); i++) {
            uniqueDates.add(hourlyTimes.getString(i).substring(0, 10));
        }
        JSONArray dates = new JSONArray();
        JSONArray chances = new JSONArray();
        JSONArray wind = new JSONArray();
        int day = 0;
        for (String date : uniqueDates) {
            dates.put(date);
            chances.put(day * 15.0);
            wind.put(5 + day * 2.0);
            day++;
        }
        return response.put("daily_units", new JSONObject().put("precipitation_probability_max", "%")
                .put("wind_speed_10m_max", "mp/h"))
                .put("daily", new JSONObject().put("time", dates).put("precipitation_probability_max", chances)
                        .put("wind_speed_10m_max", wind));
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
        JSONObject data = withDailyData(new JSONObject().put("hourly", new JSONObject()
                .put("time", times).put("temperature_2m", values)));
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
        JSONObject data = withDailyData(new JSONObject().put("hourly", new JSONObject()
                .put("time", times).put("temperature_2m", values)));
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

    @Test
    public void readsEachDaysPrecipitationPercentageWithoutRescalingAndKeepsMaximumWind() throws Exception {
        List<DailyForecast> result = ForecastParser.parse(week().toString());
        for (int day = 0; day < 7; day++) {
            assertEquals(day * 15.0, result.get(day).getPrecipitationChancePercent(), 0.000001);
            assertEquals(5 + day * 2, result.get(day).getMaxWindMph(), 0.000001);
        }
    }

    @Test
    public void matchesDailyMeasurementsByDateEvenWhenDailyRowsAreReversed() throws Exception {
        JSONObject data = week();
        JSONObject daily = data.getJSONObject("daily");
        for (String key : new String[]{"time", "precipitation_probability_max", "wind_speed_10m_max"}) {
            JSONArray original = daily.getJSONArray(key);
            JSONArray reversed = new JSONArray();
            for (int i = original.length() - 1; i >= 0; i--) {
                reversed.put(original.get(i));
            }
            daily.put(key, reversed);
        }
        List<DailyForecast> result = ForecastParser.parse(data.toString());
        assertEquals("2026-11-15", result.get(0).getDate());
        assertEquals(0, result.get(0).getPrecipitationChancePercent(), 0.000001);
        assertEquals(5, result.get(0).getMaxWindMph(), 0.000001);
        assertEquals(90, result.get(6).getPrecipitationChancePercent(), 0.000001);
        assertEquals(17, result.get(6).getMaxWindMph(), 0.000001);
    }

    @Test
    public void missingDailyValuesStayUnavailableInsteadOfBecomingZero() throws Exception {
        JSONObject data = week();
        JSONObject daily = data.getJSONObject("daily");
        daily.getJSONArray("precipitation_probability_max").put(0, JSONObject.NULL);
        daily.getJSONArray("wind_speed_10m_max").put(2, JSONObject.NULL);
        List<DailyForecast> result = ForecastParser.parse(data.toString());
        assertNull(result.get(0).getPrecipitationChancePercent());
        assertEquals(15, result.get(1).getPrecipitationChancePercent(), 0.000001);
        assertNull(result.get(2).getMaxWindMph());
        assertEquals(5, result.get(0).getMaxWindMph(), 0.000001);
        assertEquals(20.75, result.get(0).getAverageFahrenheit(), 0.000001);
    }

    @Test
    public void rejectsMismatchedOrMissingDailyArrays() throws Exception {
        JSONObject data = week();
        data.getJSONObject("daily").getJSONArray("wind_speed_10m_max").remove(0);
        assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
        JSONObject missing = week();
        missing.getJSONObject("daily").remove("precipitation_probability_max");
        assertThrows(JSONException.class, () -> ForecastParser.parse(missing.toString()));
    }

    @Test
    public void rejectsDailyDatesThatAreDuplicateInvalidOrDifferentFromHourlyDates() throws Exception {
        for (String date : new String[]{"2026-11-16", "2026-02-30", "2026-11-22"}) {
            JSONObject data = week();
            data.getJSONObject("daily").getJSONArray("time").put(0, date);
            assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
        }
    }

    @Test
    public void rejectsWrongUnitsInsteadOfMislabelingValues() throws Exception {
        for (String key : new String[]{"precipitation_probability_max", "wind_speed_10m_max"}) {
            JSONObject data = week();
            data.getJSONObject("daily_units").put(key, key.contains("wind") ? "km/h" : "mm");
            assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
        }
    }

    @Test
    public void rejectsNegativeNonnumericOrOutOfRangeDailyValues() throws Exception {
        for (String key : new String[]{"precipitation_probability_max", "wind_speed_10m_max"}) {
            for (Object invalid : new Object[]{-0.1, "unknown", true}) {
                JSONObject data = week();
                data.getJSONObject("daily").getJSONArray(key).put(0, invalid);
                assertThrows(JSONException.class, () -> ForecastParser.parse(data.toString()));
            }
        }
        for (double invalid : new double[]{100.1, 101, Double.MAX_VALUE}) {
            JSONObject overflow = week();
            overflow.getJSONObject("daily").getJSONArray("precipitation_probability_max").put(0, invalid);
            assertThrows(JSONException.class, () -> ForecastParser.parse(overflow.toString()));
        }
    }

    @Test
    public void rejectsLegacyRainfallResponsesSoTheyWillBeRefreshed() throws Exception {
        JSONObject oldResponse = week();
        oldResponse.getJSONObject("daily").remove("precipitation_probability_max");
        oldResponse.getJSONObject("daily_units").remove("precipitation_probability_max");
        oldResponse.getJSONObject("daily").put("rain_sum", new JSONArray("[0.3,0,0,0,0,0,0]"));
        oldResponse.getJSONObject("daily_units").put("rain_sum", "inch");
        assertThrows(JSONException.class, () -> ForecastParser.parse(oldResponse.toString()));
        oldResponse.remove("daily");
        oldResponse.remove("daily_units");
        assertThrows(JSONException.class, () -> ForecastParser.parse(oldResponse.toString()));
    }

    @Test
    public void acceptsZeroAndOneHundredPercent() throws Exception {
        JSONObject data = week();
        data.getJSONObject("daily").getJSONArray("precipitation_probability_max").put(0, 0).put(1, 100);
        List<DailyForecast> forecasts = ForecastParser.parse(data.toString());
        assertEquals(0, forecasts.get(0).getPrecipitationChancePercent(), 0.000001);
        assertEquals(100, forecasts.get(1).getPrecipitationChancePercent(), 0.000001);
    }
}
