package com.example.weather_o_meter;

import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import android.widget.ScrollView;
import android.os.Bundle;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Live emulator test for the gestures, day navigation, refresh, and recreation. */
@RunWith(AndroidJUnit4.class)
public class ForecastScreenTest {
    @Test
    public void exploresRealHoursDetailsAndWeekWithoutChangingDailyReadings() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            awaitRequest(scenario); awaitPage(scenario,0);
            AtomicReference<List<DailyForecast>> days=new AtomicReference<>();
            scenario.onActivity(activity->{
                Bundle state=new Bundle(); activity.onSaveInstanceState(state);
                try { days.set(ForecastParser.parse(state.getString("forecast_response"))); }
                catch(Exception error) { throw new AssertionError(error); }
                View page=currentPage(activity);
                ((ScrollView)page).scrollTo(0,page.findViewById(R.id.day_explorer).getTop());
            });
            String daily=readPage(scenario);
            // Touch the right-hand hour of the 24-hour clock: 6 AM.
            onView(allOf(withId(R.id.hour_dial),isDisplayed())).perform(
                    new androidx.test.espresso.action.GeneralClickAction(
                            androidx.test.espresso.action.Tap.SINGLE,view->{
                                int[] location=new int[2]; view.getLocationOnScreen(location);
                                return new float[]{location[0]+view.getWidth()/2f+Math.min(view.getWidth(),view.getHeight())*.35f,
                                        location[1]+view.getHeight()/2f};
                            },androidx.test.espresso.action.Press.FINGER));
            AtomicReference<Integer> six=new AtomicReference<>();
            for(int i=0;i<days.get().get(0).getHours().size();i++)
                if(days.get().get(0).getHours().get(i).getHour()==6) six.set(i);
            assertHour(scenario,days.get().get(0).getHours().get(six.get()));
            assertEquals(daily,readPage(scenario));
            scenario.recreate(); awaitPage(scenario,0);
            assertHour(scenario,days.get().get(0).getHours().get(six.get()));

            scenario.onActivity(activity->((ScrollView)currentPage(activity)).scrollTo(0,0));
            onView(allOf(withId(R.id.precipitation_card),isDisplayed())).perform(click());
            onView(withText(R.string.precipitation_explanation)).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            awaitSheet();
            // Selecting midnight from the detail sheet must update the same day's dial.
            onView(withText(WeatherUi.hour(0))).perform(click());
            scenario.onActivity(activity->assertEquals("Detail sheet changed the stored hour",0,
                    ((ForecastPagerAdapter)((ViewPager2)activity.findViewById(R.id.forecast_pager)).getAdapter()).selectedHour(0)));
            assertHour(scenario,days.get().get(0).getHours().get(0));
            assertEquals(daily,readPage(scenario));

