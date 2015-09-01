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
package com.rareventure.gps2;

import java.io.DataOutputStream;
import java.io.IOException;

import android.util.Log;

import com.rareventure.android.GpsReader;
import com.rareventure.android.IntentTimer;
import com.rareventure.android.TestUtil;
import com.rareventure.android.WriteConstants;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;

/**
 * Decides when to turn on gps and when not to, and the locations to store, etc.
 * 
 * TODO 3: if phone is turned on within a few seconds, regardless if it started moving or not, and
 *  it was in a stopped condition, it is considered still stopped.
 * TODO 3: handle multiple readings when stopped. What do we do if the reading differ significantly? (should we send statistics so
 *   we know if the general public is having problems?)
 * TODO 3: for exceptions in general, should we send stats?
 * TODO 3: store timeout stats so that we can determine how long gps takes?
 * TODO 3: failsafe... if thread is looping continously, tell us!!!
 * TODO 2.2: when phone is plugged, gps usage can be hoggy
 */
public class GpsTrailerGpsStrategy {
	/**
	 * Set to the next time we must call update(). When we just start, we want to call it immediately
	 */
	private long nextSignificantEvent = 0;

	private GpsBatteryManager gpsBatteryManager = new GpsBatteryManager();
	
	public static Preferences prefs = new Preferences();

	private GpsReader gpsReader;

	private DataOutputStream os;

	public enum State { 
		STOPPED, MOVING;
	}
	public State state = State.MOVING;
	
	/**
	 * The start time of current (or last) stopped period.
	 */
	private long stoppedStartMs;

	private IntentTimer intentTimer;

	
	/**
	 * 
	 * TODO 3: the following is a little bizarre
	 * @param gpsReader Turns gps reader on and off, but does not read directly from it!
	 */
	public GpsTrailerGpsStrategy(DataOutputStream os, GpsReader gpsReader, IntentTimer intentTimer)
	{
		this.os = os;
		this.gpsReader = gpsReader;
		this.intentTimer = intentTimer;
	}
	
	private boolean isShutdownRequested;

	protected boolean isShutdown;

	private int successfulGpsAttempts;

	private int gpsAttempts;
	
	/**
	 * Called when the gpsReader is ready to go
	 */
	public void start()
	{
		gpsBatteryManager.start();
	}
	

	/**
	 * Notify that the user has stopped moving based on the accelerometer readings
	 * @param time time when first stopped (which may be in the past)
	 */
	public void stopped(long time) {
		
		boolean callUpdate = false;
		
		synchronized(this)
		{
			if(state == State.MOVING)
			{
				state = State.STOPPED;
				
				stoppedStartMs = time;
				
				callUpdate = true;
			}
		}

		//TODO 2.5: Incorporate stopped and moving into gps times?
//		if(callUpdate)
//			gpsBatteryManager.update();
	}
	
	/**
	 * Notify that the user has started moving
	 * @param startTime 
	 */
	public void moving() {
		boolean callUpdate = false;
		
		synchronized(this)
		{
			if(state == State.STOPPED)
			{
				state = State.MOVING;
				
				callUpdate = true;
			}
		}
		
//		if(callUpdate)
//			update();
	}
	
	public void gotReading()
	{
		synchronized(this)
		{
			gpsBatteryManager.lastGpsReadingMs = System.currentTimeMillis();
			
			gpsBatteryManager.lastReadingSuccessful = true;
			this.notify();
			successfulGpsAttempts++;
		}
	}
	
