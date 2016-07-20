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

import java.io.File;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;

import com.mapzen.tangram.LngLat;
import com.mapzen.tangram.MapController;
import com.mapzen.tangram.MapView;

import com.mapzen.tangram.TouchInput;
import com.rareventure.gps2.R;
import com.rareventure.android.SuperThread;
import com.rareventure.android.Util;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;

public class OsmMapView extends MapView
{
	private ArrayList<GpsOverlay> overlays = new ArrayList<GpsOverlay>();
	
	/**
	 * Offset in pixels of the upper left corner. Note, these are doubles
	 * so that when we zoom out and zoom back in again, we will end up around
	 * the same spot
	 */
	double x = 0;

	double y = 0;
	
	/**
	 * Scale amount with 8 assumed bits of precision. ie. 256 = 1, 512 = 2, 384 = 2.5
	 *
	 * Note, it also equals the number of pixels in the whole world in one dimension
	 * 
	 */
	long zoom8bitPrec;
	
	
//	int zoom;

	public static int TILE_SIZE = 256;

	public static Preferences prefs = new Preferences();

	private Paint tickPaint;

//	private MemoryCache memoryCache;
//
//	private FileCache fileCache;
//
//	private RemoteLoader remoteLoader;

	private MaplessScaleWidget scaleWidget;


	private SuperThread fileCacheSuperThread;

	private SuperThread remoteLoaderSuperThread;

	private OsmMapGpsTrailerReviewerMapActivity activity;

	private MapController mapController;

	private Handler notifyScreenChangeHandler = new Handler();

	private Runnable notifyScreenChangeRunnable = new Runnable() {
		LngLat lastP1 = new LngLat(), lastP2 = new LngLat();

		@Override
		public void run() {
			LngLat p1 = mapController.coordinatesAtScreenPosition(0,0);
			LngLat p2 = mapController.coordinatesAtScreenPosition(windowWidth, pointAreaHeight);

			//if we haven't moved since our last run
			if(p1.equals(lastP1) && lastP2.equals(lastP2))
				return;

			Log.i(GTG.TAG,"p1 lon "+p1.longitude+" lat "+p1.latitude+" p2 lon "+p2.longitude+" lat "+p2.latitude);

			lastP1 = p1;

			lastP2 = p2;

			int apMinX = AreaPanel.convertLonToX(p1.longitude);
			int apMinY = AreaPanel.convertLatToY(p1.latitude);
			int apMaxX = AreaPanel.convertLonToX(p2.longitude);
			int apMaxY = AreaPanel.convertLatToY(p2.latitude);

			panAndZoom(apMinX,apMinY,apMaxX,apMaxY);

			//we keep queuing as long as there is a change
			//we need to time them out, as to not waste resources, hence we use a handler and delay
			//the next call
			notifyScreenChangeHandler.postDelayed(
					notifyScreenChangeHandlerRunnable
				, 250);
		}
	};

	//used to space out our checks for the map position
	private Runnable notifyScreenChangeHandlerRunnable =
		new Runnable() {
			@Override
			public void run() {
				mapController.queueEvent(notifyScreenChangeRunnable);
			}
	};

	/**
	 * This is the height of the area in which we draw points. We don't want to draw points
	 * underneath the time scale widget at the bottom of the screen, so this excludes that
	 */
	private int pointAreaHeight;

	private int windowWidth;

//	private MultiTouchController<OsmMapView> multiTouchController = new MultiTouchController<OsmMapView>(this);

