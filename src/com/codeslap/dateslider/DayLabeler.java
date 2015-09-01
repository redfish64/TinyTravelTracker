package com.codeslap.dateslider;

import android.content.Context;

import java.util.Calendar;

import com.rareventure.gps2.reviewer.EnterFromDateToToDateActivity;

/**
 * Day labeler takes care of providing each TimeTextView element in the dayScroller
 * with the right label and information about its time representation
 *
 * @author cristian
 * @version 1.0
 */
public class DayLabeler extends EnterFromDateToToDateActivity.Labeler {
    private final boolean mCustom;

    public DayLabeler(EnterFromDateToToDateActivity dateSlider) {
        this(dateSlider, false);
    }

    public DayLabeler(EnterFromDateToToDateActivity dateSlider, boolean custom) {
        super(dateSlider);
        mCustom = custom;
    }

    @Override
    public EnterFromDateToToDateActivity.TimeObject add(long time, int val) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.add(Calendar.DAY_OF_MONTH, val);
        return timeObjectFromCalendar(c);
    }

    @Override
    protected EnterFromDateToToDateActivity.TimeObject timeObjectFromCalendar(Calendar c) {
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        // set calendar to first millisecond of the day
        c.set(year, month, day, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        long startTime = c.getTimeInMillis();
        // set calendar to last millisecond of the day
        c.set(year, month, day, 23, 59, 59);
        c.set(Calendar.MILLISECOND, 999);
        long endTime = c.getTimeInMillis();
        if (mCustom) {
            return new EnterFromDateToToDateActivity.TimeObject(String.format("%tA", c), startTime, endTime);
        }
        return new EnterFromDateToToDateActivity.TimeObject(String.format("%td %ta", c, c), startTime, endTime);
    }

    @Override
    public TimeView createView(Context context, boolean isCenterView) {
        if (mCustom) {
            return super.createView(context, isCenterView);
        }
        return new TimeView.DayTimeLayoutView(context, isCenterView, TimeView.TEXT_SIZE_DP,
        		TimeView.MINI_TEXT_SIZE_DP, 0.95f);
    }
}