	private void writeUpdateStatus() {
		
		synchronized(TestUtil.class)
		{
			try {
				long time = System.currentTimeMillis();
				TestUtil.writeMode(os, WriteConstants.MODE_WRITE_STRATEGY_STATUS);
				TestUtil.writeBoolean("gpsOn", os, this.gpsBatteryManager.gpsOn);
				TestUtil.writeTime("gpsAttemptStartedMs", os, this.gpsBatteryManager.gpsAttemptStartedMs);
				TestUtil.writeTime("gpsAttemptEndedMs", os, this.gpsBatteryManager.gpsAttemptEndedMs);
				TestUtil.writeTime("lastGpsReadingMs", os, this.gpsBatteryManager.lastGpsReadingMs);
				TestUtil.writeTime("lastGpsStatsUpdate", os, this.gpsBatteryManager.lastGpsStatsUpdate);
				TestUtil.writeLong("nextSignificantEventTime", os, this.nextSignificantEvent - time);
				TestUtil.writeLong("freeGpsTimeMs", os, this.gpsBatteryManager.calcFreeGpsTimeMs(time));
				TestUtil.writeLong("totalTimeGpsRunningMs", os, this.gpsBatteryManager.totalTimeGpsRunningMs);
				TestUtil.writeLong("totalTimeNotRunningGpsMs", os, time 
						- this.gpsBatteryManager.startTimeMs - this.gpsBatteryManager.totalTimeGpsRunningMs);
				TestUtil.writeLong("totalSuccessfulGpsTries", os, successfulGpsAttempts);
				TestUtil.writeLong("totalGpsTries", os, gpsAttempts);
				TestUtil.writeLong("longTimeWanted", os, this.desireManager.longTimeWanted);
				TestUtil.writeLong("shortTimeWanted", os, this.desireManager.shortTimeWanted);
				Log.d(GTG.TAG, "gps use perc is "+prefs.batteryGpsOnTimePercentage );
			} catch (IOException e) {
	//			e.printStackTrace();
				throw new IllegalStateException(e);
			} 
		}
	}

	public class GpsBatteryManager
	{
		public boolean lastReadingSuccessful;

		/**
		 * The time we started the program in general. NOTE: this number is fudged a little
		 * to faciliate starting gps right away
		 */
		private long startTimeMs;
		
		/**
		 * Last time we updated the gps stats
		 */
		private long lastGpsStatsUpdate;
		
		/**
		 * The start of the last gps session (see also gpsOn)
		 */
		private long gpsAttemptStartedMs;
		
		/**
		 * The end of the last gps session (see also gpsOn)
		 */
		private long gpsAttemptEndedMs;
		
		/**
		 * The last time a successful gps reading was taken.
		 */
		private long lastGpsReadingMs;

		/**
		 * Total time gps has been running since we started
		 */
		private long totalTimeGpsRunningMs;
		
		private boolean gpsOn;
		
		/**
		 * This method must be called from the strategy thread only (to prevent
		 * deadlocks). If you want to update, synchronize notify the strategy thread
		 * @param currTimeMs currentTime
		 * 
		 * @return whether to turn gps on or not (which must be turned on or off in 
		 *  a non synchronized block
		 */
		protected boolean updateFromStrategyThreadOnly(long currTimeMs) {
			//first lets update the stats so far
			if(gpsOn)
			{
				//find out how long the gps has been running since we last checked
				long startTime = lastGpsStatsUpdate >  gpsAttemptStartedMs ? lastGpsStatsUpdate : 
					gpsAttemptStartedMs;
				totalTimeGpsRunningMs += currTimeMs - startTime;
			}
			
			//we don't want to allow too much gps time. This is to prevent wasting the battery needlessly if we 
			// had a stroke of luck and we're able to get the gps time very easily
			//Were basically truncating the amount of time we have allocated to use gps
			if((currTimeMs - startTimeMs) * prefs.batteryGpsOnTimePercentage - totalTimeGpsRunningMs >
				prefs.maxGpsTimeMs)
			{
				//set it so we have prefs.maxGpsTimeMs of time left to allocate
				totalTimeGpsRunningMs = (long)((currTimeMs - startTimeMs) * prefs.batteryGpsOnTimePercentage) 
				- prefs.maxGpsTimeMs + 1;
			}
			
			//now lets see what we need to do
			//the output of this is wantedGpsStatus (although we do exit in the following code block
			//if there is nothing to do)
			long gpsTimeAvailable = calcFreeGpsTimeMs(currTimeMs);
			
			if(gpsOn)
			{
				long currentTimeGpsRunning = currTimeMs - gpsAttemptStartedMs;
				
				//we want to turn gps off (because we were unsuccessful)
				if(currentTimeGpsRunning >= desireManager.currTimeWanted || gpsTimeAvailable <= 0 || lastReadingSuccessful)
				{
					gpsAttemptEndedMs = currTimeMs;
					
					if(lastReadingSuccessful)
					{
						desireManager.updateDesiresForSuccessfulReading(gpsAttemptEndedMs - gpsAttemptStartedMs);
						lastReadingSuccessful = false;
					}
					else
						desireManager.updateDesiresForUnsuccessfulReading(gpsTimeAvailable);
					
					nextSignificantEvent = desireManager.waitTimeMs + currTimeMs;
					
					return false;
				}
				else //we want to keep gps on
				{
					nextSignificantEvent = desireManager.currTimeWanted - currentTimeGpsRunning + currTimeMs;
					
					return true;
				}
			}
			else // gps is off
			{
				long currentTimeWaiting = currTimeMs - gpsAttemptEndedMs;
				
				if(currentTimeWaiting >= desireManager.waitTimeMs)
				{
					//we want to turn it on
					gpsAttemptStartedMs = currTimeMs;
					nextSignificantEvent = desireManager.currTimeWanted + currTimeMs;
					
					gpsAttempts++;
					
					return true;
				}
				else
				{
					nextSignificantEvent = desireManager.waitTimeMs + gpsAttemptEndedMs;
					
					return false;
					
				}
			}
		} //end GpsBatteryMeter.update()

