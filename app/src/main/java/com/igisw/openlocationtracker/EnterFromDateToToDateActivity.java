/** 
    Copyright 2015 Tim Engler, Rareventure LLC

    This file is part of Tiny Travel Tracker.

    Tiny Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Tiny Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Tiny Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
/*
 * Copyright (C) 2011 Daniel Berndt - Codeus Ltd  -  DateSlider
 * 
 * Class for setting up the dialog and initializing the underlying
 * ScrollLayouts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igisw.openlocationtracker;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.codeslap.dateslider.DayLabeler;
import com.codeslap.dateslider.MonthLabeler;
import com.codeslap.dateslider.ScrollLayout;
import com.codeslap.dateslider.TimeLabeler;
import com.codeslap.dateslider.TimeView;
import com.igisw.openlocationtracker.Util;
import com.igisw.openlocationtracker.GTG.GTGAction;
import com.igisw.openlocationtracker.GTGActivity;
import com.igisw.openlocationtracker.OsmMapGpsTrailerReviewerMapActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class EnterFromDateToToDateActivity extends GTGActivity {

    public static final int MINUTE_INTERVAL = 15;

    private ScrollerData fromScrollerData, toScrollerData;

    private ViewGroup mLayout;
    
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd, hh:mm a");

	private TextView fromDateText;

	private TextView toDateText;

    private class ScrollerData implements ScrollLayout.OnScrollListener
    {
        private static final long MIN_TIME_PERIOD = 1000l * 60 * 15;
		protected Calendar mTime;
		private ScrollLayout monthScroller;
		private ScrollLayout dayScroller;
		private ScrollLayout timeScroller;
		private ScrollerData otherScrollerData;
		private boolean isFrom;
        
        public ScrollerData(int containerId, boolean isFrom, long timeMs) {
        	this.isFrom = isFrom;
        	this.mTime = Calendar.getInstance();
        	mTime.setTimeInMillis(timeMs);
        	
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            LayoutParams lp = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);

            monthScroller = (ScrollLayout) inflater.inflate(R.layout.scroller, null);
            monthScroller.setLabeler(new MonthLabeler(EnterFromDateToToDateActivity.this, true), 
            		mTime.getTimeInMillis(), 180, 50);
            addSlider(monthScroller, 0, lp, containerId);

            dayScroller = (ScrollLayout) inflater.inflate(R.layout.scroller, null);
            dayScroller.setLabeler(new DayLabeler(EnterFromDateToToDateActivity.this), 
            		mTime.getTimeInMillis(), 120, 50);
            addSlider(dayScroller, 1, lp, containerId);

            timeScroller = (ScrollLayout) inflater.inflate(R.layout.scroller, null);
            timeScroller.setLabeler(new TimeLabeler(EnterFromDateToToDateActivity.this), 
            		mTime.getTimeInMillis(), 80, 50);
            addSlider(timeScroller, 2, lp, containerId);
            
    	}

        protected void addSlider(ScrollLayout scroller, int index, LinearLayout.LayoutParams lp,
        		int containerId) {
            LinearLayout container = (LinearLayout) mLayout.findViewById(containerId);
            container.addView(scroller, index, lp);
            scroller.setOnScrollListener(this);
            scroller.setMinTimeAndMaxTime(GTG.cacheCreator.minTimeSec*1000l, 
            		GTG.cacheCreator.maxTimeSec*1000l + (isFrom ? -MIN_TIME_PERIOD : 0));
        }

		@Override
		public void onScroll(ScrollLayout source, long x) {
			mTime.setTimeInMillis(x);
			
			if(source == monthScroller)
			{
				Util.clearCalendarValuesUnder(mTime, Calendar.DATE);
				//this keeps the lower level labels in the center of the screen
				//when scrolling
				mTime.set(Calendar.HOUR_OF_DAY, 12);
				mTime.set(Calendar.MINUTE, 0);
				
				dayScroller.setTime(mTime.getTimeInMillis(), 0);
				timeScroller.setTime(mTime.getTimeInMillis(), 0);
			}
			else if (source == dayScroller)
			{
				Util.clearCalendarValuesUnder(mTime, Calendar.HOUR);
				mTime.set(Calendar.HOUR_OF_DAY, mTime.get(Calendar.HOUR_OF_DAY)/4 * 4);
				mTime.set(Calendar.MINUTE, 0);
				
				monthScroller.setTime(mTime.getTimeInMillis(), 0);
				timeScroller.setTime(mTime.getTimeInMillis(), 0);
			}
			else { //source == timeScroller
				monthScroller.setTime(mTime.getTimeInMillis(), 0);
				dayScroller.setTime(mTime.getTimeInMillis(), 0);
			}
			
			otherScrollerData.notifyOtherScrollerDataChanged();
			updateFromToText();
		}

		/**
		 * Notifies us that the other scroller data changed,
		 * whether from scrolling or an absolute set 
		 */
		private void notifyOtherScrollerDataChanged() {
			if(isFrom)
			{
				monthScroller.setMaxTime(
						toScrollerData.mTime.getTimeInMillis() - MIN_TIME_PERIOD);
				dayScroller.setMaxTime(
						toScrollerData.mTime.getTimeInMillis() - MIN_TIME_PERIOD);
				timeScroller.setMaxTime(
						toScrollerData.mTime.getTimeInMillis() - MIN_TIME_PERIOD);

                mTime.setTimeInMillis(Math.min(toScrollerData.mTime.getTimeInMillis() - MIN_TIME_PERIOD,
                        mTime.getTimeInMillis()));
			}
			else
			{
				monthScroller.setMinTime(
						fromScrollerData.mTime.getTimeInMillis() + MIN_TIME_PERIOD);
				dayScroller.setMinTime(
						fromScrollerData.mTime.getTimeInMillis() + MIN_TIME_PERIOD);
				timeScroller.setMinTime(
						fromScrollerData.mTime.getTimeInMillis() + MIN_TIME_PERIOD);
                mTime.setTimeInMillis(Math.max(fromScrollerData.mTime.getTimeInMillis() + MIN_TIME_PERIOD,
                        mTime.getTimeInMillis()));
			}
            
		}

		public void setTimeMs(long timeMs) {
			mTime.setTimeInMillis(timeMs);
			
			monthScroller.setTime(timeMs, 0);
			dayScroller.setTime(timeMs, 0);
			timeScroller.setTime(timeMs, 0);
			
			otherScrollerData.notifyOtherScrollerDataChanged();
		}

    }
    
    public EnterFromDateToToDateActivity() {
    }


    /**
     * Set up the dialog with all the views and their listeners
     */
    @Override
    public void doOnCreate(Bundle savedInstanceState) {
        super.doOnCreate(savedInstanceState);

        setContentView(R.layout.enter_from_date_to_to_date_activity);

        mLayout = (ViewGroup) findViewById(R.id.dateSliderMainLayout);

        Button okButton = (Button) findViewById(R.id.date_slider_ok_button);
        okButton.setOnClickListener(mOkButtonClickListener);

        Button cancelButton = (Button) findViewById(R.id.date_slider_cancel_button);
        cancelButton.setOnClickListener(mCancelButtonClickListener);
        
        fromScrollerData = new ScrollerData(R.id.from_sliders_container, true,
        		OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec*1000l);
        toScrollerData = new ScrollerData(R.id.to_sliders_container, false,
        		(OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec+
        		OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePeriodSec)*1000l);
        fromScrollerData.otherScrollerData = toScrollerData;
        toScrollerData.otherScrollerData = fromScrollerData;
        
        //to set min and max properly
        fromScrollerData.notifyOtherScrollerDataChanged();
        toScrollerData.notifyOtherScrollerDataChanged();
        
    	this.fromDateText = (TextView)findViewById(R.id.from);
    	this.toDateText = (TextView)findViewById(R.id.to);
    	
    	updateFromToText();
    }
    
    private void updateFromToText() {
    	this.toDateText.setText(getResources().getString(R.string.toText)
            + sdf.format(toScrollerData.mTime.getTime()));
    	this.fromDateText.setText(getResources().getString(R.string.fromText)
            +sdf.format(fromScrollerData.mTime.getTime()));
	}


	@Override
    public void doOnResume()
    {
    	super.doOnResume();
    }


    private final android.view.View.OnClickListener mOkButtonClickListener = new android.view.View.OnClickListener() {
        public void onClick(View v) {
        	OsmMapGpsTrailerReviewerMapActivity.setStartAndEndTimeSec(
        			(int)(fromScrollerData.mTime.getTimeInMillis()/1000l), 
        			(int)(toScrollerData.mTime.getTimeInMillis()/1000l));
        	GTG.lastSuccessfulAction = GTGAction.SET_FROM_AND_TO_DATES;
        	finish();
        }
    };

    private final android.view.View.OnClickListener mCancelButtonClickListener = new android.view.View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };
    /**
     * Defines the interface which defines the methods of the OnDateSetListener
     */
    public interface OnDateSetListener {
        /**
         * this method is called when a date was selected by the user
         *
         * @param view the caller of the methodZone
         */
        public void onDateSet(EnterFromDateToToDateActivity view, Calendar selectedDate);
    }

    /**
     * This class has the purpose of telling the corresponding scroller, which values make up
     * a single TimeTextView element.
     */
    public static abstract class Labeler {

        private final EnterFromDateToToDateActivity mDateSlider;

        public Labeler(EnterFromDateToToDateActivity dateSlider) {
            mDateSlider = dateSlider;
        }

        /**
         * gets called once, when the scroller gets initialised
         *
         * @param time the time in milliseconds
         * @return the TimeObject representing "time"
         */
        public TimeObject getElem(long time) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(time);
            return timeObjectFromCalendar(c);
        }

        /**
         * returns a new TimeTextView instance, is only called a couple of times in the
         * initialisation process
         *
         * @param context      used to create the view
         * @param isCenterView is true when the view is the central view
         * @return a TimeView instance
         */
        public TimeView createView(Context context, boolean isCenterView) {
            return new TimeView.TimeTextView(context, isCenterView, TimeView.TEXT_SIZE_DP);
        }

        public EnterFromDateToToDateActivity getDateSlider() {
            return mDateSlider;
        }

        /**
         * This method will be called constantly, whenever new date information is required
         * it receives a timestamps and adds "val" time units to that time and returns it as
         * a TimeObject
         *
         * @param time the time in milliseconds
         * @param val  days to add
         * @return new time object
         */
        public abstract TimeObject add(long time, int val);

        protected abstract TimeObject timeObjectFromCalendar(Calendar c);
    }

    /**
     * Very simple helper class that defines a time unit with a label (text) its start-
     * and end date
     */
    public static class TimeObject {
        public final CharSequence text;
        public final long startTime, endTime;

        public TimeObject(final CharSequence text, final long startTime, final long endTime) {
            this.text = text;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
    
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI;
	}

}
