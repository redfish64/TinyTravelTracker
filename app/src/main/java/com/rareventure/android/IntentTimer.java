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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import com.rareventure.gps2.GTG;
import com.rareventure.gps2.R;
import com.rareventure.util.DebugLogFile;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;

import pl.tajchert.nammu.Nammu;

/**
 * A timer that will continue to work even if the phone sleeps
 * It has two modes, one will allow the phone to sleep, and the other will keep it awake 
 * (but leave the screen off)
 */
public class IntentTimer {

	private PendingIntent sender;

	private AlarmManager alarmManager;

	private WakeLock wakeLock;

	/**
	 * 
	 * @param componentClass class type can either be a BroadcastReceiver, a Service, or an Activity
	 */
	public IntentTimer(Context context, Class<?> componentClass) {
		Intent intent = new Intent(context, componentClass);
	    sender = PendingIntent.getBroadcast(context,
	            0, intent, 0);

	    alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
	    
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK , 
        		"rareventure intent timer");


	}
	
	/**
	 * This will turn off the wake lock and allow the phone to sleep (if it wants)
	 * It will send an alert to the component class and it is then up to the component
	 * class to decide what to do. The wakeLock will still be released when we wake
	 * up.
	 * 
	 * @param timeToWakeFromPhoneBoot time to wake, *this is in millis since the phone was started*
	 */
	public synchronized void sleepUntil(long timeToWakeFromPhoneBoot)
	{
		DebugLogFile.log("sleep until "+new Date(System.currentTimeMillis() + timeToWakeFromPhoneBoot - SystemClock.elapsedRealtime()));
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
			SetAlarmMarshmallow.setAlarm(alarmManager,AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWakeFromPhoneBoot, sender);
		else
	        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWakeFromPhoneBoot, sender);

		if(wakeLock.isHeld())
		{
			DebugLogFile.log("releasing wake lock for sleep");
			wakeLock.release();
		}
	}

	@TargetApi(Build.VERSION_CODES.M)
	private static class SetAlarmMarshmallow {
		public static void setAlarm(AlarmManager alarmManager, int elapsedRealtimeWakeup, long timeToWakeFromPhoneBoot, PendingIntent sender)
		{
			alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWakeFromPhoneBoot, sender);
		}
	}
	
	public synchronized void cancel()
	{
		DebugLogFile.log("cancel");
        alarmManager.cancel(sender);
	}

	/**
	 * Acquire a lock on the cpu to stay awake
	 */
	public synchronized void acquireWakeLock() {
		if(!wakeLock.isHeld())
		{
			if(Nammu.checkPermission(Manifest.permission.WAKE_LOCK))
			{
				DebugLogFile.log("acquiring wake lock");
				wakeLock.acquire();
				DebugLogFile.log("acquired wake lock");
			}
			else
				DebugLogFile.log("could not acquire wake lock");
		}
//		else
//			writeDebug("acquireWakeLock() wake lock already held");
	}

	public synchronized void releaseWakeLock() {
		if(wakeLock.isHeld())
		{
			DebugLogFile.log("releasing wake lock");
			wakeLock.release();
			DebugLogFile.log("released wake lock");
		}
//		else
//			writeDebug("releaseWakeLock(), wake lock already released");
	}

}
