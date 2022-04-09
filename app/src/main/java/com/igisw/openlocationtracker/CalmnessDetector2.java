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

import android.hardware.SensorManager;
import android.util.Log;

import com.igisw.openlocationtracker.AndroidPreferenceSet.AndroidPreferences;


/**
 * Detects periods of calmness given accelerometer readings
 */
//TODO 3: maybe use a more fair ... look forward and backwards STOPPED and MOVING indicator. Yes,
// we'll have to nitfy the the clients later, but at least we get good data when we display it
// in the reviewer.
//TODO 3: Also consider this forward and back looking for the compass. With both the accelerometer
// and the compass forward and back, we may get better direction
public class CalmnessDetector2
{
	public float grav = SensorManager.GRAVITY_EARTH;
	public State state = State.MOVING;
	
	public static enum State { STOPPED, MOVING };
	
	public Preferences prefs = new Preferences();
	
	//TODO 3: An alternative may be to take an integral minus a certain level. So if the value is 8 we subtract 5 (constant) and use
	//3 as the height of our integral. (if below 5 we take it as zero)
	/**
	 * Time the force is above a constant value. These are taken over segments
	 */
	private int [] aboveGPeriods = new int [prefs.aboveGSegmentCount];
	
	/**
	 * The movement in G's compared to gravity calculated from the last accel reading
	 */
	private float lastG;
	
	private int rawAboveGIndex;
	
	/**
	 * The time of the last accel reading
	 */
	private long lastTime;
	
	/**
	 * The amount of time spent above the movement threshold in the last period
	 */
	private int totalAboveG;
	public long currSegmentStartMs;
	
	public CalmnessDetector2()
	{
	}

	public boolean processAccelData(float x, float y, float z, long time) {
		//note that we are only called when the sensor *has changed*. So if it is sitting on a pile of sand for a half hour
		//in the basement, it might not notify us of any change.
		//So we have to assume that the last reading has been that value for the time since we are now notified.
		//Because so, we use lastG in our calculations and time - lastTime to indicate the duration it 
		//was at that value
		
		float g = (float) Math.sqrt(x * x + y * y + z * z);
		
		//if we just started
		if(lastTime == 0)
		{
			//skip trying to update the state
			currSegmentStartMs = lastTime = time;
			lastG = g;
			
			return false;
		}
		
		//sometimes time is equal to lastTime 
		if(time <= lastTime)
		{
//			Log.e("GPS", "updateState: lastTime is after time??? time "+time+" lastTime "+lastTime);
			return false;
		}
		
		
		
		boolean result = updateState(lastTime, time);
		
		lastG = g;

		grav = lastG * (time - lastTime) * prefs.gravitationCenterUpdateConstant 
		+ grav * (1f - (time - lastTime) * prefs.gravitationCenterUpdateConstant);
		
		lastTime = time;
		
		return result;
		
	}
	
