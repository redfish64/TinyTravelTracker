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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.igisw.openlocationtracker.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.android.HandlerTimer;
import com.rareventure.gps2.database.TAssert;

import java.util.ArrayList;
import java.util.TimerTask;

//import junit.framework.Assert;

//TODO 4: settings window for preferences
//TODO 4: redo dial movement using Scroller and VelocityTracker
public class Dial extends View
{
	/**
	 * The last location the users finger was at
	 */
	private float lastX;
	
	/**
	 * The last time the dial was updated by the users finger
	 */
	private long lastTime;
	
	private float momentumPixelsPerMs;
	
	/**
	 * The number of ticks in the clients reference frame to pixels on the screen, may be negative
	 * for a reverse layout
	 */
	public double ticksPerPixel;
	
	//TODO 4: yes, it's weird to have both a double and a long, but I 
	// am worried that a double might not have enough precision for ms (untested)
	// and a long won't work for subpixel stuff
	// PERF: We might just use a long and handle it on the client size (like microdivisions
	// Android uses for long and lat)

	/**
	 * Whole part of the ticks
	 */
	public long ticks;
	
	/**
	 * Fractional part of the ticks, will always be from 0 to 1
	 */
	public double ticksFrac;
	
	private Preferences prefs = new Preferences();
	
	private HandlerTimer timer;
	
	private ArrayList<Strip> strips = new ArrayList<Strip>();


	private long startTicks;


	private long endTicks;


	private double startTicksFrac;



	private double endTicksFrac;

	private long lastMomentumUpdatedTimeMs = System.currentTimeMillis();
	
	private long lastUpdatedViewMs;
	
	private Runnable updateStuffTimerTask = new TimerTask() {

		@Override
		public void run() {
			long time = System.currentTimeMillis();
			updateTicksForMomentum(time);
		}

	};

	private int stripFontSizeHeight;

	private int minHeight;

