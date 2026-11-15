package com.example.weather_o_meter;

import android.os.Bundle;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WeatherOMeter";
    private static final String SAVED_RESPONSE = "forecast_response";
    private static final String SAVED_UPDATED = "forecast_updated";
    private static final TimeZone AUSTIN_TIME = TimeZone.getTimeZone("America/Chicago");

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WeatherRepository repository = new WeatherRepository();
    private LinearLayout forecastList;
    private TextView statusText;
    private Button refreshButton;
    private View loadingIndicator;
    private String lastResponse;
    private String firstForecastDate;
    private long updatedAt;
    private boolean loading;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        boolean nightMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat systemBars = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        systemBars.setAppearanceLightStatusBars(!nightMode);
        systemBars.setAppearanceLightNavigationBars(!nightMode);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.app_title), true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        forecastList = findViewById(R.id.forecast_list);
        statusText = findViewById(R.id.status_text);
        refreshButton = findViewById(R.id.refresh_button);
        loadingIndicator = findViewById(R.id.loading_indicator);
        refreshButton.setOnClickListener(view -> loadForecast());

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
            forecastList.removeAllViews();
            forecastList.setVisibility(View.GONE);
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
        forecastList.removeAllViews();
        firstForecastDate = forecasts.get(0).getDate();
        // These strings already describe local dates. UTC avoids shifting the date
        // when the phone itself uses a different timezone.
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        input.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat label = new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault());
        label.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (DailyForecast forecast : forecasts) {
            View row = getLayoutInflater().inflate(R.layout.item_daily_forecast, forecastList, false);
            TextView dateView = row.findViewById(R.id.forecast_date);
            TextView temperatureView = row.findViewById(R.id.forecast_temperature);
            Date date = input.parse(forecast.getDate(), new ParsePosition(0));
            String dateLabel = date == null ? forecast.getDate() : label.format(date);
            dateView.setText(forecast.getDate().equals(todayInAustin())
                    ? getString(R.string.today_date, dateLabel) : dateLabel);
            temperatureView.setText(getString(R.string.daily_average, forecast.getAverageFahrenheit()));
            forecastList.addView(row);
        }
        forecastList.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean value) {
        loading = value;
        loadingIndicator.setVisibility(value ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!value);
        refreshButton.setText(R.string.refresh);
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
            refreshButton.setText(R.string.retry);
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
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        networkExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
