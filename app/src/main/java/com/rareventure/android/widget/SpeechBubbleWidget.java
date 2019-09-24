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
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.rareventure.gps2.R;


/**
 * Displays a speech bubble widget that has a single child view inside of it
 */
public class SpeechBubbleWidget extends ViewGroup {

	private Rect innerRect;
	private SpeechBubbleDrawer speechBubbleDrawer;

	public SpeechBubbleWidget(Context context, AttributeSet attrs) {
		super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SpeechBubbleWidget);

        this.speechBubbleDrawer = new SpeechBubbleDrawer(
				a.getDimensionPixelOffset(R.styleable.SpeechBubbleWidget_speechBubbleRoundedCorner, 5),
				a.getFloat(R.styleable.SpeechBubbleWidget_speechBubbleTriXPerc, .75f),
				a.getDimensionPixelOffset(R.styleable.SpeechBubbleWidget_speechBubbleTriWidth, 5),
				a.getDimensionPixelOffset(R.styleable.SpeechBubbleWidget_speechBubbleTriHeight, 5),
				a.getDimensionPixelOffset(R.styleable.SpeechBubbleWidget_speechBubbleInnerPad, 5));
		if (a != null) {
			a.recycle();
		}
	} 

	/**
	 * This weird method must be in every subclass of ViewGroup or Layouts within the class won't display properly
	 * We need to call onMeasure on the child in it.
	 */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        
        Rect measuredInnerRect = 
        	speechBubbleDrawer.calcInnerRect(0,0, 
        			width, 
        			height);
		
        final View child = getChildAt(0);
        
        final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
        		//this next thing is the padding between the size of the entire widget
        		//(which currently extends to the edge of the screen)
                getPaddingLeft() + getPaddingRight() + width - measuredInnerRect.width(), 
                child.getLayoutParams().width);
        final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + height - measuredInnerRect.height(), 
                child.getLayoutParams().height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);

        setMeasuredDimension(resolveSize(child.getMeasuredWidth(), widthMeasureSpec),
                resolveSize(child.getMeasuredHeight(), heightMeasureSpec));
    }
    

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        final LayoutParams lp = child.getLayoutParams();

		Rect measuredInnerRect = calcInnerRect(0,0,lp.width,lp.height);		
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight(), measuredInnerRect.width());
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), measuredInnerRect.height());

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);

        super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
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
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		innerRect = calcInnerRect(l,t,r,b);
		
        getChildAt(0).layout(innerRect.left, innerRect.top,
        		innerRect.right,
                innerRect.bottom);
	}
	
    @Override
    public void addView(View child, int index) {
        if (getChildCount() > 1) {
            throw new IllegalStateException("only one child");
        }

        super.addView(child, index);
    }

    @Override
    public void addView(View child, LayoutParams params) {
        if (getChildCount() > 1) {
            throw new IllegalStateException("only one child");
        }

        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        if (getChildCount() > 1) {
            throw new IllegalStateException("only one child");
        }

        super.addView(child, index, params);
    }
    //TODO 2.5: find out why trip through naples has so few points

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

	public void drawBubble(Canvas canvas, String name, int x, int y) {
		
	}

}
