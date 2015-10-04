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

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.GTG;

/**
 * Finds wireless connections. Don't let the bastards keep you down! 
 */
public class WirelessAdapter {
	private WifiManager wfm;
	private Context ctx;
	private IntentFilter filter;
	private Mode mode = Mode.NOOP;
	private Listener listener;
	

	private BroadcastReceiver wifiBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"Received intent "+intent);
			if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
			{
				//any scan that happens, regardless if we did it, we record.. we might as well

				//it's possible for this method to return null, so we have to be careful
				List<ScanResult> l = wfm.getScanResults();

				if(l != null)
					listener.notifyWifiScan(System.currentTimeMillis(), l);
				
				synchronized(WirelessAdapter.this)
				{
					goToSleep();
				}
				
				return;
			}
			
			//otherwise we got a wifi change, so we update our state and do whatever
			updateState();
		}

	};
		
	private Preferences prefs = new Preferences();

	//PERF: maybe we should not use it's own thread for this. Don't want to use a HandlerTimer since that
	//requires a UI thread and we may not be called by one
	private Runnable timeoutRunnable = new Runnable() {

		@Override
		public void run() {
			while(true)
			{
				//after the timeout period, regardless of where we are in the cycle, we
				//shutdown and noop
				synchronized(WirelessAdapter.this)
				{
					if(isRunning)
					{
						try {
							if(timeoutMs != 0)
							{
									WirelessAdapter.this.wait(timeoutMs);
							}
							else WirelessAdapter.this.wait();
						} catch (InterruptedException e) {
							throw new IllegalStateException(e);
						}
					}
					
					if(timeoutMs != 0 && System.currentTimeMillis() > timeoutMs || !isRunning)
					{
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"Wireless Timeout");
						goToSleep();
					}
					
					if(!isRunning)
						break;
				}
			}
		}
	};


	private boolean weEnabledWifi;
	private long timeoutMs;
	private Thread thread;
	private boolean isRunning = true;	
	
	public WirelessAdapter(Context ctx, Listener listener)
	{
		this.ctx = ctx;
		this.listener = listener;
		wfm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
		filter = new IntentFilter();
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

		ctx.registerReceiver(wifiBroadcastReceiver, filter);
		
		thread =new Thread(timeoutRunnable, "Wireless Adapter Timeout Thread");
		thread.setDaemon(true);
		thread.start();
		
	}
	
	public synchronized void shutdown()
	{
		isRunning =  false;
		this.notify();
		ctx.unregisterReceiver(wifiBroadcastReceiver);
	}
	
	private synchronized void goToSleep() {
		//if we enabled wifi, we shut it off
		if(weEnabledWifi)
		{
			wfm.setWifiEnabled(false);
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"Shutdown WIFI");
		
			weEnabledWifi = false;
		}

		mode = Mode.NOOP;
		timeoutMs = 0;

		/* ttt_installer:remove_line */Log.d(GTG.TAG,"Go To sleep");
	}
	
	protected synchronized void updateState() {
		if(mode == Mode.NOOP) //if we are not doing anything
			return; //then quit immediately
		
		int wifiState = wfm.getWifiState();
		
		if(wifiState == WifiManager.WIFI_STATE_DISABLING ||
				wifiState == WifiManager.WIFI_STATE_UNKNOWN ||
				wifiState == WifiManager.WIFI_STATE_ENABLING)
		{
			//if the wifi is in a changing state, lets wait until it is in a final
			//state before doing anything (we will be called back when the state changes)
			return;
		}
		else if(wifiState != WifiManager.WIFI_STATE_ENABLED &&
				wifiState != WifiManager.WIFI_STATE_DISABLED)
		{
			Log.e(GTG.TAG, "Got a weird, unknown state, "+wifiState); //TODO 3: what to do for errors
			goToSleep();//give up
			return;
		}
		
		if(mode == Mode.START)
		{
			//wifi is shutdown, so we enable it
			if(wifiState != WifiManager.WIFI_STATE_ENABLED)
			{
				mode = Mode.ENABLING_WIFI;
				if(!wfm.setWifiEnabled(true)) //if we can't enable wifi
				{
					Log.e(GTG.TAG, "Can't enable wifi!"); //TODO 3: What to do about errors
					goToSleep();//give up
					return;
				}
				
				//note that we enabled it. We will only disable it if we enabled it ourselves
				//of course this is not perfect because someone else could enable at about the
				//same time we did (not this code but some other code in some app, probably far far away), 
				//but that is the best we can do with the API we have
				weEnabledWifi = true;
				
				//wait until it is enabled (we will be called back when this happens)
				return; 
			}
			
			//the state is already enabled, so we can go ahead and scan
			mode = Mode.ENABLING_WIFI;
		}
		
		if(mode == Mode.ENABLING_WIFI)
		{
			//if it is enabled, lets go ahead and scan
			if(wifiState == WifiManager.WIFI_STATE_ENABLED)
			{
				mode = Mode.BEFORE_SCAN_START;
			}
			else //it must be disabled, let's leave it alone and let it sort itself out
				//if necessary, the timer will change us back to a noop
			{
				return;
			}
		}

		if(mode == Mode.BEFORE_SCAN_START)
		{
			if(wifiState == WifiManager.WIFI_STATE_DISABLED)
			{
				//something strange happened... we should be ready to scan. so let's give up
				//the timer will shut us off
				return;
			}
			
			//now try to scan
			mode = Mode.AFTER_SCAN_START;
			if(!wfm.startScan())
			{
				Log.e(GTG.TAG, "Can't start a scan!"); //TODO 3: What to do about errors
				
				//we can't start a scan, so lets give up. The timer will shut us off
				return;
			}
			
			//wait for the scan results to be retrieved... BroadCastReceiver.onReceive will handle this
			return;
		}
		
		if(mode == Mode.AFTER_SCAN_START)
		{
			//noop... we just wait until the scan is complete (which is handled by BroadCastReciever.onReceive)
		}
	}

	public synchronized void doScan()
	{
//		Log.d(GTG.TAG,"Do scan");
		timeoutMs = System.currentTimeMillis() + prefs.wirelessDoScanTimeoutMs;
		notify(); // start the timeout timer;
		
		if(mode == Mode.NOOP)
			mode = Mode.START;
		
		updateState();
	}
	
	public static interface Listener
	{
		void notifyWifiScan(long timeMs, List<ScanResult> scanResults);
	}
	
	public enum Mode {
		START, NOOP, AFTER_SCAN_START, ENABLING_WIFI, BEFORE_SCAN_START}

	public static class Preferences implements AndroidPreferences
	{



		/**
		 * Amount of time before we give up scanning and realize that android isn't going
		 * to call us back.
		 */
		public long wirelessDoScanTimeoutMs = 30000;
	}
	
}
