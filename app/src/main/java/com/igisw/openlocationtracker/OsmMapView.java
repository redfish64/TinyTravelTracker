/** 
    Copyright 2022 Igor Calì <igor.cali0@gmail.com>

    This file is part of Open Travel Tracker.

    Open Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Open Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Open Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.igisw.openlocationtracker;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.igisw.openlocationtracker.AndroidPreferenceSet.AndroidPreferences;
import com.mapzen.tangram.CameraPosition;
import com.mapzen.tangram.LngLat;
import com.mapzen.tangram.MapChangeListener;
import com.mapzen.tangram.MapController;
import com.mapzen.tangram.MapView;
import com.mapzen.tangram.TouchInput;
import com.mapzen.tangram.networking.HttpHandler;
import com.mapzen.tangram.viewholder.GLViewHolderFactory;
import com.rareventure.gps2.reviewer.map.MyTouchInput;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class OsmMapView extends MapView implements MapView.MapReadyCallback {
	private static final float ZOOM_STEP = 1.5f;
	private static final int ZOOM_EASE_MS = 500;
	private static final int PAN_EASE_MS = 500;
	private static final int AUTOZOOM_PAN_EASE_MS = 1000;
	private static final int AUTOZOOM_ZOOM_EASE_MS = 1000;
	private static final int CAMERA_FLY_SPEED = 1000;
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

	/**
	 * Center of screen in pixels
	 */
	int centerX;
	int centerY;

	/**
	 * This is the height of the area in which we draw points. We don't want to draw points
	 * underneath the time scale widget at the bottom of the screen, so this excludes that
	 */
	int pointAreaHeight;

	int windowWidth;

	/**
	 * Since mapzen doesn't tell us when the screen moves, and stops moving (after a fling
	 * for example), we continuously pull the location. We only do so when an action occurs which
	 * would start the screen in motion, and when the screen has stopped, we turn off our
	 * polling.
	 */
	private MapChangeListener mapChangeListener = new MapChangeListener() {
		@Override
		public void onViewComplete() {

		}

		@Override
		public void onRegionWillChange(boolean animated) {

		}

		@Override
		public void onRegionIsChanging() {

		}

		@Override
		public void onRegionDidChange(boolean animated) {
			LngLat p1 = Util.normalizeLngLat(mapController.screenPositionToLngLat(new PointF(0,0)));
			LngLat p2 = Util.normalizeLngLat(mapController.screenPositionToLngLat(new PointF(windowWidth, pointAreaHeight)));

			synchronized (this) {
				//update our internal representation of the screen
				double lngSize = p2.longitude - p1.longitude;

				//if we are crossing the -180/+180 border
				if(lngSize < 0)
					lngSize = 360 + lngSize;

				screenSize = new LngLat(lngSize, p1.latitude - p2.latitude);

				screenTopLeft = p1;
				screenBottomRight = p2;

				apMinX = AreaPanel.convertLonToX(screenTopLeft.longitude);
				apMinY = AreaPanel.convertLatToY(screenTopLeft.latitude);
				apMaxX = AreaPanel.convertLonToX(screenBottomRight.longitude);
				apMaxY = AreaPanel.convertLatToY(screenBottomRight.latitude);
			}

			updateScaleWidget();

			notifyOverlayScreenChanged();
		}
	};


//
//			new Runnable() {
//		LngLat lastP1 = new LngLat(), lastP2 = new LngLat();
//		PointF p = new PointF();
//
//		@Override
//		public void run() {
////			p.x = 0;
////			p.y = 0;
////			LngLat p1 = mapController.screenPositionToLngLat(p);
////			p.x = windowWidth;
////			p.y = pointAreaHeight;
////			LngLat p2 = mapController.screenPositionToLngLat(p);
//
//			//we normalize because mapcontroller lovingly returns values outside of -180/180 longitude
//			//if user wraps world while scrolling
//		}
//
//	};

	private int windowHeight;
	private Preferences.MapStyle lastLoadedSceneFile;

//	private MultiTouchController<OsmMapView> multiTouchController = new MultiTouchController<OsmMapView>(this);

	public OsmMapView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
