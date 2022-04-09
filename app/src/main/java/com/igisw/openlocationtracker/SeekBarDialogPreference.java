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

import com.igisw.openlocationtracker.SeekBarWithText;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

public class SeekBarDialogPreference extends DialogPreference {

	private TextView textView;
	private SeekBarWithText seekBarWithText;
	private float minValue;
	private float maxValue;
	private int divisions;
	private String printfFormat;
	private CharSequence title;
	private CharSequence desc;

	private float value; //this is the authoritative source of record
	private float logScale;
	//when the dialog is not being shown
	private OnPreferenceChangeListener onPreferenceChangeListener;
	private SeekBarWithText.CustomUpdateTextView customUpdateTextView;

	public SeekBarDialogPreference(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SeekBarDialogPreference);

        title = a.getString(R.styleable.SeekBarDialogPreference_title1);
        desc = a.getString(R.styleable.SeekBarDialogPreference_desc);
        minValue = a.getFloat(R.styleable.SeekBarDialogPreference_minValue, 0);
        maxValue = a.getFloat(R.styleable.SeekBarDialogPreference_maxValue, 100);
        divisions = a.getInt(R.styleable.SeekBarDialogPreference_steps, Math.round(maxValue - minValue * 10));
        printfFormat = a.getString(R.styleable.SeekBarDialogPreference_printfFormat).toString();
        
		setSummary(desc);
	}

	public SeekBarDialogPreference(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

    public SeekBarDialogPreference(Context context,
			CharSequence title, CharSequence desc, int minValue, int maxValue,
			int divisions, float logScale, String printfFormat, SeekBarWithText.CustomUpdateTextView customUpdateTextView) {
    	super(context, null);
		this.title = title;
		this.desc = desc;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.divisions = divisions;
		this.logScale = logScale;
		this.printfFormat = printfFormat;
		this.customUpdateTextView = customUpdateTextView;

    	setDialogTitle(title);
		setSummary(desc);
	}

	@Override
    protected View onCreateDialogView() {
    	View dialogView = View.inflate(this.getContext(), R.layout.dialog_seek_bar, null);
    	
    	this.textView = (TextView)dialogView.findViewById(R.id.textView);
    	this.seekBarWithText = (SeekBarWithText)dialogView.findViewById(R.id.seekBarWithText);

		
		seekBarWithText.setAttrs(minValue, maxValue, divisions, logScale, printfFormat, customUpdateTextView);
    	textView.setText(desc);
    	seekBarWithText.setValue(value);
    	
    	return dialogView;
    }

	public void setValue(float f) {
		value = f;
		if(seekBarWithText != null)
			seekBarWithText.setValue(f);
		updateTitle();
	}

	private void updateTitle() {
		if(customUpdateTextView != null)
			setTitle(title+" - "+customUpdateTextView.updateText(value));
		else
			setTitle(title+" - "+String.format(printfFormat,value));
	}

	public float getValue() {
		if(seekBarWithText != null)
			return value=seekBarWithText.getValue();
		return value;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if(positiveResult)
		{
			value = seekBarWithText.getValue();
			updateTitle();
			
			if(onPreferenceChangeListener != null)
				onPreferenceChangeListener.onPreferenceChange(this, null);
		}
		else
			seekBarWithText.setValue(value);

		super.onDialogClosed(positiveResult);
	}

	@Override
	public void setOnPreferenceChangeListener(
			OnPreferenceChangeListener onPreferenceChangeListener) {
		this.onPreferenceChangeListener = onPreferenceChangeListener;
	}
	
	
}
