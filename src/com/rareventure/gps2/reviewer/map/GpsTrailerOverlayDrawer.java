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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.rareventure.android.SortedBestOfIntArray;
import com.rareventure.android.SuperThread;
import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.MediaThumbnailCache;
import com.rareventure.gps2.MediaThumbnailCache.BitmapWrapper;
import com.rareventure.gps2.R;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelCache;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;
import com.rareventure.gps2.database.cache.TimeTree;
import com.rareventure.gps2.database.cachecreator.GpsTrailerCacheCreator;
import com.rareventure.gps2.database.cachecreator.ViewNode;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.OngoingProcessEnum;

/**
 * This actually draws what needs to be displayed for the GpsTrailerOverlay (which just displays it)
 */
//TODO 3: we need a hard limit and a soft limit for number of points displayed. Once we cross the soft limit,
// we won't draw the next depth unless the stb changes

//TODD 2.1: do we really need superthreads anymore? The only reason we have them is to "pause" 
// drawer, and map tile threads as well as to shut them down. (see onPause and onDestroy in the 
//  activity)
//I suppose that's ok, but why do we have such a complicated
// object for this?
public class GpsTrailerOverlayDrawer extends SuperThread.Task
{
	private boolean viewUpToDate = true;
	public Bitmap drawingBitmap, otherBitmap;
	public Canvas drawingCanvas, otherCanvas;
	public int[] paintColors;
	
	private OsmMapView maplessView;
	public OsmMapGpsTrailerReviewerMapActivity activity;
	public int latestOnScreenPointSec;
	public int earliestOnScreenPointSec;
	
	private Paint shadowPaint;
	private Paint pointPaint;
	
	/**
	 * This is what we request to be drawn next
	 */
	public AreaPanelSpaceTimeBox requestedStBox;

	/**
	 * This is what was last drawn and is in otherBitmap.. make sure to synchronize if you access this
	 */
	public AreaPanelSpaceTimeBox otherBitmapStBox;

	private GpsTrailerOverlay overlay;
	
	Handler myHandler = new Handler(Looper.getMainLooper()) {

		@Override
		public void handleMessage(Message msg) {
			activity.createNewUserLocation(lastCalculatedGpsLatM, lastCalculatedGpsLonM, lastCalculatedRadius);
		}
		
	};
	
	private float lastCalculatedRadius;
	private int lastCalculatedGpsLatM;
	private int lastCalculatedGpsLonM;
	public Bitmap photoBackgroundBitmap;
	public Bitmap multiPhotoBackgroundBitmap;
	private MediaThumbnailCache bitmapCache;
	public int minCirclePxRadius;
	private Paint linePaint;
	private Bitmap videoTagBitmap;
	public int currPoints;
	public static  boolean doMethodTracing;
	
	private int minTimeTreeLengthForLineCalc = Integer.MAX_VALUE;

	/**
	 * The number of view nodes to process between redrawing the picture
	 * Each view node processes ViewNode.MAX_CALCULATED_LINES_PER_ROUND lines
	 * per round
	 */
	private static final int VIEW_NODES_TO_CALC_LINES_FOR_PER_ROUND = 50;
	
	/**
	 * The maximum number of lines to draw on the screen
	 */
	private static final int MAX_LINES = 2000;
	private static final int BEST_LARGEST_TIME_TREE_LENGTH_FOR_UNCREATED_LINES_LIST_TOTAL = 20;
	private static final int NUM_COLORS = 32;
	private static final int MAX_TIME_DRAWING_BEFORE_DISPLAY_NOTICE_MS = 500;
	

	public GpsTrailerOverlayDrawer(OsmMapView maplessView, OsmMapGpsTrailerReviewerMapActivity activity, GpsTrailerOverlay overlay,
			int w, int h, SuperThread overlayDrawerSuperThread)
	{
		super(GTG.GPS_TRAILER_OVERLAY_DRAWER_PRIORITY);
		this.maplessView = maplessView;
		this.activity = activity;
		this.overlay = overlay;
		overlayDrawerSuperThread.addTask(this);
		
    	// create a bitmap with a rect, used for the "src" image
        drawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawingCanvas = new Canvas(drawingBitmap);
        otherBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        otherCanvas = new Canvas(otherBitmap);
        
        shadowPaint = new Paint();
        shadowPaint.setStyle(Style.FILL);
        shadowPaint.setARGB(255, 80, 80, 80);
        
		float strokeWidth = Math.max(1,Util.convertDpToPixel(GpsTrailerOverlay.prefs.lineWidthDp, activity));

        linePaint = new Paint();
        linePaint.setStrokeWidth(strokeWidth);
        linePaint.setARGB(255, 80, 80, 80);
        linePaint.setAntiAlias(true);

        pointPaint = new Paint();
		pointPaint.setStyle(Paint.Style.FILL);
		pointPaint.setStrokeWidth(strokeWidth);
		
		paintColors = new int[NUM_COLORS];
		
		updateForColorRangeChange();

		photoBackgroundBitmap = ((BitmapDrawable) activity.getResources().
				getDrawable(R.drawable.photo_dot_with_single_large_photo)).getBitmap();
		multiPhotoBackgroundBitmap = ((BitmapDrawable) activity.getResources().
				getDrawable(R.drawable.photo_dot_with_multi_large_photo)).getBitmap();
		
		bitmapCache = new MediaThumbnailCache(activity, 
				photoBackgroundBitmap.getWidth() - 
				activity.getResources().getInteger(R.dimen.photo_dot_with_single_large_photo_border)*2,
				photoBackgroundBitmap.getWidth() - 
				activity.getResources().getInteger(R.dimen.photo_dot_with_single_large_photo_border)*2);
		
		minCirclePxRadius = (int) Util.convertDpToPixel(GpsTrailerOverlay.prefs.minGpsRadiusDp, activity); 
    }
	
