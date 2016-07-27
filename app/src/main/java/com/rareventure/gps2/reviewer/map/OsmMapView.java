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
import android.graphics.PointF;
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
	private static final float ZOOM_STEP = 1.5f;
	private static final int ZOOM_EASE_MS = 500;
	private static final int PAN_EASE_MS = 500;
	private static final int AUTOZOOM_PAN_EASE_MS = 1000;
	private static final int AUTOZOOM_ZOOM_EASE_MS = 1000;
	private ArrayList<GpsOverlay> overlays = new ArrayList<GpsOverlay>();

	/**
	 * Coordinates of the screen in longitude and latitude. This is the most accurate representation
	 * of where the screen is (we get these values as is from mapzen).
	 * The y component of screenBottomRight is based on pointAreaHeight, *NOT* the height of the map.
	 *
	 * Writing and reading of these values are synchronized.
	 */
	private LngLat screenTopLeft = new LngLat(), screenBottomRight = new LngLat(), screenSize = new LngLat();

	/**
	 * These are the screen coordinates in ap units (based on Mercator). See {@code AreaPanel} for more info.
	 * The apMaxY is based on pointAreaHeight, *NOT* the height of the map.
	 * <p>
	 * Writing and reading of these values are synchronized.
	 */
	private int apMinX, apMinY, apMaxX, apMaxY;

	public static Preferences prefs = new Preferences();

	private Paint tickPaint;

	private MapScaleWidget scaleWidget;


	private OsmMapGpsTrailerReviewerMapActivity activity;

	private MapController mapController;

	private Handler notifyScreenChangeHandler = new Handler();

	/**
	 * Center of screen in pixels
	 */
	int centerX;
	int centerY;

	/**
	 * This is the height of the area in which we draw points. We don't want to draw points
	 * underneath the time scale widget at the bottom of the screen, so this excludes that
	 */
	private int pointAreaHeight;

	private int windowWidth;

	/**
	 * Since mapzen doesn't tell us when the screen moves, and stops moving (after a fling
	 * for example), we continuously pull the location. We only do so when an action occurs which
	 * would start the screen in motion, and when the screen has stopped, we turn off our
	 * polling.
	 */
	private Runnable notifyScreenChangeRunnable = new Runnable() {
		LngLat lastP1 = new LngLat(), lastP2 = new LngLat();
		PointF p = new PointF();

		@Override
		public void run() {
//			p.x = 0;
//			p.y = 0;
//			LngLat p1 = mapController.screenPositionToLngLat(p);
//			p.x = windowWidth;
//			p.y = pointAreaHeight;
//			LngLat p2 = mapController.screenPositionToLngLat(p);
			LngLat p1 = mapController.coordinatesAtScreenPosition(0,0);
			LngLat p2 = mapController.coordinatesAtScreenPosition(windowWidth, pointAreaHeight);

			synchronized (this) {
				//update our internal representation of the screen
				screenSize = new LngLat(p2.longitude - p1.longitude, p1.latitude - p2.latitude);

				normalizeLngLat(p1);
				normalizeLngLat(p2);

				screenTopLeft = p1;
				screenBottomRight = p2;

				apMinX = AreaPanel.convertLonToX(screenTopLeft.longitude);
				apMinY = AreaPanel.convertLatToY(screenTopLeft.latitude);
				apMaxX = AreaPanel.convertLonToX(screenBottomRight.longitude);
				apMaxY = AreaPanel.convertLatToY(screenBottomRight.latitude);
			}

			updateScaleWidget();

			notifyOverlayScreenChanged();

			//if we haven't moved since our last run
			//note that we place this check after we notify, so that if we are queued,
			//we'll always redraw once no matter what. (used by redrawMap())
			if(p1.equals(lastP1) && p2.equals(lastP2))
				return;

			lastP1 = p1;
			lastP2 = p2;

			//we keep queuing as long as there is a change
			//we need to time them out, as to not waste resources, hence we use a handler and delay
			//the next call
			notifyScreenChangeHandler.postDelayed(
					notifyScreenChangeHandlerRunnable
				, 250);
		}

		private void normalizeLngLat(LngLat p1) {
			if(p1.longitude < Util.MIN_LON || p1.longitude> Util.MAX_LON) {
				int wraps = (int) Math.floor((p1.longitude - Util.MIN_LON) / Util.LON_PER_WORLD);
				p1.longitude -= wraps * Util.LON_PER_WORLD;
			}
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
	public void init(final SuperThread fileCacheSuperThread, OsmMapGpsTrailerReviewerMapActivity activity)
	{

		this.activity = activity;

		// This starts a background process to set up the map.
		getMapAsync(new MapView.OnMapReadyCallback(){
			@Override
			public void onMapReady(final MapController mapController) {
				OsmMapView.this.mapController = mapController;

				File cacheDir = new File(GTG.getExternalStorageDirectory().toString()+"/tile_cache2");

				cacheDir.mkdirs();

//				Log.d(GTG.TAG, "cacheDir is "+cacheDir);

				GpsTrailerMapzenHttpHandler mapHandler =
						new GpsTrailerMapzenHttpHandler(cacheDir, fileCacheSuperThread);

				mapController.setHttpHandler(mapHandler);

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

				mapController.setTapResponder(new TouchInput.TapResponder() {
					@Override
					public boolean onSingleTapUp(float x, float y) {
						Log.d(GTG.TAG, "requesting render!");
						mapController.requestRender();
						return false;
					}

					@Override
					public boolean onSingleTapConfirmed(float x, float y) {
						return false;
					}
				});

				for(GpsOverlay o : overlays)
					o.startTask(mapController);

				panAndZoom2(OsmMapGpsTrailerReviewerMapActivity.prefs.leftLon,
						OsmMapGpsTrailerReviewerMapActivity.prefs.topLat,
						OsmMapGpsTrailerReviewerMapActivity.prefs.rightLon,
						OsmMapGpsTrailerReviewerMapActivity.prefs.bottomLat);
			}
		},"map_style.yaml");
	}

	/**
	 * Returns the ratio of meters to pixels at the center of the screen
     */
	public double metersToPixels() {
		//we need the lat, because the distance changes depending on location from equator
		double screenCenterLat = screenTopLeft.latitude - screenSize.latitude / 2;
		double metersToLon = 1/(Util.LON_TO_METERS_AT_EQUATOR *
				Math.cos(screenCenterLat/ 180 * Math.PI));

		return screenSize.longitude / windowWidth * metersToLon;
	}

	public synchronized AreaPanelSpaceTimeBox getCoordinatesRectangleForScreen() {
		AreaPanelSpaceTimeBox stBox = new AreaPanelSpaceTimeBox();

		stBox.minX = apMinX;
		stBox.maxX = apMaxX;
		stBox.minY = apMinY;
		stBox.maxY = apMaxY;

		return stBox;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
		super.onLayout(changed, left, top, right, bottom);
		updateScaleWidget();
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}

	public void addOverlay(GpsOverlay overlay) {
		this.overlays.add(overlay);
	}


	private void updateScaleWidget() {
		if(scaleWidget != null)
			scaleWidget.change((float) (1./metersToPixels()));
	}

	public void zoomIn() {
		float newZoom = mapController.getZoom() + ZOOM_STEP;

		mapController.setZoomEased(newZoom,ZOOM_EASE_MS);
		notifyScreenMoved();
	}

	private void notifyScreenMoved() {
		//this makes our code that checks for a screen change
		//we put a delay in there because we often do animated changes,
		//and if we run our checker too soon, it will compare the last
		//screen change to the current and determine that we've stopped,
		//when actually we haven't started moving yet
		notifyScreenChangeHandler.postDelayed(
				notifyScreenChangeHandlerRunnable
				, ZOOM_EASE_MS/2);
	}

	public void zoomOut() {
		float newZoom = mapController.getZoom() - ZOOM_STEP;

		mapController.setZoomEased(newZoom,ZOOM_EASE_MS);
		notifyScreenMoved();
	}

	/**
	 * Redraws the map for a change of points displayed or screen
	 */
	public void redrawMap() {
		mapController.queueEvent(notifyScreenChangeRunnable);
	}

	public LngLat getScreenTopLeft() {
		return screenTopLeft;
	}

	public LngLat getScreenBottomRight() {
		return screenBottomRight;
	}

	public static class Preferences implements AndroidPreferences
	{
	}

	public void setScaleWidget(MapScaleWidget scaleWidget) {
		this.scaleWidget = scaleWidget;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
        GTG.cacheCreatorLock.registerReadingThread();
        try {
		tickPaint = new Paint();
		tickPaint.setColor(0xFF000000);
		

        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
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


	public void panAndZoom(int minX, int minY, int maxX, int maxY) {
		float currZoom = mapController.getZoom();

		LngLat tl = mapController.coordinatesAtScreenPosition(0,0);
		LngLat br = mapController.coordinatesAtScreenPosition(windowWidth,pointAreaHeight);

		int fromMinX= AreaPanel.convertLonToX(tl.longitude);
		int fromMinY = AreaPanel.convertLatToY(tl.latitude);
		int fromMaxX = AreaPanel.convertLonToX(br.longitude);
		int fromMaxY = AreaPanel.convertLatToY(br.latitude);

		float zoomMultiplier = Math.min(
				((float)fromMaxX-fromMinX)/(maxX-minX),
				((float)fromMaxY-fromMinY)/(maxY-minY)
		);

		//mapzen uses 2**(zoom) for zoom level, so we have to convert to it
		float newZoom = (float) (currZoom + Math.log(zoomMultiplier)/Math.log(2));

		LngLat newPos = new LngLat(
				AreaPanel.convertXToLon((maxX-minX)/2+minX),
				AreaPanel.convertYToLat((maxY-minY)/2+minY)
		);

		mapController.setPositionEased(newPos,AUTOZOOM_PAN_EASE_MS);
		mapController.setZoomEased(newZoom,AUTOZOOM_ZOOM_EASE_MS);

		notifyScreenMoved();
	}

	public void panAndZoom2(double leftLon, double topLat, double rightLon, double bottomLat) {
		if(mapController == null)
			return;

		float currZoom = mapController.getZoom();

		LngLat tl = mapController.coordinatesAtScreenPosition(0,0);
		LngLat br = mapController.coordinatesAtScreenPosition(windowWidth,pointAreaHeight);

		int fromMinX= AreaPanel.convertLonToX(tl.longitude);
		int fromMinY = AreaPanel.convertLatToY(tl.latitude);
		int fromMaxX = AreaPanel.convertLonToX(br.longitude);
		int fromMaxY = AreaPanel.convertLatToY(br.latitude);

		int minX= AreaPanel.convertLonToX(leftLon);
		int minY = AreaPanel.convertLatToY(topLat);
		int maxX = AreaPanel.convertLonToX(rightLon);
		int maxY = AreaPanel.convertLatToY(bottomLat);

		float zoomMultiplier = Math.min(
				((float)fromMaxX-fromMinX)/(maxX-minX),
				((float)fromMaxY-fromMinY)/(maxY-minY)
		);

		//mapzen uses 2**(zoom) for zoom level, so we have to convert to it
		float newZoom = (float) (currZoom + Math.log(zoomMultiplier)/Math.log(2));

		LngLat newPos = new LngLat(
				AreaPanel.convertXToLon((maxX-minX)/2+minX),
				AreaPanel.convertYToLat((maxY-minY)/2+minY)
		);

		mapController.setPositionEased(newPos,AUTOZOOM_PAN_EASE_MS);
		mapController.setZoomEased(newZoom,AUTOZOOM_ZOOM_EASE_MS);

		notifyScreenMoved();
	}

	private void notifyOverlayScreenChanged() {
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
		for(GpsOverlay o : overlays)
			o.onPause();
	}

	public void onResume() {
		super.onResume();
		for(GpsOverlay o : overlays)
			o.onResume();
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
