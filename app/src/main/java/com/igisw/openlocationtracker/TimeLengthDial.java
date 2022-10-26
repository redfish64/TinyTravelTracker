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
import android.text.TextPaint;

import com.igisw.openlocationtracker.AndroidPreferenceSet.AndroidPreferences;

public class TimeLengthDial extends PointDial implements TextStrip.DataGenerator
{
	private TextPaint textPaint;

	private Preferences prefs = new Preferences();

	private TextStrip strip;

	private long minRangeMs;

	private long maxRangeMs;

	private static final Object [] PERIODS = { 
		"1000 years",Util.MS_PER_YEAR * 1000,
		"100 years",Util.MS_PER_YEAR * 100, 
		"10 years", Util.MS_PER_YEAR * 10,
		"5 years",Util.MS_PER_YEAR * 5, 
		"2 years",Util.MS_PER_YEAR * 2, 
		"1.5 years",Util.MS_PER_YEAR * 3/2, 
		"1 year",Util.MS_PER_YEAR, 
		"9 months", Util.MS_PER_DAY*30 *9, 
		"6 months", Util.MS_PER_DAY*30 *6, 
		"3 months", Util.MS_PER_DAY*30 *3, 
		"2 months", Util.MS_PER_DAY*30 *2, 
		"1 month", Util.MS_PER_DAY*30, 
		"3 weeks", Util.MS_PER_DAY * 21,
		"2 weeks", Util.MS_PER_DAY * 14,
		"1 week", Util.MS_PER_DAY * 7,
		"3 days", Util.MS_PER_DAY * 3,
		"2 days", Util.MS_PER_DAY * 2,
		"1 day", Util.MS_PER_DAY,
		"18 hours", Util.MS_PER_HOUR * 18,
		"12 hours", Util.MS_PER_HOUR * 12,
		"9 hours", Util.MS_PER_HOUR * 9,
		"6 hours", Util.MS_PER_HOUR * 6,
		"3 hours", Util.MS_PER_HOUR * 3,
		"2 hours", Util.MS_PER_HOUR * 2,
		"1 hour", Util.MS_PER_HOUR,
		"30 minute", 60000l * 30,
		"15 minutes", 60000l * 15,
		"10 minutes", 60000l * 10,
		"5 minutes", 60000l * 5,
		"1 minute", 60000l,
		"10 seconds", 10000l,
		"1 second", 1000l};

	private static final String BIGGEST_TEXT = /* ttt_installer:obfuscate_str */"10 seconds";
	
	/**
	 * 
	 * @param context
	 * @param startPos the maximum period of the range
	 */
	public TimeLengthDial(Context context) {
		super(context);
	}
	
	public void init(long minRangeMs, long maxRangeMs) 
	{
		this.minRangeMs = minRangeMs;
		this.maxRangeMs = maxRangeMs;
		
		textPaint = new TextPaint();
		textPaint.setTextSize(Util.convertSpToPixel(prefs.textSize, getContext() ));
		textPaint.setColor(prefs.textColor );
		
		strip = new TextStrip();
		
		strip.init(textPaint, this, BIGGEST_TEXT, 1, 20);
		
		super.setTicksPerPixel(strip.maxTicksPerPixel);
		
		addStrip(strip);
		
		setStartTicks(convertFromMsToTicks(maxRangeMs));
		setEndTicks(convertFromMsToTicks(minRangeMs));

		setMinHeight((int) Util.convertDpToPixel(prefs.minHeightDp, getContext()));
	}
	
	private double convertFromMsToTicks(long timeMs) {
		long time0Ms = 0, time1Ms = 0;
		
		//we can't do zero
		if(timeMs < 1)
			timeMs = 1;
		
		//we also can't do past 1000 years either
		if(timeMs > (Long)PERIODS[1])
			timeMs = (Long)PERIODS[1];
		
		int i;
		
		for(i = 1; i < PERIODS.length; i+=2)
		{
			long periodTimeMs = (Long) PERIODS[i];
			
			if(timeMs > periodTimeMs)
			{
				time0Ms = periodTimeMs;
				if(i == 0)
					return time0Ms;
				else 
					time1Ms = (Long) PERIODS[i-2];
				
				break;
			}
		}
		
		//ms = exp((log(sl) - log(el)) * pos + log(el))
		//log(ms) = (log(sl) - log(el)) * pos + log(el)
		//log(ms) - log(el) = (log(sl) - log(el)) * pos
		//(log(ms) - log(el)) / (log(sl) - log(el)) = pos
		//
		//ms = timeMs
		//sl = time0Ms
		//el = time1Ms
		
		return (Math.log(timeMs) - Math.log(time1Ms)) / 
			(Math.log(time0Ms) - Math.log(time1Ms)) + i/2 -1;
	}

	@Override
	public String getLabel(TextStrip ts, long tick) {
		if(tick >= PERIODS.length /2 || tick < 0)
			return "";
		
		return (String)PERIODS[(int) (tick*2)];
	}
	
	public void setPeriodLength(long lengthMs)
	{
		if(lengthMs > maxRangeMs)
			lengthMs = maxRangeMs;
		if(lengthMs < minRangeMs)
			lengthMs = minRangeMs;
		
		setTicks(convertFromMsToTicks(lengthMs));
	}
	
	public double getPeriodLength() {
		long startLengthMs = (Long)PERIODS[(int) (super.ticks*2+1)];
		if(ticks == PERIODS.length/2)
			return startLengthMs;
		
		long endLengthMs = (Long)PERIODS[(int) ((super.ticks+1)*2+1)];
		
		double ms = 
			Math.exp((Math.log(startLengthMs) - Math.log(endLengthMs)) * (1-super.ticksFrac)
					+Math.log(endLengthMs));
		
		return ms;
	}

	public static class Preferences implements AndroidPreferences
	{



		public float textSize = 16;
		public float minHeightDp = 40;
		public int textColor = 0xFFB0B0B0;
		/**
		 * Minimum pad between text labels of dialers in pixels
		 */
		public int minPadPixels= 20;
	}

}