	public void updateForColorRangeChange() {
		// MEMPERF PERF consider just changing the color everytime
		for (int i = 0; i < NUM_COLORS; i++) {
			int result = 0;

			int c1i = i * (OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange.length - 1) / (NUM_COLORS - 1);
			int c2i = c1i + 1;
			if (c2i > OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange.length - 1)
				c2i = c1i;

			for (int j = 0xFF; j > 0; j = j << 8) {
				int c1 = OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange[c2i] & j;
				int c0 = OsmMapGpsTrailerReviewerMapActivity.prefs.colorRange[c1i] & j;
				result |= ((int) (((double) c1 - c0) * i / NUM_COLORS + c0)) & j;
			}

			result |= 0xFF000000;

			paintColors[i] = result;
		}
	}
	
	public void doWork()
	{
		//MEMPERF: another option is to just wait until the cache is up to date, 
		//and use a single buffer. The only thing is if that buffer takes a long
		//time to draw, we won't have anything to display until it's done
		
		
		AreaPanelSpaceTimeBox localApStbox = null;
		
		 
		synchronized (this)
		{
			if(viewUpToDate)
			{
				activity.notifyDoneProcessing(OngoingProcessEnum.DRAW_POINTS);
				//wait for the cache to need to be changed or we're ready to retry looking for new points
				stWait(0,this);		
				return;
			}
		}

		//we do this check because if viewUpToDate is false, the above check won't ever be called,
		//but if its true, then this check won't be called
		abortOrPauseIfNecessary();
		
		GTG.ccRwtm.registerReadingThread();
		if(doMethodTracing)
			Debug.startMethodTracing("/sdcard/it.trace");
		try {
			
			boolean stillMoreNodesToCalc = true;
			boolean allLinesCalculated = false;
			
			long timeStartedDrawingMs = System.currentTimeMillis();
			
			while((!allLinesCalculated || stillMoreNodesToCalc) && !superThread.manager.isShutdown)
			{
				if(System.currentTimeMillis() - timeStartedDrawingMs > MAX_TIME_DRAWING_BEFORE_DISPLAY_NOTICE_MS)
					activity.notifyProcessing(OngoingProcessEnum.DRAW_POINTS);
				
				if(!distanceUpToDate)
				{
					int startTime, endTime;
					synchronized(this)
					{
						startTime = requestedStBox.minZ;
						endTime = requestedStBox.maxZ;
					}
					
					activity.notifyDistUpdated(GpsTrailerCacheCreator.calcTrDist(startTime, endTime, null));
					
					synchronized(this)
					{
						distanceUpToDate = true;
					}
				}
					
				synchronized(this)
				{
					if(localApStbox == null || !localApStbox.equals(requestedStBox))
					{
						//copy the stbox to local box, so if it changes we won't get weirded out.
						localApStbox = requestedStBox;
						stillMoreNodesToCalc = true;
					}
				}

				
				long startTime = System.currentTimeMillis();
				
				int minDepth = getMinDepth(localApStbox);
				
				TimeTree.hackTimesLookingUpTimeTrees = 0;
				
				while(System.currentTimeMillis() - startTime < GpsTrailerOverlay.prefs.maxDrawCalcTime 
						&& stillMoreNodesToCalc)
				{
					int status = GTG.cacheCreator.calcViewableNodes(localApStbox, minDepth,
							earliestOnScreenPointSec, latestOnScreenPointSec);
					
					stillMoreNodesToCalc = ((status & GpsTrailerCacheCreator.CALC_VIEW_NODES_STILL_MORE_NODES_TO_CALC) != 0);
					
					//if there were some view nodes that were reset or new ones created and the lines need to be recalculated
					if((status & GpsTrailerCacheCreator.CALC_VIEW_NODES_LINES_NEED_RECALC) != 0)
					{
						minTimeTreeLengthForLineCalc = Integer.MAX_VALUE;
						lastNumberOfViewNodesLinesCalculatedFor = Integer.MAX_VALUE;
					}
				}
				
	
				/* ttt_installer:remove_line */Log.d("GPS","times looking up time trees is "+TimeTree.hackTimesLookingUpTimeTrees);
	
				allLinesCalculated = calcLines(localApStbox, minDepth);
						
				drawingBitmap.eraseColor(0x00000000);
					
				//PERF: it would be faster to store the pixel locations
				
				//if while calculating all lines, we got to the maximum line count, and drawLines deletes
				//some of them, we  have to recalculate to add some more back in
				if(allLinesCalculated && isAtMaxLines())
				{
					if(drawLines(localApStbox, minDepth))
					{
						/* ttt_installer:remove_line */Log.d("GPS", "setting all lines calculated false");
						allLinesCalculated = false;
					}
				}
				else {
					drawLines(localApStbox, minDepth);
				}

				currPoints = drawPoints(true, localApStbox);
				
				drawPoints(false, localApStbox);
				
				if(OsmMapGpsTrailerReviewerMapActivity.prefs.showPhotos)
					drawMedia(localApStbox);
				
				boolean stBoxNotChanged;
		
				synchronized(this)
				{
					//switch the drawing and the displaying canvas and bitmap
					Canvas t = drawingCanvas;
					drawingCanvas = otherCanvas;
					otherCanvas = t;
					
					Bitmap bt = drawingBitmap;
					drawingBitmap = otherBitmap;
					otherBitmap = bt;
					
					//we need to know what we were drawing, so that we can do any last minute changes
					//(such as a pan) in the overlay drawer
					otherBitmapStBox = localApStbox;
					
					//we only say view is up to date if its up to date according to the requeted stbox 
					// (which may have changed while we were calculating)
					if((stBoxNotChanged = otherBitmapStBox.equals(requestedStBox)) && allLinesCalculated
							&& !stillMoreNodesToCalc)
						viewUpToDate = true;
				}
				
				if(stBoxNotChanged)
				{
					activity.runOnUiThread(activity.NOTIFY_HAS_DRAWN_RUNNABLE);
				}
				
			}
		}
		finally {
			if(doMethodTracing)
			{
				doMethodTracing = false;
				Debug.stopMethodTracing();
			}
			
			GTG.ccRwtm.unregisterReadingThread();
			activity.notifyDoneProcessing(OngoingProcessEnum.DRAW_POINTS);
			
		}
	}
	
