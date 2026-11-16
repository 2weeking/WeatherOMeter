package com.example.weather_o_meter;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ForecastMetricsBindingTest {
    @Test
    public void recycledPageBindsTheCorrectPrecipitationChanceAndWindForEveryDate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_WeatherOMeter);
            List<DailyForecast> forecasts = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                forecasts.add(new DailyForecast("2026-11-" + (15 + i), 60 + i,
                        i * 15.0, 5 + i * 2.0));
            }
            ForecastPagerAdapter adapter = new ForecastPagerAdapter(forecasts);
            ForecastPagerAdapter.DayHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
            assertEquals("Precipitation Chance", ((TextView) holder.itemView.findViewById(
                    R.id.precipitation_label)).getText().toString());
            assertEquals("Max Wind", ((TextView) holder.itemView.findViewById(
                    R.id.wind_label)).getText().toString());
            for (int i : new int[]{0, 6, 2, 5, 1, 4, 3, 0}) {
                adapter.onBindViewHolder(holder, i);
                assertEquals(context.getString(R.string.precipitation_value, i * 15.0),
                        holder.precipitationChance.getText().toString());
                assertEquals(context.getString(R.string.wind_value, 5 + i * 2.0), holder.wind.getText().toString());
                assertTrue(holder.date.getText().toString().contains(String.valueOf(15 + i)));
            }
        });
    }

    @Test
    public void distinguishesMissingReadingsZeroAndOneHundredPercent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_WeatherOMeter);
            List<DailyForecast> forecasts = new ArrayList<>();
            forecasts.add(new DailyForecast("2026-11-15", 60, 25.0, 9.0));
            forecasts.add(new DailyForecast("2026-11-16", 61, null, null));
            forecasts.add(new DailyForecast("2026-11-17", 62, 0.0, 0.0));
            forecasts.add(new DailyForecast("2026-11-18", 63, 100.0, 10.0));
            ForecastPagerAdapter adapter = new ForecastPagerAdapter(forecasts);
            ForecastPagerAdapter.DayHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
            adapter.onBindViewHolder(holder, 0);
            assertEquals("25%", holder.precipitationChance.getText().toString());
            adapter.onBindViewHolder(holder, 1);
            assertEquals("Unavailable", holder.precipitationChance.getText().toString());
            assertEquals("Unavailable", holder.wind.getText().toString());
            adapter.onBindViewHolder(holder, 2);
            assertEquals("0%", holder.precipitationChance.getText().toString());
            assertEquals("0.0 mph", holder.wind.getText().toString());
            adapter.onBindViewHolder(holder, 3);
            assertEquals("100%", holder.precipitationChance.getText().toString());
        });
    }
}
