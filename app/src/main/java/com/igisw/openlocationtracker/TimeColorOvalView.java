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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * A view simply showing the oval in the time view
 */
public class TimeColorOvalView extends View 
{

	TimeViewOvalDrawer ovalDrawer;
	private Rect selectedAreaDim;

	public TimeColorOvalView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public TimeColorOvalView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public TimeColorOvalView(Context context) {
		super(context);
		init();
	}
	

	private void init() {
		ovalDrawer = new TimeViewOvalDrawer(getContext());
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);
		
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		setMeasuredDimension(
				Util.chooseAtLeastForOnMeasure((int) (ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX*2),
					widthSpec),
					Util.chooseAtLeastForOnMeasure((int) (ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX*2),
							heightSpec));
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		
		if(selectedAreaDim == null)
			selectedAreaDim = new Rect(0, 0, getWidth(), getHeight());

		ovalDrawer.drawOval(canvas, selectedAreaDim, (int)ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX, 
			getWidth() - (int)ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX);
	}

	
}