		public long calcFreeGpsTimeMs(long currTimeMs) {
			return (long) (prefs.batteryGpsOnTimePercentage 
					* (currTimeMs - startTimeMs));
			}

		public void start() {
			desireManager.updateDesiresForStart();
			
			//we start with a 5 minute leeway so the code can turn on gps right away, rather than waiting
			//TODO 3: compute this better (later -- I'm not sure what this means???)
			gpsAttemptEndedMs = startTimeMs = System.currentTimeMillis() - prefs.extraTimeForStartMs;
			strategyThread.start();

			
		}
		
		//PERF: consider consolidating thread into a looper or a HandlerTimer or something
		private Thread strategyThread = new Thread() {

			public void run()
			{
				intentTimer.acquireWakeLock();
				try {	
					boolean wantGps = true;
					
					while(!isShutdownRequested)
					{
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"About to enter synchronization block");
						//this thread is responsible for turning on and off gpsreader
						//as well as updating nextsignificantevent and calling the desireManager
						//to determine how long to wait and how much time to use for each gps reading
						synchronized(GpsTrailerGpsStrategy.this)
						{
							
							long time = System.currentTimeMillis();
							
							if(nextSignificantEvent > time)
							{
								
								/* ttt_installer:remove_line */Log.d(GTG.TAG,"About to wait for "+(nextSignificantEvent - time));
								
								//we only turn off our wake lock if we aren't running gps
								//note, not sure if this is necessary, but even if it works for my
								//phone, it may not work for other phones, so to be sure, we
								//keep the cpu on
								//co: tim trying this off to see what happen
//								if(!wantGps)
								{
									intentTimer.sleepUntil(nextSignificantEvent);
									/* ttt_installer:remove_line */Log.d(GTG.TAG,"Turned off wake lock");
								}

								if(isShutdownRequested)
									return;
								
								GpsTrailerGpsStrategy.this.wait(nextSignificantEvent - time );
								time = System.currentTimeMillis();
								
								intentTimer.acquireWakeLock();

								/* ttt_installer:remove_line */Log.d(GTG.TAG,"Acquired wake lock, done waiting for "+(nextSignificantEvent - time));
							}
							
							if(isShutdownRequested)
								break;
							/* ttt_installer:remove_line */Log.d(GTG.TAG,"About to update strategy");
							wantGps = updateFromStrategyThreadOnly(time);
							lastGpsStatsUpdate = time;
						}
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"About to update gpsReader to "+wantGps);
						
						//we don't want deadlocks, so we keep this outside of the synchronized loop
						if(wantGps)
							gpsReader.turnOn();
						else
							gpsReader.turnOff();
						
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"About to set gpsOn and writeUpdateStatus");
						synchronized(GpsTrailerGpsStrategy.this)
						{
							gpsOn = wantGps;
							writeUpdateStatus();
						}
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"Looping while not shutdown");
						
					}
					
