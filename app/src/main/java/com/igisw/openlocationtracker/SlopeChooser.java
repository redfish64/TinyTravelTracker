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

import com.rareventure.gps2.database.TAssert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

//import junit.framework.Assert;

public class SlopeChooser {
	private HashSet<Point> pointsHash = new HashSet<SlopeChooser.Point>();
	private double calcSlope;
	private int calcMinX;
	private int calcDist;
	private long calcStartTimeMs, calcEndTimeMs;

	public SlopeChooser()
	{
	}

	public void add(int point, long time) {
		pointsHash.add(new Point(point, time));
	}

	public void finishCalculation() {
		finishCalculation(null);
	}
	
	public void finishCalculation(Integer lonmOffset) {
		ArrayList<Point> points = new ArrayList<SlopeChooser.Point>(pointsHash);
		Collections.sort(points);
		
		Point bestP1=null, bestP2=null;
		boolean bestIsLeft = false;
		double minDist = Double.MAX_VALUE;
		
		long minTimeMs = Long.MAX_VALUE, maxTimeMs = Long.MIN_VALUE; 
		
		if(lonmOffset != null)
		{
			//fix the points for the lonm offset
			for(Point p : points)
			{
				p.x = Util.makeContinuousFromStartLonm(lonmOffset, p.x);
			}
		}
		
		//go through all the points
		for(int i1 = 0; i1 < points.size(); i1++)
		{
			Point p1 = points.get(i1);
			
			minTimeMs = Math.min(minTimeMs, p1.y);
			maxTimeMs = Math.max(maxTimeMs, p1.y);
			
			//find the slope between the point and all the points after it
			for(int i2 = i1+1; i2 < points.size(); i2++)
			{
				Point p2 = points.get(i2);
				
				boolean leftSlopeFailed = false, rightSlopeFailed = false;
				
				//a completely horizontal slope is invalid (dist, no time)
				if(isSlopeInvalid(p1,p2))
					continue;
				
				//check if the slope line is valid for the left side or the right side. As long as all the other points
				//are on the right, then it can be the left side, and vice versa.
				// also find the point which should be the maximum, ie the other side. This is basically where the
				// horizontal distance is the highest given any timeslice along the slope line.
				Point rightSideMaxPoint = p1;
				Point leftSideMinPoint = p1;
				
				for(Point p3: points)
				{
					int side = calculateSideOfSlope(p3,p2,p1);
					
					if(side < 0)
						leftSlopeFailed = true;
					else if(side > 0)
						rightSlopeFailed = true;
					
					if(!leftSlopeFailed)
					{
						if(calculateSideOfSlope(p3,p2,p1,rightSideMaxPoint)>0)
							rightSideMaxPoint = p3;
					}
					if(!rightSlopeFailed)
					{
						if(calculateSideOfSlope(p3,p2,p1,leftSideMinPoint)<0)
							leftSideMinPoint = p3;
					}
					
					if(leftSlopeFailed && rightSlopeFailed)
						break;
				}
				
				//if on the left side of all points
				if(!leftSlopeFailed)
				{
					double dist = calculateDist(p1,rightSideMaxPoint,p2,p1);
					if(dist < minDist)
					{
						bestP2 = p2;
						bestP1 = p1;
						minDist = dist;
						bestIsLeft = true;
					}
					
					if(dist < 0)
						TAssert.fail("dist is "+dist);
				}
				//if on the right side of all the points
				if(!rightSlopeFailed)
				{
					double dist = calculateDist(leftSideMinPoint,p1,p2,p1);
					if(dist < minDist)
					{
						bestP2 = p2;
						bestP1 = p1;
						minDist = dist;
						bestIsLeft = false;
					}
					
					if(dist < 0)
						TAssert.fail("dist is "+dist);
				}
			}
			
		}
		
		calcSlope = calculateSlope(bestP2,bestP1);
		
		double doubleMinX = calcSlope * (minTimeMs - bestP1.y ) + bestP1.x; 
		
		if(bestIsLeft)
		{
			calcMinX = (int)Math.floor(doubleMinX);
			calcDist = (int)Math.ceil(doubleMinX + minDist) - calcMinX;
		}
		else
		{
			calcMinX = (int)Math.floor(doubleMinX - minDist);
			calcDist = (int)Math.ceil(doubleMinX) - calcMinX;
		}
		
		calcStartTimeMs = minTimeMs;
		calcEndTimeMs = maxTimeMs;
	}
	
	/**
	 * Calculates the distance as follows.
	 * Takes basispoint2 and adjusts it to where it is at the same time of basisPoint1
	 * from the line defined by slopeP2 to slopeP1.
	 * Then subtracts the difference.
	 * @param basisPoint1
	 * @param basisPoint2
	 * @param slopeP1
	 * @param slopeP2
	 * @return
	 */
	private double calculateDist(Point basisPoint1, Point basisPoint2, Point slopeP1, Point slopeP2) {
		double basisPoint2XAtPoint1Time = calculateSlope(slopeP2, slopeP1) * (basisPoint1.y - basisPoint2.y) + basisPoint2.x;
		
		return basisPoint2XAtPoint1Time - basisPoint1.x;
	}

	/**
	 * 
	 * @param p3
	 * @param p2
	 * @param p1
	 * @return 1 if p3 is on the right side of the slope, starting from basisPoint, -1 if on the left and 0 if on the slope
	 */
	private int calculateSideOfSlope(Point p3, Point p2, Point p1, Point basisPoint) {
		double slope1 = calculateSlope(p2,p1);
		double pointAtP3 = slope1 * (p3.y - basisPoint.y) + basisPoint.x;
		
		if(pointAtP3 < p3.x)
			return 1;
		if(pointAtP3 > p3.x)
			return -1;
		
		return 0;
	}

	/**
	 * 
	 * @param p3
	 * @param p2
	 * @param p1
	 * @return 1 if p3 is on the right side of the slope, -1 if on the left and 0 if on the slope
	 */
	private int calculateSideOfSlope(Point p3, Point p2, Point p1) {
		double slope1 = calculateSlope(p2,p1);
		double pointAtP3 = slope1 * (p3.y - p1.y) + p1.x;
		
		if(pointAtP3 < p3.x)
			return 1;
		if(pointAtP3 > p3.x)
			return -1;
		
		return 0;
	}

	private double calculateSlope(Point p2, Point p1) {
		return ((double)p2.x - p1.x)/(p2.y - p1.y);
	}

	private boolean isSlopeInvalid(Point p1, Point p2) {
		return p1.y == p2.y;
	}

	private static class Point implements Comparable<Point>
	{
		int x; //dist
		long y; //time
		
		public Point(int point, long time) {
			this.x = point;
			this.y = time;
		}

		@Override
		public int compareTo(Point another) {
			long v = this.y - another.y;
			
			if(v < 0)
				return -1;
			if(v > 0)
				return 1;
			
			return this.x - another.x;
		}
		
		public boolean equals(Object another)
		{
			return x == ((Point)another).x && y == ((Point)another).y;
		}
		
		public int hashCode()
		{
			return (int) (x ^ y);
		}
		
	}

	public int getMinX() {
		return calcMinX;
	}

	public int getDist() {
		return calcDist;
	}
	
	public double getSlope()
	{
		return calcSlope;
	}

	public long getStartTimeMs() {
		return calcStartTimeMs;
	}

	public long getEndTimeMs() {
		return calcEndTimeMs;
	}
}
