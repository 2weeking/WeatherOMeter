package com.example.weather_o_meter;

import android.os.Bundle;
import android.os.Build;
import android.os.PowerManager;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Gravity;
import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.HorizontalScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WeatherOMeter";
    private static final String SAVED_RESPONSE = "forecast_response";
    private static final String SAVED_UPDATED = "forecast_updated";
    private static final String SAVED_PAGE = "forecast_page";
    private static final String SAVED_HOURS = "forecast_hours";
    private static final TimeZone AUSTIN_TIME = TimeZone.getTimeZone("America/Chicago");

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WeatherRepository repository = new WeatherRepository();
    private ViewPager2 forecastPager;
    private AustinSkyView skyBackground;
    private LinearLayout weekStrip;
    private HorizontalScrollView weekScroller;
    private TextView pagePosition;
    private Button todayButton;
    private Button previousButton;
    private Button nextButton;
    private int selectedDay;
    private TextView statusText;
    private Button refreshButton;
    private View loadingIndicator;
    private String lastResponse;
    private String firstForecastDate;
    private long updatedAt;
    private boolean loading;
    private volatile boolean destroyed;
    private List<DailyForecast> activeForecasts = new ArrayList<>();
    private final Map<String, Integer> hourSelections = new HashMap<>();
    private int[] restoredHours;
    private boolean motionEnabled;
    private MaterialButton motionButton;
    private Button weekButton;
    private BottomSheetDialog openSheet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        WindowInsetsControllerCompat systemBars = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        systemBars.setAppearanceLightStatusBars(false);
        systemBars.setAppearanceLightNavigationBars(false);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.app_title), true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        forecastPager = findViewById(R.id.forecast_pager);
        skyBackground = findViewById(R.id.sky_background);
        weekStrip = findViewById(R.id.week_strip);
        weekScroller = findViewById(R.id.week_scroller);
        pagePosition = findViewById(R.id.page_position);
        todayButton = findViewById(R.id.today_button);
        previousButton = findViewById(R.id.previous_button);
        nextButton = findViewById(R.id.next_button);
        statusText = findViewById(R.id.status_text);
        refreshButton = findViewById(R.id.refresh_button);
        loadingIndicator = findViewById(R.id.loading_indicator);
        refreshButton.setOnClickListener(view -> loadForecast());
        motionButton = findViewById(R.id.motion_button);
        weekButton = findViewById(R.id.week_button);
        motionEnabled = getSharedPreferences("weather_display", MODE_PRIVATE).getBoolean("motion", true);
        motionButton.setOnClickListener(view -> {
            motionEnabled = !motionEnabled;
            getSharedPreferences("weather_display", MODE_PRIVATE).edit().putBoolean("motion", motionEnabled).apply();
            applyMotion();
        });
        weekButton.setOnClickListener(view -> {
            if (activeForecasts.isEmpty()) return;
            if (openSheet != null) openSheet.dismiss();
            openSheet = ForecastSheets.showWeek(this, activeForecasts, selectedDay,
                    index -> forecastPager.setCurrentItem(index, true));
        });
        todayButton.setOnClickListener(view -> forecastPager.setCurrentItem(0, true));
        previousButton.setOnClickListener(view -> forecastPager.setCurrentItem(selectedDay - 1, true));
        nextButton.setOnClickListener(view -> forecastPager.setCurrentItem(selectedDay + 1, true));
        ((TextView) findViewById(R.id.weather_credit)).setMovementMethod(LinkMovementMethod.getInstance());
        selectedDay = savedInstanceState == null ? 0 : savedInstanceState.getInt(SAVED_PAGE, 0);
        restoredHours = savedInstanceState == null ? null : savedInstanceState.getIntArray(SAVED_HOURS);
        forecastPager.setOffscreenPageLimit(1);
        forecastPager.getChildAt(0).setOverScrollMode(View.OVER_SCROLL_NEVER);
        forecastPager.setPageTransformer((page, position) -> {
            float distance = Math.min(1, Math.abs(position));
            page.setAlpha(1 - distance * 0.55f);
            page.setScaleX(1 - distance * 0.04f);
            page.setScaleY(1 - distance * 0.04f);
        });
        forecastPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                selectedDay = position;
                updateNavigation();
                applyAtmosphere();
            }

            @Override
            public void onPageScrolled(int position, float offset, int pixels) {
                skyBackground.setScrollPosition(position + offset);
            }
        });
        updateNavigation();

        // Retain a completed forecast through rotation and system recreation.
        if (savedInstanceState != null) {
            String response = savedInstanceState.getString(SAVED_RESPONSE);
            if (response != null) {
                try {
                    List<DailyForecast> forecast = ForecastParser.parse(response);
                    if (forecast.get(0).getDate().equals(todayInAustin())) {
                        lastResponse = response;
                        updatedAt = savedInstanceState.getLong(SAVED_UPDATED);
                        showForecast(forecast);
                        statusText.setText(updatedMessage());
                    }
                } catch (JSONException ignored) {
                    // A damaged saved state should trigger a fresh request.
                }
            }
        }
        if (lastResponse == null) {
            loadForecast();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (skyBackground != null) {
            skyBackground.setActive(true);
            applyMotion();
        }
        // Reopening after midnight must start the week on the new Austin date.
        if (!loading && firstForecastDate != null
                && !firstForecastDate.equals(todayInAustin())) {
            loadForecast();
        }
    }

    private void loadForecast() {
        if (loading) {
            return;
        }
        // Do not keep yesterday's seven-day list on screen as today's forecast.
        if (firstForecastDate != null && !firstForecastDate.equals(todayInAustin())) {
            lastResponse = null;
            firstForecastDate = null;
            forecastPager.setAdapter(null);
            activeForecasts = new ArrayList<>();
            hourSelections.clear();
            weekStrip.removeAllViews();
            selectedDay = 0;
            skyBackground.setScrollPosition(0);
            findViewById(R.id.empty_state).setVisibility(View.VISIBLE);
            updateNavigation();
        }
        setLoading(true);
        statusText.setText(lastResponse == null ? R.string.loading : R.string.refreshing);
        networkExecutor.execute(() -> {
            try {
                String response = repository.fetchForecast();
                List<DailyForecast> forecasts = ForecastParser.parse(response);
                mainHandler.post(() -> {
                    if (destroyed) {
                        return;
                    }
                    lastResponse = response;
                    updatedAt = System.currentTimeMillis();
                    showForecast(forecasts);
                    setLoading(false);
                    statusText.setText(updatedMessage());
                });
            } catch (SocketTimeoutException exception) {
                Log.w(TAG, "Weather request timed out", exception);
                postError(getString(R.string.error_timeout));
            } catch (WeatherRepository.HttpException exception) {
                Log.w(TAG, "Weather service returned an HTTP error", exception);
                postError(getString(R.string.error_service, exception.getStatusCode()));
            } catch (JSONException exception) {
                Log.w(TAG, "Invalid weather response", exception);
                postError(getString(R.string.error_data));
            } catch (IOException exception) {
                Log.w(TAG, "Weather network request failed", exception);
                postError(getString(R.string.error_network));
            }
        });
    }

    private void showForecast(List<DailyForecast> forecasts) {
        int page = Math.max(0, Math.min(selectedDay, forecasts.size() - 1));
        if (firstForecastDate != null && !firstForecastDate.equals(forecasts.get(0).getDate())) {
            page = 0;
        }
        firstForecastDate = forecasts.get(0).getDate();
        activeForecasts = forecasts;
        if (restoredHours != null && restoredHours.length == forecasts.size()) {
            for (int i = 0; i < forecasts.size(); i++) hourSelections.put(forecasts.get(i).getDate(), restoredHours[i]);
            restoredHours = null;
        }
        weekStrip.removeAllViews();
        for (int i = 0; i < forecasts.size(); i++) {
            final int dayIndex = i;
            String date = forecasts.get(i).getDate();
            TextView tab = new TextView(this);
            tab.setId(View.generateViewId());
            tab.setText(getString(R.string.day_tab_label,
                    ForecastPagerAdapter.formatDate(date, "EEE"),
                    ForecastPagerAdapter.formatDate(date, "d")));
            tab.setTextSize(12);
            tab.setGravity(Gravity.CENTER);
            tab.setLineSpacing(dp(5), 1);
            tab.setBackgroundResource(R.drawable.day_tab_background);
            tab.setContentDescription(getString(R.string.day_tab_accessibility,
                    ForecastPagerAdapter.formatDate(date, "EEEE, MMMM d")));
            tab.setFocusable(true);
            tab.setOnClickListener(view -> forecastPager.setCurrentItem(dayIndex, true));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(62), 1);
            params.setMarginStart(dp(2));
            params.setMarginEnd(dp(2));
            weekStrip.addView(tab, params);
        }
        forecastPager.setAdapter(new ForecastPagerAdapter(forecasts, hourSelections, new ForecastPagerAdapter.Listener() {
            @Override public void onHourSelected(int position, int hourIndex) {
                if (position == selectedDay) applyAtmosphere();
            }
            @Override public void onMetricSelected(int position, boolean precipitation) {
                if (position < 0 || position >= activeForecasts.size()) return;
                if (openSheet != null) openSheet.dismiss();
                openSheet = ForecastSheets.showHours(MainActivity.this, activeForecasts.get(position), precipitation,
                        index -> {
                            ForecastPagerAdapter adapter = (ForecastPagerAdapter) forecastPager.getAdapter();
                            if (adapter != null) {
                                adapter.selectHour(position, index);
                                applyAtmosphere();
                            }
                        });
            }
        }, motionAllowed()));
        forecastPager.setCurrentItem(page, false);
        selectedDay = page;
        skyBackground.setScrollPosition(page);
        findViewById(R.id.empty_state).setVisibility(View.GONE);
        updateNavigation();
        applyAtmosphere();
        applyMotion();
    }

    private void updateNavigation() {
        int count = forecastPager.getAdapter() == null ? 0 : forecastPager.getAdapter().getItemCount();
        todayButton.setEnabled(count > 0);
        weekButton.setEnabled(count > 0);
        weekButton.setAlpha(count > 0 ? 1 : 0.3f);
        previousButton.setEnabled(count > 0 && selectedDay > 0);
        nextButton.setEnabled(count > 0 && selectedDay < count - 1);
        previousButton.setAlpha(previousButton.isEnabled() ? 1 : 0.3f);
        nextButton.setAlpha(nextButton.isEnabled() ? 1 : 0.3f);
        todayButton.setAlpha(count > 0 ? 1 : 0.3f);
        pagePosition.setText(count == 0 ? "" : getString(R.string.page_position, selectedDay + 1, count));
        for (int i = 0; i < weekStrip.getChildCount(); i++) {
            TextView tab = (TextView) weekStrip.getChildAt(i);
            tab.setSelected(i == selectedDay);
            tab.setTextColor(i == selectedDay ? Color.WHITE : 0xBBD9E4F3);
        }
        weekStrip.post(() -> {
            View tab = weekStrip.getChildAt(selectedDay);
            if (!destroyed && tab != null) {
                weekScroller.smoothScrollTo(tab.getLeft()
                        - (weekScroller.getWidth() - tab.getWidth()) / 2, 0);
            }
        });
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applyAtmosphere() {
        if (selectedDay < 0 || selectedDay >= activeForecasts.size()) return;
        ForecastPagerAdapter adapter = (ForecastPagerAdapter) forecastPager.getAdapter();
        List<HourlyForecast> hours = activeForecasts.get(selectedDay).getHours();
        if (adapter != null && !hours.isEmpty()) skyBackground.setForecast(hours.get(adapter.selectedHour(selectedDay)));
    }

    private boolean motionAllowed() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return motionEnabled && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
                && (power == null || !power.isPowerSaveMode());
    }

    private void applyMotion() {
        boolean systemAllows = (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        systemAllows &= power == null || !power.isPowerSaveMode();
        motionButton.setEnabled(systemAllows);
        motionButton.setContentDescription(getString(!systemAllows ? R.string.motion_system_off
                : motionEnabled ? R.string.pause_motion : R.string.resume_motion));
        motionButton.setIconResource(motionEnabled && systemAllows ? R.drawable.ic_motion_pause : R.drawable.ic_motion_play);
        skyBackground.setMotionEnabled(motionAllowed());
        if (forecastPager.getAdapter() instanceof ForecastPagerAdapter) {
            ((ForecastPagerAdapter) forecastPager.getAdapter()).setMotionEnabled(motionAllowed());
        }
    }

    @Override protected void onPause() {
        if (skyBackground != null) skyBackground.setActive(false);
        if (forecastPager != null && forecastPager.getAdapter() instanceof ForecastPagerAdapter) {
            ((ForecastPagerAdapter) forecastPager.getAdapter()).setMotionEnabled(false);
        }
        super.onPause();
    }

    private void setLoading(boolean value) {
        loading = value;
        loadingIndicator.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        refreshButton.setEnabled(!value);
        refreshButton.setAlpha(value ? 0.4f : 1);
        refreshButton.setContentDescription(getString(R.string.refresh));
    }

    private void postError(String message) {
        if (destroyed) {
            return;
        }
        mainHandler.post(() -> {
            if (destroyed) {
                return;
            }
            setLoading(false);
            statusText.setText(lastResponse == null ? message
                    : getString(R.string.previous_forecast, message, updatedMessage()));
            refreshButton.setContentDescription(getString(R.string.retry));
        });
    }

    private String updatedMessage() {
        SimpleDateFormat format = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        format.setTimeZone(AUSTIN_TIME);
        return getString(R.string.updated, format.format(new Date(updatedAt)));
    }

    private String todayInAustin() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(AUSTIN_TIME);
        return format.format(new Date());
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (lastResponse != null) {
            state.putString(SAVED_RESPONSE, lastResponse);
            state.putLong(SAVED_UPDATED, updatedAt);
            state.putInt(SAVED_PAGE, selectedDay);
            int[] hours = new int[activeForecasts.size()];
            ForecastPagerAdapter adapter = (ForecastPagerAdapter) forecastPager.getAdapter();
            if (adapter != null) for (int i = 0; i < hours.length; i++) hours[i] = adapter.selectedHour(i);
            state.putIntArray(SAVED_HOURS, hours);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (openSheet != null) openSheet.dismiss();
        networkExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