	public OsmMapView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	/**
	 * Must be called after all addOverlay() calls
     */
	public void init(SuperThread remoteLoaderSuperThread, SuperThread fileCacheSuperThread, OsmMapGpsTrailerReviewerMapActivity activity)
	{
		this.remoteLoaderSuperThread = remoteLoaderSuperThread;
		this.fileCacheSuperThread = fileCacheSuperThread;
		
		this.activity = activity;

//		memoryCache = new MemoryCache(this);
//
//		fileCache = new FileCache(getContext(), memoryCache, fileCacheSuperThread);
//		memoryCache.setFileCache(fileCache);
//
//		remoteLoader = new RemoteLoader(activity, this, fileCache, remoteLoaderSuperThread);
//		memoryCache.setRemoteCache(remoteLoader);
		// This starts a background process to set up the map.
		getMapAsync(new MapView.OnMapReadyCallback(){
			@Override
			public void onMapReady(final MapController mapController) {
				OsmMapView.this.mapController = mapController;

				GpsTrailerMapzenHandler mapHandler = new GpsTrailerMapzenHandler();
				//TODO 1 we need to encrypt the tile cache again

				File cacheDir = new File(GTG.getExternalStorageDirectory().toString()+"/tile_cache2");

				cacheDir.mkdirs();

				Log.d(GTG.TAG, "cacheDir is "+cacheDir);

				mapHandler.setCache(cacheDir, GTG.MAX_CACHE_SIZE);
				mapController.setHttpHandler(new GpsTrailerMapzenHandler());

				mapController.setShoveResponder(new TouchInput.ShoveResponder() {
					@Override
					public boolean onShove(float distance) {
						//this rotates the screen downwards for more 3d look. We don't allow it currently
						//because it would mess up our calculations as to what points to
						//display
						//TODO 3 allow shoving
						return true;
					}
				});

				mapController.setRotateResponder(new TouchInput.RotateResponder() {
					@Override
					public boolean onRotate(float x, float y, float rotation) {
						//this rotates the screen to change the northern direction. We don't allow it currently
						//because it would mess up our calculations as to what points to
						//display
						//TODO 3 allow rotation
						return true;
					}
				});

				mapController.setPanResponder(new TouchInput.PanResponder() {
					@Override
					public boolean onPan(float startX, float startY, float endX, float endY) {
						Log.d(GTG.TAG,String.format("panning sx %f sy %f ex %f ey %f",startX, startY,
								endX, endY));
						mapController.queueEvent(notifyScreenChangeRunnable);
						return false;
					}

					@Override
					public boolean onFling(float posX, float posY, float velocityX, float velocityY) {
						Log.d(GTG.TAG,String.format("flinging px %f py %f vx %f vy %f",
								posX, posY, velocityX, velocityY));

						mapController.queueEvent(notifyScreenChangeRunnable);
						return false;
					}
				});

				mapController.setScaleResponder(new TouchInput.ScaleResponder() {
					@Override
					public boolean onScale(float x, float y, float scale, float velocity) {
						Log.d(GTG.TAG,String.format("scaling x %f y %f sx %f sy %f",
								x, y, scale, velocity));
						mapController.queueEvent(notifyScreenChangeRunnable);
						return false;
					}
				});

				for(GpsOverlay o : overlays)
					o.startTask(mapController);


			}
		},"map_style.yaml");
	}

	public int getZoomLevel() {
		long z = zoom8bitPrec >> 8;
		int zl = 0;
		while(z != 0)
		{
			z = (z>>1);
			zl++;
		}
		
		return zl-1;
	}

	public float metersToPixels(float meters, int latm) {
		//TODO 3: test
		return (float) (1./(meters * Util.LONM_TO_METERS_AT_EQUATOR * 1/Math.cos(Math.toRadians(latm * .000001)) / Util.LONM_PER_WORLD 
//				* (1<<(getRoundedZoomLevel()+8))
				* zoom8bitPrec
				));
		//return meters * scaleWidget.pixelsPerMeter;
	}

	public AreaPanelSpaceTimeBox getCoordinatesRectangleForScreen() {
		AreaPanelSpaceTimeBox stBox = new AreaPanelSpaceTimeBox();
		
		long maxPixels = zoom8bitPrec; 
			//(1 << getRoundedZoomLevel() + 8);
		
		stBox.minX = (int) (((long)x) * AreaPanel.MAX_AP_UNITS / maxPixels); 
		stBox.maxX = (int) (((long)x + windowWidth) * AreaPanel.MAX_AP_UNITS / maxPixels);
		stBox.minY = (int) (((long)y) * AreaPanel.MAX_AP_UNITS / maxPixels); 
		stBox.maxY = (int) (((long)y + pointAreaHeight) * AreaPanel.MAX_AP_UNITS / maxPixels);
		
		return stBox;
	}

	/**
	 * Center of screen in pixels
	 */
	int centerX;
	int centerY;

	/**
	 * An index to track the current time the user clicked down, so that if they
	 * do so multiple times, we don't get confused
	 */
	protected int actionDownIndex;

	protected boolean longPressOn;

	private boolean thumbDown;

	private long lastShortPressTimeMs;

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        GTG.ccRwtm.registerReadingThread();
        try {
		super.onLayout(changed, left, top, right, bottom);
		updateScaleWidget();
        }
        finally {
        	GTG.ccRwtm.unregisterReadingThread();
        }
	}

	public void addOverlay(GpsOverlay overlay) {
		this.overlays.add(overlay);
	}

