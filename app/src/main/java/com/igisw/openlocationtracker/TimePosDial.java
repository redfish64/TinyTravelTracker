/** 
    Copyright 2022 Igor Calì <igor.cali0@gmail.com>

    This file is part of Open Travel Tracker.

    Open Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Open Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Open Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.igisw.openlocationtracker;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;

import com.igisw.openlocationtracker.AndroidPreferenceSet.AndroidPreferences;

import java.util.Calendar;

//TODO 3: what about other languages?
public class TimePosDial extends RangeDial implements Strip.DataGenerator
{
	private static final String[] DAY_OF_WEEK = new String [] { "Sun", "Mon",
		"Tue", "Wed", "Thu", "Fri", "Sat" };

	
	private String [] MONTHS = new String [] {
			"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
			"Nov", "Dec" };

	/**
	 * The amount of step the years can be on the dial
	 */
	private static final int [] YEAR_FACTORS = {1,2,3, 5, 10, 20, 30, 50, 100, 200, 500, 1000};

	/**
	 * The amount of step the months can be on the dial up to the years
	 */
	private static final int[] MONTH_FACTORS = {1,2,3,6,9};
	
	/**
	 * The amount of step the days can be on the dial up to the months, etc etc etc
	 */
	private static final int[] DAY_OF_MONTH_FACTORS = {1,2,3,5,10,15};
	private static final int[] DAY_OF_WEEK_FACTORS = {1,2,3,5,10,15};
	private static final int[] MINUTE_FACTORS = {1,2,3,5,10,15,20,30,60,90,2*60,3*60,4*60,6*60,9*60,12*60,
			18*60};

	private TextPaint tp;
	
	private Preferences prefs = new Preferences();

	private StripGroup[] stripGroups;

	private TextStrip yearStrip;

	private TextStrip monthStrip;

	private TextStrip dayOfMonthStrip;

	private TextStrip dayOfWeekStrip;

	private TextStrip minuteStrip;

	private Calendar calendar = Calendar.getInstance();

	public TimePosDial(Context context) {
		super(context);
		tp = new TextPaint();
		tp.setTextSize(Util.convertSpToPixel(prefs.textSize, context) );
		tp.setColor(prefs.textColor );
		
	}
	
	public void init(int [] colors, long startTicks, long endTicks, long initialTicks, long initialRange) {
		super.init(colors);
		setRangeSizeTicks(initialRange);
		super.setStartTicks(startTicks);
		super.setEndTicks(endTicks);
		setTicks(initialTicks);
		
		createStrips();
		
		//create strip groups
		stripGroups = new StripGroup [1];
		
		int i = 0;
		stripGroups[i++] = new StripGroup(yearStrip,monthStrip,dayOfMonthStrip,dayOfWeekStrip, minuteStrip);
//		stripGroups[i++] = new StripGroup(yearStrip,monthStrip,dayOfMonthStrip);
//		stripGroups[i++] = new StripGroup(yearStrip,monthStrip);
//		stripGroups[i++] = new StripGroup(yearStrip);
	}

	//TODO 3: What to do about year not always displaying when down to the second
	//strip group? In other words, the year, month, day, hour, and minute strips
	//may all be blank and the only strip with anything at all on it is the seconds
	
	private void createStrips()
	{
		int ml = Util.getTextLength(tp, "2000") + prefs.minPadPixels;
		long ticks = Util.MS_PER_YEAR * 100;
		
		ml = Util.getTextLength(tp, "2000") + prefs.minPadPixels;
		ticks = Util.MS_PER_YEAR - Util.MS_PER_DAY;
		
		yearStrip = new TextStrip();
		yearStrip.init(tp, this, (double)ticks/ml);
		
		ml = Util.getTextLength(tp, "Feb") + prefs.minPadPixels;
		ticks = Util.MS_PER_DAY * 28;
		
		monthStrip = new TextStrip();
		monthStrip.init(tp, this, (double)ticks/ml);
		
		ml = Util.getTextLength(tp, "30") + prefs.minPadPixels;
		ticks = Util.MS_PER_DAY - Util.MS_PER_HOUR;
		
		dayOfMonthStrip = new TextStrip();
		dayOfMonthStrip.init(tp, this, (double)ticks/ml);
		
		ml = Util.getTextLength(tp, "Sun") + prefs.minPadPixels;
		ticks = Util.MS_PER_DAY - Util.MS_PER_HOUR;
		
		dayOfWeekStrip = new TextStrip();
		dayOfWeekStrip.init(tp, this, (double)ticks/ml);
		
		ml = Util.getTextLength(tp, "20:00") + prefs.minPadPixels;
		ticks = 60*1000;

		minuteStrip = new TextStrip();
		minuteStrip.init(tp, this, (double)ticks/ml);
		
	}
	
	@Override
	public void setData(Strip strip, Canvas canvas, long startTicks, long endTicks) {
		if(strip == yearStrip)
		{
			calendar.setTimeInMillis(startTicks);
			calendar.set(Calendar.MONTH,0);
			calendar.set(Calendar.DAY_OF_MONTH,1);
			calendar.set(Calendar.HOUR_OF_DAY,0);
			calendar.set(Calendar.MINUTE,0);
			calendar.set(Calendar.SECOND,0);
			calendar.set(Calendar.MILLISECOND,0);
			
			int labelStep = getAppropriateStep(yearStrip.getMinLabelStep(ticksPerPixel),
					YEAR_FACTORS);
			
			drawLabels(canvas, startTicks, endTicks, Calendar.YEAR, yearStrip, labelStep, calendar, new LabelDrawer()
			{
				public String createString(int value)
				{
					return String.valueOf(value);
				}
			});
		}
		else if(strip == monthStrip)
		{
			calendar.setTimeInMillis(startTicks);
			calendar.set(Calendar.DAY_OF_MONTH,1);
			calendar.set(Calendar.HOUR_OF_DAY,0);
			calendar.set(Calendar.MINUTE,0);
			calendar.set(Calendar.SECOND,0);
			calendar.set(Calendar.MILLISECOND,0);
			
			int labelStep = getAppropriateStep(monthStrip.getMinLabelStep(ticksPerPixel),
					MONTH_FACTORS);
			
			drawLabels(canvas, startTicks, endTicks, Calendar.MONTH, monthStrip, labelStep, calendar, new LabelDrawer()
			{
				public String createString(int value)
				{
					return MONTHS[value - Calendar.JANUARY];
				}
			});
		}
		else if(strip == dayOfMonthStrip)
		{
			calendar.setTimeInMillis(startTicks);
			calendar.set(Calendar.HOUR_OF_DAY,0);
			calendar.set(Calendar.MINUTE,0);
			calendar.set(Calendar.SECOND,0);
			calendar.set(Calendar.MILLISECOND,0);
			
			int labelStep = getAppropriateStep(dayOfMonthStrip.getMinLabelStep(ticksPerPixel),
					DAY_OF_MONTH_FACTORS);
			
			drawLabels(canvas, startTicks, endTicks, Calendar.DAY_OF_MONTH, dayOfMonthStrip, labelStep, 
					calendar, new LabelDrawer()
					{
						public String createString(int value)
						{
							return String.valueOf(value);
						}
					});
		}
		else if(strip == dayOfWeekStrip)
		{
			calendar.setTimeInMillis(startTicks);
			calendar.set(Calendar.HOUR_OF_DAY,0);
			calendar.set(Calendar.MINUTE,0);
			calendar.set(Calendar.SECOND,0);
			calendar.set(Calendar.MILLISECOND,0);
			
			int labelStep = getAppropriateStep(dayOfWeekStrip.getMinLabelStep(ticksPerPixel),
					DAY_OF_WEEK_FACTORS);
			
			drawLabels(canvas, startTicks, endTicks, Calendar.DAY_OF_WEEK, dayOfWeekStrip, labelStep, 
					calendar, new LabelDrawer()
					{
						public String createString(int value)
						{
							return DAY_OF_WEEK[value - Calendar.SUNDAY];
						}
					});
		}
		else if(strip == minuteStrip)
		{
			calendar.setTimeInMillis(startTicks);
			calendar.set(Calendar.MINUTE,0);
			calendar.set(Calendar.SECOND,0);
			calendar.set(Calendar.MILLISECOND,0);
			
			int labelStep = getAppropriateStep(minuteStrip.getMinLabelStep(ticksPerPixel),
					MINUTE_FACTORS);
			
			drawLabels(canvas, startTicks, endTicks, Calendar.MINUTE, minuteStrip, labelStep, 
					calendar, new LabelDrawer()
					{
						public String createString(int value)
						{
							return String.format("%02d:%02d", value / 60, value % 60);
						}
					});
		}
	} 

	private void drawLabels(Canvas canvas, long startTicks, long endTicks, int calendarTimeType, 
			TextStrip strip, int labelStep, Calendar calendar, LabelDrawer labelDrawer) {
		int timeTypeValue = calendar.get(calendarTimeType);
		
		timeTypeValue += timeTypeValue % labelStep;
		
		while(true) //PERF: we always draw the first label, even if it's off the screen
		{
			if(calendar.getTimeInMillis() > endTicks) //TODO 3: we need to consider pad here, if we are halfway off the screen, we still need to draw
				break;
			
			int value = calendar.get(calendarTimeType);
			
			//TODO 4: big hack
			if(calendarTimeType == Calendar.MINUTE)
				value += calendar.get(Calendar.HOUR_OF_DAY) * 60;
		
			strip.dgDrawText(canvas, calendar.getTimeInMillis(), 
					labelDrawer.createString(value));

			calendar.add(calendarTimeType, labelStep);
			
		}
	}

	private int getAppropriateStep(int minLabelStep, int[] factors) {
		for(int factor : factors)
		{
			if(factor >= minLabelStep)
				return factor;
		}
		
		return minLabelStep;
	}

	private StripGroup currentStripGroup;			
	
			
			
	//TODO 3: Consider a label to the left of each strip that designates the category
	// ex:
	// year        2010
	// month       jan
	// day         10
	// hour        12
	// min         05
	// sec         01
