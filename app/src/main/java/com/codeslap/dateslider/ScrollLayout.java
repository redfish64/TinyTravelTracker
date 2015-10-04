/*
 * Copyright (C) 2011 Daniel Berndt - Codeus Ltd  -  DateSlider
 * 
 * This class contains all the scrolling logic of the slide-able elements
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codeslap.dateslider;

import com.rareventure.gps2.GTG;
import com.rareventure.gps2.reviewer.EnterFromDateToToDateActivity;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.Scroller;


public class ScrollLayout extends LinearLayout {

    private static final String TAG = "SCROLLLAYOUT";

    private final Scroller mScroller;
    private boolean mDragMode;
    private int mLastX, mLastScroll, mFirstElemOffset, childrenWidth, mScrollX;
    private VelocityTracker mVelocityTracker;
    private final int mMinimumVelocity;
    private final int mMaximumVelocity;
    private int mInitialOffset;
    private long currentTime;
    private int mObjWidth;

    private EnterFromDateToToDateActivity.Labeler mLabeler;
    private OnScrollListener mListener;
    private TimeView mCenterView;

	private long maxTime;

	private long minTime;


    public ScrollLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroller = new Scroller(getContext());
        setGravity(Gravity.CENTER_VERTICAL);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        // as mMaximumVelocity does not exist in API<4
        float density = getContext().getResources().getDisplayMetrics().density;
        mMaximumVelocity = (int) (4000 * 0.5f * density);
    }
    
    public void setMinTimeAndMaxTime(long minTime, long maxTime)
    {
    	this.minTime = minTime;
    	this.maxTime = maxTime;
    }

    public void setMinTime(long minTime)
    {
    	this.minTime = minTime;
    }

    public void setMaxTime(long maxTime)
    {
    	this.maxTime = maxTime;
    }

    /**
     * This method is called usually after a ScrollLayout is instantiated, it provides the scroller
     * with all necessary information
     *
     * @param labeler   the labeler instance which will provide the ScrollLayout with time
     *                  unit information
     * @param time      the start time as timestamp representation
     * @param objWidth  the width of an TimeTextView in dps
     * @param objHeight the height of an TimeTextView in dps
     */
    public void setLabeler(EnterFromDateToToDateActivity.Labeler labeler, long time, int objWidth, int objHeight) {
        this.mLabeler = labeler;
        currentTime = time;
        mObjWidth = (int) (objWidth * getContext().getResources().getDisplayMetrics().density);
        objHeight = (int) (objHeight * getContext().getResources().getDisplayMetrics().density);

        // TODO: make it not dependent on the display width but rather on the layout width
        Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int displayWidth = display.getWidth();
        while (displayWidth > childrenWidth && labeler != null) {
            LayoutParams lp = new LayoutParams(mObjWidth, objHeight);
            if (childrenWidth == 0) {
                TimeView ttv = labeler.createView(getContext(), true);
                ttv.setVals(labeler.getElem(currentTime));
                addView((View) ttv, lp);
                mCenterView = ttv;
                childrenWidth += mObjWidth;
            }
            TimeView ttv = labeler.createView(getContext(), false);
            TimeView lastChild = (TimeView) getChildAt(getChildCount() - 1);
            ttv.setVals(labeler.add(lastChild.getEndTime(), 1));
            addView((View) ttv, lp);
            ttv = labeler.createView(getContext(), false);
            ttv.setVals(labeler.add(((TimeView) getChildAt(0)).getEndTime(), -1));
            addView((View) ttv, 0, lp);
            childrenWidth += mObjWidth + mObjWidth;
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);
        mInitialOffset = (childrenWidth - w) / 2;
        super.scrollTo(mInitialOffset, 0);
        mScrollX = mInitialOffset;
        setTime(currentTime, 0);
    }

    /**
     * this element will position the TimeTextViews such that they correspond to the given time
     *
     * @param time  the time in milliseconds
     * @param loops prevents setTime getting called too often, if loop is > 2 the procedure will be
     *              stopped
     */
    public void setTime(long time, int loops) {
        currentTime = time;
        if (!mScroller.isFinished()) mScroller.abortAnimation();
        int pos = getChildCount() / 2;
        TimeView currentElement = (TimeView) getChildAt(pos);
        if (loops > 2 || currentElement.getStartTime() <= time && currentElement.getEndTime() >= time) {
            if (loops > 2) {
//                Log.d(TAG, String.format("time: %d, start: %d, end: %d", time, currentElement.getStartTime(), currentElement.getStartTime()));
                return;
            }
            double center = getWidth() / 2.0;
            int left = (getChildCount() / 2) * mObjWidth - getScrollX();
            double currper = (center - left) / mObjWidth;
            double goalper = (time - currentElement.getStartTime()) / (double) (currentElement.getEndTime() - currentElement.getStartTime());
            int shift = (int) Math.round((currper - goalper) * mObjWidth);
            mScrollX -= shift;
            reScrollTo(mScrollX, 0, false);
        } else {
            double diff = currentElement.getEndTime() - currentElement.getStartTime();
            int steps = (int) Math.round(((time - (currentElement.getStartTime() + diff / 2)) / diff));
            moveElements(-steps);
            setTime(time, loops + 1);
        }
    }


    /**
     * scroll the element when the mScroller is still scrolling
     */
    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mScrollX = mScroller.getCurrX();
            reScrollTo(mScrollX, 0, true);
            // Keep on drawing until the animation has finished.
            postInvalidate();
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        reScrollTo(x, y, true);
    }

    /**
     * core scroll function which will replace and move TimeTextViews so that they don't get
     * scrolled out of the layout
     *
     * @param x      the x scroll
     * @param y      the y scroll
     * @param notify if false, the listeners won't be called
     */
    void reScrollTo(int x, int y, boolean notify) {
        if (getChildCount() > 0) {
            mFirstElemOffset += x - mLastScroll;
            double center = getWidth() / 2.0;
            int left = (getChildCount() / 2) * mObjWidth - mFirstElemOffset;
            double f = (center - left) / mObjWidth;
            long newTime = (long) (mCenterView.getStartTime() + (mCenterView.getEndTime() 
            		- mCenterView.getStartTime()) * f);
            
            if(newTime > maxTime)
            {
                newTime = maxTime;
                
//                left = (getChildCount() / 2) * mObjWidth - mFirstElemOffset;
//                f = (center - left) / mObjWidth;
//
//                f = (center - (getChildCount() / 2) * mObjWidth + mFirstElemOffset) / mObjWidth
//
//                f * mObjWidth = (center - (getChildCount() / 2) * mObjWidth + mFirstElemOffset)
//
//                f * mObjWidth - center + (getChildCount() / 2) * mObjWidth = mFirstElemOffset


                mFirstElemOffset = (int)(((double)newTime - mCenterView.getStartTime()) / (mCenterView.getEndTime() 
                		- mCenterView.getStartTime())  * mObjWidth - center	+ (getChildCount() / 2) * mObjWidth);
            }
            else if(newTime < minTime)
            {
                newTime = minTime;
                mFirstElemOffset = (int)(((double)newTime - mCenterView.getStartTime()) / (mCenterView.getEndTime() 
                		- mCenterView.getStartTime())  * mObjWidth - center	+ (getChildCount() / 2) * mObjWidth);
            }
            
            if (mFirstElemOffset - mInitialOffset > mObjWidth / 2) {
                int stepsRight = (mFirstElemOffset - mInitialOffset + mObjWidth / 2) / mObjWidth;
                moveElements(-stepsRight);
                mFirstElemOffset = ((mFirstElemOffset - mInitialOffset - mObjWidth / 2) % mObjWidth) 
                + mInitialOffset - mObjWidth / 2;
            } else if (mInitialOffset - mFirstElemOffset > mObjWidth / 2) {
                int stepsLeft = (mInitialOffset + mObjWidth / 2 - mFirstElemOffset) / mObjWidth;
                moveElements(stepsLeft);
                mFirstElemOffset = (mInitialOffset + mObjWidth / 2 - ((mInitialOffset + mObjWidth / 2 
                		- mFirstElemOffset) % mObjWidth));
            }
	        super.scrollTo(mFirstElemOffset, y);
	        if (mListener != null && notify) {
	
	            mListener.onScroll(this, newTime);
	        }
	        
            mLastScroll = x;
            
	    }
    }

    /**
     * when the scrolling procedure causes "steps" elements to fall out of the visible layout,
     * all TimeTextViews swap their contents so that it appears that there happens an endless
     * scrolling with a very limited amount of views
     *
     * @param steps the amount of steps to move
     */
    void moveElements(int steps) {
        if (steps < 0) {
            for (int i = 0; i < getChildCount() + steps; i++) {
                ((TimeView) getChildAt(i)).setVals((TimeView) getChildAt(i - steps));
            }
            for (int i = getChildCount() + steps; i > 0 && i < getChildCount(); i++) {
                EnterFromDateToToDateActivity.TimeObject newTo = mLabeler.add(((TimeView) getChildAt(i - 1)).getEndTime(), 1);
                ((TimeView) getChildAt(i)).setVals(newTo);
            }
            if (getChildCount() + steps <= 0) {
                for (int i = 0; i < getChildCount(); i++) {
                    EnterFromDateToToDateActivity.TimeObject newTo = mLabeler.add(((TimeView) getChildAt(i)).getEndTime(), -steps);
                    ((TimeView) getChildAt(i)).setVals(newTo);
                }
            }
        } else if (steps > 0) {
            for (int i = getChildCount() - 1; i >= steps; i--) {
                ((TimeView) getChildAt(i)).setVals((TimeView) getChildAt(i - steps));
            }
            for (int i = steps - 1; i >= 0 && i < getChildCount() - 1; i--) {
                EnterFromDateToToDateActivity.TimeObject newTo = mLabeler.add(((TimeView) getChildAt(i + 1)).getEndTime(), -1);
                ((TimeView) getChildAt(i)).setVals(newTo);
            }
            if (steps >= getChildCount()) {
                for (int i = 0; i < getChildCount(); i++) {
                    EnterFromDateToToDateActivity.TimeObject newTo = mLabeler.add(((TimeView) getChildAt(i)).getEndTime(), -steps);
                    ((TimeView) getChildAt(i)).setVals(newTo);
                }
            }
        }
    }

    /**
     * finding whether to scroll or not
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
//    	Log.d(GTG.TAG,"event is "+ev);
        final int action = ev.getAction();
        final int x = (int) ev.getX();
        if (action == MotionEvent.ACTION_DOWN) {
            mDragMode = true;
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
        }

        if (!mDragMode)
            return super.onTouchEvent(ev);

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                mScrollX += mLastX - x;
                reScrollTo(mScrollX, 0, true);
                break;
            case MotionEvent.ACTION_UP:
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000);
                int initialVelocity = (int) Math.min(velocityTracker.getXVelocity(), mMaximumVelocity);

                if (getChildCount() > 0 && Math.abs(initialVelocity) > mMinimumVelocity) {
                    fling(-initialVelocity);
                }
              mDragMode = false;
//            case MotionEvent.ACTION_CANCEL:
//            default:
//                mDragMode = false;
              default:
            	  return false;

        }
        mLastX = x;

        return true;
    }

    /**
     * causes the underlying mScroller to do a fling action which will be recovered in the
     * computeScroll method
     *
     * @param velocityX the speed of the fling
     */
    private void fling(int velocityX) {
        if (getChildCount() > 0) {
            mScroller.fling(mScrollX, 0, velocityX, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
            invalidate();
        }
    }

    public void setOnScrollListener(OnScrollListener l) {
        mListener = l;
    }

    public interface OnScrollListener {
        public void onScroll(ScrollLayout source, long x);
    }
}