	private boolean isAtMaxLines() {
		return GTG.cacheCreator.startTimeToViewLine.size() >= MAX_LINES;
	}
	private AreaPanelSpaceTimeBox drawMediaRunnableApStBox = null;
	
	private Runnable DRAW_MEDIA_RUNNABLE = new Runnable() {
		
		@Override
		public void run() {
			//do photo processing
			GTG.mediaLocTimeMap.calcViewableMediaNodes(activity, drawMediaRunnableApStBox);

			Point p = new Point();
			
			Paint mediaPaint = new Paint();
			
			synchronized (GTG.mediaLocTimeMap)
			{
				//sort the viewmlts by y direction so they look right from a 3dish
				//perspective 
				ArrayList<ViewMLT> viewMlts = 
					new ArrayList<ViewMLT>(GTG.mediaLocTimeMap.displayedViewMlts);
				
				Collections.sort(viewMlts, 
						new Comparator<ViewMLT>() {

							@Override
							public int compare(ViewMLT lhs, ViewMLT rhs) {
								return lhs.getCenterY() - rhs.getCenterY();
							}
				});
				
				for (Iterator<ViewMLT> i = viewMlts.iterator(); i.hasNext();) {
					//it is our responsibility to cull out view mlts that should be cleared out
					ViewMLT viewMlt = i.next();
					
					int centerX = viewMlt.getCenterX(); 
					int centerY = viewMlt.getCenterY();
					
					drawMediaRunnableApStBox.apUnitsToPixels(p, centerX, centerY, 
							drawingBitmap.getWidth(), drawingBitmap.getHeight());
		
					//HACK TO DRAW WIDTH'S
		//			mediaPaint.setColor(Color.MAGENTA);
		//			mediaPaint.setStrokeWidth(3);
		//			mediaPaint.setStyle(Style.STROKE);
		//			Point h1 = new Point(),h2 = new Point();
		//			drawMediaRunnableApStBox.apUnitsToPixels(h1, centerX-viewMlt.width/2, centerY-viewMlt.width/2, 
		//					drawingBitmap.getWidth(), drawingBitmap.getHeight());
		//			drawMediaRunnableApStBox.apUnitsToPixels(h2, centerX+viewMlt.width/2, centerY+viewMlt.width/2, 
		//					drawingBitmap.getWidth(), drawingBitmap.getHeight());
		//			drawingCanvas.drawRect(h1.x, h1.y, h2.x, h2.y, mediaPaint);
		//			mediaPaint.setColor(Color.BLUE);
		//			mediaPaint.setStrokeWidth(1);
		//			mediaPaint.setTextSize(12);
		//			drawingCanvas.drawText(viewMlt.totalNodes+"", h1.x, h1.y, mediaPaint);
		
					Bitmap bitmap = null;
					
					//make sure the media actually exists... the thumbnail can sometimes exist without
					//the actual image, so we do this check
					if(viewMlt.firstMlt.isClean(activity))
					{
						BitmapWrapper bitmapWrapper = bitmapCache.getBitmapWrapper(viewMlt.firstMlt.getCacheId());
						if(bitmapWrapper != null)
							bitmap = bitmapWrapper.bitmap;
					}
					
					/* ttt_installer:remove_line */Log.d(GTG.TAG, "drawing photo "+viewMlt.firstMlt+" at "+p);
					
					int left, top;
					
					if(viewMlt.totalNodes == 1)
					{
						left = p.x - activity.getResources().getInteger(R.dimen.photo_dot_with_single_large_photo_pointer_loc_x);
						top = p.y - activity.getResources().getInteger(R.dimen.photo_dot_with_single_large_photo_pointer_loc_y);
						
						drawingCanvas.drawBitmap(photoBackgroundBitmap, left, top, mediaPaint);
						
						left += activity.getResources().getInteger(R.dimen.photo_dot_with_single_large_photo_border);
						top += activity.getResources().getInteger(R.dimen.photo_dot_with_single_large_photo_border);
					}
					else
					{
						left = p.x - activity.getResources().getInteger(R.dimen.photo_dot_with_multi_large_photo_pointer_loc_x);
						top = p.y - activity.getResources().getInteger(R.dimen.photo_dot_with_multi_large_photo_pointer_loc_y);
						
						drawingCanvas.drawBitmap(multiPhotoBackgroundBitmap, left, top, mediaPaint);
						
						left += activity.getResources().getInteger(R.dimen.photo_dot_with_multi_large_photo_border_x);
						top += activity.getResources().getInteger(R.dimen.photo_dot_with_multi_large_photo_border_y);

					}
					
					if (bitmap != null) {
						drawingCanvas.drawBitmap(bitmap, left, top, mediaPaint);
						
						if(viewMlt.firstMlt.isVideo())
						{
							if(videoTagBitmap == null)
								videoTagBitmap = Bitmap.createScaledBitmap(
										((BitmapDrawable) activity.getResources().
												getDrawable(R.drawable.small_video_indicator)).getBitmap(), 
												bitmap.getWidth(), bitmap.getHeight(), false);
							drawingCanvas.drawBitmap(videoTagBitmap, left, top, mediaPaint);
						}
					}
					//else we were unable to display the photo, we assume its because it was deleted
					else
					{
						//this will clear it out of the rtree, and eventually the database
						GTG.mediaLocTimeMap.notifyMltNotClean(viewMlt.firstMlt);
					}
				}
			}
			
		}
	};


