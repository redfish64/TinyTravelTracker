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

import android.graphics.Canvas;

public class Strip
{
	private DataGenerator dg;
	
	public int pxHeight;
	
	protected int upperLeftX;
	protected int upperLeftY;
	protected Dial dial;
	public double maxTicksPerPixel;

	private long overEdgePixels;

	/**
	 * 
	 * @param dg data generator
	 * @param maxTicksPerPixel ratio of client ticks to pixels
	 * @param overEdgeTicks max amount that the text or label may extend
	 *   past its position 
	 * @param pxHeight the height of the strip in pixels
	 */
	public Strip()
	{
	}
	
	public void init(
			DataGenerator dg,  
			double maxTicksPerPixel,
			long overEdgePixels, int pxHeight)
	{
		
		this.dg = dg;
		this.overEdgePixels = overEdgePixels;
		this.pxHeight = pxHeight;
		this.maxTicksPerPixel = maxTicksPerPixel;
		
	}
	
	public void setTextGenerator(DataGenerator dg)
	{
		this.dg = dg;
	}
	
	public void draw(Canvas canvas, int x, int y) {
		this.upperLeftX = x;
		this.upperLeftY = y;
		//PERF: use a cache or something?
		dg.setData(this, canvas,
				(long)Math.floor(dial.ticks + dial.ticksFrac - 
						((dial.getWidth()+ overEdgePixels) * dial.ticksPerPixel / 2))
						,
				(long)Math.ceil(dial.ticks + dial.ticksFrac + 
						((dial.getWidth()+ overEdgePixels) * dial.ticksPerPixel / 2))
						+ overEdgePixels);
	}
	
	//TODO 3: add method for drawing Drawables as labels  
	
	public static interface DataGenerator
	{
		/**
		 * Tells the calle it must draw ticks from startPos to endPos
		 *
		 * @param canvas 
		 * @param startPos
		 * @param endPos
		 */
		public void setData(Strip strip, Canvas canvas, long startPos, long endPos);
	}

	public int getHeight() {
		return pxHeight;
	}

	public void setDial(Dial dial) {
		this.dial = dial;
	}

	public int getMinLabelStep(double ticksPerPixel) {
		return (int)Math.ceil(ticksPerPixel / this.maxTicksPerPixel);
	}
}