//	@Override
//	public boolean onTouchEvent(MotionEvent event) {
//		//TODO 1 HACK
//		if(1==1) return super.onTouchEvent(event);
//
//
//        GTG.ccRwtm.registerReadingThread();
//        try {
//
//    		if(event.getAction() == MotionEvent.ACTION_DOWN)
//    		{
//    			thumbDown = true;
//    			actionStartX = actionLastX = event.getX();
//    			actionStartY = actionLastY = event.getY();
//    			startEventTime = event.getEventTime();
//    			final int localActionDownIndex = actionDownIndex;
//
//    			getHandler().postDelayed(new Runnable() {
//
//					@Override
//					public void run() {
//						if(localActionDownIndex == actionDownIndex &&
//		    					(actionLastX - actionStartX) * (actionLastX - actionStartX) +
//		    					(actionLastY - actionStartY) * (actionLastY - actionStartY) <=
//		    						ViewConfiguration.getTouchSlop() * ViewConfiguration.getTouchSlop())
//						{
//							// Get instance of Vibrator from current Context
//							Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
//
//							// Vibrate for a short time
//							v.vibrate(50);
//
//							longPressOn = true;
//						}
//					}
//				}, prefs.longPressTimeout);
//    		}
//    		if(event.getAction() == MotionEvent.ACTION_UP)
//    		{
//    			thumbDown = false;
//    			actionDownIndex++;
//
//
//    			if(lastShortPressTimeMs >= System.currentTimeMillis() - DOUBLE_TAP_MS)
//    			{
//    				//zoom in after recentering to the current x, y
//    				x += event.getX() - centerX;
//    				y += event.getY() - centerY;
//    				if(shouldZoomInBeEnabled())
//    					zoomIn();
//    			}
//    			else if(longPressOn)
//    			{
//    				//we go in reverse, since the last overlay is on top of the previously added ones
//    				//(this is the way that google maps does this, also
//    				for(int i = overlays.size()-1; i >= 0; i--)
//    					if(overlays.get(i).onLongPressEnd(actionStartX, actionStartY, event.getX(), event.getY(), this)) break;
//
//    				longPressOn = false;
//    			}
//    			else if(
//    					event.getEventTime() - startEventTime < prefs.tapTimeout &&
//    					(event.getX() - actionStartX)*(event.getX() - actionStartX) +
//    					(event.getY() - actionStartY) * (event.getY() - actionStartY) <=
//    						ViewConfiguration.getTouchSlop() * ViewConfiguration.getTouchSlop())
//    			{
//    				lastShortPressTimeMs = System.currentTimeMillis();
//    				//we go in reverse, since the last overlay is on top of the previously added ones
//    				//(this is the way that google maps does this, also
//    				for(int i = overlays.size()-1; i >= 0; i--)
//    					if(overlays.get(i).onTap(event.getX(), event.getY(), this)) break;
//    			}
//    			else
//    			{
//    				//round to a multiple of 2 to keep map from looking pixelated
//    				//co: this can make the map jump back to a zoom level already passed when the user
//    				// is doing their pinch to zoom thing.
////    				int newZoom8BitPrec = 1<<(Util.minIntegerLog2(zoom8bitPrec)-1);
////    				x = (x + centerX) *(newZoom8BitPrec)/(zoom8bitPrec) - centerX;
////    				y = (y + centerY) *(newZoom8BitPrec)/(zoom8bitPrec) - centerY;
////    				zoom8bitPrec = newZoom8BitPrec;
////    				invalidate();
////
////    				//update scale widget for regular move
////        			updateScaleWidget();
//    			}
//
////    			else
////    				Log.d(GTG.TAG,"dist squared is "+(event.getX() - actionStartX)*(event.getX() - actionStartX) +
////    						(event.getY() - actionStartY) * (event.getY() - actionStartY));
//
//    		}
//
//    		if(event.getAction() == MotionEvent.ACTION_MOVE)
//			{
//    			actionLastX = event.getX();
//    			actionLastY = event.getY();
//    			if(longPressOn)
//    			{
//    				//we go in reverse, since the last overlay is on top of the previously added ones
//    				//(this is the way that google maps does this, also
//    				for(int i = overlays.size()-1; i >= 0; i--)
//    					if(overlays.get(i).onLongPressMove(actionStartX, actionStartY, event.getX(), event.getY(), this)) break;
//
//    				invalidate();
//    				//don't let the multi touch controller handle it if we long pressed, so the user can drag a selection
//    				return true;
//    			}
//
//			}
//
//        	return multiTouchController.onTouchEvent(event);
//        }
//        finally {
//        	GTG.ccRwtm.unregisterReadingThread();
//        }
//	}
	
	public boolean shouldZoomInBeEnabled()
	{
		return zoom8bitPrec <= (OsmMapGpsTrailerReviewerMapActivity.prefs.maxZoom >> 1);
	}

	

	private void updateScaleWidget() {
		if(scaleWidget != null)
			scaleWidget.change(1/metersToPixels(1, 
					AreaPanel.convertYToLatm((int) ((y+getHeight()/2) 
							*AreaPanel.MAX_AP_UNITS
							/
							//getMapSizeInPx(getRoundedZoomLevel()
							zoom8bitPrec
									))));
	}

	public void zoomIn() {
        GTG.ccRwtm.registerReadingThread();
        try {
		zoom8bitPrec <<= 1;
		
		x = ((x + centerX)*2) - centerX;
		y = ((y + centerY)*2) - centerY;
		
		updateScaleWidget();
		invalidate();
        }
        finally {
        	GTG.ccRwtm.unregisterReadingThread();
        }
	}

	public void zoomOut() {
        GTG.ccRwtm.registerReadingThread();
        try {
		zoom8bitPrec >>= 1;
		
		x = ((x + centerX)*.5) - centerX;
		y = ((y + centerY)*.5) - centerY;
		
		updateScaleWidget();
		invalidate();
        }
        finally {
        	GTG.ccRwtm.unregisterReadingThread();
        }
	}
	
	public static class Preferences implements AndroidPreferences
	{

		
		public long longPressTimeout = 1000;
		/**
		 * ViewConfiguration.TAP_TIMEOUT is way too small
		 */
		public long tapTimeout = 750;
		
	}

	public void setScaleWidget(MaplessScaleWidget scaleWidget) {
		this.scaleWidget = scaleWidget;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
        GTG.ccRwtm.registerReadingThread();
        try {
		tickPaint = new Paint();
		tickPaint.setColor(0xFF000000);
		

        }
        finally {
        	GTG.ccRwtm.unregisterReadingThread();
        }
	}

	public void notifyNewBitmapInCache() {
		if(getHandler() != null)
		{
			getHandler().post(new Runnable() {
				
				@Override
				public void run() {
					invalidate();
				}
			});
		}
	}


	/**
	 * Pans and zooms to the given box
	 * All arguments are in AP_UNITS. See AreaPanel.convertXToLatm, etc. for
	 * converting betweeen lat lon and AP_UNITS.
	 * May be called from any thread
	 *
	 * @param minApX
	 * @param minApY
	 * @param maxApX
     * @param maxApY
     */
	public void panAndZoom(int minApX, int minApY, int maxApX, int maxApY) {
        GTG.ccRwtm.registerReadingThread();
        try {
		
		//to handle when we wrap the world
		int width = maxApX - minApX;
		if(width < 0) width = - width;
		
		//since we have buttons on the bottom and the top, as well as the time view, we won't want the points to
		//be covered by them, so we subtract their height from the actual height to get
		//a proper zoom areas

		
		//1<<(zl+8) == max pixels for world = MPW
		//AreaPanel.MAX_AP_UNITS == max ap units for world = MAW
		//width of window in pixels = wwP
		//width of stb in ap units = apW
		// apW * (2 ** (zl+8)) / MAW < wwP
		// 2 ** (zl+8) < wwP * MAW / apW
		// log2 (2 ** (zl+8)) < log2 (wwP * MAW / apW)
		// zl+8 < log2 (wwP * MAW / apW)
		// zl < log2 (wwP  * MAW / apW) - 8
		
		int maxXZoomLevel = Util.minIntegerLog2((long) Math.ceil(((double)getWidth()) * AreaPanel.MAX_AP_UNITS / width)) - 8 -1; 
		int maxYZoomLevel = Util.minIntegerLog2((long) Math.ceil(((double) pointAreaHeight) * AreaPanel.MAX_AP_UNITS / (maxApY - minApY))) - 8 -1;
		
		zoom8bitPrec = 1 << (8+Math.min(Math.min(maxXZoomLevel, maxYZoomLevel),
				OsmMapGpsTrailerReviewerMapActivity.prefs.maxAutoZoomLevel));

		double apUnitsToPixels = (double)zoom8bitPrec / AreaPanel.MAX_AP_UNITS;

		//TODO 3 handle zoom padding
		x = ((minApX + maxApX) >> 1) * apUnitsToPixels - centerX;
		y = ((minApY + maxApY) >> 1) * apUnitsToPixels - centerY;
		
		updateScaleWidget();
        }
        finally {
        	GTG.ccRwtm.unregisterReadingThread();
        }

		notifyOverlayScreenChanged();
	}
	
	public void panAndZoom2(long zoom8BitPrec, double currX, double currY)
	{
        GTG.ccRwtm.registerReadingThread();
        try {
		this.zoom8bitPrec = zoom8BitPrec;
		this.x = currX;
		this.y = currY;
		
		updateScaleWidget();
		
		activity.updatePlusMinusButtonsForNewZoom();
		
        }
        finally {
        	GTG.ccRwtm.unregisterReadingThread();
        }

		notifyOverlayScreenChanged();
	}

	private void notifyOverlayScreenChanged() {
		//TODO 3 we probably don't need zoom8bitperc anymore, since zooming is handled by
		// tangram. Then we could directly calculate ap coords from lon/lat specified by
		// tangram, rather then our intermidiate x/y and zoom format
		AreaPanelSpaceTimeBox newStBox = getCoordinatesRectangleForScreen();

		//we access the min and max time from the activity which is altered by the main ui thread
		newStBox.minZ = OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec;
		newStBox.maxZ = OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec +
				OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePeriodSec;

		for(GpsOverlay o : overlays)
			o.notifyScreenChanged(newStBox);
	}

	public void onPause() {
		super.onPause();
	}

	public void onResume() {
		super.onResume();
	}

	/**
	 * Set location of crosshairs and where zooms are centered. ie, this is the
	 * center of the screen in pixels.
	 *
	 * @param x
	 * @param y
	 */
	public void setZoomCenter(int x, int y) {
		centerX = x;
		centerY = y;
		//TODO 2 FIXME
//		activity.gpsTrailerOverlay.setZoomCenter(x,y);
	}

