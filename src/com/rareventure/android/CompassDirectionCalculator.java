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
package com.rareventure.android;

import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;


/**
 * Determines the direction of the compass by taking a rolling average.
 */
public class CompassDirectionCalculator {
	
	private long lastTime;
	
	private Preferences prefs = new Preferences();

	private float currAvgDirection;

	private long currAvgDirectionStartTimeMs;

	/**
	 * The last average direction the compass was facing over the minimum period
	 * or longer 
	 */
	public float lastAvgDirection;

	/**
	 * The time the compass started facing the last avg direction
	 */
	public long lastAvgDirectionStartTimeMs;

	/**
	 * The time the compass stopped facing the last avg direction
	 */
	public long lastAvgDirectionEndTimeMs;

	/**
	 * 
	 * @param time time of reading
	 * @param direction direction in degrees from 0 to 360
	 * @return true if there is a significant change in direction (see lastDirection* for values)
	 */
	public boolean processCompass(long time, float direction)
	{
		if(lastTime == 0)
		{
			lastTime = time-1;
			currAvgDirectionStartTimeMs = time-1;
			currAvgDirection = direction;
			return false;
		}
		
		//if we can break off to another direction
		if(time - currAvgDirectionStartTimeMs >= prefs.minDirectionTimeMs)
		{
			//if we've changed direction enough that we want to break it off now
			if(Math.abs(direction - adjustDirectionForCompare(currAvgDirection, direction)) 
					> prefs.minDegreesForChange)
			{
				lastAvgDirection = currAvgDirection;
				lastAvgDirectionStartTimeMs = currAvgDirectionStartTimeMs;
				lastAvgDirectionEndTimeMs = lastTime;
				
				currAvgDirectionStartTimeMs = time;
				currAvgDirection = direction;
				
				return true;
			}
		}
		
		//make curr direction the average of all the directions over the specified time period
		currAvgDirection = (currAvgDirection * (lastTime - currAvgDirectionStartTimeMs) +
				adjustDirectionForCompare(direction, currAvgDirection) * (time - lastTime)) 
				/ (time - currAvgDirectionStartTimeMs);
		
		currAvgDirection = adjustDirection(currAvgDirection);

		lastTime = time;
		
		return false;
	}
	
	/**
	 * Standardizes a compass direction from 0 to 360 degrees
	 */
	private float adjustDirection(float dir) {
		if(dir < 0)
			return dir + 360;
		if(dir >= 360)
			return dir - 360;
		return dir;
	}

	/**
	 * Adjusts a compass direction for comparision with another direction.
	 * Makes sure the compass values are at most -/+ 180 degrees of each other.
	 * So, for example, if the first is 359 degrees and the second is 1 degree, it will return
	 * the first one adjusted to -1 degrees.
	 * 
	 * @param direction direction to modify
	 * @param directionToCompare comparision direction
	 * @return direction, modified to be close to directionToCompare in absolute terms
	 */
	private float adjustDirectionForCompare(float direction, float directionToCompare) {
		if(direction - directionToCompare > 180)
			direction -= 360;
		else if(direction - directionToCompare < - 180)
			direction += 360;
		
		return direction;
	}

	public static class Preferences implements AndroidPreferences
	{


		/**
		 * The minimum time period a change in direction has to be before it is registered
		 */
		public long minDirectionTimeMs = 30*1000;
		
		
		/**
		 * The minimum number of degrees that the current direction is different from the previous
		 * one to record a change in the database
		 */
		public float minDegreesForChange = 10;
		/**
		 * Amount to adjust the rolling average compass value per ms
		 */
		public float compassAdjustmentPerMs = 1f / (15 * 1000);
	}
}