            scenario.onActivity(activity->((ScrollView)currentPage(activity)).scrollTo(0,0));
            onView(allOf(withId(R.id.wind_card),isDisplayed())).perform(click());
            onView(withText(R.string.wind_explanation)).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            awaitSheet();
            onView(withText(R.string.close_sheet)).perform(click());
            onView(withId(R.id.week_button)).perform(click());
            awaitSheet();
            String tomorrow=ForecastPagerAdapter.formatDate(days.get().get(1).getDate(),"EEE")+"\n"
                    +ForecastPagerAdapter.formatDate(days.get().get(1).getDate(),"MMM d");
            onView(withText(tomorrow)).perform(click()); awaitPage(scenario,1);
            onView(withId(R.id.today_button)).perform(click()); awaitPage(scenario,0);
            scenario.onActivity(activity->assertEquals("Returning to Today preserved the chosen hour",0,
                    ((ForecastPagerAdapter)((ViewPager2)activity.findViewById(R.id.forecast_pager)).getAdapter()).selectedHour(0)));
            assertHour(scenario,days.get().get(0).getHours().get(0));
        }
    }

    private View currentPage(MainActivity activity) {
        ViewPager2 pager=activity.findViewById(R.id.forecast_pager);
        return ((RecyclerView)pager.getChildAt(0)).findViewHolderForAdapterPosition(pager.getCurrentItem()).itemView;
    }
    private void awaitSheet() {
        onView(withId(com.google.android.material.R.id.design_bottom_sheet)).perform(new androidx.test.espresso.ViewAction() {
            public org.hamcrest.Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "Wait for the detail sheet to finish opening"; }
            public void perform(androidx.test.espresso.UiController controller,View view) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior=
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(view);
                long deadline=SystemClock.uptimeMillis()+2500;
                do { controller.loopMainThreadForAtLeast(50); }
                while(behavior.getState()!=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                        && SystemClock.uptimeMillis()<deadline);
                // The dialog window has its own entrance animation after the behavior settles.
                controller.loopMainThreadForAtLeast(300);
            }
        });
    }
    private void assertHour(ActivityScenario<MainActivity> scenario,HourlyForecast hour) {
        scenario.onActivity(activity->{
            View page=currentPage(activity);
            assertEquals(WeatherUi.hour(hour.getHour()),((TextView)page.findViewById(R.id.explorer_time)).getText().toString());
            assertEquals(WeatherUi.temperature(activity,hour.getTemperatureFahrenheit()),
                    ((TextView)page.findViewById(R.id.hourly_temperature)).getText().toString());
            assertEquals(WeatherUi.chance(activity,hour.getPrecipitationChancePercent()),
                    ((TextView)page.findViewById(R.id.hourly_chance)).getText().toString());
            assertEquals(WeatherUi.wind(activity,hour.getWindMph()),
                    ((TextView)page.findViewById(R.id.hourly_wind)).getText().toString());
        });
    }

    @Test
    public void exploresSevenDaysAndReturnsToToday() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitRequest(scenario);
            awaitPage(scenario, 0);
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.forecast_pager);
                assertEquals(7, pager.getAdapter().getItemCount());
                assertFalse(activity.findViewById(R.id.previous_button).isEnabled());
                String status = ((TextView) activity.findViewById(R.id.status_text)).getText().toString();
                assertTrue(status, status.startsWith("Updated "));
            });
            String today = readPage(scenario);
            Set<String> days = new HashSet<>();
            days.add(today);
            for (int i = 1; i < 7; i++) {
                onView(withId(R.id.forecast_pager)).perform(swipeLeft());
                awaitPage(scenario, i);
                days.add(readPage(scenario));
            }
            assertEquals("Every swipe should reveal a distinct forecast date", 7, days.size());
            scenario.onActivity(activity -> assertFalse(activity.findViewById(R.id.next_button).isEnabled()));
            onView(withId(R.id.today_button)).perform(click());
            awaitPage(scenario, 0);
            assertEquals(today, readPage(scenario));

            onView(withId(R.id.next_button)).perform(click());
            awaitPage(scenario, 1);
            String tomorrow = readPage(scenario);
            scenario.recreate();
            awaitPage(scenario, 1);
            assertEquals(tomorrow, readPage(scenario));

            onView(withId(R.id.refresh_button)).perform(click());
            awaitRequest(scenario);
            awaitPage(scenario, 1);
            onView(withId(R.id.forecast_pager)).perform(swipeRight());
            awaitPage(scenario, 0);
            onView(withId(R.id.next_button)).perform(click());
            awaitPage(scenario, 1);
            onView(withId(R.id.previous_button)).perform(click());
            awaitPage(scenario, 0);

            // Direct date selection uses the same pager navigation as the gestures.
            scenario.onActivity(activity ->
                    ((android.widget.LinearLayout) activity.findViewById(R.id.week_strip))
                            .getChildAt(5).performClick());
            awaitPage(scenario, 5);
            onView(withId(R.id.today_button)).perform(click());
            awaitPage(scenario, 0);
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

    private void awaitPage(ActivityScenario<MainActivity> scenario, int index) {
        AtomicBoolean ready = new AtomicBoolean(false);
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!ready.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.forecast_pager);
                RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
                ready.set(pager.getCurrentItem() == index
                        && pager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE
                        && recycler.findViewHolderForAdapterPosition(index) != null);
            });
            if (!ready.get()) {
                SystemClock.sleep(50);
            }
        }
        assertTrue("The pager did not settle on day " + index, ready.get());
    }

    private String readPage(ActivityScenario<MainActivity> scenario) {
        AtomicReference<String> value = new AtomicReference<>();
        scenario.onActivity(activity -> {
            ViewPager2 pager = activity.findViewById(R.id.forecast_pager);
            RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
            View page = recycler.findViewHolderForAdapterPosition(pager.getCurrentItem()).itemView;
            String date = ((TextView) page.findViewById(R.id.forecast_date)).getText().toString();
            String temperature = ((TextView) page.findViewById(R.id.forecast_temperature)).getText().toString();
            String chance = ((TextView) page.findViewById(R.id.forecast_precipitation_chance)).getText().toString();
            String wind = ((TextView) page.findViewById(R.id.forecast_wind)).getText().toString();
            assertFalse(date.isEmpty());
            assertTrue(temperature, temperature.matches("-?\\d+\\.\\d"));
            assertTrue(chance, chance.matches("(?:100|\\d{1,2})%|Unavailable"));
            assertTrue(wind, wind.matches("\\d+\\.\\d mph|Unavailable"));
            value.set(date + " " + temperature + " " + chance + " " + wind);
        });
        return value.get();
    }
}
