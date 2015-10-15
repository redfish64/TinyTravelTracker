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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.rareventure.gps2.R;

public class SeekBarWithText extends RelativeLayout {

	private String printfFormat;
	private float minValue;
	private float maxValue;
	private int divisions;
	private SeekBar seekBar;
	private TextView textView;
	private double logScale;
	private CustomUpdateTextView customUpdateTextView;

	public static interface CustomUpdateTextView
	{

		String updateText(float value);
	}

	public SeekBarWithText(Context context, AttributeSet attrs, int defStyle) {
		this(context,attrs,defStyle,null);
	}

	public SeekBarWithText(Context context, AttributeSet attrs, int defStyle, CustomUpdateTextView customUpdateTextView) {
		super(context, attrs, defStyle);
		
		View.inflate(context, R.layout.seek_bar_with_text, this);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.com_rareventure_android_widget_SeekBarWithText);

        minValue = a.getFloat(R.styleable.com_rareventure_android_widget_SeekBarWithText_minValue, 0);
        maxValue = a.getFloat(R.styleable.com_rareventure_android_widget_SeekBarWithText_maxValue, 100);
        divisions = a.getInt(R.styleable.com_rareventure_android_widget_SeekBarWithText_steps, Math.round(maxValue - minValue * 10));
        logScale = a.getFloat(R.styleable.com_rareventure_android_widget_SeekBarWithText_steps, 0);
        printfFormat = a.getString(R.styleable.com_rareventure_android_widget_SeekBarWithText_printfFormat);
        if(printfFormat == null)
        	printfFormat = "%.1f";

		this.customUpdateTextView = customUpdateTextView;
        
        seekBar = ((SeekBar)findViewById(R.id.seekBar));
        textView = ((TextView)findViewById(R.id.textView));
        
        seekBar.setMax(divisions-1);
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				updateTextView();
			}
		});

        updateTextView();
        
	}
	
	private void updateTextView()
	{
		float value = calcValue(seekBar.getProgress());

		if(customUpdateTextView != null)
			textView.setText(customUpdateTextView.updateText(value));
		else
			textView.setText(String.format(printfFormat, value));
	}
	
	private float calcValue(int progress)
	{
		if(logScale == 0)
			return minValue + (maxValue - minValue) * progress / (divisions-1);
		else
			return (float) (minValue + (maxValue - minValue) * (Math.exp((double)progress / logScale)-1) /
					(Math.exp((double)(divisions-1)/logScale)-1));
	}
	
	public float getValue()
	{
		return calcValue(seekBar.getProgress());
	}
	
	/**
	 * Will round to the nearest step
	 * @param value
	 */
	public void setValue(float value)
	{
		if(logScale == 0)
			seekBar.setProgress(Math.round((value - minValue) * (divisions-1) / (maxValue - minValue)));
		else
		{
//			(float) (minValue + (maxValue - minValue) * (Math.exp((double)progress / logScale)-1) / (Math.exp((double)divisions/logScale)-1));
//
//			m = minValue
//			n = maxValue
//			p = progress
//			l = logScale
//			d = divisions
//			x = scale value
//
//			m + (n - m) * ((e^(p/l))-1) / ((e^(d/l))-1) = x
//			x - m = (n - m) * ((e^(p/l))-1) / ((e^(d/l))-1)
//			(x - m) * ((e^(d/l))-1) = (n - m) * ((e^(p/l))-1)
//			(x - m) * ((e^(d/l))-1) / (n - m) = (e^(p/l))-1
//			(x - m) * ((e^(d/l))-1) / (n - m) + 1 = e^(p/l)
//			lg((x - m) * ((e^(d/l))-1) / (n - m) + 1) = p/l
//			lg((x - m) * ((e^(d/l))-1) / (n - m) + 1) * l = p
			seekBar.setProgress((int) Math.round((Math.log((value - minValue) * (Math.exp((divisions-1)/logScale)-1)/ (maxValue - minValue) + 1)) * logScale));
		}
	}

	public SeekBarWithText(Context context, AttributeSet attrs) {
		this(context, attrs,0,null);
	}

	public SeekBarWithText(Context context) {
		this(context, null);
	}

	public void setAttrs(float minValue, float maxValue, int divisions,		
			float logScale, CharSequence printfFormat, CustomUpdateTextView customUpdateTextView) {
		if(printfFormat != null)
			this.printfFormat = printfFormat.toString();
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.divisions = divisions;
		this.logScale = logScale;
		this.customUpdateTextView = customUpdateTextView;

        seekBar.setMax(divisions-1);
        updateTextView();
	}

}