	public int preferredHeight;
	
	
	public Dial(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public Dial(Context context) {
		super(context);
	}
	
	private void _init()
	{
		timer = new HandlerTimer(updateStuffTimerTask, prefs.dialAnimationDelayMs);
	}

	public void addStrip(Strip s)
	{
		strips.add(s);
		s.setDial(this);

		stripFontSizeHeight += s.pxHeight;
//		Assert.assertFalse(
//			"Strip max ticks per pixel is less than ticksPerPixel", 
//					Math.abs(s.maxTicksPerPixel) < Math.abs(ticksPerPixel));

    	preferredHeight = Math.max(stripFontSizeHeight, minHeight);

//		Log.d(GTG.TAG,"addStrip called with preferredHeight "+preferredHeight);
	}
	
	
	
	public void setStrips(Strip[] strips)
	{
		this.strips.clear();
		stripFontSizeHeight = 0;
		
		for(Strip s : strips)
			addStrip(s);
	}

	public double getTicksPerPixel()
	{
		return ticksPerPixel;
	}
	
	protected void setTicksPerPixel(double ticksPerPixel)
	{
		this.ticksPerPixel = ticksPerPixel;
	}

	public float getPixelLoc(long pos)
	{
		return (float) ((pos - ticks - ticksFrac) / ticksPerPixel 
		+ getWidth() / 2);
	}

    /**
     * Render the text
     * 
     * @see android.view.View#onDraw(android.graphics.Canvas)
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //put all the strips in the middle of the dial
        int y = (preferredHeight - stripFontSizeHeight)/2;
        
        for(Strip s : strips)
        {
        	s.draw(canvas,0, y);
        	y+=s.getHeight();
        }
    }
    
    
    protected void setMinHeight(int minHeight)
    {
    	this.minHeight = minHeight;
    	
    	preferredHeight = Math.max(stripFontSizeHeight, minHeight);
    }

    /**
     * @see android.view.View#measure(int, int)
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    	
        setMeasuredDimension(Util.measureWithPreferredSize(widthMeasureSpec, 
        		//TODO 3 HACK what should we really do here?
        		preferredHeight
        		+ getPaddingLeft() + getPaddingRight()),
                Util.measureWithPreferredSize(heightMeasureSpec, preferredHeight 
                		+ getPaddingTop() + getPaddingBottom()));
    }
    
    /**
     * Regardless if ticksPerPixel is negative or not, this should be the minimum position,
     * ie it is always true that startTicks < endTicks
     */
	public void setStartTicks(long startTicks) {
		this.startTicks = startTicks;
		this.startTicksFrac = 0;
	}
	
	public void setEndTicks(long endTicks) {
		this.endTicks = endTicks;
		this.endTicksFrac = 0;
	}
	
	public void setTicks(double ticksWithFrac)
	{
		this.ticks = (long) Math.floor(ticksWithFrac);
		this.ticksFrac = ticksWithFrac - ticks;
	}
	
	public void setStartTicks(double startTicksWithFrac) {
		this.ticks = this.startTicks = (long) Math.floor(startTicksWithFrac);
		this.ticksFrac = this.startTicksFrac = startTicksWithFrac - startTicks;
		
	}

	public void setEndTicks(double endTicksWithFrac) {
		this.endTicks = (long) Math.floor(endTicksWithFrac);
		this.endTicksFrac = endTicksWithFrac - endTicks;
	}
	
	//TODO 2.3: when reached the time when there are no more gps measurements in the future,
	// then mark it somehow on the dial that it's useless to go on. Same with the past.

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		long time = System.currentTimeMillis();
		if(event.getAction() == MotionEvent.ACTION_DOWN)
		{
			timer.stop();
			lastX = event.getX();
			lastTime = time;
			return true;
		}
		else if(event.getAction() == MotionEvent.ACTION_MOVE)
		{
			float pixelMovement = (lastX - event.getX());
//			Log.d("GPS","action move, pixelmovement: "+pixelMovement+" time: "+(time-lastTime));
			
			adjustTicks(time, pixelMovement * ticksPerPixel);
			
			momentumPixelsPerMs = (float) (pixelMovement / (time - lastTime+1) * prefs.movementMomentumRatio) 
				* (time-lastTime) / (float)prefs.lastMovementTotalMs +
				momentumPixelsPerMs * (1-(time-lastTime)/ (float)prefs.lastMovementTotalMs);
			if(momentumPixelsPerMs < 0)
				momentumPixelsPerMs = -momentumPixelsPerMs * momentumPixelsPerMs;
			else
				momentumPixelsPerMs = momentumPixelsPerMs * momentumPixelsPerMs;
			
			if(momentumPixelsPerMs > prefs.maxMomentumPixelsPerMs)
				momentumPixelsPerMs = prefs.maxMomentumPixelsPerMs;
			if(momentumPixelsPerMs < -prefs.maxMomentumPixelsPerMs)
				momentumPixelsPerMs = -prefs.maxMomentumPixelsPerMs;
					
			
			lastX = event.getX();
			lastTime = time;
			
			invalidate();
			
			return true;
		}
		else if(event.getAction() == MotionEvent.ACTION_UP)
		{
//			Log.d("GPS","action up");
			if(momentumPixelsPerMs != 0)
			{
				lastMomentumUpdatedTimeMs = System.currentTimeMillis();
				timer.start(prefs.dialAnimationDelayMs);
			}

			
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @param movement
	 * @return true if hit the edge of the dial
	 */
	private boolean adjustTicks(long time, double movement) {
		
		//Note that this automagically always keeps ticksFrac between 0 and 1 regardless
		// if movement is positive or negative
		ticksFrac += movement;

		long adj = (long) Math.floor(ticksFrac);

		ticksFrac -= adj;
		ticks += adj;
		
		if(ticksFrac < 0 || ticksFrac > 1)
			TAssert.fail("ticksfrac is out of bounds "+ticksFrac);

		if(ticks > endTicks || ticks == endTicks && ticksFrac > endTicksFrac)
		{
			ticks = endTicks;
			ticksFrac = endTicksFrac;
			return true;
		}
		else if(ticks < startTicks || ticks == startTicks && ticksFrac < startTicksFrac)
		{
			ticks = startTicks;
			ticksFrac = startTicksFrac;
			return true;
		}

		if(time - lastUpdatedViewMs > prefs.viewAnimationDelayMs)
		{
			updateView(time);
			invalidate();
		}
			
		
		return false;
	}
	
    protected void onDetachedFromWindow() {
    	super.onDetachedFromWindow();
    	if(timer != null)
    		timer.stop();
    }
    
    //TODO 4: display start and end time
    //TODO 3: display vertical dial when in horizontal mode?
    
    //TODO 3: have advanced option for playing through time?
    //TODO 4: maybe have skins? Different ways of displaying time, etc.
    

    /**
     * @param time
     * @return true if the display needs to be updated
     */
	private boolean updateTicksForMomentum(long time) {
		
		if(momentumPixelsPerMs == 0)
		{
			stopDial();
			return false;
		}
		
		//if we've hit the edge of the dial
		if(adjustTicks(time, (time - lastMomentumUpdatedTimeMs) * momentumPixelsPerMs * ticksPerPixel))
		{
			stopDial();
			return true; //we still need to invalidate
		}
		
		float nextMomentumPixelsPerMs;
		
		if(momentumPixelsPerMs < 0)
			nextMomentumPixelsPerMs = momentumPixelsPerMs + 
			(time - lastMomentumUpdatedTimeMs) * prefs.momentumDrainPixelsPerMs2;
		else 
			nextMomentumPixelsPerMs = momentumPixelsPerMs - 
			(time - lastMomentumUpdatedTimeMs) * prefs.momentumDrainPixelsPerMs2;
		
		
		//if we flipped from negative to positive or vice versa
		if(nextMomentumPixelsPerMs * momentumPixelsPerMs < 0)
			momentumPixelsPerMs = 0;
		else momentumPixelsPerMs = nextMomentumPixelsPerMs;
		
		lastMomentumUpdatedTimeMs = time;

		if(momentumPixelsPerMs == 0)
			stopDial();
		
		return true;
	}

	private void updateView(long time) {
		if(l != null)
		{
			l.posChanged(this);
		}
		lastUpdatedViewMs = time;
	}	
	
	private void stopDial() {
		momentumPixelsPerMs = 0;
		timer.stop();
		updateView(System.currentTimeMillis());
		
		postInvalidate();
	}

	private Listener l;

	public void setListener(Listener l)
	{
		this.l = l;
	}

	public interface Listener
	{
		public void posChanged(Dial dial);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		_init();
	}

	public static class Preferences implements AndroidPreferences
	{


		/**
		 * The number of milliseconds to consider when choosing the momentum for
		 * the dial. If we get a move event and it only covers 1 millsecond,
		 * then its affect will only be 1/<this value>  
		 */
		public int lastMovementTotalMs = 200;

		public float maxMomentumPixelsPerMs = 300/1000f;

		/**
		 * The speed the dial slows down when moving
		 */
		public float momentumDrainPixelsPerMs2 = .0001f;

		/**
		 * The delay of animating the dial
		 */
		public long dialAnimationDelayMs = 33;
		
		/**
		 * The delay of animating the dial
		 */
		public long viewAnimationDelayMs = 99;
		
		/**
		 * The speed the dial should move when the user spins it.
		 */
		public double movementMomentumRatio = 1;
	}

}
