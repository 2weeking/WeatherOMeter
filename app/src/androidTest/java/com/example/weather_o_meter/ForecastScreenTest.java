package com.example.weather_o_meter;

import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Live API smoke test. Run on an emulator with internet access. */
@RunWith(AndroidJUnit4.class)
public class ForecastScreenTest {
    @Test
    public void loadsSevenDaysPreservesThemOnRecreationAndRefreshes() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitRequest(scenario);
            List<String> original = readRows(scenario);
            scenario.onActivity(activity -> {
                String status = ((TextView) activity.findViewById(R.id.status_text)).getText().toString();
                assertTrue(status, status.startsWith("Updated "));
            });
            scenario.recreate();
            assertEquals(original, readRows(scenario));
            onView(withId(R.id.refresh_button)).perform(scrollTo(), click());
            awaitRequest(scenario);
            assertEquals(7, readRows(scenario).size());
            scenario.onActivity(activity -> assertTrue(activity.findViewById(R.id.refresh_button).isEnabled()));
        }
    }

    private void awaitRequest(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean loading = new AtomicBoolean(true);
        long deadline = SystemClock.elapsedRealtime() + 45000;
        while (loading.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(activity -> loading.set(
                    activity.findViewById(R.id.loading_indicator).getVisibility() == View.VISIBLE));
            if (loading.get()) {
                SystemClock.sleep(100);
            }
        }
        assertFalse("The forecast request did not finish in 45 seconds", loading.get());
    }

    private List<String> readRows(ActivityScenario<MainActivity> scenario) {
        AtomicReference<List<String>> rows = new AtomicReference<>();
        scenario.onActivity(activity -> {
            LinearLayout list = activity.findViewById(R.id.forecast_list);
            assertEquals("Expected seven real forecast rows. Status: "
                    + ((TextView) activity.findViewById(R.id.status_text)).getText(), 7, list.getChildCount());
            List<String> values = new ArrayList<>();
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                String date = ((TextView) row.findViewById(R.id.forecast_date)).getText().toString();
                String temperature = ((TextView) row.findViewById(R.id.forecast_temperature)).getText().toString();
                assertFalse(date.isEmpty());
                assertTrue(temperature, temperature.matches("Average: -?\\d+\\.\\d °F"));
                values.add(date + " " + temperature);
            }
            rows.set(values);
        });
        return rows.get();
    }
}
