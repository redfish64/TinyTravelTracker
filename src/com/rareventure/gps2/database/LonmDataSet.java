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
package com.rareventure.gps2.database;

import java.util.ArrayList;
import java.util.Collections;

import com.rareventure.android.Util;

/**
 * Keeps track of data set of lonm values. Finds the largest gap between points and ranges of lonm data,
 * so that a continuous block of lonm data that is narrowest can be found
 * @author tim
 *
 */
public class LonmDataSet {
	private ArrayList<LonmData> data = new ArrayList<LonmData>();
	private boolean calcFinished;
	private int start;
	private int end;
	private boolean isInfinite;
	
	public LonmDataSet()
	{
	}
	
	public void addPoint(int lonm)
	{
		calcFinished = false;
		data.add(new LonmData(lonm,lonm));
	}
	
	/**
	 * Note this must be in order from left to right. If it over extends +/- 180M, that's alright.
	 * @param lonm1
	 * @param lonm2
	 */
	public void addRange(int lonm1, int lonm2)
	{
		calcFinished = false;
		data.add(new LonmData(Util.normalizeLonm(lonm1),Util.normalizeLonm(lonm2)));
	}
	
	/**
	 * Adds a range using doubles. Items will be rounded to a range that includes the specified one
	 * and is composed of ints
	 */
	public void addRangeWithWidth(double lonm1, double width) {
		if(width < 0)
			addRange((int)Math.floor(lonm1+width), (int)Math.ceil(lonm1));
		else 
			addRange((int)Math.floor(lonm1), (int)Math.ceil(lonm1+width));
	}

	/**
	 * Add a range using a starting position and width
	 * @param lonm1
	 * @param width
	 */
	public void addRangeWithWidth(int lonm1, int width)
	{
		int lonm2;
		if(width < 0)
		{
			lonm2 = lonm1;
			lonm1 = lonm2 + width;
		}
		else
		{
			lonm2 = lonm1 + width;
		}
		
		addRange(lonm1, lonm2);
	}
	
	private void finishCalc()
	{
		if(!calcFinished)
		{
			calcFinished = true;

			if(isInfinite)
				return;
			
			//we need to find the maximum distance between points, which will determine where we don't
			//want the center of this guy to be
			Collections.sort(data);
			
			int largestGapEndPos = 0; //the end position of the largest gap in the data
			int largestGapSize = 0; // the size of the largest gap in data
			
			//first we need to find the maximum end value, and subtract 360 degrees.
			//This becomes the end of the last range when we are evaluating the first point
			int lastRangeEnd = Util.MIN_LONM;
			
			for(int i = 0; i < data.size(); i++)
			{
				int currRangeEnd = data.get(i).v2;
				if(data.get(i).v2 < data.get(i).v1) // if we wrapped around the end
					currRangeEnd += Util.LONM_PER_WORLD; //by adding 360 degrees,
				//we are making the guys the wrap around have a later range end then the guys that don't,
				//which is what should be correct
				
				if(lastRangeEnd < currRangeEnd)
					lastRangeEnd = currRangeEnd;
					
			}
			lastRangeEnd -= Util.LONM_PER_WORLD;
			
			for(int i = 0; i < data.size(); i++)
			{
				LonmData llsd = data.get(i);
				
				//if the current gap is greater than the max
				if(llsd.v1 - lastRangeEnd > largestGapSize)
				{
					largestGapSize = llsd.v1 - lastRangeEnd;
					largestGapEndPos = llsd.v1;
				}
				
				//since the point may extend past 179.9999M we need to make sure we have a range end 
				//that is beyond 180M so that when we compare it, it won't be negative
				int currRangeEnd = Util.makeContinuousFromStartLonm(llsd.v1, llsd.v2);
				
				//if the range end of the current point extends past the range of the last
				if(lastRangeEnd < currRangeEnd)
					lastRangeEnd = currRangeEnd; 
			}
			
			if(largestGapSize == 0)
			{
				isInfinite = true;
			}
			else {
				end = Util.normalizeLonm(largestGapEndPos - largestGapSize);
				start = largestGapEndPos;
			}
			
		}
	}
	
	public int getStartLonm()
	{
		finishCalc();
		return start;
		
	}
	
	public int getWidth() {
		finishCalc();
		if(start == Integer.MIN_VALUE)
			return Util.LONM_PER_WORLD;
		return Util.makeContinuousFromStartLonm(start, end) - start;
	}
	
	private static class LonmData implements Comparable<LonmData>
	{
		public LonmData(int lonm1, int lonm2) {
			this.v1 = lonm1;
			this.v2 = lonm2;
		}

		public int v1,v2;

		@Override
		public int compareTo(LonmData another) {
			return this.v1 - another.v1;
		}
	}

	public boolean isInfinite() {
		return isInfinite;
	}

	public void addInfiniteRange() {
		isInfinite = true;
	}

}