					/* ttt_installer:remove_line */Log.d(GTG.TAG,"Gps Strateg is shutdown");
				} catch (Exception e)
				{
					e.printStackTrace();
					try {
						TestUtil.writeException(os ,e);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				finally
				{
					intentTimer.releaseWakeLock();
				}
				
				synchronized(GpsTrailerGpsStrategy.this)
				{
					GpsTrailerGpsStrategy.this.isShutdown = true;
					GpsTrailerGpsStrategy.this.notify();
				}
			} //end run

//			private void writeGpsStrategyThreadStatus(long timeToSleep, byte mode) {
//				if(os == null)
//					return;
//				
//				synchronized(TestUtil.class)
//				{
//					try {
//						TestUtil.writeMode(os, WriteConstants.MODE_WRITE_STRATEGY_STATUS);
//						TestUtil.writeTime("currTime", os, System.currentTimeMillis());
//						TestUtil.writeLong("timeToSleep", os, timeToSleep);
//						TestUtil.writeByte("mode, 2=update, 1=sleep", os, mode);
//					} catch (IOException e) {
//			//			e.printStackTrace();
//						throw new IllegalStateException(e);
//					} 
//				}
//
//			}
//			
		};

	} // end class GpsBatteryMeter
	
	public static class Preferences implements AndroidPreferences
	{


		/**
		 * The amount of extra time to allow the gps reading to go if
		 * we really want to have the data
		 */
		public int gpsBoostMs; //TODO 3: fix this

		/**
		 * The maximum time we are allowed to have to allocate.
		 * We will truncate to this time.
		 * <p>
		 * If we had a stroke of luck and were able to take many readings
		 * with a small amount of time, we don't want to waste the rest
		 * if we obviously can't take a measurement.
		 * 
		 * This is not superseced by gpsTimeWantedMaxMs because if we 
		 * are having a stroke of luck, this will bleed off excess gps time
		 * that we otherwise use when gps readings are not so easy to get. 
		 * 
		 * This should not be less than maxLongTimeWantedMs
		 */
		public long maxGpsTimeMs = 30 * 60 * 1000;

		/**
		 * Max time the gps should be as a percentage of total time
		 */
		public float batteryGpsOnTimePercentage = .10f;

		/**
		 * Minimum time that the accelerometer is stopped before we consider the user to 
		 * have actually rested 
		 */
		public long minStopTimeForGpsReadingMs = 60*1000;

		/**
		 * Amount of time we want initially for short period readings
		 */
		public long initialShortTimeWantedMs = 15*1000 ;

		public long initialLongTimeWanted = 2 * 60 * 1000;

		/**
		 * One to one ratio between short time and spare time multiplier
		 */
		public float spareTimeMultiplier = 1f;

		/**
		 * Multiplier to reduce short time after a successful gps reading
		 */
		public float shortTimeSuccessfulMultiplier = .9f;

		/**
		 * Multiplier to lengthen short time after an unsuccessful gps reading
		 */
		public float shortTimeUnsuccessfulMultipler = 1.1f;

		/**
		 * Minimum short time wanted
		 */
		public long shortTimeMinMs = 5*1000;

		/**
		 * Minimum short time wanted compared to last successful reading.
		 * If the last gps was successful and the short time falls below 
		 * the amount of time it took to read the last gps multiplied by
		 * this percentage, we set it to this minimum. 
		 */
		public float minReadingTimeMultipler = 1.3f;

		/**
		 * The minimum time a long gps measurement can be as a multiplier
		 * of the last successful measurement. The long
		 * gps measurement will be reset here when a successful reading
		 * was taken
		 */
		public float minLongTimeOfShortTimeMultiplier = 3f;

		/**
		 * The amount to multiply longTimeWanted when a long run was unsuccessful
		 */
		public float longTimeMultiplier = 2f;

		/**
		 * Maximum time a long gps try can be. This should not be greater than maxGpsTimeMs
		 */
		public long maxLongTimeWantedMs = 20 * 1000 * 60;

		/**
		 * Amount of time to increase the short time multiplier on an
		 * unsuccessful measurement
		 */
		public float shortTimeUnsuccessfulMultiplier = 1.1f;

		/**
		 * The maximum time a short run can be
		 */
		public long shortTimeMaxMs = 30 * 1000;

		/**
		 * Extra time for when we start so we turn on gps right away
		 */
		public long extraTimeForStartMs = (long) (initialShortTimeWantedMs * (this.spareTimeMultiplier + 1) / batteryGpsOnTimePercentage) + 1;

	}

	public void shutdown() {
		synchronized(this)
		{
			isShutdownRequested = true;
			notify();
			
			while(!isShutdown)
				try {
					wait();
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
		}
		
		
	}
	
	private DesireManager desireManager = new DesireManager();
	
	private class DesireManager
	{
		private long shortTimeWanted;
		private long longTimeWanted;
		
		/**
		 * The current amount of time wanted
		 */
		private long currTimeWanted;
		
		/**
		 * time to wait until next reading (from previous reading)
		 */
		private long waitTimeMs;

		private void updateDesiresForStart()
		{
			shortTimeWanted = prefs.initialShortTimeWantedMs;
			longTimeWanted = prefs.initialLongTimeWanted;
			
			currTimeWanted = shortTimeWanted;
			waitTimeMs = 0;
		}
		
		private void updateDesiresForSuccessfulReading(long readingTimeMs)
		{
			//if the reading time is less than the current short time
			if(readingTimeMs < shortTimeWanted)
			{
				//we divide out shortTimeUnsuccessfulMultipler since we always update
				//short time wanted as if it failed whenever we start
				shortTimeWanted = (long) (shortTimeWanted * prefs.shortTimeSuccessfulMultiplier
				 / prefs.shortTimeUnsuccessfulMultipler)+1 ;
				if(shortTimeWanted < prefs.shortTimeMinMs)
					shortTimeWanted = prefs.shortTimeMinMs;
				if(shortTimeWanted < readingTimeMs * prefs.minReadingTimeMultipler)
					shortTimeWanted = (long) (readingTimeMs * prefs.minReadingTimeMultipler)+1;
			}
			
			longTimeWanted = (long) (prefs.minLongTimeOfShortTimeMultiplier * shortTimeWanted) + 1;
			
			waitTimeMs = calculateAbsTimeNeeded((long) (shortTimeWanted * prefs.spareTimeMultiplier + shortTimeWanted)+1);
			currTimeWanted = shortTimeWanted;
		}
	
		/**
		 * Updates what this strategy wants to do next given
		 * that the last gps reading was unsuccessful
		 */
		private void updateDesiresForUnsuccessfulReading(long spareReadingTime) {
			long totalShortTimeMs = (long) (shortTimeWanted * prefs.spareTimeMultiplier + shortTimeWanted)+1;
			waitTimeMs = calculateAbsTimeNeeded(totalShortTimeMs);
			
			//if there is enough time to do a long run
			if(totalShortTimeMs + spareReadingTime >= longTimeWanted)
			{
				currTimeWanted = longTimeWanted;
				
				//update long time wanted as if the current run will fail
				longTimeWanted = (long) (longTimeWanted * prefs.longTimeMultiplier) + 1;
				if(longTimeWanted > prefs.maxLongTimeWantedMs )
					longTimeWanted = prefs.maxLongTimeWantedMs;
			}
			else //do a short run
			{
				currTimeWanted = shortTimeWanted;
				
				//update short time wanted as if the current run will fail
				shortTimeWanted *= prefs.shortTimeUnsuccessfulMultiplier;
				if(shortTimeWanted > prefs.shortTimeMaxMs)
					shortTimeWanted = prefs.shortTimeMaxMs;
				
			}
			
		}

		/**
		 * Simply calculates the total time needed to wait for a 
		 * certain amount of gps time, ignoring any current data.
		 * i.e. regardless of the situation, this will always return
		 * the same value
		 * 
		 * @param timeWanted
		 * @return
		 */
		private long calculateAbsTimeNeeded(long timeWanted) {
			return (long) (timeWanted / prefs.batteryGpsOnTimePercentage - timeWanted + 1);
		}
	}

	public void notifyWoken() {
		//make sure we stay awake until we want to sleep again
//		intentTimer.writeDebug("gps manager notify woken");
		intentTimer.acquireWakeLock();
		synchronized(this)
		{
			this.notify();
		}
	}

}