	private void drawMedia(AreaPanelSpaceTimeBox apStBox) {
		drawMediaRunnableApStBox = apStBox;
		
		//TODO 2.1 I thought running on the ui thread would help but it appear, sometimes the wrong
		// image is displayed in a thumbnail. I think that we may need to create our own thumbnails.???
		
		//co: when we shutdown we're on the main thread, which waits for this thread to
		//complete so we would have to find a way to break out of this deadlock. furthermore,
		//running on the ui thread does not seem to help
//		Util.runOnUiThreadSynchronously(activity, DRAW_MEDIA_RUNNABLE); 
		
		DRAW_MEDIA_RUNNABLE.run();
	}

	private boolean distanceUpToDate;

	public int closestToCenterTimeSec;
	
	/**
	 * For each round when we calculate lines for viewnodes, we must calculate the most
	 * important lines first, which we determine by the time length with the time trees of the nodes.
	 * This is used to keep track of the view nodes with the biggest time jumps that we didn't
	 * process this round, so that next round, we know which ones to start from
	 */
	private SortedBestOfIntArray bestLargestTimeTreeLengthForUncreatedLinesList = new SortedBestOfIntArray(BEST_LARGEST_TIME_TREE_LENGTH_FOR_UNCREATED_LINES_LIST_TOTAL);
	
	/**
	 * We use this to determine how many lines at a time should be pulled from each viewnode. At the beginning, we pull very few, but as
	 * the number of active nodes becomes less and less, we pull more and more, so we don't have to keep relooping over and over.
	 * The reason we pull a few at first is because we want to give priority to viewnodes with few lines coming from them, because
	 * this often means an easily viewable trail
	 */
	private int lastNumberOfViewNodesLinesCalculatedFor = Integer.MAX_VALUE;;
	
