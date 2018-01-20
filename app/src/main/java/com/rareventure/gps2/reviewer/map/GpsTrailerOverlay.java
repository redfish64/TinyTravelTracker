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

import android.graphics.Point;
import android.graphics.PointF;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.mapzen.tangram.LngLat;
import com.mapzen.tangram.MapController;
import com.mapzen.tangram.MapData;
import com.mapzen.tangram.SceneUpdate;
import com.rareventure.android.AndroidPreferenceSet;
import com.rareventure.android.SortedBestOfIntArray;
import com.rareventure.android.SuperThread;
import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;
import com.rareventure.gps2.database.cache.TimeTree;
import com.rareventure.gps2.database.cachecreator.GpsTrailerCacheCreator;
import com.rareventure.gps2.database.cachecreator.ViewNode;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.OngoingProcessEnum;
import com.rareventure.gps2.reviewer.map.sas.Area;
import com.rareventure.gps2.reviewer.map.sas.SelectedAreaSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This figures the points for the screen. Because point finding can take a long time
 * if the user visited the same area many times, we calculate points in stages, in a drill down
 * fashion. ie. We may first return a point for a depth corresponding to an entire city, and then
 * drill down to sub points, and then to the sub sub points, and so forth until we get to a point
 * size that is reasonable given the current display depth.
 */
//TODO 3: we need a hard limit and a soft limit for number of points displayed. Once we cross the soft limit,
// we won't draw the next depth unless the stb changes

//TODD 2.1: do we really need superthreads anymore? The only reason we have them is to "pause"
// drawer, and map tile threads as well as to shut them down. (see onPause and onDestroy in the
//  activity)
//I suppose that's ok, but why do we have such a complicated
// object for this?
public class GpsTrailerOverlay extends SuperThread.Task implements GpsOverlay
{
	private final int minCirclePxRadius;
	//this is where we write our data to, which gets picked up by mapzen and drawn
	//using the yaml file to style the points and lines
	//We use two buffers and swap between them to avoid flickering
	private MapData mapData1, mapData2;
	private MapData mapData;

	private final OsmMapView osmMapView;
	private boolean viewUpToDate = true;

	public OsmMapGpsTrailerReviewerMapActivity activity;
	public int latestOnScreenPointSec;
	public int earliestOnScreenPointSec;

	public SelectedAreaSet sas;

	/**
	 * This is what we request to be drawn next
	 */
	private AreaPanelSpaceTimeBox requestedStBox;

	private int lastCalculatedGpsLatM;
	private int lastCalculatedGpsLonM;
	private float lastCalculatedRadius;

	Handler myHandler = new Handler(Looper.getMainLooper()) {

		@Override
		public void handleMessage(Message msg) {
			activity.createNewUserLocation(lastCalculatedGpsLatM, lastCalculatedGpsLonM, lastCalculatedRadius);
		}

	};

	public static  boolean doMethodTracing;

	private int minTimeTreeLengthForLineCalc = Integer.MAX_VALUE;

	/**
	 * The number of lines betweem view nodes to process between redrawing the picture
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

    public static Preferences prefs = new Preferences();
	private MapController mapController;

 	public int[] paintColors;

	/**
	 * If true, then when a new area is created, the previous ones will remain
	 * TODO 3: (currently not used because there is no way in the interface to activate this)
	 */
	private boolean selectedAreaAddLock;
	private SasDrawer sasDrawer;

	private List<SceneUpdate> mapData1SceneUpdate =
			Arrays.asList(new SceneUpdate[] {
					new SceneUpdate("global.ttt_use_point_set1", "true")
			});
	private List<SceneUpdate> mapData2SceneUpdate =
			Arrays.asList(new SceneUpdate[] {
					new SceneUpdate("global.ttt_use_point_set1", "false")
			});


