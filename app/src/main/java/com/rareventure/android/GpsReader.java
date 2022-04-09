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

import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

public class GpsReader implements DataReader
{
	private Object lock = new Object();
	private GpsProcessor gpsProcessor;
	private GpsDataBuffer gpsDataBuffer;
	
	private ProcessThread processThread;
	
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

		@Override
		public void onProviderDisabled(String provider) {
//			Log.d(GpsTrailerService.TAG,"GPS provider disabled: "+provider);
			GTG.alert( GTG.GTGEvent.ERROR_GPS_DISABLED);
		}

		@Override
		public void onProviderEnabled(String provider) {
//			Log.d(GpsTrailerService.TAG,"GPS provider enabled: "+provider);
			GTG.alert( GTG.GTGEvent.ERROR_GPS_DISABLED, false);
		}

		@Override
		public void onStatusChanged(String provider, int status,
				Bundle extras) {
		}
	};

	private LocationManager lm;
	
	private boolean gpsOn;
	
	private Looper looper;
	private String tag;
	private DataOutputStream os;
	private Context ctx;
	
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
    	
    	//TODO 3.2: handle multile levels of accuracy
    	//basically read from every gps system available
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        
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

	public void turn(boolean on) {
		if (on) {
			turnOn();
		} else {
			turnOff();
		}
	}

	public void turnOn() {
    	synchronized(lock)
    	{
//    		Log.d(GpsTrailerService.TAG,"Turning on gps");
			if(!lm.isProviderEnabled( LocationManager.GPS_PROVIDER))
			{
				GTG.alert( GTGEvent.ERROR_GPS_DISABLED);
				return;
			}
			if(ActivityCompat.checkSelfPermission(this.ctx, Manifest.permission.ACCESS_FINE_LOCATION)
					!= PackageManager.PERMISSION_GRANTED)
			{
				GTG.alert( GTGEvent.ERROR_GPS_NO_PERMISSION);
//				Log.d(GpsTrailerService.TAG,"Failed no permission");
				return;
			}

    		if(gpsOn) {
//				Log.d(GpsTrailerService.TAG,"Gps already on");
				return; //already on
			}
			gpsOn = true;

//			Log.d(GpsTrailerService.TAG,"Really turning on");
			Criteria criteria = new Criteria();
			criteria.setSpeedRequired(false);
			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			criteria.setAltitudeRequired(false);
			criteria.setBearingRequired(false);
			criteria.setCostAllowed(false);

			String providerName =  lm.getBestProvider(criteria, true);
			lm.requestLocationUpdates(providerName, prefs.gpsRecurringTimeMs, 0,
					locationListener, looper);
    	}
	}
	
	public void turnOff() {
    	synchronized(lock)
    	{
//			Log.d(GpsTrailerService.TAG,"Turning off gps");
    		if(!gpsOn)
    			return; //already off
			gpsOn = false;
//			Log.d(GpsTrailerService.TAG,"Really offing gps");
			lm.removeUpdates(locationListener);
    	}
    	
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
