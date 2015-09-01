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
package com.rareventure.gps2.reviewer.map;

import junit.framework.Assert;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint.FontMetrics;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TAssert;

/**
 * Display the current scale
 */
public class MaplessScaleWidget extends View
{
	public static final double METERS_PER_INCH = 1/39.3700787;
	
	public static final double METERS_PER_LIGHT_YEAR = 9.4605284E15;
	
	public static final Unit INCH_UNIT = new Unit("1 inch",METERS_PER_INCH, "%.2f in");
	public static final Unit FOOT_UNIT = new Unit("1 foot",METERS_PER_INCH * 12, "%.1f ft");
	public static final Unit MILE_UNIT = new Unit("1 mile",METERS_PER_INCH * 12 * 5280, "%.1f miles");
	
	public static final Unit MILLIMETER_UNIT = new Unit("1 millimeter",.001, "%.2f mm");
	public static final Unit CENTIMETER_UNIT = new Unit("1 centimeter",.01, "%.1f cm");
	public static final Unit METER_UNIT = new Unit("1 meter",1, "%.1f m");
	public static final Unit KM_UNIT = new Unit("1 km",1000, "%.2f km");
	public static final Unit LIGHT_YEAR_UNIT = new Unit("1 light year",METERS_PER_LIGHT_YEAR, "%.3f ly");
	
	public static final Unit [] ABS_DIST_ENGLISH_UNITS =
	{
		FOOT_UNIT, MILE_UNIT, LIGHT_YEAR_UNIT
	};
	
	public static final Unit [] ABS_DIST_METRIC_UNITS =
	{
		METER_UNIT, KM_UNIT, LIGHT_YEAR_UNIT
	};
	
	public static final Unit [] ENGLISH_UNITS =
		new Unit [] {
		INCH_UNIT,
		FOOT_UNIT,
		new Unit("2 feet",METERS_PER_INCH * 12 * 2),
		new Unit("5 feet",METERS_PER_INCH * 12 * 5),
		new Unit("10 feet",METERS_PER_INCH * 12 * 10),
		new Unit("25 feet",METERS_PER_INCH * 12 * 25),
		new Unit("50 feet",METERS_PER_INCH * 12 * 50),
		new Unit("100 feet",METERS_PER_INCH * 12 * 100),
		new Unit("200 feet",METERS_PER_INCH * 12 * 200),
		new Unit("500 feet",METERS_PER_INCH * 12 * 500),
		new Unit("1000 feet",METERS_PER_INCH * 12 * 1000),
		new Unit("2000 feet",METERS_PER_INCH * 12 * 2000),
		MILE_UNIT,
		new Unit("2 miles",METERS_PER_INCH * 12 * 5280 * 2),
		new Unit("5 miles",METERS_PER_INCH * 12 * 5280 * 5),
		new Unit("10 miles",METERS_PER_INCH * 12 * 5280 * 10),
		new Unit("25 miles",METERS_PER_INCH * 12 * 5280 * 25),
		new Unit("50 miles",METERS_PER_INCH * 12 * 5280 * 50),
		new Unit("100 miles",METERS_PER_INCH * 12 * 5280 * 100),
		new Unit("200 miles",METERS_PER_INCH * 12 * 5280 * 200),
		new Unit("500 miles",METERS_PER_INCH * 12 * 5280 * 500),
		new Unit("1000 miles",METERS_PER_INCH * 12 * 5280 * 1000),
		new Unit("2000 miles",METERS_PER_INCH * 12 * 5280 * 2000),
		new Unit("5000 miles",METERS_PER_INCH * 12 * 5280 * 5000),
		new Unit("10,000 miles",METERS_PER_INCH * 12 * 5280 * 10000),
		LIGHT_YEAR_UNIT
	};
	
	public static final Unit [] METRIC_UNITS = new Unit [] { 
		MILLIMETER_UNIT,
		new Unit("2 millimeters",.002),
		new Unit("5 millimeters",.005),
		CENTIMETER_UNIT,
		new Unit("2 centimeters",.02),
		new Unit("5 centimeters",.05),
		new Unit("10 centimeters",.1),
		new Unit("20 centimeters",.2),
		new Unit("50 centimeters",.5),
		METER_UNIT,
		new Unit("2 meters",2),
		new Unit("5 meters",5),
		new Unit("10 meters",10),
		new Unit("20 meters",20),
		new Unit("50 meters",50),
		new Unit("100 meters",100),
		new Unit("200 meters",200),
		new Unit("500 meters",500),
		KM_UNIT,
		new Unit("2 km",2000),
		new Unit("5 km",5000),
		new Unit("10 km",10000),
		new Unit("20 km",20000),
		new Unit("50 km",50000),
		new Unit("100 km",100000),
		new Unit("200 km",200000),
		new Unit("500 km",500000),
		new Unit("1000 km",1000000),
		new Unit("2000 km",2000000),
		new Unit("5000 km",5000000),
		new Unit("10,000 km",10000000),
		new Unit("20,000 km",20000000),
		new Unit("50,000 km",50000000),
		new Unit("100,000 km",100000000),
		new Unit("200,000 km",200000000),
		new Unit("500,000 km",500000000),
		new Unit("1,000,000 km",1000000000),
		LIGHT_YEAR_UNIT
	};
	