//	private StripGroup createSecondStripGroup() {
//		Strip seconds =
		
//		StripGroup sg = new StripGroup();
//	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		super.setRangeSizePx((int) ((right - left) * prefs.widthOfTimeWindowPerc));
		calculateStrips();
	}

	/**
	 * @param periodLength period to set the time frame to
	 */
	public void setPeriodLength(double periodLength) {
		//keep the beginning point the same
		setTicks(super.ticks + super.ticksFrac - super.rangeSizeTicks/2 + periodLength/2);
		calculateStrips();
		super.setRangeSizeTicks(periodLength);
	}
		

	private void calculateStrips() {
		StripGroup bestStripGroup = null;
		
//		if(ticksPerPixel != 0)
//		{
//			for(StripGroup sg : stripGroups)
//			{
//				if(sg.maxTicksPerPixel > super.ticksPerPixel && 
//						(bestStripGroup == null ||
//								bestStripGroup.maxTicksPerPixel > sg.maxTicksPerPixel)
//								)
//					bestStripGroup = sg;
//			}
//		}
//		
//		if(this.currentStripGroup == bestStripGroup)
//			return;
//		
//		currentStripGroup = bestStripGroup;

		//TODO 3: Fix! We need to choose a strip group
		currentStripGroup = stripGroups[0];
		
		if(currentStripGroup == null)
			super.setStrips(new Strip[0]);
		else
			super.setStrips(currentStripGroup.strips);
	}
	
	public static class StripGroup
	{
		double maxTicksPerPixel = Double.MAX_VALUE;
		
		public Strip [] strips;

		public StripGroup(Strip ... strips) {
			this.strips = strips;
			
			for(Strip s : strips)
			{
				if(maxTicksPerPixel > s.maxTicksPerPixel)
					maxTicksPerPixel = s.maxTicksPerPixel;
			}
		}
	}

	public void setTicks(long ticks) {
		super.ticks = ticks;
		invalidate();
	}
	
	public interface LabelDrawer
	{
		public String createString(int value);
	}

	public static class Preferences implements AndroidPreferences
	{



		public float textSize = 16;

		public int textColor = 0xFFFFFFFF;
		
		/**
		 * Minimum pad between text labels of dialers in pixels
		 */
		public int minPadPixels= 20;
		
		/**
		 * The width of the window of time on the screen as a percentage of
		 * the total size of the dial
		 */
		public float widthOfTimeWindowPerc = .65f;
	}

	public void setColorRange(long startMs, long endMs) {
		super.setColorRangePx(this.getPixelLoc(startMs), this.getPixelLoc(endMs));
	}

}
