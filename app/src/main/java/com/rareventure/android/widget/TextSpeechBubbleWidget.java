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
import android.graphics.Canvas;
import android.graphics.Paint.FontMetrics;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.rareventure.gps2.R;

public class TextSpeechBubbleWidget extends View
{

	private Rect innerRect;
	private SpeechBubbleDrawer speechBubbleDrawer;
	private TextPaint textPaint;
	private FontMetrics fontMetrics;

	public TextSpeechBubbleWidget(Context context, AttributeSet attrs) {
		super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.TextSpeechBubbleWidget);

        this.speechBubbleDrawer = new SpeechBubbleDrawer(
				a.getDimensionPixelOffset(R.styleable.TextSpeechBubbleWidget_roundedCorner, 5),
				a.getFloat(R.styleable.TextSpeechBubbleWidget_triXPerc, .75f),
				a.getDimensionPixelOffset(R.styleable.TextSpeechBubbleWidget_triWidth, 5),
				a.getDimensionPixelOffset(R.styleable.TextSpeechBubbleWidget_triHeight, 5),
				a.getDimensionPixelOffset(R.styleable.TextSpeechBubbleWidget_innerPad, 5)
        );

        textPaint = new TextPaint();
        textPaint.setColor(0xFF000000);
        fontMetrics = textPaint.getFontMetrics();
        
        int textSize = a.getDimensionPixelOffset(R.styleable.TextSpeechBubbleWidget_fontSize, 0);
        textPaint.setTextSize(textSize);

        a.recycle();
	} 

	private Rect calcInnerRect(int l, int t, int r, int b) {
		return speechBubbleDrawer.calcInnerRect(
				l + getPaddingLeft(),
				t + getPaddingTop(),
				r - getPaddingRight(),
				b - getPaddingBottom()
				);
	}

    @Override
    protected void dispatchDraw(Canvas canvas) {
    	if(super.getVisibility() == VISIBLE)
    	{
	    	speechBubbleDrawer.draw(canvas, innerRect.left, innerRect.top, innerRect.width(), innerRect.height());
	        super.dispatchDraw(canvas);
    	}
    }

    /**
     * @return the location of the \|
     */
	public Point getPoint() {
		return speechBubbleDrawer.getPointLocation(getLeft()+getPaddingLeft(),
				getTop()+getPaddingTop(),getWidth()-getPaddingLeft()-getPaddingRight(),
				getHeight() - getPaddingTop() - getPaddingBottom());
	}

	public void drawBubble(Canvas canvas, String text, int x, int y) {
		
		Rect bounds = new Rect();
		textPaint.getTextBounds(text, 0, text.length(), bounds);
		bounds.offsetTo(0, 0);
		
		Point point = speechBubbleDrawer.getPointLocationGivenInner(bounds);
		
		bounds.offsetTo(x - point.x, y - point.y);
		
		speechBubbleDrawer.draw(canvas, bounds.left, bounds.top, bounds.width(), bounds.height());
		canvas.drawText(text, bounds.left, bounds.top - fontMetrics.ascent, textPaint);
	}

	public void getClickableArea(Rect result, View view, String text, int x, int y) {
		textPaint.getTextBounds(text, 0, text.length(), result);
		result.offsetTo(0, 0);
		
		Point point = speechBubbleDrawer.getPointLocationGivenInner(result);
		
		result.offsetTo(x - point.x, y - point.y);
		
		speechBubbleDrawer.updateInnerRectForClickableArea(result);
	}

}
