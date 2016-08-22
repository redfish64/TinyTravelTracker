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
package com.rareventure.gps2.reviewer.map;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.mapzen.tangram.LngLat;
import com.mapzen.tangram.MapController;
import com.mapzen.tangram.MapData;
import com.rareventure.gps2.R;
import com.rareventure.android.Util;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelCache;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;

//TODO 3: make this use gps service to determine the current location
public class GpsLocationOverlay implements GpsOverlay, LocationListener
{
	private OsmMapGpsTrailerReviewerMapActivity activity;
	private LocationManager lm;
	private long lastLocationReadingMs = 0;
	private float lastLocationAccuracy;

	private MapController mapController;
	private MapData mapData;
	private LngLat lastLoc = new LngLat();
	private Map<String,String> props = new HashMap<>();

	public GpsLocationOverlay(OsmMapGpsTrailerReviewerMapActivity activity) {
		this.activity = activity;
		
		//TODO 2.5: gps reader should piggyback when any other program starts
		//reading from the gps (if possible)
		
		//TODO 4: gps reader should start up all location updates, and continue
		// to run until it gets the best location (from the most accurate provider)
		// turning off each provider as soon as it gets a location
		
		lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
		
//		locationAnim = AnimationUtils.loadAnimation(activity, R.drawable.location_anim);
	}

	public LngLat getLastLoc()
	{
		return lastLoc;
	}
	
	public double getAbsPixelX2(long zoom8BitPrec) {
		double apX = AreaPanel.convertLonToXDouble(lastLoc.longitude);
		
		return AreaPanel.convertApXToAbsPixelX2(apX, zoom8BitPrec);
	}
	
	@Override
	public void onLocationChanged(Location location) {
		//if this location is a poorer reading 
		// or the location provider doesn't keep track of accuracy, in case we'll
		// just say screw it and replace it
		//TODO 3: is this the right thing to do?
		if(location.getAccuracy() > lastLocationAccuracy || lastLocationAccuracy == 0)
		{
			//ignore it if it's within a certain threshold of time
			if(location.getTime() - lastLocationReadingMs 
					<= prefs.maxTimeToKeepBetterAccuracyLocationReadingMs)
				return;
			if(location.getLatitude() == 0 && location.getLongitude() == 0)
				return;
		}
		
		boolean notifyWeHaveGps = lastLocationReadingMs == 0;

		//NOTE: this may return 0 is the gps provider doesn't keep track of accuracy
		lastLocationAccuracy = location.getAccuracy();
		lastLocationReadingMs = location.getTime();
		lastLoc.latitude = location.getLatitude();
		lastLoc.longitude = location.getLongitude();

		//TODO 2.1 turn off location known when unknown
		if(notifyWeHaveGps)
		{
			activity.notifyLocationKnown();
			notifyWeHaveGps = false;
		}

		resetMapData();
	}

	private void resetMapData() {
		synchronized(this)
		{
			if(mapData == null)
				return;
		}
		props.clear();
		props.put("rotation", Double.toString(lastCompass));

		mapData.beginChangeBlock();
		mapData.clear();
		mapData.addPoint(lastLoc, props);
		mapData.endChangeBlock();

		mapController.requestRender();
	}

	@Override
	public void onProviderDisabled(String provider) {
		//TODO 3: handle this gps location stuff better
	}

	@Override
	public void onProviderEnabled(String provider) {
		//TODO 3: handle this gps location stuff better
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		//TODO 3: handle this gps location stuff better
	}
	
	private Preferences prefs = new Preferences();
	private float lastCompass;
	private long lastCompassReadingMs;

	@Override
	public void notifyScreenChanged(AreaPanelSpaceTimeBox newStBox) {

	}

	@Override
	public boolean onTap(float x, float y) {
		return false;
	}

	@Override
	public boolean onLongPressMove(float startX, float startY, float endX, float endY) {
		return false;
	}

	@Override
	public boolean onLongPressEnd(float startX, float startY, float endX, float endY) {
		return false;
	}

	@Override
	public void onPause() {
		lm.removeUpdates(this);
	}

	@Override
	public void onResume() {
		Criteria criteria = new Criteria();
		criteria.setSpeedRequired(false);
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setAltitudeRequired(false);
		criteria.setBearingRequired(false);
		criteria.setCostAllowed(false);

		String providerName = lm.getBestProvider(criteria, true);
		lm.requestLocationUpdates(providerName, 0, 0, this, activity.getMainLooper());
	}

	@Override
	public void startTask(MapController mapController) {
		synchronized(this) {
			this.mapController = mapController;
			mapData = mapController.addDataLayer("mz_current_location");
		}
	}

	public static class Preferences implements AndroidPreferences
	{


		/**
		 * When polling the gps devices, we may get a poorer accuracy reading
		 * after a reading with better accuracy. In this case, if the poorer
		 * accuracy reading happens within the time threshold specified here
		 * (as compared to the more accurate one, we keep the more accurate one)
		 */
		public int maxTimeToKeepBetterAccuracyLocationReadingMs = 30 * 1000;
		public float minChangeForRedraw = (float) (5*Math.PI / 180);
		public int minTimeForRedrawMs = 2000;
	}

}
