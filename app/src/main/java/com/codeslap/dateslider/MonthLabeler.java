package com.codeslap.dateslider;

import android.content.Context;

import com.igisw.openlocationtracker.EnterFromDateToToDateActivity;

import java.util.Calendar;

/**
 * the month labeler  takes care of providing each TimeTextView element in the monthScroller
 * with the right label and information about its time representation
 *
 * @author cristian
 * @version 1.0
 */
public class MonthLabeler extends EnterFromDateToToDateActivity.Labeler {
    private final boolean mFullDate;

    public MonthLabeler(EnterFromDateToToDateActivity dateSlider, boolean fullDate) {
        super(dateSlider);
        mFullDate = fullDate;
    }

    /**
     * add "val" months to the month object that contains "time" and returns the new TimeObject
     */
    @Override
    public EnterFromDateToToDateActivity.TimeObject add(long time, int val) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.add(Calendar.MONTH, val);
        return timeObjectFromCalendar(c);
    }

    /**
     * creates an TimeObject from a CalendarInstance
     */
    @Override
    protected EnterFromDateToToDateActivity.TimeObject timeObjectFromCalendar(Calendar c) {
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        // set calendar to first millisecond of the month
        c.set(year, month, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        long startTime = c.getTimeInMillis();
        // set calendar to last millisecond of the month
        c.set(year, month, c.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
        c.set(Calendar.MILLISECOND, 999);
        long endTime = c.getTimeInMillis();
        if (mFullDate) {
            return new EnterFromDateToToDateActivity.TimeObject(String.format("%tb %tY", c, c), startTime, endTime);
        } else {
            return new EnterFromDateToToDateActivity.TimeObject(String.format("%tB", c), startTime, endTime);
        }
    }

    @Override
    public TimeView createView(Context context, boolean isCenterView) {
        if (mFullDate) {
            // rather than a standard TextView this is returning a LinearLayout with two TextViews
            return new TimeView.TimeLayoutView(context, isCenterView, 
            		TimeView.TEXT_SIZE_DP, 
            		TimeView.MINI_TEXT_SIZE_DP, 0.95f);
        }
        return super.createView(context, isCenterView);
    }
}
