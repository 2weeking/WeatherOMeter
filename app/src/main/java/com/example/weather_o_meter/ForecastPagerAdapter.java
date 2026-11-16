package com.example.weather_o_meter;

import android.view.LayoutInflater;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Each recycled page has no background, so the same scenery remains visible during swipes. */
public final class ForecastPagerAdapter extends RecyclerView.Adapter<ForecastPagerAdapter.DayHolder> {
    private final List<DailyForecast> forecasts;

    public ForecastPagerAdapter(List<DailyForecast> forecasts) {
        this.forecasts = forecasts;
    }

    @NonNull
    @Override
    public DayHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DayHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_forecast, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull DayHolder holder, int position) {
        DailyForecast forecast = forecasts.get(position);
        String day = position == 0 ? holder.itemView.getContext().getString(R.string.today)
                : position == 1 ? holder.itemView.getContext().getString(R.string.tomorrow)
                : formatDate(forecast.getDate(), "EEEE");
        holder.day.setText(day);
        holder.date.setText(formatDate(forecast.getDate(), "EEEE, MMMM d, yyyy"));
        holder.temperature.setText(holder.itemView.getContext().getString(
                R.string.temperature_value, forecast.getAverageFahrenheit()));
        holder.temperature.setContentDescription(holder.itemView.getContext().getString(
                R.string.forecast_accessibility, holder.date.getText(), forecast.getAverageFahrenheit()));
        Context context = holder.itemView.getContext();
        Double chance = forecast.getPrecipitationChancePercent();
        String chanceText = chance == null ? context.getString(R.string.measurement_unavailable)
                : context.getString(R.string.precipitation_value, chance);
        Double wind = forecast.getMaxWindMph();
        String windText = wind == null ? context.getString(R.string.measurement_unavailable)
                : context.getString(R.string.wind_value, wind);
        // Always bind both values, including nulls, so a recycled slide cannot
        // retain the previous day's precipitation chance or wind.
        holder.precipitationChance.setText(chanceText);
        holder.wind.setText(windText);
        holder.precipitationChance.setContentDescription(context.getString(
                R.string.precipitation_accessibility, holder.date.getText(), chanceText));
        holder.wind.setContentDescription(context.getString(
                R.string.wind_accessibility, holder.date.getText(), windText));
        ViewCompat.setAccessibilityHeading(holder.day, true);
    }

    @Override
    public int getItemCount() {
        return forecasts.size();
    }

    static String formatDate(String isoDate, String pattern) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        input.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = input.parse(isoDate, new ParsePosition(0));
        SimpleDateFormat output = new SimpleDateFormat(pattern, Locale.getDefault());
        output.setTimeZone(TimeZone.getTimeZone("UTC"));
        return date == null ? isoDate : output.format(date);
    }

    static final class DayHolder extends RecyclerView.ViewHolder {
        final TextView day;
        final TextView date;
        final TextView temperature;
        final TextView precipitationChance;
        final TextView wind;

        DayHolder(View view) {
            super(view);
            day = view.findViewById(R.id.forecast_day);
            date = view.findViewById(R.id.forecast_date);
            temperature = view.findViewById(R.id.forecast_temperature);
            precipitationChance = view.findViewById(R.id.forecast_precipitation_chance);
            wind = view.findViewById(R.id.forecast_wind);
        }
    }
}
