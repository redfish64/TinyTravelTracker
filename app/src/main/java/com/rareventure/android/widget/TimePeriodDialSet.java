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
package com.rareventure.android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;

import com.rareventure.android.Util;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;

public class TimePeriodDialSet extends ViewGroup implements Dial.Listener
{
	public TimePeriodDialSet(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	private long startTimeMs;
	private long endTimeMs;
	private TimeLengthDial timeLengthDial;
	public TimePosDial timePosDial;
	private Listener listener;
	

	public TimePeriodDialSet(Context context, long startTimeMs, long endTimeMs,
			long timePosMs, long timePeriodMs, int[] colors) {
		super(context);
		
		init(startTimeMs, endTimeMs, colors, startTimeMs, endTimeMs - startTimeMs);
	}
	
	public void init(long startTimeMs, long endTimeMs, int[] colors, long currTimePosMs, long currTimePeriodMs) {
		this.startTimeMs = startTimeMs;
		this.endTimeMs = endTimeMs;

		removeAllViews();
		
		initAbsTimeDial(colors, currTimePeriodMs, currTimePosMs);
		initTimeLengthDial(endTimeMs - startTimeMs, currTimePeriodMs);
	}

	private Preferences prefs = new Preferences();

	private void initTimeLengthDial(long maxRangeMs, long initialRange) {
		timeLengthDial = new TimeLengthDial(getContext());
		timeLengthDial.init(prefs.minRangeMs, maxRangeMs);
		
		timeLengthDial.setPeriodLength(initialRange);
		
		this.addView(timeLengthDial);
		
		timeLengthDial.setListener(this);
	}

	

	private void initAbsTimeDial(int [] colors, long range, long initialTicks) {
		timePosDial = new TimePosDial(getContext());
		
		//TODO 4: ticks on the dial for hour and minute markers
		timePosDial.init(
				colors , startTimeMs, endTimeMs, initialTicks,
				range);
		this.addView(timePosDial);
		timePosDial.setListener(this);
	}


	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if(timeLengthDial != null) //so we can display in eclipse's layout manager
		{
			timeLengthDial.layout(0,0,r-l,timeLengthDial.preferredHeight); //TODO 4: layout this stuff better, it's hard coded
			timePosDial.layout(0,timeLengthDial.preferredHeight,r-l,b);
		}
	}

    /**
     * @see android.view.View#measure(int, int)
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    	int preferredHeight = timeLengthDial.preferredHeight + timePosDial.preferredHeight;
    	
//    	Log.d(GTG.TAG,"on Measure tpds preferredHeight "+preferredHeight);
    	
        setMeasuredDimension(Util.measureWithPreferredSize(widthMeasureSpec,
        		//TODO 3 HACK what do we really want for preferredWidth
        		preferredHeight
        		+ getPaddingLeft() + getPaddingRight()),
                Util.measureWithPreferredSize(heightMeasureSpec, preferredHeight 
                		+ getPaddingTop() + getPaddingBottom()));
    }
    

	public static class Preferences implements AndroidPreferences
	{


		/**
		 * Minimum range which we will display
		 */
		public long minRangeMs = 60*1000;
		
		/**
		 * Minimum pad between text labels of dialers in pixels
		 */
		public int minPadPixels= 10;

	}

	@Override
	public void posChanged(Dial dial) {
		if(dial == timeLengthDial)
		{
			double periodLength = timeLengthDial.getPeriodLength();
			
			timePosDial.setPeriodLength(periodLength);
		}
		
		if(listener != null)
			listener.onTimePeriodDialSetChange();
	}
	
	public double getPeriodLength()
	{
		return timeLengthDial.getPeriodLength();
	}
	
	public long getTimePos()
	{
		return timePosDial.ticks;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}
	

	
	public interface Listener
	{
		public void onTimePeriodDialSetChange();
	}
}
