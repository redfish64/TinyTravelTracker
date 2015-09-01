package com.codeslap.dateslider;

import java.util.Calendar;

import com.rareventure.gps2.reviewer.EnterFromDateToToDateActivity;

/**
 * @author cristian
 * @version 1.0
 */
class MinuteLabeler extends EnterFromDateToToDateActivity.Labeler {
    public MinuteLabeler(EnterFromDateToToDateActivity dateSlider) {
        super(dateSlider);
    }

    @Override
    public EnterFromDateToToDateActivity.TimeObject add(long time, int val) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.add(Calendar.MINUTE, val);
        return timeObjectFromCalendar(c);
    }

    @Override
    protected EnterFromDateToToDateActivity.TimeObject timeObjectFromCalendar(Calendar c) {
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        // get the first millisecond of that minute
        c.set(year, month, day, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        long startTime = c.getTimeInMillis();
        // get the last millisecond of that minute
        c.set(year, month, day, hour, minute, 59);
        c.set(Calendar.MILLISECOND, 999);
        long endTime = c.getTimeInMillis();
        return new EnterFromDateToToDateActivity.TimeObject(String.valueOf(minute), startTime, endTime);
    }
}
