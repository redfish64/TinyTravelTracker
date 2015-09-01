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

import java.util.List;

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

import com.rareventure.gps2.R;
import com.rareventure.android.Util;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelCache;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;

//TODO 3: make this use gps service to determine the current location
public class MaplessLocationOverlay implements MaplessOverlay, LocationListener, SensorEventListener 
{
	private OsmMapGpsTrailerReviewerMapActivity activity;
	private LocationManager lm;
	private long lastLocationReadingMs = 0;
	private float lastLocationAccuracy;
	private int lastLocationLatM;
	private int lastLocationLonM;
	private Paint circlePaint;
	private SensorManager sm;
	
	//compass shit
    private float[] gData = new float[3];
    private float[] mData = new float[3];
    private float[] r = new float[16];
    private float[] orientation = new float[3];
    private float[] i = new float[16];
	private Bitmap compassArrow;

	public MaplessLocationOverlay(OsmMapGpsTrailerReviewerMapActivity activity) {
		this.activity = activity;
		
		//TODO 2.5: gps reader should piggyback when any other program starts
		//reading from the gps (if possible)
		
		//TODO 4: gps reader should start up all location updates, and continue
		// to run until it gets the best location (from the most accurate provider)
		// turning off each provider as soon as it gets a location
		
		circlePaint = new Paint();
		//TODO 3: Preferences, sigh.. I hate preferences
		circlePaint.setColor(0x600000FF);
		circlePaint.setStrokeWidth(3);

		lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
		
		sm = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
		
		Bitmap fullCompassArrow = BitmapFactory.decodeResource(activity.getResources(), R.drawable.location);
		compassArrow = Bitmap.createScaledBitmap(fullCompassArrow, (int)Util.convertDpToPixel(40, activity), 
						(int)Util.convertDpToPixel(40 * fullCompassArrow.getHeight() / fullCompassArrow.getWidth(), activity),
						true); 
		
//		locationAnim = AnimationUtils.loadAnimation(activity, R.drawable.location_anim);
	}

	@Override
	public void draw(Canvas canvas, OsmMapView view, boolean thumbDown) {
		if(lastLocationReadingMs != 0 && !thumbDown)
		{
			int x = AreaPanel.convertLonmToX(lastLocationLonM);
			int y = AreaPanel.convertLatmToY(lastLocationLatM);
			
			AreaPanelSpaceTimeBox apBox = view.getCoordinatesRectangleForScreen();
			
			Point screenPoint = new Point();
			
			//note that canvas has a different height
			apBox.apUnitsToPixels(screenPoint, x, y, view.getWidth(), view.getHeight());
			
			Matrix transform = new Matrix();
		    transform.setTranslate(screenPoint.x - compassArrow.getWidth()/2, screenPoint.y - compassArrow.getHeight()/2);
		    transform.preRotate((float)Math.toDegrees(orientation[0])-90, compassArrow.getWidth()/2, compassArrow.getHeight()/2);
		    canvas.drawBitmap(compassArrow, transform, null);

		}
	}

	public double getAbsPixelY2(long zoom8BitPrec) {
		double apY = AreaPanel.convertLatmToYDouble(lastLocationLatM);
		
		return AreaPanel.convertApYToAbsPixelY2(apY, zoom8BitPrec);
	}
	
	public double getAbsPixelX2(long zoom8BitPrec) {
		double apX = AreaPanel.convertLonmToXDouble(lastLocationLonM);
		
		return AreaPanel.convertApXToAbsPixelX2(apX, zoom8BitPrec);
	}
	
	@Override
	public boolean onTap(float x, float y, OsmMapView mapView) {
		return false; //do nothing with taps
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
		lastLocationLatM = (int) Math.round(location.getLatitude() * 1000000);
		lastLocationLonM = (int) Math.round(location.getLongitude() * 1000000);
		
		//TODO 2.1 turn off location known when unknown
		if(notifyWeHaveGps)
		{
			activity.notifyLocationKnown();
			notifyWeHaveGps = false;
		}
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

	public void onPause() {
		lm.removeUpdates(this);
		sm.unregisterListener(this);
	}

	public void onResume()
	{
        Criteria criteria = new Criteria();
        criteria.setSpeedRequired(false);
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(false);
        
        String providerName = lm.getBestProvider(criteria, true);
    	lm.requestLocationUpdates(providerName, 0, 0, this, activity.getMainLooper());
        
        Sensor gsensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor msensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        
        sm.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_UI);
        sm.registerListener(this, msensor, SensorManager.SENSOR_DELAY_UI);
 
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        float[] data;
        
        if (type == Sensor.TYPE_ACCELEROMETER) {
            data = gData;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            data = mData;
        } else {
            // we should not be here.
            return;
        }
        for (int i=0 ; i<3 ; i++)
            data[i] = event.values[i];

        SensorManager.getRotationMatrix(r, i, gData, mData);
        

        SensorManager.getOrientation(r, orientation);
        
        if(System.currentTimeMillis() - lastCompassReadingMs > prefs.minTimeForRedrawMs && Math.abs(lastCompass - orientation[0]) > prefs.minChangeForRedraw)
        {
            lastCompass = orientation[0];
            lastCompassReadingMs = System.currentTimeMillis();
            
        	activity.maplessView.invalidate();
        }
	}

	@Override
	public boolean onLongPressMove(float startX, float startY, float endX,
			float endY, OsmMapView osmMapView) {
		return false;
	}

	@Override
	public boolean onLongPressEnd(float startX, float startY, float endX,
			float endY, OsmMapView osmMapView) {
		return false;
	}

}