	private boolean updateState(long lastTime, long time)
	{
		//TODO 3: measurements are coming every quarter of a second, can we (should we) adjust the sample size based on the data received?
		
		//if we are above the force detection
		boolean aboveG = getForce() > prefs.minGMovementDetector;
		
		long timeOfMeasurement = time - lastTime;
		
		//TODO 3: we are sometimes getting in an infinite loop in the while below. I used to think it may be there is just a lot of difference
		// between time and last time, but now I think its because lastTime sometimes equals time. Verify this
		if(timeOfMeasurement > 600000)
		{
			Log.e("GPS", "updateState: too long of a difference, timeOfMeasurement="+timeOfMeasurement+", time="+time+", lastTime="+lastTime);
			return false;
		}
		int hackCount = 0;
		
		//note that lastTime should always be later than currSegmentStartMs
		
		while(timeOfMeasurement > 0)
		{
			long startTimeMs = currSegmentStartMs > lastTime ? currSegmentStartMs : lastTime;
			long endTimeMs = currSegmentStartMs + prefs.aboveGSegmentSizeMs < time ? currSegmentStartMs + prefs.aboveGSegmentSizeMs
				: time;
		
			if(aboveG)
			{
				this.totalAboveG += endTimeMs - startTimeMs;
				this.aboveGPeriods[rawAboveGIndex] += endTimeMs - startTimeMs;
			}
			
			timeOfMeasurement -= endTimeMs - startTimeMs;
			
			
			//E/GPS     (11575): updateState: took 1000 tries, don't know what the heck, timeOfMeasurement=1159553, time=1336878214452, lastTime=1336878214213 startTime: 1336878215609 endTime: 1336878214452
//			E/GPS     (11575): updateState: took 1000 tries, don't know what the heck, timeOfMeasurement=698854, time=1336878214912, lastTime=1336878214452 startTime: 1336878215609 endTime: 1336878214912
//			E/GPS     (11575): updateState: took 1000 tries, don't know what the heck, timeOfMeasurement=457153, time=1336878215153, lastTime=1336878214912 startTime: 1336878215609 endTime: 1336878215153
//			E/GPS     (11575): updateState: took 1000 tries, don't know what the heck, timeOfMeasurement=216672, time=1336878215393, lastTime=1336878215153 startTime: 1336878215609 endTime: 1336878215393
			//HACK
			if(hackCount++ > 1000)
			{

				Log.e("GPS", "updateState: took 1000 tries, don't know what the heck, timeOfMeasurement="+timeOfMeasurement+
						", time="+time+", lastTime="+lastTime+" startTime: "+startTimeMs+" endTime: "+endTimeMs);
				return false;
			}
			
			//if this measurement continues beyond the segment
			if(currSegmentStartMs + prefs.aboveGSegmentSizeMs <= time)
			{
				//move to the next one
				rawAboveGIndex = (rawAboveGIndex + 1) % prefs.aboveGSegmentCount;
				currSegmentStartMs += prefs.aboveGSegmentSizeMs;
				this.totalAboveG -= aboveGPeriods[rawAboveGIndex];
				aboveGPeriods[rawAboveGIndex] = 0;
			}
		}	
				
		long periodStartTimeMs = currSegmentStartMs - (prefs.aboveGSegmentCount - 1) * prefs.aboveGSegmentSizeMs;
		
		if(state == State.STOPPED)
		{
			//if we need to switch from stopped to moving
			if(((float)totalAboveG) / (time - periodStartTimeMs)  > prefs.minPercMovingToSwitchFromStoppedToMoving)
			{
				state = State.MOVING;
				return true;
			}
		
		}
		else if(state == State.MOVING)
		{
			//if we need to switch from moving to stopped
			if(((float)totalAboveG) / (time - periodStartTimeMs)  <= prefs.maxPercMovingToSwitchFromMovingToStopped)
			{
				state = State.STOPPED;
				return true;
			}
		}
		
		return false;
	}
	
	public float getForce()
	{
		//PERF: maybe used force squared? or some weird value?
		return (float)Math.abs(lastG - grav) / SensorManager.GRAVITY_EARTH;
	}
	
	public static class Preferences implements AndroidPreferences
	{

		/**
		 * Total number of segments (MUST be a power of 2)
		 */
		public int aboveGSegmentCount = 16;
		
		/**
		 * Size of each segment in milleseconds
		 */
		public int aboveGSegmentSizeMs = 60 * 1000 / aboveGSegmentCount;

		/**
		 * The maximum percentage of time moving 
		 * to switch from a moving state to a stopped state
		 */
		//TODO 3: optimize these values
		public float maxPercMovingToSwitchFromMovingToStopped = .005f;

		/**
		 * The minimum percentage of time moving to switch
		 * from a stopped state to a moving state (shoud be higher than
		 *   maxPercMovingToSwitchFromMovingToStopped)
		 */
		//TODO 3: optimize these values
		public float minPercMovingToSwitchFromStoppedToMoving = .01f;

		/**
		 * The minimum time necessary before we may switch from stopped to moving
		 */
		public long minTimeToSwitchFromStopToMovingMs = 500;

		/**
		 * The amount to update to adjust the gravitational to the current value per ms
		 */
		public float gravitationCenterUpdateConstant = 1f / (30 * 60 * 1000); //let it completely readjust over 30 minutes or so
		
		/**
		 * The amount of force in g's to indicate movement
		 */
		public float minGMovementDetector = .2f;
	}

}

