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
package com.igisw.openlocationtracker;

import android.graphics.Canvas;
import android.graphics.Paint.FontMetrics;
import android.text.TextPaint;

public class TextStrip extends Strip
{
	private DataGenerator dg;
	private TextPaint tp;
	private int longestTextSize;
	private long ticksPerEntry;
	
	public TextStrip()
	{
	}
	
	/**
	 * This init is used when the labels aren't always the same distance apart (such as for months)
	 * 
	 * @param tp
	 * @param dg
	 * @param maxTicksPerPixel
	 */
	public void init(TextPaint tp, Strip.DataGenerator dg, double maxTicksPerPixel)
	{
		this.tp = tp;
		
		FontMetrics fm = tp.getFontMetrics();
		int pxHeight = (int) (-fm.ascent+fm.descent);
		
		super.init(dg, 
				maxTicksPerPixel, longestTextSize/2+1, pxHeight); 
		
	}
	
	/**
	 * @param tp
	 * @param dg
	 * @param probablyTheBiggestText probably the biggest text (used to determine the
	 *   max ticks per pixels before the label start to overlap
	 * @param estTicksPerEntry ticks between labels
	 * @param pad extra distance to handle the slop from the above two measurements
	 */
	public void init(TextPaint tp, DataGenerator dg, String probablyTheBiggestText,
			long estTicksPerEntry, int pad)
	{
		this.dg = dg;
		this.tp = tp;
		this.ticksPerEntry = estTicksPerEntry;
		
		this.longestTextSize = Util.getTextLength(tp, probablyTheBiggestText); 
		
		FontMetrics fm = tp.getFontMetrics();
		
		int pxHeight = (int) (-fm.ascent+fm.descent);
		
		double maxTicksPerPixel = 
			(double)ticksPerEntry / (longestTextSize + pad);
		
		super.init(new Strip.DataGenerator() {

			@Override
			public void setData(Strip strip, Canvas canvas, long startPos, long endPos) {
				for(long i = startPos + startPos % TextStrip.this.ticksPerEntry; i < endPos;
					i += TextStrip.this.ticksPerEntry)
				{
					String label = TextStrip.this.dg.getLabel(TextStrip.this,i);
					
					dgDrawText(canvas, i, label);
				}
			}
			}, 
				maxTicksPerPixel, longestTextSize/2+1, pxHeight); 
		
	}
	
	public static interface DataGenerator
	{
		public String getLabel(TextStrip textStrip, long tick);
	}
	
	/**
	 * The data generator should call this method in response to
	 * setData() if it'd like to draw some data.
	 * 
	 * @param canvas
	 * @param paint
	 * @param pos
	 * @param text
	 */
	public void dgDrawText(Canvas canvas, long pos, String text)
	{
		int x = (int) (upperLeftX + (pos - dial.ticks - dial.ticksFrac) / dial.ticksPerPixel 
			+ dial.getWidth() / 2);
		
		int length = Util.getTextLength(tp, text);
		
		canvas.drawText(text, x - length/2, upperLeftY - tp.ascent(), tp);
	}

}
