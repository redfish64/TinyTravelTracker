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
package com.igisw.openlocationtracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

//TODO 3: test negative ticks per pixels
public class RangeDial extends Dial {

	private int rangeSizePx;
	protected double rangeSizeTicks;
	
	private Paint markerPaint;
	private Paint gradientPaint = new Paint();
	private int[] gradientColors;
	
	/**
	 * The starting position of the left side of the range
	 */
	private long rangeStartStartTicks;
	
	/**
	 * The ending position of the left side of the range
	 */
	private long rangeEndStartTicks;
	private float colorRangeStart;
	private float colorRangeEnd;

	public RangeDial(Context context) {
		super(context);
		
		markerPaint = new Paint();
		markerPaint.setARGB(255, 255, 255, 255);
	}
	public void init(int[] gradientColors) {
		this.gradientColors = gradientColors;
	}


	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		setRangeSizePx((right - left) / 2);
	}
	
	public void setRangeSizePx(int rangeSizePx)
	{
		this.rangeSizePx = rangeSizePx;
		LinearGradient shader = new LinearGradient(
				(getWidth() - rangeSizePx)/2, 0,(getWidth() + rangeSizePx)/2, 
				0, gradientColors,
                null, Shader.TileMode.REPEAT);
		
		gradientPaint.setShader(shader);
		
		updateData();
	}

	public void setRangeSizeTicks(double periodLength)
	{
		this.rangeSizeTicks = periodLength;
		super.setStartTicks((long) (rangeStartStartTicks + rangeSizeTicks/2));
		super.setEndTicks((long) (rangeEndStartTicks + rangeSizeTicks/2));
		updateData();
	}

	private void updateData() {
		//if not set up yet
		if(rangeSizePx == 0)
			return;
		setTicksPerPixel((double)rangeSizeTicks / rangeSizePx);
		
		invalidate();
	}
	
	public void setStartTicks(long rangeStartStartTicks) {
		this.rangeStartStartTicks = rangeStartStartTicks;
		super.setStartTicks((long) (rangeStartStartTicks + rangeSizeTicks/2));
	}
	
	public void setEndTicks(long rangeEndStartTicks) {
		this.rangeEndStartTicks = rangeEndStartTicks;
		super.setEndTicks((long) (rangeEndStartTicks + rangeSizeTicks/2));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		int rangeStart = (getWidth() - rangeSizePx)/2;
		int rangeEnd = (getWidth() + rangeSizePx)/2;

		//PERF, draw background into a bitmap
		if(colorRangeStart != colorRangeEnd)
			canvas.drawRect(colorRangeStart,0,colorRangeEnd,getHeight(), gradientPaint);
		
		canvas.drawLine(rangeStart, 0, rangeStart, getHeight(),
				markerPaint);
		canvas.drawLine(rangeEnd, 0, 
				rangeEnd, getHeight(),
				markerPaint);
		
		super.onDraw(canvas);
	}
	
	public void setColorRangePx(float colorRangeStart, float colorRangeEnd)
	{
		this.colorRangeStart = colorRangeStart;
		this.colorRangeEnd = colorRangeEnd;
		
		invalidate();
	}

}
