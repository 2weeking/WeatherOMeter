package com.example.weather_o_meter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class ForecastPagerAdapter extends RecyclerView.Adapter<ForecastPagerAdapter.DayHolder> {
    public interface Listener {
        void onHourSelected(int position,int hourIndex);
        void onMetricSelected(int position,boolean precipitation);
    }
    private final List<DailyForecast> forecasts;
    private final Map<String,Integer> selections;
    private final Listener listener;
    private final Set<DayHolder> attached=new HashSet<>();
    private boolean motion=true;

    public ForecastPagerAdapter(List<DailyForecast> forecasts) { this(forecasts,new HashMap<>(),null,true); }
    public ForecastPagerAdapter(List<DailyForecast> forecasts,Map<String,Integer> selections,Listener listener,boolean motion) {
        this.forecasts=forecasts; this.selections=selections; this.listener=listener; this.motion=motion;
    }
    public int selectedHour(int position) {
        DailyForecast day=forecasts.get(position);
        Integer saved=selections.get(day.getDate());
        if(saved!=null) return Math.max(0,Math.min(saved,Math.max(0,day.getHours().size()-1)));
        int target=position==0?Calendar.getInstance(TimeZone.getTimeZone("America/Chicago")).get(Calendar.HOUR_OF_DAY):12;
        int nearest=0,distance=25;
        for(int i=0;i<day.getHours().size();i++) {
            int d=Math.abs(day.getHours().get(i).getHour()-target);
            if(d<distance) { distance=d; nearest=i; }
        }
        return nearest;
    }
    public void selectHour(int position,int index) {
        if(position<0 || position>=forecasts.size() || forecasts.get(position).getHours().isEmpty()) return;
        int safe=Math.max(0,Math.min(index,forecasts.get(position).getHours().size()-1));
        selections.put(forecasts.get(position).getDate(),safe);
        for(DayHolder holder:attached) if(holder.getBindingAdapterPosition()==position) {
            updateHour(holder,position,safe,true);
            holder.scroll.post(()->holder.scroll.smoothScrollTo(0,holder.explorer.getTop()));
        }
    }
    public void setMotionEnabled(boolean enabled) {
        motion=enabled; for(DayHolder holder:attached) holder.precipitationArt.setMotionEnabled(enabled);
    }
    @NonNull @Override public DayHolder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
        return new DayHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily_forecast,parent,false));
    }
    @Override public void onBindViewHolder(@NonNull DayHolder holder,int position) {
        DailyForecast forecast=forecasts.get(position); Context c=holder.itemView.getContext();
        holder.day.setText(position==0?c.getString(R.string.today):position==1?c.getString(R.string.tomorrow):
                formatDate(forecast.getDate(),"EEEE"));
        holder.date.setText(formatDate(forecast.getDate(),"EEE, MMM d, yyyy"));
        holder.temperature.setText(c.getString(R.string.temperature_value,forecast.getAverageFahrenheit()));
        holder.temperature.setContentDescription(c.getString(R.string.forecast_accessibility,
                holder.date.getText(),forecast.getAverageFahrenheit()));
        holder.precipitationChance.setText(WeatherUi.chance(c,forecast.getPrecipitationChancePercent()));
        holder.wind.setText(WeatherUi.wind(c,forecast.getMaxWindMph()));
        holder.precipitationChance.setContentDescription(c.getString(R.string.precipitation_accessibility,
                holder.date.getText(),holder.precipitationChance.getText()));
        holder.wind.setContentDescription(c.getString(R.string.wind_accessibility,holder.date.getText(),holder.wind.getText()));
        holder.range.setText(forecast.getLowFahrenheit()==null?c.getString(R.string.range_unavailable):
                c.getString(R.string.daily_range,forecast.getLowFahrenheit(),forecast.getHighFahrenheit()));
        holder.precipitationArt.setProbability(forecast.getPrecipitationChancePercent());
        holder.precipitationArt.setMotionEnabled(motion);
        holder.windArt.setDirection(forecast.getDominantWindDegrees());
        holder.windDirection.setText(forecast.getDominantWindDegrees()==null?c.getString(R.string.direction_unavailable):
                c.getString(R.string.dominant_wind,WeatherCondition.compassPoint(forecast.getDominantWindDegrees())));
        holder.precipitationCard.setOnClickListener(v->openMetric(holder,true));
        holder.windCard.setOnClickListener(v->openMetric(holder,false));
        holder.slider.setOnSeekBarChangeListener(null);
        holder.slider.setMax(Math.max(0,forecast.getHours().size()-1));
        holder.slider.setEnabled(!forecast.getHours().isEmpty());
        int selected=selectedHour(position); holder.slider.setProgress(selected);
        holder.dial.setHours(forecast.getHours(),selected,index->updateBoundHour(holder,index));
        holder.slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int index,boolean fromUser) { if(fromUser) updateBoundHour(holder,index); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        int warmest=forecast.getWarmestHourIndex();
        holder.warmest.setVisibility(warmest<0?View.GONE:View.VISIBLE);
        if(warmest>=0) {
            holder.warmest.setText(c.getString(R.string.warmest_hour,WeatherUi.hour(forecast.getHours().get(warmest).getHour())));
            holder.warmest.setOnClickListener(v->updateBoundHour(holder,warmest));
        }
        bindHour(holder,forecast,selected);
        ViewCompat.setAccessibilityHeading(holder.day,true);
    }
    private void openMetric(DayHolder holder,boolean precipitation) {
        int position=holder.getBindingAdapterPosition();
        if(position!=RecyclerView.NO_POSITION && listener!=null) listener.onMetricSelected(position,precipitation);
    }
    private void updateBoundHour(DayHolder holder,int index) {
        int position=holder.getBindingAdapterPosition();
        if(position!=RecyclerView.NO_POSITION) updateHour(holder,position,index,true);
    }
    private void updateHour(DayHolder holder,int position,int index,boolean notify) {
        DailyForecast day=forecasts.get(position);
        bindHour(holder,day,index);
        if(notify && listener!=null) listener.onHourSelected(position,index);
    }
    private void bindHour(DayHolder holder,DailyForecast day,int index) {
        if(day.getHours().isEmpty()) return;
        HourlyForecast hour=day.getHours().get(index); Context c=holder.itemView.getContext();
        selections.put(day.getDate(),index);
        holder.slider.setProgress(index); holder.dial.setSelectedHour(index);
        String condition=WeatherUi.condition(c,hour.getWeatherCode(),hour.getDaylight());
        holder.conditionIcon.setForecast(hour);
        holder.condition.setText(c.getString(R.string.atmosphere_at,WeatherUi.hour(hour.getHour()),condition));
        holder.explorerTime.setText(WeatherUi.hour(hour.getHour()));
        holder.explorerCondition.setText(c.getString(R.string.hour_condition,condition,
                WeatherCondition.compassPoint(hour.getWindDirectionDegrees())));
        holder.hourTemperature.setText(WeatherUi.temperature(c,hour.getTemperatureFahrenheit()));
        holder.hourChance.setText(WeatherUi.chance(c,hour.getPrecipitationChancePercent()));
        holder.hourWind.setText(WeatherUi.wind(c,hour.getWindMph()));
        holder.slider.setContentDescription(c.getString(R.string.select_hour)+": "+WeatherUi.hour(hour.getHour()));
    }
    @Override public int getItemCount() { return forecasts.size(); }
    @Override public void onViewAttachedToWindow(@NonNull DayHolder holder) { attached.add(holder); holder.precipitationArt.setMotionEnabled(motion); }
    @Override public void onViewDetachedFromWindow(@NonNull DayHolder holder) { attached.remove(holder); holder.precipitationArt.setMotionEnabled(false); }
    static String formatDate(String isoDate,String pattern) {
        SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd",Locale.US); input.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date=input.parse(isoDate,new ParsePosition(0));
        SimpleDateFormat output=new SimpleDateFormat(pattern,Locale.getDefault()); output.setTimeZone(TimeZone.getTimeZone("UTC"));
        return date==null?isoDate:output.format(date);
    }
    static final class DayHolder extends RecyclerView.ViewHolder {
        final TextView day,date,temperature,precipitationChance,wind,range,windDirection,condition,
                explorerTime,explorerCondition,hourTemperature,hourChance,hourWind,warmest;
        final WeatherGlyphView conditionIcon;
        final MetricArtView precipitationArt,windArt;
        final DayDialView dial;
        final SeekBar slider;
        final ScrollView scroll;
        final View explorer,precipitationCard,windCard;
        DayHolder(View v) {
            super(v); day=v.findViewById(R.id.forecast_day); date=v.findViewById(R.id.forecast_date);
            temperature=v.findViewById(R.id.forecast_temperature); precipitationChance=v.findViewById(R.id.forecast_precipitation_chance);
            wind=v.findViewById(R.id.forecast_wind); range=v.findViewById(R.id.forecast_range);
            windDirection=v.findViewById(R.id.wind_direction); condition=v.findViewById(R.id.forecast_condition);
            conditionIcon=v.findViewById(R.id.condition_icon); precipitationArt=v.findViewById(R.id.precipitation_art);
            windArt=v.findViewById(R.id.wind_art); dial=v.findViewById(R.id.hour_dial); slider=v.findViewById(R.id.hour_slider);
            scroll=v.findViewById(R.id.day_scroll); explorer=v.findViewById(R.id.day_explorer);
            explorerTime=v.findViewById(R.id.explorer_time); explorerCondition=v.findViewById(R.id.explorer_condition);
            hourTemperature=v.findViewById(R.id.hourly_temperature); hourChance=v.findViewById(R.id.hourly_chance);
            hourWind=v.findViewById(R.id.hourly_wind); warmest=v.findViewById(R.id.warmest_hour_button);
            precipitationCard=v.findViewById(R.id.precipitation_card); windCard=v.findViewById(R.id.wind_card);
        }
    }
}