	private static class Unit
	{

		private int divisions;
		private double meters;
		private String name;
		private String absoluteDistLabel;

		public Unit(String name, double meters) {
			this.name = name;
			this.meters = meters;
			
			divisions = name.charAt(0) - '0';
			if(divisions == 1)
				divisions = 4;
			else if(divisions < 1 || divisions > 9)
				 TAssert.fail("can't figure out divisions for "+name);
			
		}
		
		public Unit(String name, double meters, String absoluteDistLabel) {
			this(name, meters);
			this.absoluteDistLabel = absoluteDistLabel;
		}

		public String toString()
		{
			return name;
		}
	}
	

	private int index;

	private Unit[] units;

	double pixelsPerUnit;

	private TextPaint paint;

	private FontMetrics fontMetrics;

	public float pixelsPerMeter;

	private int preferredHeight;

	private int preferredWidth;
	
	public static String calcLabelForLength(double meters, boolean metric)
	{
		Unit [] units = metric ? ABS_DIST_METRIC_UNITS : ABS_DIST_ENGLISH_UNITS;
		
		int i;
		for(i = units.length-1; i > 0; i--)
		{
			if(units[i].meters < meters)
				break;
		}
		
		return String.format(units[i].absoluteDistLabel, meters / units[i].meters);
			
	}


	public MaplessScaleWidget(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		units = METRIC_UNITS;
		
		paint = new TextPaint();
		paint.setColor(0xFF000000);
		paint.setTextSize(Util.convertSpToPixel(16, context));
		paint.setAntiAlias(true);
		paint.setStrokeWidth(Util.convertSpToPixel(1, context));
		fontMetrics = paint.getFontMetrics();
		
		setMinimumWidth(preferredWidth = Util.getMaximumWidth(paint, units));
		setMinimumHeight(preferredHeight = (int) Math.ceil((-fontMetrics.ascent + fontMetrics.descent) * 2));
		
		change(1);
	}
			
	private void setUnits(Unit[] units) {
		this.units = units;
		change(pixelsPerMeter);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		
		change(pixelsPerMeter);
	}

	public void change(float pixelsPerMeter)
	{
		this.pixelsPerMeter = pixelsPerMeter;
		index = units.length - 1;
		
		while(index > 0)
		{
			pixelsPerUnit = pixelsPerMeter * units[index].meters;
			if(pixelsPerUnit < getWidth())
				break;
			
			index --;
			
		}
		
		invalidate();
	}
	
    /**
     * @see android.view.View#measure(int, int)
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Util.measureWithPreferredSize(widthMeasureSpec, preferredWidth 
        		+ getPaddingLeft() + getPaddingRight()),
                Util.measureWithPreferredSize(heightMeasureSpec, preferredHeight 
                		+ getPaddingTop() + getPaddingBottom()));
    }
    
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		canvas.drawText(units[index].name, 0, -fontMetrics.ascent, paint);
		int y = (int) (-fontMetrics.ascent+ fontMetrics.descent);

		canvas.drawLine(1, y*3/2, (float) pixelsPerUnit+1, y*3/2, paint);
		canvas.drawLine(1, y, 1, y*2, paint);
		canvas.drawLine((float)pixelsPerUnit+1, y, (float)pixelsPerUnit+1, y*2, paint);
		
		for(int i = 1; i < units[index].divisions; i++)
		{
			canvas.drawLine((float)(pixelsPerUnit * i / units[index].divisions + 1), y*5/4, 
					(float)(pixelsPerUnit * i / units[index].divisions + 1), y*7/4, paint);
		}
	}

	public int getDivisions() {
		return units[index].divisions;
	}

	public void setUnitsToMetric(boolean useMetric) {
		setUnits(useMetric ? METRIC_UNITS : ENGLISH_UNITS);
	}

	
}
