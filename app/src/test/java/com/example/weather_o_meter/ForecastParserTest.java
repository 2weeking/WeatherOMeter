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
    @Test
    public void keepsHourlyDetailsTogetherAndDerivesTrueRangeAndWarmestHour() throws Exception {
        DailyForecast day=ForecastParser.parse(week().toString()).get(0);
        assertEquals(24,day.getHours().size());
        HourlyForecast hour=day.getHours().get(9);
        assertEquals("2026-11-15T09:00",hour.getTime());
        assertEquals(19.5,hour.getTemperatureFahrenheit(),0.000001);
        assertEquals(27,hour.getPrecipitationChancePercent(),0.000001);
        assertEquals(6.6,hour.getWindMph(),0.000001);
        assertEquals(45,hour.getWindDirectionDegrees(),0.000001);
        assertEquals(Integer.valueOf(0),hour.getWeatherCode());
        assertEquals(Boolean.TRUE,hour.getDaylight());
        assertEquals(15,day.getLowFahrenheit(),0.000001);
        assertEquals(26.5,day.getHighFahrenheit(),0.000001);
        assertEquals(23,day.getWarmestHourIndex());
        assertThrows(UnsupportedOperationException.class,()->day.getHours().clear());
    }

    @Test
    public void preservesMissingHourlyReadingsWithoutInventingWeatherOrChangingMeans() throws Exception {
        JSONObject data=week();
        JSONObject hourly=data.getJSONObject("hourly");
        for(String key:new String[]{"temperature_2m","precipitation_probability","wind_speed_10m",
                "wind_direction_10m","weather_code","is_day"}) hourly.getJSONArray(key).put(0,JSONObject.NULL);
        DailyForecast day=ForecastParser.parse(data.toString()).get(0);
        HourlyForecast hour=day.getHours().get(0);
        assertEquals(24,day.getHours().size());
        assertNull(hour.getTemperatureFahrenheit()); assertNull(hour.getPrecipitationChancePercent());
        assertNull(hour.getWindMph()); assertNull(hour.getWindDirectionDegrees());
        assertNull(hour.getWeatherCode()); assertNull(hour.getDaylight());
        assertEquals(21,day.getAverageFahrenheit(),0.000001);
        assertEquals(15.5,day.getLowFahrenheit(),0.000001);
    }

    @Test
    public void rejectsInvalidHourlyDetailsAndMismatchedUnits() throws Exception {
        String[] keys={"precipitation_probability","wind_speed_10m","wind_direction_10m","weather_code","is_day"};
        double[] invalid={101,-1,361,2.5,2};
        for(int i=0;i<keys.length;i++) {
            JSONObject bad=week(); bad.getJSONObject("hourly").getJSONArray(keys[i]).put(0,invalid[i]);
            assertThrows(JSONException.class,()->ForecastParser.parse(bad.toString()));
            JSONObject truncated=week(); truncated.getJSONObject("hourly").getJSONArray(keys[i]).remove(0);
            assertThrows(JSONException.class,()->ForecastParser.parse(truncated.toString()));
        }
        for(String key:new String[]{"temperature_2m","precipitation_probability","wind_speed_10m","wind_direction_10m"}) {
            JSONObject bad=week(); bad.getJSONObject("hourly_units").put(key,"wrong unit");
            assertThrows(JSONException.class,()->ForecastParser.parse(bad.toString()));
        }
    }

    @Test
    public void sortsUnorderedHoursWithoutDetachingTheirMeasurements() throws Exception {
        JSONObject data=week(); JSONObject hourly=data.getJSONObject("hourly");
        for(String key:new String[]{"time","temperature_2m","precipitation_probability","wind_speed_10m",
                "wind_direction_10m","weather_code","is_day"}) {
            JSONArray original=hourly.getJSONArray(key),reverse=new JSONArray();
            for(int i=original.length()-1;i>=0;i--) reverse.put(original.get(i));
            hourly.put(key,reverse);
        }
        HourlyForecast hour=ForecastParser.parse(data.toString()).get(0).getHours().get(2);
        assertEquals(2,hour.getHour()); assertEquals(6,hour.getPrecipitationChancePercent(),0.000001);
        assertEquals(Integer.valueOf(61),hour.getWeatherCode());
        assertEquals(Boolean.FALSE,hour.getDaylight());
    }

    @Test
    public void mapsWmoConditionsAndWindBearingsIncludingNorthWraparound() {
        assertEquals(WeatherCondition.CLEAR,WeatherCondition.fromCode(0));
        assertEquals(WeatherCondition.PARTLY_CLOUDY,WeatherCondition.fromCode(2));
        assertEquals(WeatherCondition.CLOUDY,WeatherCondition.fromCode(3));
        assertEquals(WeatherCondition.FOG,WeatherCondition.fromCode(48));
        assertEquals(WeatherCondition.RAIN,WeatherCondition.fromCode(82));
        assertEquals(WeatherCondition.SNOW,WeatherCondition.fromCode(86));
        assertEquals(WeatherCondition.STORM,WeatherCondition.fromCode(99));
        assertEquals(WeatherCondition.UNKNOWN,WeatherCondition.fromCode(null));
        assertEquals(WeatherCondition.UNKNOWN,WeatherCondition.fromCode(123));
        assertEquals("N",WeatherCondition.compassPoint(360.0));
        assertEquals("N",WeatherCondition.compassPoint(359.0));
        assertEquals("NE",WeatherCondition.compassPoint(45.0));
        assertEquals("S",WeatherCondition.compassPoint(180.0));
        assertEquals("—",WeatherCondition.compassPoint(null));
    }

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
        JSONArray directions = new JSONArray();
        int day = 0;
        for (String date : uniqueDates) {
            dates.put(date);
            chances.put(day * 15.0);
            wind.put(5 + day * 2.0);
            directions.put(day * 45.0);
            day++;
        }
        JSONObject hourly = response.getJSONObject("hourly");
        JSONArray hourChance = new JSONArray(), hourWind = new JSONArray(), hourDirection = new JSONArray();
        JSONArray codes = new JSONArray(), daylight = new JSONArray();
        for (int i=0;i<hourlyTimes.length();i++) {
            int hour = Integer.parseInt(hourlyTimes.getString(i).substring(11,13));
            hourChance.put(hour*3); hourWind.put(3+hour*.4); hourDirection.put((hour%8)*45);
            codes.put(hour<6?61:hour<19?0:3); daylight.put(hour>=7 && hour<19?1:0);
        }
        hourly.put("precipitation_probability",hourChance).put("wind_speed_10m",hourWind)
                .put("wind_direction_10m",hourDirection).put("weather_code",codes).put("is_day",daylight);
        response.put("hourly_units",new JSONObject().put("temperature_2m","°F")
                .put("precipitation_probability","%").put("wind_speed_10m","mp/h").put("wind_direction_10m","°"));
        return response.put("daily_units", new JSONObject().put("precipitation_probability_max", "%")
                .put("wind_speed_10m_max", "mp/h").put("wind_direction_10m_dominant","°"))
                .put("daily", new JSONObject().put("time", dates).put("precipitation_probability_max", chances)
                        .put("wind_speed_10m_max", wind).put("wind_direction_10m_dominant",directions));
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
        for (String key : new String[]{"time", "precipitation_probability_max", "wind_speed_10m_max", "wind_direction_10m_dominant"}) {
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
        assertEquals(0, result.get(0).getDominantWindDegrees(), 0.000001);
        assertEquals(270, result.get(6).getDominantWindDegrees(), 0.000001);
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