	/**
	 * Updates startTimeToViewLines and endTimeToViewLines with visible lines. 
	 * @param stillMoreNodesToCalc if there are still more view nodes to calculate,
	 *  we will only calculate lines for the ends of the viewnodes
	 * 
	 * @return true if there are no more lines to calculate, false otherwise
	 */
	private boolean calcLines(AreaPanelSpaceTimeBox apStBox, int minDepth)
	{
		
//		float metersPerApUnits = activity.calcMetersPerApUnits();
		
//		Log.d(GTG.TAG,"vn calcLines ----");
		
		//note, technically, this isn't needed, since writing to the view nodes will
		//never interfere with code at this point to read them. But to make the code
		//clearer, we do it anyway
		GTG.cacheCreator.viewNodeThreadManager.registerReadingThread();
		
		int numberOfViewNodesLinesCalculatedFor = 0;
		
		try {		
			Iterator<ViewNode> iter = GTG.cacheCreator.getViewNodeIter();
			
			ArrayList<TimeTree> scratchPath = new ArrayList<TimeTree>();
			
			//if there are no visible ap's (view nodes)
			if(!iter.hasNext())
			{
				//we still may need to draw a line. Consider, A at 10:00,
				// B at 11:00 and the time range at 10:30 - 10:35
				AreaPanel topAp = GTG.apCache.getTopRow();
				
				//if there is no data at all
				if(topAp == null || topAp.getTimeTree() == null)
					return true;
				
				//start and end ap are the same because we are in the middle of the line,
				//and we don't know which one we'll get
				AreaPanel ap = topAp.getChildApAtDepthAndTime(minDepth, apStBox.minZ);
				
				//if there isn't an ap, we must be off the edge of the start and end times
				if(ap == null)
					return true;
				
				TimeTree tt = ap.getTimeTree().getBottomLevelEncompassigTimeTree(apStBox.minZ);
				
				ViewLine vl;
				
				int endCutTime = tt.calcTimeRangeCutEnd();
				
				if(endCutTime <= apStBox.minZ)
				{
					vl = new ViewLine(endCutTime, tt.getMaxTimeSecs());
					
					vl.startApId = ap.id;
					vl.endApId = tt.getNextApId();
				}
				else
				{
					vl = new ViewLine(tt.getMinTimeSecs(), tt.calcTimeRangeCutStart());
					
					vl.startApId = tt.getPrevApId();
					vl.endApId = ap.id;
				}
								
				GTG.cacheCreator.startTimeToViewLine.put(vl.startTimeSec, vl);
				GTG.cacheCreator.endTimeToViewLine.put(vl.endTimeSec, vl);
				
				return true;
			}
			
			//PERF: we could combine this with drawPoints and not iterate twice (and draw lines after drawing points shadow)
			while(iter.hasNext()) {
				ViewNode vn = iter.next();
				
				//if we need to calculate lines for the view node... we don't do this for
				// ap's not at min depth, since they'll be replaced anyway
				if(vn.largestTimeTreeLengthForUncreatedLines >= minTimeTreeLengthForLineCalc && vn.ap().getDepth() == minDepth)
				{
					vn.calcLinesForStBox(scratchPath, apStBox,
							GTG.cacheCreator.startTimeToViewLine, GTG.cacheCreator.endTimeToViewLine,
							lastNumberOfViewNodesLinesCalculatedFor);

					numberOfViewNodesLinesCalculatedFor++;
					
					if(numberOfViewNodesLinesCalculatedFor > VIEW_NODES_TO_CALC_LINES_FOR_PER_ROUND)
						//this is a fine point. The first round, we need to process all the view nodes 
						//to get their start and end times done first. So we don't want to change
						//minTimeTreeLengthForLineCalc until that is done. However, when the ends are
						//finished, we need to choose the next minTimeTreeLengthForLineCalc, so at that
						//time we will exit the while loop. This means a lot of restarting the iterator
						// and skipping view nodes, but since calculating view nodes will change the
						//underlying tree, I can't see reusing the iterator to be very easy
						//PERF reuse the iterator
						return false;
					
					if(isAtMaxLines())
					{
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"Reached max lines: "+GTG.cacheCreator.startTimeToViewLine.size()+" minTimeTreeLengthForLineCalc is "+minTimeTreeLengthForLineCalc);
						return true;
					}
				}
				
			}
			
			//we've gone through the whole list of viewnodes for this round
			bestLargestTimeTreeLengthForUncreatedLinesList.clear();
			
			iter = GTG.cacheCreator.getViewNodeIter();
			
			while(iter.hasNext())
			{
				ViewNode vn = iter.next();
				
				if(vn.ap().getDepth() != minDepth)
					continue;
				
				bestLargestTimeTreeLengthForUncreatedLinesList.add(vn.largestTimeTreeLengthForUncreatedLines);
			}
			
			//if there are no time gaps that are more finely grained then those we have already
			//created viewlines for
			if(bestLargestTimeTreeLengthForUncreatedLinesList.data[BEST_LARGEST_TIME_TREE_LENGTH_FOR_UNCREATED_LINES_LIST_TOTAL-1] <= 0)
				return true; ///we're done
			
			//choose a min time jump for next round
			minTimeTreeLengthForLineCalc = bestLargestTimeTreeLengthForUncreatedLinesList.data[0];
			
			return false;
		}
		finally {
			GTG.cacheCreator.viewNodeThreadManager.unregisterReadingThread();
			
			lastNumberOfViewNodesLinesCalculatedFor = numberOfViewNodesLinesCalculatedFor;

			/* ttt_installer:remove_line */Log.d(GTG.TAG,"numberOfViewNodesLinesCalculatedFor: "+numberOfViewNodesLinesCalculatedFor+" lines: "+GTG.cacheCreator.startTimeToViewLine.size()+
			/* ttt_installer:remove_line */		" minTimeTreeLengthForLineCalc is "+minTimeTreeLengthForLineCalc+
			/* ttt_installer:remove_line */		" blttlfull0="+bestLargestTimeTreeLengthForUncreatedLinesList.data[0]
			/* ttt_installer:remove_line */				+" blttlfullMAX="+bestLargestTimeTreeLengthForUncreatedLinesList.data[BEST_LARGEST_TIME_TREE_LENGTH_FOR_UNCREATED_LINES_LIST_TOTAL-1]);
		}
		
	}

	/**
	 * This both draws and calculates lines at the same time. We do this together so
	 * we don't have to iterate through all the ap's twice. The strategy here is
	 * to incrementally calculate some lines, draw our work, and keep looping
	 * until all the lines are calculated and drawn. By doing it this way,
	 * we show our work to the user periodically as we draw more and more lines
	 * 
	 * @param apStBox
	 * @param minDepth
	 * @return
	 */
	//TODO 3: update lines calculated for viewnodes when the time of the stb changes... later: not sure what this means??
	//  moving back in forth in time works fine for lines
	private boolean drawLines(AreaPanelSpaceTimeBox apStBox, int minDepth) {
		
//		float metersPerApUnits = activity.calcMetersPerApUnits();
		
		/* ttt_installer:remove_line */Log.d(GTG.TAG,"vn drawLines ----");
		
		boolean removedLines = false;
		
		for(Iterator<Entry<Integer, ViewLine>> i = GTG.cacheCreator.startTimeToViewLine.entrySet().iterator(); i.hasNext();)
		{
			Entry<Integer, ViewLine> e = i.next();
			
			ViewLine vl = e.getValue();
			
			if(vl.endTimeSec > apStBox.minZ && vl.startTimeSec < apStBox.maxZ)
			{
				AreaPanel priorAp = vl.getStartAp();			
				
				if(priorAp.getDepth() == minDepth)
				{
					AreaPanel nextAp = vl.getEndAp();			
					//if the start or end part of the line is within the area panel
					if(apStBox.contains(priorAp.getX(), priorAp.getY()) || apStBox.contains(nextAp.getX(), nextAp.getY()))
					{
						drawLine(priorAp,nextAp, apStBox);
						continue;
					}
				}
			}
			
			//if we didn't draw the line, we remove it here
			i.remove();
			
			GTG.cacheCreator.endTimeToViewLine.remove(vl.endTimeSec);
			
			removedLines = true;
		}
		
		return removedLines;
	}

	private void drawLine(AreaPanel ap1, AreaPanel ap2,
			AreaPanelSpaceTimeBox apStBox) {
		if(ap1 == null || ap2 == null)
			return;
		
		//if one of the aps is out of time range
		if(ap2.getStartTimeSec() >= apStBox.maxZ ||
				ap1.getEndTimeSec() <= apStBox.minZ)
			return;
		
		//PERF if points are directly next to each other, don't draw lines
		
		int apX1 = ap1.getCenterX();
		int apY1 = ap1.getCenterY();
		
		int apX2 = ap2.getCenterX();
		int apY2 = ap2.getCenterY();
		
		if(apX2 - apX1 > AreaPanel.MAX_AP_UNITS>>1)
		{
			
			int yAtEdge = apY1+ (int) (((long)apX1)*(apY2 - apY1)/(apX1 + AreaPanel.MAX_AP_UNITS - apX2));

			drawLineBetweeenApPoints(apStBox, apX1, apY1, 0, yAtEdge);
			drawLineBetweeenApPoints(apStBox, AreaPanel.MAX_AP_UNITS-1, yAtEdge, apX2, apY2);
		}
		else if(apX1 - apX2 > AreaPanel.MAX_AP_UNITS>>1)
		{
			
			int yAtEdge = apY1 + (int) (((long)AreaPanel.MAX_AP_UNITS - apX1)*(apY2 - apY1)/(apX2 + AreaPanel.MAX_AP_UNITS - apX1));

			drawLineBetweeenApPoints(apStBox, apX1, apY1, AreaPanel.MAX_AP_UNITS-1, yAtEdge);
			drawLineBetweeenApPoints(apStBox, 0, yAtEdge, apX2, apY2);
		}
		else
			drawLineBetweeenApPoints(apStBox, apX1, apY1, apX2, apY2);
	}
	
	private static Point p1 = new Point();
	private static Point p2 = new Point();

	private void drawLineBetweeenApPoints(AreaPanelSpaceTimeBox stBox, int apX1, int apY1, 
			int apX2, int apY2) {
		
		stBox.apUnitsToPixels(p1,
				apX1, apY1,
				drawingBitmap.getWidth(), drawingBitmap.getHeight());
		stBox.apUnitsToPixels(p2,
				apX2, apY2,
				drawingBitmap.getWidth(), drawingBitmap.getHeight());

		drawingCanvas.drawLine(p1.x, p1.y, p2.x, p2.y,
				linePaint);
	}

	private static int getMaxDepth(AreaPanelSpaceTimeBox stBox)
	{
		/*index of the search key, if it is contained in the array; otherwise, (-(insertion point) - 1).
		 *  The insertion point is defined as the point at which the key would be inserted into the array: 
		 *  the index of the first element greater than the key, or a.length if all elements in the array 
		 *  are less than the specified key. Note that this guarantees that the return value will be >= 0 
		 *  if and only if the key is found.*/
			
		int index = Arrays.binarySearch(AreaPanel.DEPTH_TO_WIDTH, (int)(stBox.getWidth() * GpsTrailerOverlay.prefs.maxPointSizePerc));
		
		//if we exactly matched a depth, that will be the max depth
		if(index >= 0)
			return index;
		//if our max depth is below the minimum depth
		if (index == -1)
			return 0;
		//otherwise we want the depth 1 below our max
		return -index - 2;
	}
	
	public static int getMinDepth(AreaPanelSpaceTimeBox apStbox)
	{
		int index = Arrays.binarySearch(AreaPanel.DEPTH_TO_WIDTH, 
				(int)(apStbox.getWidth() * GpsTrailerOverlay.prefs.minPointSizePerc));

		//if we exactly matched a depth, that will be the min depth
		if(index >= 0)
			return index;
		//if our min depth is below the level 0 depth
		if (index == -1)
			return 0;
		//otherwise we want the depth 1 above our min
		//TODO 3 or should it be one below our min?
		return -index - 1;
	}

	/**
	 * @return number of points drawn
	 */
	private int drawPoints(boolean drawShadow, AreaPanelSpaceTimeBox apStBox)
	{
		//note, technically, this isn't needed, since writing to the view nodes will
		//never interfere with code at this point to read them. But to make the code
		//clearer, we do it anyway
		GTG.cacheCreator.viewNodeThreadManager.registerReadingThread();
		
		try {		
			long time = System.currentTimeMillis();
			
			int localEarliestOnScreenPointSec = Integer.MAX_VALUE;
			int localLatestOnScreenPointSec = Integer.MIN_VALUE;
			
			int points = 0;
			
			int maxDepth = getMaxDepth(apStBox);
			
			Iterator<ViewNode> iter = GTG.cacheCreator.getViewNodeIter();
			
			Point p = new Point();
			
			Point p2 = new Point();
			
			float closestPointDistSquared = Float.MAX_VALUE;
			int closestToCenterEndTimeSec = 0;
			AreaPanel closestToCenterAp = null;
			
			while(iter.hasNext()) {
				ViewNode vn = iter.next();
				
				AreaPanel row = vn.ap();
				
//				if(drawShadow)
//					Log.d("GPS","drawing "+row+" with "+row.getTimeTree());
				
				//skip any row that's "too big" to display (and will look weird)
//				if(row.getDepth() > maxDepth)
//					continue;
				
				points++;
				
				
				apStBox.apUnitsToPixels(p, row.getCenterX(), row.getCenterY(),
						drawingBitmap.getWidth(), drawingBitmap.getHeight()
						);
				
				if(localLatestOnScreenPointSec < vn.overlappingRange[1])
					localLatestOnScreenPointSec = vn.overlappingRange[1];

				if(localEarliestOnScreenPointSec > vn.overlappingRange[0])
					localEarliestOnScreenPointSec = vn.overlappingRange[0];
	
				//TODO 3: hack to make big points fat
				
				//if we are not at zero depth we choose a minimum size based
				// on the view node depth
				if(row.getDepth() != 0)
					apStBox.apUnitsToPixels(p2, 
							row.getMaxX(),
							row.getMaxY(), 
							drawingBitmap.getWidth(), drawingBitmap.getHeight());
				else
				{
					//otherwise we use the minimum point size always
					p2.x = p.x; p2.y = p.y;
				}
				
				float speedMult = calcSpeedMult(vn, row.getDepth());
				
				int circleRadiusPx = (int) (Math.max(minCirclePxRadius, 2*(p2.x-p.x)) * speedMult);
			
				if(drawShadow)
				{
					circleRadiusPx = (int) (circleRadiusPx * GpsTrailerOverlay.prefs.pointShadowMultiplier);
					drawingCanvas.drawCircle(p.x, p.y, circleRadiusPx , 
							shadowPaint);
				}
				else
				{
					int paintIndex = figurePaintIndex(vn.overlappingRange[0],vn.overlappingRange[1]);
					
					pointPaint.setColor(paintColors[paintIndex]);
					drawingCanvas.drawCircle(p.x, p.y, circleRadiusPx ,  
								pointPaint);
					
				}

				float distSquared = Util.square(overlay.centerCrossHairs.centerX() - p.x) + Util.square(overlay.centerCrossHairs.centerY() - p.y);
				
				if(distSquared < closestPointDistSquared)
				{
					closestToCenterEndTimeSec = vn.overlappingRange[1];
					closestToCenterAp = row;
					closestPointDistSquared = distSquared;
				}
	
			} //while examining points
			
			if(closestToCenterAp != null)
			{
				//note that we don't get the bottom level here because the overlapping range may contain some fuzziness
				TimeTree tt = closestToCenterAp.getTimeTree().getEncompassigTimeTreeOrMaxTimeTreeBeforeTime(closestToCenterEndTimeSec-1, false);
				closestToCenterTimeSec = tt.calcTimeRangeCutEnd();
			}
			
			/* ttt_installer:remove_line */Log.d("GPS","drew "+points+" points");
			
			//note that we only need to do this for draw shadow, but we're doing this for all
			//also note that there is a thread race with the main thread and time view here
			//but I don't think this will affect much
			//TODO 2.5 should we synchronize here? It's growing with the addition of colorRangeStartEndSec
			latestOnScreenPointSec = localLatestOnScreenPointSec;
			earliestOnScreenPointSec = localEarliestOnScreenPointSec;
			
			return points;
		}
		finally
		{
			GTG.cacheCreator.viewNodeThreadManager.unregisterReadingThread();
		}
	}

	//see depth_to_max_seconds.ods
	private static float [] DEPTH_TO_MAX_SECONDS = new float []
			{
		0.625f,
		1.25f,
		2.5f,
		5f,
		10f,
		20f,
		40f,
		80f,
		160f,
		320f,
		640f,
		1280f,
		2560f,
		5120f,
		10240f,
		20480f,
		40960f,
		81920f,
		163840f,
		327680f,
		655360f,
		1310720f,
		2621440f,
		5242880f,
		10485760f,
		20971520f,
		41943040f
			};
	
	private float calcSpeedMult(ViewNode vn, int depth) {
		return .7f* (1-DEPTH_TO_MAX_SECONDS[depth]/(DEPTH_TO_MAX_SECONDS[depth]+(vn.overlappingRange[1] - vn.overlappingRange[0])))+.3f;
	}

	private int figurePaintIndex(int startTimeSec, int endTimeSec) {
		//we convert everything to long to avoid overflow problems
		//co: used to use average time
//		int val = (int)( ((long)(startTimeSec>>1) + (endTimeSec>>1) - earliestOnScreenPointSec)
//				* (paint.length) / ((latestOnScreenPointSec - earliestOnScreenPointSec)+1));
		
		//we use the latest time that the point was visited. If we use the average point, and
		//lets say the user started in a position, traveled in a circle and came back, then
		//the color would be green, or represent the user was there at the midpoint of the trip.
		//This is quite confusing, so we go with the idea that the later points "paint over" the
		//earlier ones when the user was there more than once
		int val = (int)( ((long)endTimeSec - earliestOnScreenPointSec)
				* (NUM_COLORS) / ((latestOnScreenPointSec - earliestOnScreenPointSec)+1));
		
		if (val > NUM_COLORS - 1)
			return NUM_COLORS - 1;
		if (val < 0)
			return 0;

		return val;
	}

	/**
	 * Called by UI thread only
	 */
	public void updateViewCalcAndTimeUI(OsmMapView maplessView) {
		synchronized(this)
		{
			AreaPanelSpaceTimeBox newStBox = maplessView.getCoordinatesRectangleForScreen();
			
			//we access the min and max time from the activity which is altered by the main ui thread
			newStBox.minZ = OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec;
			newStBox.maxZ = OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec +
					OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePeriodSec;
			
			if(requestedStBox != null)
				newStBox.pathList = requestedStBox.pathList;
			
			if(requestedStBox == null || !requestedStBox.equals(newStBox))
			{
				viewUpToDate = false;
				
				if(requestedStBox == null || requestedStBox.minZ != newStBox.minZ || requestedStBox.maxZ != newStBox.maxZ)
					distanceUpToDate = false;
				
				requestedStBox = newStBox;
				
				
				this.stNotify(this);
			}
		}
	}
	
	public void notifyViewNodesChanged()
	{
		synchronized (this)
		{
			if(requestedStBox != null)
			{
				viewUpToDate = false;
				distanceUpToDate = false;
				this.stNotify(this);
			}
		}
	}

	public void clearGalleryCache() {
		synchronized (bitmapCache) {
			bitmapCache.clear();
		}
	}
	
	public void notifyPathsChanged() {
		//TODO 3 technically we should register as a reading thread, but we are actually
		//being called by the writing thread, so registering the reading thread here would
		//cause the rwtm to deadlock the thread on itself.. maybe we should make this is a 
		//noop for the writing thread and
		// use a  thread local to identify this?
//		overlay.sas.rwtm.registerReadingThread();
		synchronized (this)
		{
			requestedStBox = new AreaPanelSpaceTimeBox(requestedStBox);
			requestedStBox.pathList = overlay.sas.getResultPaths();
			viewUpToDate = false;
			this.stNotify(this);
		}
//		overlay.sas.rwtm.unregisterReadingThread();
	}

}
