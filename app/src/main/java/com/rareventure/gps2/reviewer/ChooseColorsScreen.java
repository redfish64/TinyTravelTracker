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
package com.rareventure.gps2.reviewer;

import java.nio.channels.IllegalSelectorException;

import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTGActivity;
import com.rareventure.gps2.R;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class ChooseColorsScreen extends GTGActivity implements OnCheckedChangeListener {

	private ViewGroup checkboxes1;
	private ViewGroup checkboxes2;
	private TimeColorOvalView colorView;
	private int oldColorRangeBitmap;

	public ChooseColorsScreen() {
	}

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.choose_colors_screen);
		
		if(OsmMapGpsTrailerReviewerMapActivity.prefs.allColorRanges.length != 12)
			throw new IllegalStateException("foo");
		
		checkboxes1 = (ViewGroup)findViewById(R.id.checkBoxes1);
		checkboxes2 = (ViewGroup) findViewById(R.id.checkBoxes2);
		
		
		colorView = (TimeColorOvalView) findViewById(R.id.colorView); 
		
	}
	
	public void doOnResume()
	{
//		if(1==1)throw new IllegalSelectorException();
		oldColorRangeBitmap = OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap;
		
		setColorRange(checkboxes1, OsmMapGpsTrailerReviewerMapActivity.prefs.allColorRanges
				, OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap,0,6);
		setColorRange(checkboxes2, OsmMapGpsTrailerReviewerMapActivity.prefs.allColorRanges
				, OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap,6
				, 12);
		
	}

	private void setColorRange(ViewGroup vg, int[] colorRanges,
			int bitmap, int start, int end) {
		int viewIndex = 0;
		for(int i = start; i < end; i++)
		{
			View colorCheckboxLayout = vg.getChildAt(viewIndex++);
			View color = colorCheckboxLayout.findViewById(R.id.color);
			
			color.setBackgroundColor(colorRanges[i]);
			
			final CheckBox checkBox = (CheckBox) colorCheckboxLayout.findViewById(R.id.checkBox);
			
			checkBox.setChecked((bitmap & (1 << i)) != 0);
			
			colorCheckboxLayout.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					checkBox.setChecked(!checkBox.isChecked());
					updateColorView();
					
				}
			});
			
			checkBox.setOnCheckedChangeListener(this);
		}
	}

	public void onOk(View v)
	{
		oldColorRangeBitmap = OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap;
		finish();
	}
	
	public void doOnPause(boolean doOnResumeCalled)
	{
		super.doOnPause(doOnResumeCalled);
		
		if(doOnResumeCalled)
			OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap = oldColorRangeBitmap;
	}

	private void updateColorView() {
		
		int colorRangeBitmap = updateColorRangeBitmap(checkboxes1, 0, 6)|
		updateColorRangeBitmap(checkboxes2, 6, 12);
		if(colorRangeBitmap == 0)
			colorRangeBitmap = 1;
		
		OsmMapGpsTrailerReviewerMapActivity.prefs.updateColorRangeBitmap(colorRangeBitmap);
		
		colorView.ovalDrawer.updateColorRange();
		colorView.invalidate();
	}

	private int updateColorRangeBitmap(ViewGroup vg, int start, int end) {
		int result = 0;
		int viewIndex = 0;
		for(int i = start; i < end; i++)
		{
			View colorCheckboxLayout = vg.getChildAt(viewIndex++);
			CheckBox checkBox = (CheckBox) colorCheckboxLayout.findViewById(R.id.checkBox);

			if(checkBox.isChecked())
				result |= 1<<i;
		}
		
		return result;
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		updateColorView();
		
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI;
	}

}