//	@Override
//	public OsmMapView getDraggableObjectAtPoint(PointInfo touchPoint) {
//		return this;
//	}
//
//	@Override
//	public boolean pointInObjectGrabArea(PointInfo touchPoint, OsmMapView obj) {
//		return false;
//	}
//
//	@Override
//	public void getPositionAndScale(OsmMapView obj,
//			PositionAndScale objPosAndScaleOut) {
//		objPosAndScaleOut.set(-(float)x, -(float)y, true, zoom8bitPrec,
//				false, 1,1,false,0);
//
//	}
//
//	@Override
//	public boolean setPositionAndScale(OsmMapView obj,
//			PositionAndScale newObjPosAndScale, PointInfo touchPoint) {
//		long newZoom8BitPrec = (int) newObjPosAndScale.getScale();
//
//		if(newZoom8BitPrec != zoom8bitPrec)
//		{
//			if(newZoom8BitPrec < OsmMapGpsTrailerReviewerMapActivity.prefs.maxZoom &&
//					newZoom8BitPrec > OsmMapGpsTrailerReviewerMapActivity.prefs.minZoom)
//			{
//				x = (-newObjPosAndScale.getXOff() + centerX) *(newZoom8BitPrec)/(zoom8bitPrec) - centerX;
//				y = (-newObjPosAndScale.getYOff() + centerY) *(newZoom8BitPrec)/(zoom8bitPrec) - centerY;
//
//				zoom8bitPrec = newZoom8BitPrec;
//
////				Log.d("GTG", "xxxxxxx = "
////						+ newZoom8BitPrec + " , "
////						+ zoom8bitPrec + " : "
////						+ newObjPosAndScale.getXOff() + " - " + newObjPosAndScale.getYOff());
//
//			}
//
//			activity.toolTip.setAction(UserAction.MAP_VIEW_PINCH_ZOOM);
//		}
//		else
//		{
//			x = -newObjPosAndScale.getXOff();
//			y = -newObjPosAndScale.getYOff();
//
//			activity.toolTip.setAction(UserAction.MAP_VIEW_MOVE);
//		}
//
//		invalidate();
//
//		updateScaleWidget();
//		activity.updatePlusMinusButtonsForNewZoom();
//		return false;
//	}
//
//	@Override
//	public void selectObject(OsmMapView obj, PointInfo touchPoint) {
//
//	}

	public void initAfterLayout() {
		windowWidth = getWidth();
		this.pointAreaHeight = activity.findViewById(R.id.main_window_area).getBottom();

//		memoryCache.setWidthAndHeight(getWidth(), getHeight());
	}
}
