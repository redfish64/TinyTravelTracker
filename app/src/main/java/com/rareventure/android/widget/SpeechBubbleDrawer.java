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
package com.rareventure.android.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Style;

public class SpeechBubbleDrawer {
	private Paint rectPaint;
	private int roundedCornerPx;
	private float triXPerc;
	private int triWidth;
	private int triYSize;
	private int innerPad;

	public SpeechBubbleDrawer(int roundedCornerPx, float triXPerc, int triWidth, int triYSize, 
			int innerPad)
	{
		this.roundedCornerPx = roundedCornerPx;
		this.triXPerc = triXPerc;
		this.triWidth = triWidth;
		this.triYSize = triYSize;
		this.innerPad = innerPad;
				
		rectPaint = new Paint();
		rectPaint.setColor(0xffa0a0a0);
		rectPaint.setStyle(Style.FILL);
		
	}
	
	public void calcOuterRect(Point result, int innerWidth, int innerHeight) {
		result.x = innerWidth + innerPad * 2;
		result.y = innerHeight + innerPad * 2;
	}

	public void draw(Canvas c, int innerX, int innerY, int innerWidth, int innerHeight)
	{
		int outerWidth = innerWidth + innerPad * 2;
		int outerHeight = innerHeight + innerPad * 2;
		
		int outerX = innerX - innerPad;
		int outerY = innerY - innerPad;
		
		RectF outsideRect = new RectF(outerX, outerY, 
				outerX + outerWidth, outerY + outerHeight);
		c.drawRoundRect(outsideRect, roundedCornerPx, roundedCornerPx, rectPaint);
		//                                       ____
		//now draw point, I mean the \| part of /    \
		//                                      \_  _/
		//                                        \|
		
		float triX1 = outerX + outerWidth * triXPerc - triWidth;
		float triX2 = triX1 + triWidth;
		float triY1 = outerY + outerHeight;
		float triY2 = triY1 + triYSize;
		
		Paint hackPaint = new Paint();
		hackPaint.setColor(0xFF00FF00);
		
		Path path = new Path();
		path.moveTo(triX1, triY1);
		path.lineTo(triX2, triY1);
		path.lineTo(triX2, triY2);
		c.drawPath(path, rectPaint);
	}

	/**
	 * Calculates the inner rect for text given the outer rect
	 */
	public Rect calcInnerRect(int l, int t, int r, int b) {
		Rect rect = new Rect(l,t,r,b);
		
		rect.left += innerPad;  
		rect.top += innerPad; 
		rect.right -= innerPad; 
		rect.bottom -= triYSize + innerPad;
		
		return rect;
	}

    /**
     * @return the location of the \|
     */
	public Point getPointLocation(int outerX, int outerY, int outerWidth, int outerHeight) {
		//TODO 3: duped with draw()
		float triX1 = outerX + outerWidth * triXPerc;
		float triY2 = outerY + outerHeight;
		return new Point((int)triX1, (int)triY2);
	}

	public Point getPointLocationGivenInner(Rect bounds) {
		return getPointLocation(bounds.left - innerPad, bounds.top - innerPad, bounds.width() + innerPad*2, 
				bounds.height() + innerPad*2 + triYSize);
		
	}

	public void updateInnerRectForClickableArea(Rect result) {
		result.bottom += innerPad;
		result.top -= innerPad;
		result.left -= innerPad;
		result.right += innerPad;
	}

}
