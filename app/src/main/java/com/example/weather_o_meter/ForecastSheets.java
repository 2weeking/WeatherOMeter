package com.example.weather_o_meter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.List;
import java.util.function.IntConsumer;

final class ForecastSheets {
    private ForecastSheets() {}
    private static int dp(Context c,float value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    private static TextView text(Context c,String text,int size,int color) {
        TextView view=new TextView(c); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private static final class Sheet {
        final BottomSheetDialog dialog;
        final LinearLayout body;
        Sheet(Context c,String title,String explanation) {
            dialog=new BottomSheetDialog(c);
            LinearLayout root=new LinearLayout(c); root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(c,22),dp(c,14),dp(c,18),dp(c,22));
            root.setBackgroundResource(R.drawable.glass_sheet);
            View handle=new View(c); handle.setBackgroundColor(0x77D8E9F9);
            LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(c,38),dp(c,3));
            handleParams.gravity=Gravity.CENTER_HORIZONTAL; handleParams.bottomMargin=dp(c,20); root.addView(handle,handleParams);
            TextView heading=text(c,title,25,Color.WHITE); root.addView(heading);
            androidx.core.view.ViewCompat.setAccessibilityHeading(heading,true);
            TextView info=text(c,explanation,12,0xFFCADDED);
            info.setPadding(0,dp(c,10),0,dp(c,14)); root.addView(info);
            ScrollView scroll=new ScrollView(c); scroll.setFillViewport(false);
            body=new LinearLayout(c); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
            root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            Button close=new com.google.android.material.button.MaterialButton(c,null,
                    com.google.android.material.R.attr.borderlessButtonStyle);
            close.setText(R.string.close_sheet); close.setTextColor(Color.WHITE);
            close.setOnClickListener(v->dialog.dismiss()); root.addView(close,new LinearLayout.LayoutParams(-1,dp(c,48)));
            int height=(int)(c.getResources().getDisplayMetrics().heightPixels*.82f);
            dialog.setContentView(root,new android.view.ViewGroup.LayoutParams(-1,height));
            dialog.setOnShowListener(v->{
                View container=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if(container!=null) container.setBackground(new ColorDrawable(Color.TRANSPARENT));
                dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            });
        }
    }
    static BottomSheetDialog showWeek(Context c,List<DailyForecast> days,int selected,IntConsumer onDay) {
        Sheet sheet=new Sheet(c,c.getString(R.string.week_outlook),c.getString(R.string.week_explanation));
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(DailyForecast day:days) if(day.getLowFahrenheit()!=null) {
            min=Math.min(min,day.getLowFahrenheit()); max=Math.max(max,day.getHighFahrenheit());
        }
        if(!Double.isFinite(min)) { min=0; max=1; }
        for(int i=0;i<days.size();i++) {
            DailyForecast day=days.get(i); final int index=i;
            LinearLayout row=new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6));
            row.setBackgroundResource(R.drawable.day_tab_background); row.setSelected(i==selected);
            TextView name=text(c,(i==0?c.getString(R.string.today):ForecastPagerAdapter.formatDate(day.getDate(),"EEE"))
                    +"\n"+ForecastPagerAdapter.formatDate(day.getDate(),"MMM d"),13,Color.WHITE);
            row.addView(name,new LinearLayout.LayoutParams(dp(c,63),-2));
            WeatherGlyphView glyph=new WeatherGlyphView(c);
            if(!day.getHours().isEmpty()) {
                HourlyForecast midday=day.getHours().get(0);
                for(HourlyForecast hour:day.getHours())
                    if(Math.abs(hour.getHour()-12)<Math.abs(midday.getHour()-12)) midday=hour;
                glyph.setForecast(midday);
            }
            row.addView(glyph,new LinearLayout.LayoutParams(dp(c,36),dp(c,42)));
            TemperatureRangeView range=new TemperatureRangeView(c); range.setRange(day,min,max);
            range.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams rangeParams=new LinearLayout.LayoutParams(0,dp(c,46),1);
            rangeParams.setMarginStart(dp(c,10)); row.addView(range,rangeParams);
            if(day.getLowFahrenheit()!=null) row.setContentDescription(c.getString(R.string.week_row_accessibility,
                    ForecastPagerAdapter.formatDate(day.getDate(),"EEEE, MMMM d"),day.getAverageFahrenheit(),
                    day.getLowFahrenheit(),day.getHighFahrenheit()));
            row.setFocusable(true); row.setOnClickListener(v->{sheet.dialog.dismiss(); onDay.accept(index);});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(c,76));
            params.bottomMargin=dp(c,5); sheet.body.addView(row,params);
        }
        sheet.dialog.show(); return sheet.dialog;
    }
    static BottomSheetDialog showHours(Context c,DailyForecast day,boolean precipitation,IntConsumer onHour) {
        String title=c.getString(precipitation?R.string.precipitation_label:R.string.wind_label);
        Sheet sheet=new Sheet(c,title,c.getString(precipitation?R.string.precipitation_explanation:R.string.wind_explanation));
        TextView date=text(c,ForecastPagerAdapter.formatDate(day.getDate(),"EEEE, MMMM d"),15,0xFFFFDFBB);
        date.setPadding(0,0,0,dp(c,12)); sheet.body.addView(date);
        for(int i=0;i<day.getHours().size();i++) {
            final int index=i; HourlyForecast hour=day.getHours().get(i);
            LinearLayout row=new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c,8),dp(c,4),dp(c,8),dp(c,4));
            row.setBackgroundResource(R.drawable.day_tab_background);
            row.addView(text(c,WeatherUi.hour(hour.getHour()),14,Color.WHITE),new LinearLayout.LayoutParams(dp(c,65),-2));
            WeatherGlyphView glyph=new WeatherGlyphView(c); glyph.setForecast(hour);
            row.addView(glyph,new LinearLayout.LayoutParams(dp(c,38),dp(c,42)));
            TextView value=text(c,precipitation?WeatherUi.chance(c,hour.getPrecipitationChancePercent()):
                    WeatherUi.wind(c,hour.getWindMph()),18,precipitation?0xFFBEEAFF:0xFFFFDCB9);
            value.setGravity(Gravity.END); row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=text(c,"  ›",22,0xBBDCEBFA); row.addView(arrow);
            row.setFocusable(true); row.setOnClickListener(v->{sheet.dialog.dismiss(); onHour.accept(index);});
            sheet.body.addView(row,new LinearLayout.LayoutParams(-1,dp(c,55)));
        }
        sheet.dialog.show(); return sheet.dialog;
    }
}