//		Create(null, null);	// TODO: igor: move here if needed and working
	}

	/**
	 * Must be called after all addOverlay() calls
     */
	public void init(final SuperThread fileCacheSuperThread, final OsmMapGpsTrailerReviewerMapActivity activity)
	{

		this.activity = activity;

		getMapAsync(this);
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

	private CameraPosition tmpCamPos = new CameraPosition();

	public void zoomIn() {
		mapController.getCameraPosition(tmpCamPos);
		tmpCamPos.zoom += ZOOM_STEP;
		mapController.flyToCameraPosition(tmpCamPos, CAMERA_FLY_SPEED, null);
	}

	public void zoomOut() {
		mapController.getCameraPosition(tmpCamPos);
		tmpCamPos.zoom -= ZOOM_STEP;
		mapController.flyToCameraPosition(tmpCamPos, CAMERA_FLY_SPEED, null);
	}

	/**
	 * Redraws the map for a change of points displayed or screen (such as a timeline movement)
	 */
	public void redrawMap() {
		notifyOverlayScreenChanged();
	}

	public LngLat getScreenTopLeft() {
		return screenTopLeft;
	}

	public LngLat getScreenBottomRight() {
		return screenBottomRight;
	}

	public MapController getMapController() {
		return mapController;
	}

	@Override
	public void onMapReady(@Nullable MapController mapController) {
		loadSceneFileIfNecessary();
	}

	private void loadSceneFileIfNecessary() {
		if(lastLoadedSceneFile != prefs.mapStyle) {
			mapController.loadSceneFile(prefs.mapStyle.fn);
			lastLoadedSceneFile = prefs.mapStyle;
		}
	}

	private void loadSceneFiles(String ... files) {
		StringBuilder data = new StringBuilder();

		AssetManager am = getContext().getAssets();

		for(String file : files) {
			try {
				BufferedReader r = new BufferedReader(new InputStreamReader(am.open("map_assets/"+file)));
				Util.readReaderIntoStringBuilder(r,data);
				r.close();
			} catch (IOException e) {
				throw new IllegalStateException("can't find yaml: "+file);
			}

			data.append('\n');
		}

		mapController.loadSceneYaml(data.toString(),"map_assets",null);
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

	/**
	 * Pans and zooms so the given points will show up as the top left and
	 * bottom right of the view. Note that the zooming/panning will be done so
	 * that the given bottom will be placed above the time view and the zoom buttons.
     */
	public void panAndZoom(int minX, int minY, int maxX, int maxY) {
		mapController.getCameraPosition(tmpCamPos);

		LngLat tl = Util.normalizeLngLat(mapController.screenPositionToLngLat(new PointF(0,0)));
		LngLat br = Util.normalizeLngLat(mapController.screenPositionToLngLat(new PointF(windowWidth,windowHeight)));

		int fromMinX= AreaPanel.convertLonToX(tl.longitude);
		int fromMinY = AreaPanel.convertLatToY(tl.latitude);
		int fromMaxX = AreaPanel.convertLonToX(br.longitude);
		int fromMaxY = AreaPanel.convertLatToY(br.latitude);

		//panAndZoom uses the center of the visible area, excluding the time view
		//and buttons. However, mapzen, uses the entire window. So we need to adjust the
		//y size to the whole window so mapzen will zoom and pan correctly
		maxY = (int) (((float)maxY - minY) * windowHeight / pointAreaHeight) + minY;

		float zoomMultiplier = Math.min(
				((float)fromMaxX-fromMinX)/(maxX-minX),
				((float)fromMaxY-fromMinY)/(maxY-minY)
		);

		//mapzen uses 2**(zoom) for zoom level, so we have to convert to it
		tmpCamPos.zoom = (float) (tmpCamPos.zoom + Math.log(zoomMultiplier)/Math.log(2));

		tmpCamPos.longitude = AreaPanel.convertXToLon((maxX-minX)/2+minX);
		tmpCamPos.latitude = AreaPanel.convertYToLat((maxY-minY)/2+minY);

		mapController.flyToCameraPosition(tmpCamPos, CAMERA_FLY_SPEED, null);
	}

	public void panAndZoom2(double lon, double lat, float zoom) {
		if(mapController == null)
			return;

		mapController.getCameraPosition(tmpCamPos);
		tmpCamPos.longitude = lon;
		tmpCamPos.latitude = lat;
		tmpCamPos.zoom = zoom;

		mapController.flyToCameraPosition(tmpCamPos, CAMERA_FLY_SPEED, null);
	}

	public void panTo(LngLat loc) {
		mapController.getCameraPosition(tmpCamPos);
		tmpCamPos.longitude = loc.longitude;
		tmpCamPos.latitude = loc.latitude;

		mapController.flyToCameraPosition(tmpCamPos, CAMERA_FLY_SPEED, null);
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

		//if the user changed the map style in settings, we want to reflect it right away
		//even if the osmmapview wasn't destroyed
		if(mapController != null)
			loadSceneFileIfNecessary();
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
	}


	public void initAfterLayout() {
		windowWidth = getWidth();
		this.pointAreaHeight = activity.findViewById(R.id.main_window_area).getBottom();
		windowHeight = getHeight();
//		memoryCache.setWidthAndHeight(getWidth(), getHeight());
	}

	@Override
	protected void	onMapInitOnUIThread(MapController controller, HttpHandler handler, GLViewHolderFactory viewHolderFactory, MapView.MapReadyCallback callback) {
//	private void	Create(/*MapController controller,*/ MapView.MapReadyCallback callback, HttpHandler handler/*, GLViewHolderFactory viewHolderFactory, */) {
		super.onMapInitOnUIThread(controller,handler,viewHolderFactory,callback);
//		if(! loadNativeLibrary())return;
//		mapController = this.initMapController(callback, handler);
		mapController.setMapChangeListener(mapChangeListener);
		((MyMapController)mapController).setupTouchListener();

		File cacheDir = new File(GTG.getExternalStorageDirectory().toString()+"/tile_cache2");

		cacheDir.mkdirs();

//				Log.d(GTG.TAG, "cacheDir is "+cacheDir);

//				GpsTrailerMapzenHttpHandler mapHandler =
//						new GpsTrailerMapzenHttpHandler(cacheDir, fileCacheSuperThread);
//
//				mapController.setHttpHandler(mapHandler);

		TouchInput touchInput = mapController.getTouchInput();

		//this rotates the screen downwards for more 3d look. We don't allow it currently
		//because it would mess up our calculations as to what points to
		//display
		//TODO 3 allow shoving
		touchInput.setShoveResponder(new TouchInput.ShoveResponder() {
			@Override
			public boolean onShoveBegin() {
				return true;
			}

			@Override
			public boolean onShove(float distance) {
				return true;
			}

			@Override
			public boolean onShoveEnd() {
				return true;
			}
		});

		touchInput.setRotateResponder(new TouchInput.RotateResponder() {
			@Override
			public boolean onRotateBegin() {
				return true;
			}

			@Override
			public boolean onRotate(float x, float y, float rotation) {
				return true;
			}

			@Override
			public boolean onRotateEnd() {
				return true;
			}
		});

		touchInput.setTapResponder(new TouchInput.TapResponder() {
			@Override
			public boolean onSingleTapUp(float x, float y) {
				return false;
			}

			@Override
			public boolean onSingleTapConfirmed(float x, float y) {
				for(GpsOverlay overlay : overlays)
				{
					overlay.onTap(x,y);
				}
				return false;
			}
		});

		touchInput.setDoubleTapResponder(new TouchInput.DoubleTapResponder() {
			@Override
			public boolean onDoubleTap(float x, float y) {
				Log.d(GTG.TAG, "onDoubleTap "+x+" "+y);
				return true;
			}
		});


		((MyMapController)mapController).setLongPressResponderExt(new MyTouchInput.LongPressResponder() {
			public float startX;
			public float startY;

			public void onLongPress(float x, float y) {
				// Get instance of Vibrator from current Context
				Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);

				// Vibrate for a short time
				v.vibrate(50);

				startX = x;
				startY = y;
			}

			@Override
			public void onLongPressUp(float x, float y) {
				for(GpsOverlay overlay : overlays)
				{
					overlay.onLongPressEnd(startX, startY, x,y);
				}
			}

			@Override
			public boolean onLongPressPan(float movementStartX, float movementStartY, float endX, float endY) {
				for(GpsOverlay overlay : overlays)
				{
					overlay.onLongPressMove(startX, startY, endX,endY);
				}
				return false;
			}
		});

		for(GpsOverlay o : overlays)
			o.startTask(mapController);

		panAndZoom2(OsmMapGpsTrailerReviewerMapActivity.prefs.lastLon,
				OsmMapGpsTrailerReviewerMapActivity.prefs.lastLat,
				OsmMapGpsTrailerReviewerMapActivity.prefs.lastZoom);

	}

    @Override
	protected MapController getMapInstance() {
//	protected MapController getMapInstance(Context context) {
		//We do this because we want to use our own TouchInput (MyTouchInput) which can handle long
		// press pans correctly
//		return new MyMapController(this.getContext());
		return new MyMapController(this.getContext());
	}


	public static class Preferences implements AndroidPreferences
	{
		public static enum MapStyle {
			BUBBLE_WRAP (R.string.BUBBLE_WRAP_MAP_STYLE_DESC, "bubble_wrap_style.yaml"),
			CINNABAR (R.string.CINNABAR_MAP_STYLE_DESC, "cinnabar_style.yaml"),
			CINNABAR_LARGE (R.string.CINNABAR_LARGE_MAP_STYLE_DESC, "cinnabar_large_style.yaml"),
			REFILL (R.string.REFILL_MAP_STYLE_DESC, "refill_style.yaml"),
			//co: SDK_DEFAULT doesn't work
			//SDK_DEFAULT (R.string.SDK_DEFAULT_MAP_STYLE_DESC, "sdk_default_style.yaml"),
			TRON (R.string.TRON_MAP_STYLE_DESC, "tron_style.yaml"),
			WALKABOUT (R.string.WALKABOUT_MAP_STYLE_DESC, "walkabout_style.yaml");

			private final int r;
			public String fn;

			private MapStyle(int r, String fn)
			{
				this.r = r;
				this.fn = fn;
			}

			public static String [] entryNames(Context c) {
				MapStyle [] ms = MapStyle.values();
				String[] res = new String[ms.length];
				for(int i = 0; i < ms.length; i++)
				{
					res[i] = c.getResources().getString(ms[i].r);
				}

				return res;
			}

			public static String [] entryValues(Context c) {
				MapStyle [] ms = MapStyle.values();
				String[] entryNames = new String[ms.length];
				for(int i = 0; i < ms.length; i++)
				{
					entryNames[i] = ms[i].toString();
				}

				return entryNames;
			}
		}

		public MapStyle mapStyle = MapStyle.CINNABAR;
	}

}
