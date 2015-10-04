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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import rtree.BoundedObject;
import rtree.RTree;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.android.SuperThread;
import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.R;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.MediaLocTime;
import com.rareventure.gps2.reviewer.imageviewer.ViewImage;
import com.rareventure.gps2.reviewer.map.sas.Area;
import com.rareventure.gps2.reviewer.map.sas.SelectedAreaSet;

//PERF: we could combine the encryptedindex code with actually pulling the data, so that we don't
//have to query twice while we fumble around looking for the start and end of the data we need (for plotting the gps points)

//TODO 4: display average speed, scale and distances
//TODO 3: color removal in range for colorblind people
public class GpsTrailerOverlay implements MaplessOverlay {
	private OsmMapGpsTrailerReviewerMapActivity gtum;
	
	public static Preferences prefs = new Preferences();

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	private OsmMapView maplessView;

	public GpsTrailerOverlayDrawer drawer;

	private SuperThread overlayDrawerSuperThread;

	SelectedAreaSet sas;

	private Paint crossHairsPaint;

	private Paint selectedAreaPaint;

	private Paint alphaPaint;
		
	public GpsTrailerOverlay(OsmMapGpsTrailerReviewerMapActivity activity, OsmMapView maplessView, 
			SuperThread overlayDrawerSuperThread) {
		this.gtum = activity;
		this.maplessView = maplessView;
		this.overlayDrawerSuperThread = overlayDrawerSuperThread;
		
		this.crossHairsPaint = new Paint(0xFF000000);
	
//		co: alpha looks weird
		this.alphaPaint = new Paint();
		alphaPaint.setAlpha(255);
		
		this.selectedAreaPaint = new Paint(0xFF000000);
		selectedAreaPaint.setStyle(Style.STROKE);
		selectedAreaPaint.setStrokeWidth(Util.convertDpToPixel(1.5f, gtum));
		selectedAreaPaint.setPathEffect(new DashPathEffect(new float[] {
				Util.convertDpToPixel(5, gtum),
				Util.convertDpToPixel(2, gtum)}
				, 0));
		selectedAreaPaint.setAntiAlias(true);
		
		this.sas = new SelectedAreaSet(activity);
	}
	
	public void createDrawer()
	{
		if(drawer == null)
		{
			this.drawer = new GpsTrailerOverlayDrawer(maplessView, gtum, this,
					maplessView.getWidth(), maplessView.getHeight(), overlayDrawerSuperThread);
			
		}
		
	}
	
	public void shutdown()
	{
		sas.shutdown();
	}
	
	@Override
	public void draw(Canvas canvas, OsmMapView maplessView, boolean thumbDown) 
	{
		GTG.ccRwtm.registerReadingThread();
		try {		
		//this sets up requestedStbBox to the current view
		drawer.updateViewCalcAndTimeUI(maplessView);
		sas.setRequestedTime(drawer.requestedStBox.minZ, drawer.requestedStBox.maxZ);
		
		synchronized(drawer)
		{
			//if we were able to draw something into a previous view
			if(drawer.otherBitmapStBox != null)
			{
				
				Point upperLeft = new Point();
				Point lowerRight = new Point();
				//pan it to the right position and scale it in the current view
				drawer.requestedStBox.apUnitsToPixels(upperLeft,drawer.otherBitmapStBox.minX,
						 drawer.otherBitmapStBox.minY,  maplessView.getWidth(), 
						maplessView.getHeight());
				drawer.requestedStBox.apUnitsToPixels(lowerRight,drawer.otherBitmapStBox.maxX,
						 drawer.otherBitmapStBox.maxY,  maplessView.getWidth(), 
						maplessView.getHeight());
				
				if(thumbDown)
					alphaPaint.setAlpha(getThumbDownAlpha());
				else
					alphaPaint.setAlpha(getThumbUpAlpha());
				
//				Log.d(GpsTrailer.TAG,"drawing bitmap from "+upperLeft+" to "+lowerRight);
				canvas.drawBitmap(drawer.otherBitmap, null, new Rect(upperLeft.x, upperLeft.y, lowerRight.x, lowerRight.y), alphaPaint );
//						null);
				//canvas.drawBitmap(drawer.otherBitmap, 0,0,null); 
			}
		}
		
		sas.drawSelectedAreaSet(canvas, drawer.requestedStBox, selectedAreaPaint, maplessView.getWidth(),
				maplessView.getHeight());
		
		if(longPressRect.left != 0 || longPressRect.right != 0)
			canvas.drawRect(longPressRect, selectedAreaPaint);
		
		canvas.drawLine(centerCrossHairs.left, centerCrossHairs.centerY(), 
				centerCrossHairs.right, centerCrossHairs.centerY(), crossHairsPaint);
		canvas.drawLine(centerCrossHairs.centerX(), centerCrossHairs.top, 			
				centerCrossHairs.centerX(), centerCrossHairs.bottom, crossHairsPaint);
		
		
//		canvas.drawLine(lastTapX - 20, lastTapY, lastTapX+20, lastTapY, crossHairsPaint); 
//		canvas.drawLine(lastTapX, lastTapY - 20, lastTapX, lastTapY + 20, crossHairsPaint); 
		}
		finally {
			GTG.ccRwtm.unregisterReadingThread();
		}
	}
	

