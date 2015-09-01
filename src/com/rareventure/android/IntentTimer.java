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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/**
 * A timer that will continue to work even if the phone sleeps
 * It has two modes, one will allow the phone to sleep, and the other will keep it awake 
 * (but leave the screen off)
 */
public class IntentTimer {

	private PendingIntent sender;

	private AlarmManager alarmManager;

	private WakeLock wakeLock;
	
//	private BufferedWriter debugOut;
	
	/**
	 * 
	 * @param component class type can either be a BroadcastReceiver, a Service, or an Activity
	 */
	public IntentTimer(Context context, Class<?> componentClass) {
		Intent intent = new Intent(context, componentClass);
	    sender = PendingIntent.getBroadcast(context,
	            0, intent, 0);

	    alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
	    
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK , 
        		"rareventure intent timer");
        
//        try {
//			debugOut = new BufferedWriter(new FileWriter("/sdcard/wake_lock_debug.txt", true));
//			writeDebug("IntentTimer constructor");
//		} catch (IOException e) {
//			Log.e(GTG.TAG, "Couldn't out wake lock debug file",e);
//		}
	}
	
	/**
	 * This will turn off the wake lock and allow the phone to sleep (if it wants)
	 * It will send an alert to the component class and it is then up to the component
	 * class to decide what to do. The wakeLock will still be released when we wake
	 * up.
	 * 
	 * @param timeToWake time this all goes down
	 */
	public void sleepUntil(long timeToWake)
	{
//		writeDebug("sleep until "+new Date(timeToWake));
//		if(wakeLock.isHeld())
//		{
//			writeDebug("releasing wake lock for sleep");
//			wakeLock.release();
//		}
		
        alarmManager.set(AlarmManager.RTC_WAKEUP, timeToWake, sender);
	}	
	
	public void cancel()
	{
//		writeDebug("cancel");
        alarmManager.cancel(sender);
	}

	/**
	 * Acquire a lock on the cpu to stay awake
	 */
	public void acquireWakeLock() {
//		writeDebug("trying to acquire wake lock");
		if(!wakeLock.isHeld())
		{
//			try {
//				writeDebug("acquiring wake lock");
//				debugOut.flush();
//			} catch (IOException e) {
//				Log.e(GTG.TAG, "Couldn't out wake lock debug file",e);
//			}
			wakeLock.acquire();
		}
	}

	public void releaseWakeLock() {
//		writeDebug("wake lock release called");
		if(wakeLock.isHeld())
		{
//			writeDebug("releasing wake lock");
			wakeLock.release();
		}
	}

//	public synchronized void writeDebug(String string) {
//		try {
//		/* ttt_installer:remove_line */Log.i("IntentTimer", string);
//			debugOut.write(new Date().toString()+" ");
//			debugOut.write(string);
//			debugOut.write("\n");
//			debugOut.flush();
//		} catch (IOException e) {
//			Log.e(GTG.TAG, "Couldn't out wake lock debug file",e);
//		}
//	}

}
