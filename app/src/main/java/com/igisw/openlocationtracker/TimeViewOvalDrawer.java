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
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

public class TimeViewOvalDrawer {
	public final float DISPLAYED_POINTS_BAR_RADIUS_PX;
	public final float DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
	private static final float MAX_SHADER_VALUE = 100;

	private Paint gradientPaint;
	
	private Paint leftDPBarColor, rightDPBarColor;
	Paint selectedRegionPaint;

	public TimeViewOvalDrawer(Context context)
	{
		DISPLAYED_POINTS_BAR_RADIUS_PX = Util.convertDpToPixel(8, context);
		DISPLAYED_POINTS_BAR_INNER_RADIUS_PX = Util.convertDpToPixel(6, context);
		
		gradientPaint = new Paint();
		
		selectedRegionPaint = new Paint();
		selectedRegionPaint.setStrokeWidth(Util.convertDpToPixel(2, context));
		selectedRegionPaint.setColor(Color.LTGRAY);
		
		updateColorRange();
	}
	
	private RectF tempRect = new RectF();
	private Matrix displayBarMatrix = new Matrix();
	
	public void drawOval(Canvas canvas,
			Rect selectedAreaDim,
			int onScreenPointStartX,
			int onScreenPointEndX) {
		int selectedAreaCenterY = (selectedAreaDim.top + selectedAreaDim.bottom)/2;

		//draw left half circle
		if(onScreenPointStartX > selectedAreaDim.left &&
				onScreenPointStartX - DISPLAYED_POINTS_BAR_RADIUS_PX < selectedAreaDim.right)
		{
			tempRect.left = onScreenPointStartX - DISPLAYED_POINTS_BAR_RADIUS_PX;
			tempRect.right = onScreenPointStartX + DISPLAYED_POINTS_BAR_RADIUS_PX;
			tempRect.top = selectedAreaCenterY - DISPLAYED_POINTS_BAR_RADIUS_PX;
			tempRect.bottom = selectedAreaCenterY + DISPLAYED_POINTS_BAR_RADIUS_PX;
			
			canvas.drawArc(tempRect, 90, 180, true, selectedRegionPaint);

			tempRect.left = onScreenPointStartX - DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			tempRect.right = onScreenPointStartX + DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			tempRect.top = selectedAreaCenterY - DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			tempRect.bottom = selectedAreaCenterY + DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			
			canvas.drawArc(tempRect, 90, 180, true, leftDPBarColor);
			
		}
		
		//draw right half circle
		if(onScreenPointEndX < selectedAreaDim.right &&
				onScreenPointEndX + DISPLAYED_POINTS_BAR_RADIUS_PX > selectedAreaDim.left)
		{
			tempRect.left = onScreenPointEndX - DISPLAYED_POINTS_BAR_RADIUS_PX;
			tempRect.right = onScreenPointEndX + DISPLAYED_POINTS_BAR_RADIUS_PX;
			tempRect.top = selectedAreaCenterY - DISPLAYED_POINTS_BAR_RADIUS_PX;
			tempRect.bottom = selectedAreaCenterY + DISPLAYED_POINTS_BAR_RADIUS_PX;
			
			canvas.drawArc(tempRect, 270, 180, true, selectedRegionPaint);
			
			tempRect.left = onScreenPointEndX - DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			tempRect.right = onScreenPointEndX + DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			tempRect.top = selectedAreaCenterY - DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			tempRect.bottom = selectedAreaCenterY + DISPLAYED_POINTS_BAR_INNER_RADIUS_PX;
			
			canvas.drawArc(tempRect, 270, 180, true, rightDPBarColor);
		}

		//draw center part of thingy
		if(onScreenPointEndX > selectedAreaDim.left 
				&& onScreenPointStartX < selectedAreaDim.right )
		{
			canvas.drawRect(onScreenPointStartX, 
					selectedAreaCenterY - DISPLAYED_POINTS_BAR_RADIUS_PX, 
					onScreenPointEndX, 
					selectedAreaCenterY + DISPLAYED_POINTS_BAR_RADIUS_PX,
					selectedRegionPaint);

			if(gradientPaint.getShader() != null)
			{
				displayBarMatrix.reset();
				displayBarMatrix.setTranslate(onScreenPointStartX, 0);
				displayBarMatrix.preScale(
						(onScreenPointEndX - onScreenPointStartX+1)/ MAX_SHADER_VALUE, 1);
				
				gradientPaint.getShader().setLocalMatrix(displayBarMatrix);
			}

//			//HACK
//			canvas.drawRect(0, 
//			selectedAreaCenterY - DISPLAYED_POINTS_BAR_INNER_RADIUS_PX, 
//			getWidth(), 
//			selectedAreaCenterY + DISPLAYED_POINTS_BAR_INNER_RADIUS_PX,
//			gradientPaint);
			canvas.drawRect(onScreenPointStartX, 
					selectedAreaCenterY - DISPLAYED_POINTS_BAR_INNER_RADIUS_PX, 
					onScreenPointEndX, 
					selectedAreaCenterY + DISPLAYED_POINTS_BAR_INNER_RADIUS_PX,
					gradientPaint);
		}
		
	}

	public void updateColorRange() {
		if(OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange.length > 1)
		{
			LinearGradient shader = new LinearGradient(
					0, 0, MAX_SHADER_VALUE,0, OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange,
	                null, Shader.TileMode.REPEAT);
			
			gradientPaint.setShader(shader);
		}
		else
		{
			gradientPaint.setShader(null);
			gradientPaint.setColor(OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange[0]);
		}
		
		leftDPBarColor = new Paint();
		leftDPBarColor.setColor(OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange[0]);
		rightDPBarColor = new Paint();
		rightDPBarColor.setColor(OsmMapGpsTrailerReviewerMapActivity.prefs.
				colorRange[OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange.length-1]);
		
	}


}