	private int getThumbDownAlpha() {
		return (int) (100f/(drawer.currPoints + 100)* 50)+40;
	}

	private int getThumbUpAlpha() {
		return 255;
	}

	@Override
	public boolean onTap(float x, float y, OsmMapView mapView)
	{
		if(handleTapForPhotos(x,y))
			return true;
		
		return handleTapForSelectedArea(x,y);
	}
	
	private boolean handleTapForSelectedArea(float x, float y) {
		lastTapX = (int) x;
		lastTapY = (int) y;
		
		Point p = new Point();
		
		drawer.requestedStBox.pixelsToApUnits(p, (int)x, (int)y, drawer.drawingBitmap.getWidth(), drawer.drawingBitmap.getHeight());
		

		//align to a minDepth boundary for speed when sas computes time through area
		int minDepth = GpsTrailerOverlayDrawer.getMinDepth(drawer.requestedStBox);
		int radius = (int)(Util.convertDpToPixel(prefs.clickDefaultSelectedAreaDp, gtum) 
		* drawer.requestedStBox.getWidth() / drawer.drawingBitmap.getWidth() / 2);
		
		if(!selectedAreaAddLock)
			sas.clearAreas();
		
		Area a = new Area(p.x - radius, p.y - radius,
				p.x + radius, p.y + radius, minDepth);
		if(a.y2 - a.y1 > a.x2 - a.x1)
			a.x2 = a.x1 + (a.y2 - a.y1);
		else 
			a.y2 = a.y1 + (a.x2 - a.x1);
		
		if(GTG.cacheCreator.doViewNodesIntersect(a.x1, a.y1, a.x2, a.y2))
		{
			sas.addArea(a);
			gtum.notifySelectedAreasChanged(true);
		}
		else if(!selectedAreaAddLock)
			gtum.notifySelectedAreasChanged(false);
		
		gtum.maplessView.invalidate();
		
		return true;
	}

