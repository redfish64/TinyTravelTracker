package com.codeslap.dateslider;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;

import com.igisw.openlocationtracker.EnterFromDateToToDateActivity;

import java.util.Calendar;

/**
 * @author cristian
 * @version 1.0
 */
class WeekLabeler extends EnterFromDateToToDateActivity.Labeler {
    public WeekLabeler(EnterFromDateToToDateActivity dateSlider) {
        super(dateSlider);
    }

    @Override
    public EnterFromDateToToDateActivity.TimeObject add(long time, int val) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.add(Calendar.WEEK_OF_YEAR, val);
        return timeObjectFromCalendar(c);
    }

    @Override
    protected EnterFromDateToToDateActivity.TimeObject timeObjectFromCalendar(Calendar c) {
        int week = c.get(Calendar.WEEK_OF_YEAR);
        int day_of_week = c.get(Calendar.DAY_OF_WEEK) - 1;
        // set calendar to first millisecond of the week
        c.add(Calendar.DAY_OF_MONTH, -day_of_week);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long startTime = c.getTimeInMillis();
        // set calendar to last millisecond of the week
        c.add(Calendar.DAY_OF_WEEK, 6);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        long endTime = c.getTimeInMillis();
        return new EnterFromDateToToDateActivity.TimeObject(String.format("week %d", week), startTime, endTime);
    }

    public TimeView createView(Context context, boolean isCenterView) {
        return new CustomTimeTextView(context, isCenterView, TimeView.TEXT_SIZE_DP);
    }

    /**
     * Here we define our Custom TimeTextView which will display the fonts in its very own way.
     */
    private static class CustomTimeTextView extends TimeView.TimeTextView {

        public CustomTimeTextView(Context context, boolean isCenterView, float textSize) {
            super(context, isCenterView, textSize);
        }

        /**
         * Here we set up the text characteristics for the TextView, i.e. red colour,
         * serif font and semi-transparent white background for the centerView... and shadow!!!
         */
        @Override
        protected void setupView(boolean isCenterView, float textSize) {
            setGravity(Gravity.CENTER);
            setTextColor(0xFF883333);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
            setTypeface(Typeface.SERIF);
            if (isCenterView) {
                setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
                setBackgroundColor(0x55FFFFFF);
                setShadowLayer(2.5f, 3, 3, 0xFF999999);
            }
        }
    }
}