	public GpsTrailerOverlay(OsmMapGpsTrailerReviewerMapActivity activity,
							   SuperThread superThread,
							 OsmMapView osmMapView) {
		super(GTG.GPS_TRAILER_OVERLAY_DRAWER_PRIORITY);
		this.activity = activity;
		this.sas = new SelectedAreaSet(activity);
		this.osmMapView = osmMapView;
		this.superThread = superThread;

		paintColors = new int[NUM_COLORS];
		minCirclePxRadius = (int) Util.convertDpToPixel(GpsTrailerOverlay.prefs.minGpsRadiusDp, activity);

		updateForColorRangeChange();
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

		abortOrPauseIfNecessary();

		GTG.cacheCreatorLock.registerReadingThread();
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

				//Log.d(GTG.TAG,"calc points for "+localApStbox+" minDepth "+minDepth);

				TimeTree.hackTimesLookingUpTimeTrees = 0;

				while(System.currentTimeMillis() - startTime < prefs.maxDrawCalcTime
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


				///* ttt_installer:remove_line */Log.d("GPS","times looking up time trees is "+TimeTree.hackTimesLookingUpTimeTrees);

				allLinesCalculated = calcLines(localApStbox, minDepth);

				//PERF: it would be faster to store the pixel locations

				//if while calculating all lines, we got to the maximum line count, and drawLines deletes
				//some of them, we  have to recalculate to add some more back in
				if(allLinesCalculated && isAtMaxLines())
				{
					if(drawLines(localApStbox, minDepth))
					{
						///* ttt_installer:remove_line */Log.d("GPS", "setting all lines calculated false");
						allLinesCalculated = false;
					}
				}
				else {
					drawLines(localApStbox, minDepth);
				}

				calcStartAndEndTimeOnScreen();
				int currPoints = drawPoints(localApStbox);

				//TODO 2 handle photos
//				if(OsmMapGpsTrailerReviewerMapActivity.prefs.showPhotos)
//					drawMedia(localApStbox);

				boolean stBoxNotChanged;

				synchronized(this)
				{
					//we only say view is up to date if its up to date according to the requeted stbox
					// (which may have changed while we were calculating)
					if((stBoxNotChanged = localApStbox.equals(requestedStBox)) && allLinesCalculated
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

			GTG.cacheCreatorLock.unregisterReadingThread();
			activity.notifyDoneProcessing(OngoingProcessEnum.DRAW_POINTS);

		}
	}

	private boolean isAtMaxLines() {
		return GTG.cacheCreator.startTimeToViewLine.size() >= MAX_LINES;
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
		if(1==1) return false;


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

		//TODO 1.5 fix lines!
//		stBox.apUnitsToPixels(p1,
//				apX1, apY1,
//				drawingBitmap.getWidth(), drawingBitmap.getHeight());
//		stBox.apUnitsToPixels(p2,
//				apX2, apY2,
//				drawingBitmap.getWidth(), drawingBitmap.getHeight());
//
//		drawingCanvas.drawLine(p1.x, p1.y, p2.x, p2.y,
//				linePaint);
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
	 * This calculates the start and end time the path is visible on screen, so we can choose
	 * appropriate colors. It uses the points calculated by cache creator
	 * and places the results in {@code earliestOnScreenPointSec} and {@code latestOnScreenPointSec}
	 */
	private void calcStartAndEndTimeOnScreen() {
		GTG.cacheCreator.viewNodeThreadManager.registerReadingThread();

		try {
			int localEarliestOnScreenPointSec = Integer.MAX_VALUE;
			int localLatestOnScreenPointSec = Integer.MIN_VALUE;

			Iterator<ViewNode> iter = GTG.cacheCreator.getViewNodeIter();

			while(iter.hasNext()) {
				ViewNode vn = iter.next();

				if (localLatestOnScreenPointSec < vn.overlappingRange[1])
					localLatestOnScreenPointSec = vn.overlappingRange[1];

				if (localEarliestOnScreenPointSec > vn.overlappingRange[0])
					localEarliestOnScreenPointSec = vn.overlappingRange[0];

				//note that there is a thread race with the main thread and time view here
				//but I don't think this will affect much
				//TODO 2.5 should we synchronize here? It's growing with the addition of colorRangeStartEndSec
				latestOnScreenPointSec = localLatestOnScreenPointSec;
				earliestOnScreenPointSec = localEarliestOnScreenPointSec;

			}
		}
		finally
		{
			GTG.cacheCreator.viewNodeThreadManager.unregisterReadingThread();
		}

	}


	/**
	 * @return number of points drawn
	 */
	private int drawPoints(AreaPanelSpaceTimeBox apStBox)
	{
		//note, technically, this isn't needed, since writing to the view nodes will
		//never interfere with code at this point to read them. But to make the code
		//clearer, we do it anyway
		GTG.cacheCreator.viewNodeThreadManager.registerReadingThread();

//		Log.d(GTG.TAG,"Drawing points for "+apStBox);
		try {
			long time = System.currentTimeMillis();


			int pointCount = 0;

			int maxDepth = getMaxDepth(apStBox);

			Iterator<ViewNode> iter = GTG.cacheCreator.getViewNodeIter();

			Point p = new Point();

			Point p2 = new Point();

			float closestPointDistSquared = Float.MAX_VALUE;
			int closestToCenterEndTimeSec = 0;
			AreaPanel closestToCenterAp = null;

			LngLat ll = new LngLat();

			Map<String, String> props = new HashMap<>();

			mapData = mapData == mapData1 ? mapData2 : mapData1;
//			mapData.beginChangeBlock();
			mapData.clear();

			//co:hack to show top and bottom of view area
//			LngLat tl = mapController.coordinatesAtScreenPosition(0,0);
//			LngLat br = mapController.coordinatesAtScreenPosition(osmMapView.windowWidth,
//					osmMapView.pointAreaHeight);
//
//			props.put("color","#ffffff");
//			props.put("size",String.format("%dpx %dpx", 10, 10));
//			mapData.addPoint(tl,props);
//
//			props.put("color","#000000");
//			props.put("size",String.format("%dpx %dpx", 10, 10));
//			mapData.addPoint(br,props);

			while(iter.hasNext()) {
				ViewNode vn = iter.next();

				AreaPanel areaPanel = vn.ap();

				//skip any areaPanel that's "too big" to display (and will look weird)
//				if(areaPanel.getDepth() > maxDepth)
//					continue;

				pointCount++;

				ll.set(
						AreaPanel.convertXToLon(areaPanel.getCenterX()),
						AreaPanel.convertYToLat(areaPanel.getCenterY())
				);

//				Log.d(GTG.TAG,"Drawing point at lon "+ll.longitude+" lat "+ll.latitude);

				//TODO 3 maybe one day handle altitude

				float speedMult = calcSpeedMult(vn, areaPanel.getDepth());

				int circleSizePx = (int) (Math.max(minCirclePxRadius, 2*(p2.x-p.x))* speedMult) * 2;

				int paintIndex = figurePaintIndex(vn.overlappingRange[0],vn.overlappingRange[1]);
				props.put("color",String.format("#%06x",paintColors[paintIndex]));
				props.put("size",String.format("%dpx %dpx", circleSizePx, circleSizePx));

				//Here we add the actual point into mapzen.
				//
				// Note that we aren't in the UI thread
				//at this point. However, even though it's not specified in the documentation, I
				//believe its safe to call this method from our thread. This is because mapzen
				//uses opengl which doesn't use the UI thread, *and* I examined the code from the
				//current version, and it does use a mutex before adding the point. See here:
				//tangram-es/core/src/data/clientGeoJsonSource.cpp:
				//void ClientGeoJsonSource::addPoint(const Properties& _tags, LngLat _point)
				mapData.addPoint(ll,props);

				//we need to figure out what timezone to use. We do this by looking for the point
				//closest to the center of the screen and using the timezone we were in during
				//the same time the point was recorded to choose a good timezone.
				//(Otherwise figuring it out based on where the point is on the earth is rather
				// complex)
				float distSquared = Util.square(osmMapView.centerX - p.x) + Util.square(osmMapView.centerY - p.y);

				if(distSquared < closestPointDistSquared)
				{
					closestToCenterEndTimeSec = vn.overlappingRange[1];
					closestToCenterAp = areaPanel;
					closestPointDistSquared = distSquared;
				}

			} //while examining pointCount

//			mapData.endChangeBlock();
			mapController.updateSceneAsync(mapData == mapData1 ? mapData1SceneUpdate :
					mapData2SceneUpdate);
			//mapController.requestRender();

			if(closestToCenterAp != null)
			{
				//note that we don't get the bottom level here because the overlapping range may contain some fuzziness
				TimeTree tt = closestToCenterAp.getTimeTree().getEncompassigTimeTreeOrMaxTimeTreeBeforeTime(closestToCenterEndTimeSec-1, false);
				closestToCenterTimeSec = tt.calcTimeRangeCutEnd();
			}

			///* ttt_installer:remove_line */Log.d("GPS","drew "+pointCount+" pointCount");

			return pointCount;
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
		return .5f* (1-DEPTH_TO_MAX_SECONDS[depth]/(DEPTH_TO_MAX_SECONDS[depth]+(vn.overlappingRange[1] - vn.overlappingRange[0])))+.5f;
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

	public void notifyScreenChanged(AreaPanelSpaceTimeBox newStBox) {
		synchronized(this)
		{
			if(requestedStBox != null)
				newStBox.pathList = requestedStBox.pathList;

			if(requestedStBox == null || !requestedStBox.equals(newStBox))
			{
				viewUpToDate = false;

				if(requestedStBox == null || requestedStBox.minZ != newStBox.minZ || requestedStBox.maxZ != newStBox.maxZ) {
					//this sets up requestedStbBox to the current view
					sas.setRequestedTime(newStBox.minZ, newStBox.maxZ);

					distanceUpToDate = false;
				}

				requestedStBox = newStBox;

				this.stNotify(this);
			}
		}
	}

	@Override
	public boolean onTap(float x, float y) {
//		if(handleTapForPhotos(x,y))
//			return true;

		return handleTapForSelectedArea(x,y);
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

	public void notifyPathsChanged() {
		//TODO 3 technically we should register as a reading thread, but we are actually
		//being called by the writing thread, so registering the reading thread here would
		//cause the rwtm to deadlock the thread on itself.. maybe we should make this is a
		//noop for the writing thread and
		// use a  thread local to identify this?
//		overlay.sas.rwtm.registerReadingThread();
		synchronized (this)
		{
			if(requestedStBox != null) {
				requestedStBox = new AreaPanelSpaceTimeBox(requestedStBox);
				requestedStBox.pathList = sas.getResultPaths();
				viewUpToDate = false;
				this.stNotify(this);
			}
		}
//		overlay.sas.rwtm.unregisterReadingThread();
	}

	@Override
	public void startTask(MapController mapController) {
		this.mapController = mapController;
		mapData1 = mapData = mapController.addDataLayer("gt_point1");
		mapData2 = mapController.addDataLayer("gt_point2");
		mapController.updateSceneAsync(mapData1SceneUpdate);
		sasDrawer = new SasDrawer(sas,mapController);
		superThread.addTask(this);
	}

	@Override
	public void onPause() {
		//this is handled automatically by the super thread manager
	}

	@Override
	public void onResume() {
		//this is handled automatically by the super thread manager
	}

	public void shutdown() {
		sas.shutdown();
	}

	@Override
	public boolean onLongPressEnd(float startX, float startY, float endX,
								  float endY) {

		getApUnitsFromPixels(p1, startX, startY);
		getApUnitsFromPixels(p2, endX, endY);

		if (p1.x > p2.x) {
			int t = p2.x;
			p2.x = p1.x;
			p1.x = t;
		}
		if (p1.y > p2.y) {
			int t = p2.y;
			p2.y = p1.y;
			p1.y = t;
		}

		Area a = new Area(p1.x, p1.y, p2.x, p2.y, getMinDepth(requestedStBox));

		sas.addArea(a);

		sasDrawer.resetToSas();

		activity.notifySelectedAreasChanged(true);

		return true;
	}

	private void getApUnitsFromPixels(Point p, float x, float y) {
		//co; doesn't work when zoomed out all the way for some reason, x value is off
		//requestedStBox.apUnitsToPixels();
		LngLat l = Util.normalizeLngLat(mapController.screenPositionToLngLat(new PointF(x, y)));
		p.x = AreaPanel.convertLonToX(l.longitude);
		p.y = AreaPanel.convertLatToY(l.latitude);
	}

	@Override
	public boolean onLongPressMove(float startX, float startY, float endX,
								   float endY) {
		//if (!selectedAreaAddLock)
			//PERF we only need to do this once
			//sas.clearAreas();

		int minDepth = getMinDepth(requestedStBox);

		getApUnitsFromPixels(p1, startX, startY);
		getApUnitsFromPixels(p2, endX, endY);

		sasDrawer.setRectangle(p1.x, p1.y, p2.x, p2.y);
		return true;
	}


	private boolean handleTapForSelectedArea(float x, float y) {
		Point apUnitsPoint = new Point();

		getApUnitsFromPixels(apUnitsPoint, x, y);

		//align to a minDepth boundary for speed when sas computes time through area
		int minDepth = getMinDepth(requestedStBox);

		//compute radius in ap units
		int radius = (int) (Util.convertDpToPixel(prefs.clickDefaultSelectedAreaDp, activity)
				* requestedStBox.getWidth() / osmMapView.windowWidth / 2);

		if (!selectedAreaAddLock)
			sas.clearAreas();

		//create an area, rounded to minDepth
		Area a = new Area(apUnitsPoint.x - radius, apUnitsPoint.y - radius,
				apUnitsPoint.x + radius, apUnitsPoint.y + radius, minDepth);

		//make it a perfect square (since we may round in one axis but not another
		if (a.y2 - a.y1 > a.x2 - a.x1)
			a.x2 = a.x1 + (a.y2 - a.y1);
		else
			a.y2 = a.y1 + (a.x2 - a.x1);

		//if they actually selected a point
		if (GTG.cacheCreator.doViewNodesIntersect(a.x1, a.y1, a.x2, a.y2)) {
			sas.addArea(a);
			activity.notifySelectedAreasChanged(true);
		} else if (!selectedAreaAddLock)
			activity.notifySelectedAreasChanged(false);

		sasDrawer.resetToSas();

		return true;
	}

	public static class Preferences implements AndroidPreferenceSet.AndroidPreferences {

		public float clickDefaultSelectedAreaDp = 30;

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
		 * The amount of guesswork allowed when determining the time that the stb overlaps
		 * an area panel, in terms of a percentage of leeway of the overlap. This is used
		 * to choose a color for the area panel, so we allow a lot of leeway typically.
		 */
		public float timeTreeFuzzinessPerc = .33f;

		/**
		 * The min radius of the points on the graph
		 */
		public float minGpsRadiusDp = 2f;
	}
}