	private boolean handleTapForPhotos(float x, float y) {
		if (!OsmMapGpsTrailerReviewerMapActivity.prefs.showPhotos)
			return false;
		
		if(drawer.requestedStBox == null)
			return true;

		GTG.ccRwtm.registerReadingThread();
		try {
			synchronized (GTG.mediaLocTimeMap) {
				GTG.mediaLocTimeMap.calcViewableMediaNodes(gtum,
						drawer.requestedStBox);

				Point p = new Point();

				int width = drawer.photoBackgroundBitmap.getWidth();

				int left = -this.gtum
						.getResources()
						.getInteger(
								R.dimen.photo_dot_with_single_large_photo_pointer_loc_x);
				int top = -this.gtum
						.getResources()
						.getInteger(
								R.dimen.photo_dot_with_single_large_photo_pointer_loc_y);

				for (Iterator<ViewMLT> i = GTG.mediaLocTimeMap.displayedViewMlts
						.iterator(); i.hasNext();) {
					// it is our responsibility to cull out view mlts that
					// should be cleared out
					final ViewMLT viewMlt = i.next();

					drawer.requestedStBox.apUnitsToPixels(p,
							viewMlt.getCenterX(), viewMlt.getCenterY(),
							drawer.drawingBitmap.getWidth(),
							drawer.drawingBitmap.getHeight());

					if (p.x + left < x && p.x + left + width > x
							&& p.y + top < y && p.y + top + width > y) {
						final ArrayList<MediaLocTime> results = new ArrayList<MediaLocTime>(
								viewMlt.totalNodes);

						// we loop through nearby medialoctimes and find the
						// ones associated with the viewmlt
						GTG.mediaLocTimeMap.rTree.query(new RTree.Processor() {

							@Override
							public boolean process(BoundedObject bo) {
								MediaLocTime mlt = (MediaLocTime) bo;

								if (mlt.viewMlt == viewMlt && !mlt.isDeleted())
									results.add(mlt);

								return true;
							}
						}, viewMlt.getGenerouslyApproximatedArea());

						if (results.size() > 1) {
							Collections.sort(results,
									new Comparator<MediaLocTime>() {

										@Override
										public int compare(MediaLocTime lhs,
												MediaLocTime rhs) {
											return lhs.getTimeSecs()
													- rhs.getTimeSecs();
										}
									});

							FragmentManager fragmentManager = gtum
									.getSupportFragmentManager();
							FragmentTransaction fragmentTransaction = fragmentManager
									.beginTransaction();

							MediaGalleryFragment fragment = new MediaGalleryFragment(
									gtum, results);
							fragmentTransaction.add(R.id.mainlayout, fragment);
							fragmentTransaction.addToBackStack(null);
							fragmentTransaction.commit();
						} else {
							//we start up the internal view image rather than going to the gallery (see below)
							//since if it's a video, android provides no way to delete it. So to be consistent,
							//we always go to the internal viewer
			            	MediaLocTime mlt = results.get(0);
			            	
			            	if(!mlt.isClean(gtum))
			            		return true;
			            	
			            	ViewImage.mAllImages = results;
			            	ViewImage.mCurrentPosition = 0;
			            	
			                Intent intent = new Intent(gtum, ViewImage.class);  
			                gtum.startInternalActivity(intent); 

			                //co: what we used to do this
//			                Util.viewMediaInGallery(
//									gtum,
//									results.get(0).getFilename(gtum.getContentResolver()),
//									results.get(0).getType() == MediaLocTime.TYPE_IMAGE);
						}
						return true;
					}
				}
			}
			// mapView.invalidate();
			return false;
		} finally {
			GTG.ccRwtm.unregisterReadingThread();
		}
	}
	
	public void setZoomCenter(int x, int y) {
		centerCrossHairs = Util.makeRectForCrossHairs(x,y, (int) Util.convertDpToPixel(20, gtum));
	}

	private Point p1 = new Point();
	private Point p2 = new Point();

	@Override
	public boolean onLongPressMove(float startX, float startY, float endX,
			float endY, OsmMapView osmMapView) {
		if(!selectedAreaAddLock)
			//PERF we only need to do this once
			sas.clearAreas();
		
		int minDepth = GpsTrailerOverlayDrawer.getMinDepth(drawer.requestedStBox);

		drawer.requestedStBox.pixelsToApUnits(p1, (int)startX, (int)startY, drawer.drawingBitmap.getWidth(), drawer.drawingBitmap.getHeight());
		drawer.requestedStBox.pixelsToApUnits(p2, (int)endX, (int)endY, drawer.drawingBitmap.getWidth(), drawer.drawingBitmap.getHeight());
		
		p1.x = AreaPanel.alignToDepth((int)p1.x, minDepth);
		p1.y = AreaPanel.alignToDepth((int)p1.y, minDepth);
		p2.x = AreaPanel.alignToDepth((int)p2.x, minDepth);
		p2.y = AreaPanel.alignToDepth((int)p2.y, minDepth);
		
		drawer.requestedStBox.apUnitsToPixels(p1, (int)p1.x, (int)p1.y, drawer.drawingBitmap.getWidth(), drawer.drawingBitmap.getHeight());
		drawer.requestedStBox.apUnitsToPixels(p2, (int)p2.x, (int)p2.y, drawer.drawingBitmap.getWidth(), drawer.drawingBitmap.getHeight());

		longPressRect.left= p1.x;
		longPressRect.top = p1.y;
		longPressRect.right = p2.x;
		longPressRect.bottom = p2.y;
		return true;
	}

