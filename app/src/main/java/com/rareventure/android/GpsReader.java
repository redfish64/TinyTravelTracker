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

import java.io.DataOutputStream;
import java.io.IOException;

import com.mapzen.android.lost.api.LocationListener;
import com.mapzen.android.lost.api.LocationRequest;
import com.mapzen.android.lost.api.LocationServices;
import com.mapzen.android.lost.api.LostApiClient;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

public class GpsReader implements DataReader
{
	private final LostApiClient lostApiClient;
	private Object lock = new Object();
	private GpsProcessor gpsProcessor;
	private GpsDataBuffer gpsDataBuffer;
	
	private ProcessThread processThread;

	private boolean connected;
	private final LostApiClient.ConnectionCallbacks ccListener = new LostApiClient.ConnectionCallbacks() {
		@Override
		public void onConnected() {
			synchronized(lock) {
				connected = true;
				if (activateOnConnect) {
					activateOnConnect = false;
					activateGps();
				}
			}
		}

		@Override
		public void onConnectionSuspended() {

		}
	};
	
	private LocationListener locationListener = new LocationListener() {

		@Override
		public void onLocationChanged(Location location) {
			//store the location

			gpsDataBuffer.lat[gpsDataBuffer.rawReadIndex] = location.getLatitude();
			gpsDataBuffer.lon[gpsDataBuffer.rawReadIndex] = location.getLongitude();
			gpsDataBuffer.alt[gpsDataBuffer.rawReadIndex] = location.getAltitude();
			gpsDataBuffer.timeRead[gpsDataBuffer.rawReadIndex] = System.currentTimeMillis();

			synchronized(processThread.lock)
			{
				gpsDataBuffer.updateReadIndex();
				processThread.lock.notify();
			}

		}
	};

	private boolean gpsOn;
	
	private Looper looper;
	private String tag;
	private DataOutputStream os;
	private Context ctx;

	private LocationRequest request = LocationRequest.create()
			.setInterval(5000)
			.setSmallestDisplacement(5)
			.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

	public interface GpsProcessor {

		void processGpsData(double lon, double lat, double alt, long time);
	}

    public GpsReader(DataOutputStream os, Context ctx, 
    		GpsProcessor gpsProcessor, 
    		String tag, Looper looper)
    {
    	this.ctx = ctx;
    	this.os = os;
    	this.tag = tag;
    	this.gpsProcessor = gpsProcessor;
    	this.gpsDataBuffer = new GpsDataBuffer(16);
    	
    	this.looper = looper;

		lostApiClient = new LostApiClient.Builder(ctx).addConnectionCallbacks(ccListener).build();
		//TODO 1 onconnetlistener
		lostApiClient.connect();

    }
    
    
    @Override
    public void setProcessThread(ProcessThread processThread)
    {
    	this.processThread = processThread;
    }
    

	@Override
	public boolean canProcess() {
		return gpsDataBuffer.rawProcessIndex != gpsDataBuffer.rawReadIndex;
	}

	@Override
	public void process() {
		gpsProcessor.processGpsData(gpsDataBuffer.lon[gpsDataBuffer.rawProcessIndex],
				gpsDataBuffer.lat[gpsDataBuffer.rawProcessIndex],
				gpsDataBuffer.alt[gpsDataBuffer.rawProcessIndex],
				gpsDataBuffer.timeRead[gpsDataBuffer.rawProcessIndex]);
		
		if(os != null)
			writeTestData();

		gpsDataBuffer.updateProcessIndex();
	}
	
	@Override
	public void notifyShutdown() {
		turnOff();
	}

	private void writeTestData() {
		synchronized(TestUtil.class)
		{
			try {
				TestUtil.writeMode(os, WriteConstants.MODE_WRITE_GPS_DATA2);
		
				TestUtil.writeDouble("lat",os, gpsDataBuffer.lat[gpsDataBuffer.rawProcessIndex]);
		    	TestUtil.writeDouble("lon",os, gpsDataBuffer.lon[gpsDataBuffer.rawProcessIndex]);
		    	TestUtil.writeDouble("alt",os, gpsDataBuffer.alt[gpsDataBuffer.rawProcessIndex]);
		    	TestUtil.writeLong("time",os, gpsDataBuffer.timeRead[gpsDataBuffer.rawProcessIndex]);
			}
			catch(IOException e)
			{
				e.printStackTrace();
				throw new IllegalStateException(e); //punt but not a todo because this is test code
			}
		}        
	}

	private boolean activateOnConnect;

	private void activateGps()
	{
		synchronized(lock) {

			if (!connected) {
				activateOnConnect = true;
			} else
				LocationServices.FusedLocationApi.requestLocationUpdates(lostApiClient, request, locationListener);
		}
	}

	private void deactivateGps() {
		synchronized(lock) {
			activateOnConnect = false;
			LocationServices.FusedLocationApi.removeLocationUpdates(lostApiClient, locationListener);
		}
	}

	public void turnOn() {
    	synchronized(lock)
    	{
			//TODO 2.2 reenable check for not having viable gps
//			if(!lm.isProviderEnabled( LocationManager.GPS_PROVIDER))
//			{
//				return;
//			}
    		if(gpsOn)
    			return; //already on
			gpsOn = true;

			activateGps();
    	}
	}
	
	public void turnOff() {
    	synchronized(lock)
    	{
    		if(!gpsOn)
    			return; //already off
			gpsOn = false;
			deactivateGps();
    	}

    	//TODO 2: add passive gps somehow

    	//TODO x1: HACK ADDDS FAKE GPS LOCATION DATA
//    	Location l = new Location("test");
//    	l.setAltitude(9999);
//    	l.setLatitude(1);
//    	l.setLongitude(1);
//    	l.setBearing(0);
//    	this.locationListener.onLocationChanged(l);
	}

	public Preferences prefs = new Preferences();
	
	public static class Preferences implements AndroidPreferences
	{


		/**
		 * The time to set the internal android recurring timer for gps measurements.
		 */
		public long gpsRecurringTimeMs = 1000 * 60;
	}

	public boolean isGpsOn() {
		synchronized(lock)
		{
			return gpsOn;
		}
	}

}