	@Override
	public boolean onLongPressEnd(float startX, float startY, float endX,
			float endY, OsmMapView osmMapView) {
		
		drawer.requestedStBox.pixelsToApUnits(p1, (int)startX, (int)startY, drawer.drawingBitmap.getWidth(), drawer.drawingBitmap.getHeight());
		drawer.requestedStBox.pixelsToApUnits(p2, (int)endX, (int)endY, drawer.drawingBitmap.getWidth(), drawer.drawingBitmap.getHeight());
		
		if(p1.x > p2.x)
		{
			int t = p2.x;
			p2.x = p1.x;
			p1.x = t;
		}
		if(p1.y > p2.y)
		{
			int t = p2.y;
			p2.y = p1.y;
			p1.y = t;
		}
		
		Area a = new Area(p1.x, p1.y, p2.x, p2.y, GpsTrailerOverlayDrawer.getMinDepth(drawer.requestedStBox));
		
		sas.addArea(a);

		gtum.notifySelectedAreasChanged(true);
		
		//clear the visible selected area
		longPressRect.left = longPressRect.right = 0;
		
		return true;
	}
	
	/**
	 * the last location the user actually clicked (or dabbed their finger)
	 */
	public int lastTapX, lastTapY;
	
	public boolean tapActive = false;

	public Rect centerCrossHairs;

	private Rect longPressRect = new Rect();

	private boolean selectedAreaAddLock;
	
	public static class Preferences implements AndroidPreferences {



		public float clickDefaultSelectedAreaDp = 30;

		/**
		 * The radius from the center to the sides of a square corresponding to the gps dots that will be considered
		 * "near" where the person clicked or draped their finger for a second on the screen
		 */
		public int clickSquareRadius = 20;

		/**
		 * The max radius of the points on the graph
		 */
		public float maxGpsRadius = 5f;
		/**
		 * Gps point radius * speed in mps
		 */
		public float gpsPointRadiusPixelsXSpeed = 
			(maxGpsRadius * 0.277f / 1000f) ; // 1 km/hour


		/**
		 * The min radius of the points on the graph, excluding shadow
		 */
		public float minGpsRadiusDp = 2f;

		public float minDistanceForCompassDrawing = 10;

		/**
		 * Maximum time to calculate between redraws
		 */
		public long maxDrawCalcTime = 200;

		/**
		 * The percentage of screen size the size of smallest viewable AreaPanel should be.
		 * This prevents too many points from being drawn when they are too small to see.
		 * and saves on performance. 
		 */
		public float minPointSizePerc = .01f;

		/**
		 * The percentage of screen size the size of the biggest viewable point should be.
		 * When displaying points, we sometimes put a larger point as a placeholder while
		 * we are calculating it's components points. If it's too big, it looks really 
		 * weird to have it flicker on.
		 */
		public float maxPointSizePerc = .15f;

		/**
		 * Number of subpanels used to estimate latitude to pixel translation. (Used to 
		 * deal with the fact that google maps stretches pixels near the poles
		 */
		public int latToPixelCalcSize = 8;

		/**
		 * The amount of guesswork allowed when determining the time that the stb overlaps
		 * an area panel, in terms of a percentage of leeway of the overlap. This is used
		 * to choose a color for the area panel, so we allow a lot of leeway typically.
		 */
		public float timeTreeFuzzinessPerc = .33f;

		/**
		 * Width of lines between points
		 */
		public float lineWidthDp = .5f;

		public float pointShadowMultiplier = 1.75f;

	}

	public void setSelectedAreaAddLock(boolean b) {
		selectedAreaAddLock = b;
		
	}

}
