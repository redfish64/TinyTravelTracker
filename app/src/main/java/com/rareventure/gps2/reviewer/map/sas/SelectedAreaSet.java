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
package com.rareventure.gps2.reviewer.map.sas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ListIterator;
import java.util.TimeZone;
import java.util.TreeSet;

import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Looper;

import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;
import com.rareventure.gps2.database.cache.TimeTree;
import com.rareventure.gps2.database.cachecreator.GpsTrailerCacheCreator;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;
import com.rareventure.gps2.reviewer.map.sas.Area.AreaPanelInfo;
import com.rareventure.util.ReadWriteThreadManager;

/**
 * This finds when a user entered and left a given set of areas. It is meant to be updated dynamically
 * and notify registered observers when the data changes .When finished, it will call
 * activity.notifyPathsChanged() when the paths through the data set changes
 *
 * Note for path formation we use an AreaPanel structures rather than time ranges. The reason is
 * that there can only be so many area panels (tens of them) per area, but there can be 1000 or more
 * time ranges depending on how often the user came and left the place of interest.
 * @author tim
 *
 */
public class SelectedAreaSet extends Thread {
	private static final float MIN_PATH_SPEED_M_PER_S_SQUARED = .5f * .5f;

	private boolean isRunning = true;

	private boolean isShutdown;

	/**
	 * All access to this object must be synchronized through calls
	 * to this rwtm. If input variables are changed, this is considered
	 * a "write". The caller can have a reasonable expectation of timeliness
	 * when changing an input variable (ie. the result calculator will quit
	 * early if it detects the input variables are attempting to be changed)
	 */
	public ReadWriteThreadManager rwtm = new ReadWriteThreadManager();

	private OsmMapGpsTrailerReviewerMapActivity activity;

	private ArrayList<Area> requestedAreas = new ArrayList<Area>();

	private int requestedStartTimeSec, requestedEndTimeSec;

	/**
	 * Access to this variable can only occur after registering with rwtm
	 */
	private ArrayList<Path> resultPaths = new ArrayList<Path>();

	private TreeSet<AreaTimeRange> resultTimeRangeTree = new TreeSet<AreaTimeRange>();

	/**
	 * The areas for the current resultPaths and resultTimeRangeTree results
	 */
	private ArrayList<Area> resultAreas = new ArrayList<Area>();

	/**
	 * The start and end times for the current resultPaths and resultTimeRangeTree results
	 */
	private int resultEndTime;
	private int resultStartTime;

	/**
	 * True if the time range tree and paths are up to date with the input data.
	 * This field is only set to false by the ui thread, and synchronization of
	 * it occurs outside of the rwtm. This is because while the run() method
	 * is updating the next valid results, we can't wait for it to finish
	 * before being able to read upToDate
	 */
	private boolean upToDate;

	private boolean distCalculated;

	public TimeZone timeZone = Util.getCurrTimeZone();

	public SelectedAreaSet(OsmMapGpsTrailerReviewerMapActivity activity) {
		this.activity = activity;

		this.start();
	}

	public synchronized void shutdown() {
		isRunning = false;
		notify();

		while (!isShutdown) {
			try {
				wait();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	//	private void addPath(AreaPanelInfo stApi,
	//			AreaPanelInfo endApi) {
	//		int startTime = calcBestPathStart(stApi);
	//		int endTime = calcBestPathEnd(endApi);
	//		
	//		if(startTime < 
	//	}

	/**
	 * Note that for speed, it is up to the caller to
	 * create areas that are multiples of area panels
	 * whenever possible. ie. if we are selecting
	 * a huge swath of area, it would be best to be 
	 * aligned at an area panel boundary, so that
	 * we aren't messing with a bunch of small area panels 
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 */
	public void addArea(Area area) {
		rwtm.registerWritingThread();

		area.setIndex(requestedAreas.size());
		requestedAreas.add(area);

		upToDate = false;

		notifyObserver();

		synchronized (this) {
			this.notify();
		}

		rwtm.unregisterWritingThread();
	}

	public void setRequestedTime(int startTime, int endTime) {
		if (requestedStartTimeSec != startTime
				|| requestedEndTimeSec != endTime) {
			rwtm.registerWritingThread();
			this.requestedStartTimeSec = startTime;
			this.requestedEndTimeSec = endTime;

			upToDate = false;

			notifyObserver();

			synchronized (this) {
				this.notify();
			}

			rwtm.unregisterWritingThread();
		}
	}

	private void notifyObserver() {
		for (DataSetObserver observer : observers) {
			if (Looper.getMainLooper().getThread() == Thread.currentThread())
				observer.onChanged();
			else {
				final DataSetObserver localObserver = observer;

				activity.runOnUiThread(new Runnable() {

					@Override
					public void run() {
						localObserver.onChanged();
					}
				});

				//co: this version may cause deadlocks 
				//if were not on the main thread, we need to notify the observer, but this can
				//only be done on the ui thread. So we tell the ui thread to do it, and wait
				//for it to finish

				//				final boolean [] done = new boolean [1];
				//				Runnable r = new Runnable() {
				//					
				//					@Override
				//					public void run() {
				//						observer.onChanged();
				//						
				//						synchronized(this)
				//						{
				//							done[0] = true;
				//							notify();
				//						}
				//					}
				//				};
				//				
				//				activity.runOnUiThread(r);
				//				synchronized(r)
				//				{
				//					while(!done[0])
				//						try {
				//							r.wait();
				//						} catch (InterruptedException e) {
				//							throw new IllegalStateException(e);
				//						}
				//				}
			}
		}
	}

	public void run() {
		while (isRunning) {
			GTG.cacheCreatorLock.registerReadingThread();
			rwtm.registerWritingThread();
			
			boolean notifyPathsChanged = true;
			try {
				for (Area a : requestedAreas) {
					a.calcAreaPanelInfos();

					//if we were interrupted by a change to the input variables
					//restart
					if (rwtm.isWritingHoldingUpWritingThreads()) {
						continue;
					}
				}

				if (requestedAreas.size() > 1) {
					if (runBackgroundTaskForPathCalculator()) {
						distCalculated = false;
						notifyPathsChanged = true;
					}
				} else if (requestedAreas.size() == 1) {
					if (runBackgroundTaskForTimeRange()) {
						distCalculated = false;
						notifyPathsChanged = true;

						calcTimeZone();
					}
					
				} else {
					resultTimeRangeTree = new TreeSet<AreaTimeRange>();
					copyRequestedInputToResultInput(true);
					notifyPathsChanged = true;
					upToDate = true;
				}

			} finally {
				rwtm.unregisterWritingThread();
				GTG.cacheCreatorLock.unregisterReadingThread();
			}

			//note we assume that we started not up to date, or we wouldn't be running anyway
			if (upToDate) {
				//notify the observer because we are now ready to display the results
				notifyObserver();

				rwtm.registerWritingThread();
				calcDistForTimeRanges();
				rwtm.unregisterWritingThread();

				if (distCalculated) {
					notifyObserver();
				}
			}
			if(notifyPathsChanged)
				activity.notifyPathsChanged();
			
			synchronized (this) {
				while (upToDate && isRunning)
					try {
						wait();
					} catch (InterruptedException e) {
						throw new IllegalStateException(e);
					}
			}
		}

		isShutdown = true;
		synchronized (this) {
			notify();
		}

	}

	private void calcTimeZone() {
		timeZone = null;
		if(!resultTimeRangeTree.isEmpty())
		{
			TimeRange tr = this.resultTimeRangeTree.first();
			
			TimeZoneTimeRow tztr = GTG.tztSet.getTimeZoneCovering(tr.endTimeSec/2 + tr.startTimeSec/2);
			
			if(tztr != null)
				timeZone = tztr.getTimeZone();
			
		}
		
		if(timeZone == null)
			timeZone = Util.getCurrTimeZone();
	}

	private void calcDistForTimeRanges() {

		for (TimeRange tr : getTimeRangesAsCollection()) {
			rwtm.pauseForReadingThreads();
			if (rwtm.isWritingHoldingUpWritingThreads()) {
				return;
			}

			tr.dist = GpsTrailerCacheCreator.calcTrDist(Math.max(tr.fullRangeStartSec, resultStartTime),
					Math.min(tr.fullRangeEndSec, resultEndTime), rwtm);
			
			//if we were interrupted
			if(tr.dist == -1)
			{
				tr.dist = 0;
				return;
			}

		}

		distCalculated = true;
	}

	private Collection<? extends TimeRange> getTimeRangesAsCollection() {
		if (resultAreas.isEmpty()) {
			return new ArrayList<TimeRange>();
		}
		if (resultAreas.size() == 1)
			return resultTimeRangeTree;

		return resultPaths;
	}

	private Point p1 = new Point();
	private Point p2 = new Point();
	private AreaTimeRange lastTimeRange;
	private int lastIndex;

	private ArrayList<DataSetObserver> observers = new ArrayList<DataSetObserver>();

	public void drawSelectedAreaSet(Canvas canvas,
			AreaPanelSpaceTimeBox requestedStBox, Paint paint, int width,
			int height) {

		for (int i = requestedAreas.size() - 1; i >= 0; i--) {
			Area a = requestedAreas.get(i);

			requestedStBox.apUnitsToPixels(p1, a.x1, a.y1, width, height);
			requestedStBox.apUnitsToPixels(p2, a.x2, a.y2, width, height);

			canvas.drawRect(p1.x, p1.y, p2.x, p2.y, paint);
		}
	}

	public void clearAreas() {
		rwtm.registerWritingThread();
		requestedAreas.clear();

		upToDate = false;

		notifyObserver();

		synchronized (this) {
			this.notify();
		}

		rwtm.unregisterWritingThread();
	}

	public void registerDataSetObserver(DataSetObserver observer) {
		if (!observers.contains(observer))
			observers.add(observer);
	}

	public void unregisterDataSetObserver(DataSetObserver observer) {
		observers.remove(observer);
	}

	private AreaTimeRange getTimeRangeTr = new AreaTimeRange();

	/**
	 * True if the worker thread was interrupted by a request to change
	 * the input variables
	 */
	private boolean workerInterrupted;

	/**
	 * True if the current result was partially calculated last 
	 * and not correct
	 */
	private boolean resultIncomplete;

	private Object upToDateLock = new Object();

	/**
	 * Note that this does a sequential search for the given index
	 * It saves the last position however, so as long as the requests
	 * are clustered, it should be pretty fast
	 * 
	 * @param position
	 * @return
	 */
	public TimeRange getTimeRange(int position) {
		synchronized (upToDateLock) {
			if (!upToDate)
				return null;

			rwtm.registerReadingThread();
			try {
				if (isSingleAreaCalc()) {
					if (lastTimeRange == null) {
						lastTimeRange = resultTimeRangeTree.first();

						lastIndex = 0;
					}

					while (lastIndex < position) {
						//we compute the index on the fly,
						lastTimeRange = resultTimeRangeTree
								.higher(lastTimeRange);
						lastIndex = lastIndex + 1;
					}

					while (lastIndex > position) {
						lastTimeRange = resultTimeRangeTree
								.lower(lastTimeRange);
						lastIndex = lastIndex - 1;
					}

					//note technically we don't need to set the start and
					//end times, (just the cut ones) but for consistancy...
					getTimeRangeTr.fullRangeStartSec = lastTimeRange.fullRangeStartSec;
					getTimeRangeTr.fullRangeEndSec = lastTimeRange.fullRangeEndSec;

					getTimeRangeTr.startTimeSec = lastTimeRange.startTimeSec;
					getTimeRangeTr.endTimeSec = lastTimeRange.endTimeSec;
					getTimeRangeTr.dist = lastTimeRange.dist;

					if (getTimeRangeTr.startTimeSec < requestedStartTimeSec) {
						getTimeRangeTr.startTimeSec = requestedStartTimeSec;
					}

					if (getTimeRangeTr.endTimeSec > requestedEndTimeSec) {
						getTimeRangeTr.endTimeSec = requestedEndTimeSec;
					}

					return getTimeRangeTr;
				} else {
					return resultPaths.get(position);
				}
			} finally {
				rwtm.unregisterReadingThread();
			}
		}
	}

	private boolean isSingleAreaCalc() {
		return resultAreas.size() <= 1;
	}

	public int getTimeRangeCount() {
		synchronized (upToDateLock) {
			if (!upToDate)
				return 0;
			rwtm.registerReadingThread();

			try {
				return isSingleAreaCalc() ? resultTimeRangeTree.size()
						: resultPaths.size();
			} finally {
				rwtm.unregisterReadingThread();
			}
		}
	}

	public boolean isEmpty() {
		synchronized (upToDateLock) {
			if (upToDate) {
				rwtm.registerReadingThread();
				try {
					return (isSingleAreaCalc() ? resultTimeRangeTree.isEmpty()
							: resultPaths.isEmpty());
				} finally {
					rwtm.unregisterReadingThread();
				}
			}

			return false;
		}
	}

	private void deletePathsOutsideOfRange(ArrayList<Path> paths,
			int startTime, int endTime) {
		for (ListIterator<Path> i = paths.listIterator(); i.hasNext();) {
			Path p = i.next();

			if (p.startTimeSec < startTime || p.endTimeSec > endTime)
				i.remove();
		}
	}

	private ArrayList<Path> findApiPaths(ArrayList<Area> areas, int startTime,
			int endTime) {
		TreeSet<AreaPanelInfo> sortedApiTree = new TreeSet<AreaPanelInfo>();

		sortedApiTree.clear();
		for (Area a : areas) {
			a.addResetApisToTree(sortedApiTree, startTime, endTime);

			if (rwtm.isWritingHoldingUpWritingThreads()) {
				workerInterrupted = true;
				return null;
			}
		}

		//we do this as follows:
		//1. find the first api which corresponds to each area in path so
		//   that api for 1st area < api for 2nd area < api for 3rd area
		//2. find the highest api for the second to last area in the path 
		//   that is less than the api of the higher level of the path,
		//   moving the api's to the next time tree if necessary
		//3. go to step 2, now using the api of the previous area to the last one 
		//   handled. Continue this loop until we are at the start of the path
		//4. now the complete path is found, push all apis after the time of
		// the end of the path (including the last api of the path itself)
		//5. loop back to one until we have exhausted all paths
		//
		// For instance:
		// A1 B2 C1 D1 E3
		// A1 B2 C1 D1 E3, path : null null E3
		// A1 B2 C1 D1 E3, path : null B2 E3
		// try to push B2 higher but lower than E3
		// A1 C1 D1 B2 E3, path : null B2 E3
		// now deal with 1
		// A1 C1 D1 B2 E3, path : D1 B2 E3
		// now push all items A,B,C,D,E past E and try again

		ArrayList<Path> myPaths = new ArrayList<Path>();

		//for each path to find in the tree
		for (;;) {
			AreaPanelInfo[] apiPath = new AreaPanelInfo[areas.size()];

			//find the first api of the first area, then the first api
			// of the second area that is greater than the first, etc
			for (int currApiIndex = 0; currApiIndex < apiPath.length; currApiIndex++) {
				//note that we have to start over each time, because we skip api's
				//that are at a higher area than the currApiIndex (since we don't know
				//the earliest apiIndex of the previous level, we can't find the earliest
				//one that is greater than the last)

				AreaPanelInfo api = sortedApiTree.isEmpty() ? null
						: sortedApiTree.first();

				while (api != null) {
					if (api.areaIndex == currApiIndex) {
						//if we found the earliest one that matches
						if (currApiIndex == 0
								|| api.currTtStartTime > apiPath[currApiIndex - 1].currTtStartTime) {
							apiPath[currApiIndex] = api;
							//go to the next area in the path
							break;
						} else {
							AreaPanelInfo prevApi = sortedApiTree.lower(api);

							//push it to the next tt that would work for the current path, or beyond if there
							//isn't one
							advanceApiToAtLeast(sortedApiTree, api,
									apiPath[currApiIndex - 1].currTtStartTime,
									endTime);

							if (prevApi == null)
								api = sortedApiTree.first();
							else
								api = sortedApiTree.higher(prevApi);
						}
					} else //not the area level that were interested in
					{
						//go to the next api
						api = sortedApiTree.higher(api);
					}

					if (rwtm.isWritingHoldingUpWritingThreads()) {
						workerInterrupted = true;
						return null;
					}
				}

				//if we went through the entire list without finding a valid api for the path at the current level
				if (api == null)
				//there are no more api paths
				{
					return myPaths;
				}
			}//while looking for the first valid path

			//now that we found a valid path, we need to get the latest exit points for all waypoints (aka areas)
			//in the path. This is so we don't get paths like 1 -> 2 -> 1 --> 2 --> 3

			AreaPanelInfo endPathApi = apiPath[apiPath.length - 1];

			//apiPathIndex is the index of the next higher area in the path
			for (int apiPathIndex = areas.size() - 1; apiPathIndex >= 1; apiPathIndex--) {
				//now find the highest api that does not exceed the api of the next level in the path
				AreaPanelInfo currApi = sortedApiTree.first();

				while (currApi != endPathApi) {
					if (rwtm.isWritingHoldingUpWritingThreads()) {
						workerInterrupted = true;
						return null;
					}

					if (currApi.areaIndex != apiPathIndex - 1) {
						currApi = sortedApiTree.higher(currApi);
						continue;
					}

					//if we are currently the best api in the path for the current waypoint aka area 
					if (currApi == apiPath[apiPathIndex - 1]) {
						//push as close as we can to the next level, but do not go past it
						if (advanceApiUpTo(sortedApiTree, currApi,
								apiPath[apiPathIndex].currTtStartTime)) {
							//no-op, but note that apiPath now contains the same currApi
							//but with a later start time
						} else {
							//if we were not able to move it at all then we keep going to 
							//the next item
							currApi = sortedApiTree.higher(currApi);
						}

					} else {
						AreaPanelInfo nextApi = sortedApiTree.higher(currApi);

						//if we could advance the api beyond the current best without over shooting it
						if (advanceApiBetweenOrLater(sortedApiTree, currApi,
								apiPath[apiPathIndex - 1].currTtStartTime,
								apiPath[apiPathIndex].currTtStartTime, endTime)) {
							apiPath[apiPathIndex - 1] = currApi;
						}

						currApi = nextApi;
					}
				}//while looking at apis for the current waypoint aka area

			}//for each waypoint aka area

			//now apiPath should contain the first path, where for all waypoints besides the last one,
			// the api is the last exit of the waypoint for the path, and for the last one, it is the 
			//first entry of the last waypoint

			TimeTree startTree = calcActualStartOrEnd(areas.get(0), apiPath[0],
					startTime, endTime, true);
			TimeTree endTree = calcActualStartOrEnd(
					areas.get(apiPath.length - 1), apiPath[apiPath.length - 1],
					startTime, endTime, false);

			Path p = new Path(apiPath, startTree, endTree);
			myPaths.add(p);

			//now move all the apis beyond the end point of the path to clear it out and go on to the 
			//next one
			//now find the highest api that does not exceed the api of the next level in the path
			AreaPanelInfo currApi = sortedApiTree.first();

			while (currApi != endPathApi) {
				AreaPanelInfo nextApi = sortedApiTree.higher(currApi);

				advanceApiToAtLeast(sortedApiTree, currApi,
						apiPath[apiPath.length - 1].currTtStartTime + 1,
						endTime);

				currApi = nextApi;

				if (rwtm.isWritingHoldingUpWritingThreads()) {
					workerInterrupted = true;
					return null;
				}
			}
		}//for each path in the tree
	} //find api paths

	private TimeTree calcActualStartOrEnd(Area a, AreaPanelInfo api,
			int minStartTime, int maxEndTime, boolean calcStart) {
		//we estimate the actual end of the path by tracing it
		//through the area until:
		// a) it leaves the area,
		// b) it moves further away from the center of the area,
		// c) the speed of movement as referenced from the distance
		//    between the area and the last area is less than a certain value
		// Note that we don't use the distance traveled within the ap to
		// make the speed calculation, because we don't want to include a path
		// that went back and forth a lot
		// We use the minDepth of the area as the depth level for the aps

		int areaCenterX = a.getCenterX();
		int areaCenterY = a.getCenterY();

		TimeTree apiTt = api.tt();

		AreaPanel lastAp = api.ap().getChildApAtDepthAndTime(a.minDepth,
				apiTt.getMinTimeSecs());
		TimeTree lastTt = lastAp.getTimeTree()
				.getBottomLevelEncompassigTimeTree(apiTt.getMinTimeSecs());

		long lastDistFromCenterSqr = ((long) lastAp.getX() - areaCenterX)
				* (lastAp.getX() - areaCenterX)
				+ ((long) lastAp.getY() - areaCenterY)
				* (lastAp.getY() - areaCenterY);
		for (;;) {
			AreaPanel currAp = calcStart ? lastTt.getPrevAp() : lastTt
					.getNextAp();
			if (currAp == null)
				break;

			long currDistFromCenterSqr = ((long) currAp.getX() - areaCenterX)
					* ((long) currAp.getX() - areaCenterX)
					+ ((long) currAp.getY() - areaCenterY)
					* ((long) currAp.getY() - areaCenterY);
			if (lastDistFromCenterSqr < currDistFromCenterSqr)
				break;

			long currDistFromLastSqr = ((long) currAp.getX() - lastAp.getX())
					* ((long) currAp.getX() - lastAp.getX())
					+ ((long) currAp.getY() - lastAp.getY())
					* ((long) currAp.getY() - lastAp.getY());

			TimeTree currTt = currAp.getTimeTree()
					.getBottomLevelEncompassigTimeTree(
							calcStart ? lastTt.getMinTimeSecs() : lastTt
									.getMaxTimeSecs());

			//make sure the time tree isn't out of bounds of the stb
			if (calcStart ? (currTt.getMinTimeSecs() < minStartTime) : (currTt
					.getMaxTimeSecs() > maxEndTime))
				break;

			if (currDistFromLastSqr / (float) currTt.getTimeSec()
					/ currTt.getTimeSec() < MIN_PATH_SPEED_M_PER_S_SQUARED)
				break;

			lastAp = currAp;
			lastDistFromCenterSqr = currDistFromCenterSqr;
			lastTt = currTt;
		}

		return lastTt;
	}

	/**
	 * Moves api to the latest value before time.
	 * @param sortedApiTree 
	 * 
	 * @return false if it couldn't be moved at all
	 */
	private boolean advanceApiUpTo(TreeSet<AreaPanelInfo> sortedApiTree,
			AreaPanelInfo api, int time) {
		AreaPanel ap = api.ap();

		TimeTree rootTt = ap.getTimeTree();

		TimeTree tt = rootTt.getEncompassigTimeTreeOrMaxTimeTreeBeforeTime(
				time, true);

		if (tt == null || tt.id == api.currTtId) //if we weren't able to move it at all
			return false;

		sortedApiTree.remove(api);

		//add it back it in (it's guaranteed that we won't pass endtime so we pass Integer.MAX_VALUE for end time)
		if (api.setTt(tt, Integer.MAX_VALUE))
			sortedApiTree.add(api);

		return true;

	}

	/**
	 * Moves api to the minimum value past start time, or deletes it completely if there isn't
	 * a time after start time
	 * @param sortedApiTree 
	 * @param endTime if api extends past this time it is killed
	 */
	private void advanceApiToAtLeast(TreeSet<AreaPanelInfo> sortedApiTree,
			AreaPanelInfo api, int startTime, int endTime) {
		sortedApiTree.remove(api);

		AreaPanel ap = api.ap();

		TimeTree rootTt = ap.getTimeTree();

		if (api.setTt(rootTt.getMinTimeTreeAfterTime(startTime), endTime))
			//add it back it in
			sortedApiTree.add(api);
	}

	/**
	 * Advances the api to the latest time tree that is >= minTime and <= maxTime. If this is
	 * not possible, will advance even further to the earliest time after maxTime
	 * @param sortedApiTree 
	 * @param endTime the absolute end time for the tt. If it goes beyond this point the
	 *  api is marked exhausted
	 *  
	 *  @return true if was able to advance within minTime and maxTime
	 */
	private boolean advanceApiBetweenOrLater(
			TreeSet<AreaPanelInfo> sortedApiTree, AreaPanelInfo api,
			int minTime, int maxTime, int endTime) {
		sortedApiTree.remove(api);

		AreaPanel ap = api.ap();

		TimeTree rootTt = ap.getTimeTree();

		TimeTree tt = rootTt.getEncompassigTimeTreeOrMaxTimeTreeBeforeTime(
				maxTime, true);

		//if we can't find a time tree between min and than max time
		if (tt == null || tt.getMinTimeSecs() < minTime) {
			//choose the earliest one after max time
			tt = rootTt.getEncompassigTimeTreeOrMinTimeTreeAfterTime(maxTime,
					true);

			//if there is one 
			if (api.setTt(tt, endTime)) {
				//add it back it in
				sortedApiTree.add(api);
			}
			//otherwise leave it removed

			return false;
		} else {
			//we found one less than amx time and greater than min time
			if (api.setTt(tt, endTime))
				sortedApiTree.add(api);
			return true;
		}
	}

	/**
	 * @return true if a change to the result has been made. Note that 
	 *    the gui may still have to update itself if the process has been interrupted
	 *    (in which case the result does not match the request)
	 */
	private boolean runBackgroundTaskForPathCalculator() {

		if (resultAreas.size() != requestedAreas.size()
				|| requestedEndTimeSec <= resultStartTime
				|| requestedStartTimeSec >= resultEndTime || resultIncomplete
				|| resultPaths.size() == 0) {
			ArrayList<Path> paths = findApiPaths(requestedAreas,
					requestedStartTimeSec, requestedEndTimeSec);

			if (workerInterrupted) {
				workerInterrupted = false;
				return false;
			}

			resultPaths = paths;
			copyRequestedInputToResultInput(false);
			upToDate = true;
			return true;
		}

		//since we have to keep the old results paths valid 
		// (in case there are still references to it somewhere)
		// we construct a new array to hold our changed results
		ArrayList<Path> newResultPaths = null;

		if (requestedEndTimeSec < resultEndTime
				|| requestedStartTimeSec > resultStartTime) {
			newResultPaths = new ArrayList<Path>(resultPaths);
			deletePathsOutsideOfRange(newResultPaths, requestedStartTimeSec,
					requestedEndTimeSec);
			//this is set here incase we are interrupted later, so that we 
			// still have a valid result
			resultStartTime = Math.max(requestedStartTimeSec, resultStartTime);
			resultEndTime = Math.min(requestedEndTimeSec, resultEndTime);
		}

		if (requestedStartTimeSec < resultStartTime)
		//we have to find paths up to the start of the currently known paths
		//or a partial path that we ignored last time wouldn't be found this time
		{
			ArrayList<Path> newPaths = findApiPaths(requestedAreas,
					requestedStartTimeSec, resultPaths.get(0).startTimeSec);

			if (workerInterrupted) {
				workerInterrupted = false;
				return false; //if we we're up to date before and we're not null
				//it doesn't really affect the viewer
			}

			if (!newPaths.isEmpty()) {
				if (newResultPaths == null)
					newResultPaths = new ArrayList<Path>(resultPaths);

				newResultPaths.addAll(0, newPaths);
			}
		}
		if (requestedEndTimeSec > resultEndTime) {
			//we have to find paths up to the start of the currently known paths
			//or a partial path that we ignored last time wouldn't be found this time
			ArrayList<Path> newPaths = findApiPaths(requestedAreas,
					resultPaths.get(resultPaths.size() - 1).endTimeSec,
					requestedEndTimeSec);

			if (workerInterrupted) {
				workerInterrupted = false;
				return false; //if we we're up to date before and we're not null
				//it doesn't really affect the viewer
			}

			if (!newPaths.isEmpty()) {
				if (newResultPaths == null)
					newResultPaths = new ArrayList<Path>(resultPaths);

				newResultPaths.addAll(newPaths);
			}
		}

		copyRequestedInputToResultInput(false);
		if (newResultPaths != null) {
			resultPaths = newResultPaths;
			upToDate = true;
			return true;
		}

		upToDate = true;
		return false;
	}

	/**
	 * Adds to the internal time range tree for the given area
	 * 
	 * @param requestedStartTimeSec
	 * @param requestedEndTimeSec
	 */
	public void updateTimeRangeTree(int incStartTimeSec, int incEndTimeSec) {
		// path from root of time trees for current area panel
		ArrayList<Integer> timeTreeParentPath = new ArrayList<Integer>();

		for (AreaPanelInfo api : requestedAreas.get(0).apiList) {
			GTG.cacheCreatorLock.registerReadingThread();
			try {
				AreaPanel ap = api.ap();

				timeTreeParentPath.clear();

				timeTreeParentPath.add(ap.getTimeTree().id);
				int lastSiblingTtFk = -1;

				//loop for searching through the ap's tt's
				while (!timeTreeParentPath.isEmpty()) {
					if (rwtm.isWritingHoldingUpWritingThreads()) {
						workerInterrupted = true;
						resultIncomplete = true;
						return;
					}

					TimeTree tt = GTG.ttCache.getRow(timeTreeParentPath
							.get(timeTreeParentPath.size() - 1));

					if (tt.getSubNodeFk(0) == Integer.MIN_VALUE)
					{
						if( tt.getMinTimeSecs() < incEndTimeSec
							&& tt.getMaxTimeSecs() > incStartTimeSec)
						// if at the bottom and within the area we're
						// interested in
						{
							int cutStartTimeSec = tt.calcTimeRangeCutStart();
							int cutEndTimeSec = tt.calcTimeRangeCutEnd();
							
							if(cutStartTimeSec < incEndTimeSec && cutEndTimeSec > incStartTimeSec)
							
							addTimeRange(tt, cutStartTimeSec, cutEndTimeSec);
	
						}
						
						// pop the tt off the stack so we can check out its
						// siblings
						lastSiblingTtFk = timeTreeParentPath
								.remove(timeTreeParentPath.size() - 1);
					} else {
						// check the children
						int nextTTChildIndex = 0;

						// if we already handled a prior child from the current
						// branch
						if (lastSiblingTtFk != -1) {
							for (; nextTTChildIndex < TimeTree.NUM_NODES; nextTTChildIndex++) {
								if (tt.getSubNodeFk(nextTTChildIndex) == lastSiblingTtFk) {
									break;
								}
							}

							if (nextTTChildIndex == TimeTree.NUM_NODES) {
								throw new IllegalStateException(
										"Couldn't find last tt sibling? "
												+ lastSiblingTtFk + ": "
												+ tt.getSubNodeFk(0) + ", "
												+ tt.getSubNodeFk(1) + ", "
												+ tt.getSubNodeFk(2) + ", "
												+ tt.getSubNodeFk(3));
							}

							lastSiblingTtFk = -1;

							// increase nextTTChildIndex so that we skip the
							// last sibling (which we already went down)
							nextTTChildIndex++;
						}

						// find a sub tt that matches
						// note that there may not be one, since we might be
						// starting half way through the
						// sub nodes because lastSiblingTtFk was set
						TimeTree subTT = null;

						for (; nextTTChildIndex < TimeTree.NUM_NODES; nextTTChildIndex++) {
							subTT = tt.getSubNode(nextTTChildIndex);

							if (subTT == null)
								break;

							// if we found it
							if (subTT.getMinTimeSecs() < incEndTimeSec
									&& subTT.getMaxTimeSecs() > incStartTimeSec)
								break;
							// if we are completely past the time, no need to
							// check any further items
							else if (subTT.getMinTimeSecs() >= incEndTimeSec) {
								subTT = null;
								break;
							} else
								// we continue to check. We set subTT to null so
								// that if we exhaust the children,
								// we will know that we didn't find any
								subTT = null;
						}

						// if we overlap
						if (subTT != null)
							timeTreeParentPath.add(subTT.id);
						// else we must go back up
						else {
							// pop the tt off the stack so we can check out its
							// siblings
							lastSiblingTtFk = timeTreeParentPath
									.remove(timeTreeParentPath.size() - 1);

						}
					}// else not at the bottom for tt processing
				}// if we are doing tt processing
			} finally {
				GTG.cacheCreatorLock.unregisterReadingThread();
			}

		}
	}

	// these are scrap time ranges that can be used whenever SAS is synchronized
	// (and only then)
	private AreaTimeRange t1TimeRange = new AreaTimeRange();
	private AreaTimeRange t2TimeRange = new AreaTimeRange();

	/**
	 * An ever increasing counter that is updated every time a new result is calculated
	 */
	public int resultId = 1;

	public boolean isResultTimeTree;

	/**
	 * 
	 * @param tt
	 * @param cutStartTimeSec must be within the stb or bugs will occur
	 * @param cutEndTimeSec same as above
	 */
	private void addTimeRange(TimeTree tt, int cutStartTimeSec, int cutEndTimeSec) {
		int fullRangeMinTimeSecs = tt.getMinTimeSecs();
		int fullRangeMaxTimeSecs = tt.getMaxTimeSecs();

		t1TimeRange.startTimeSec = fullRangeMinTimeSecs;
		t2TimeRange.startTimeSec = Integer.MAX_VALUE;

		// look for overlaps

		//find the tr to start with, that would be within
		// the time range being added
		AreaTimeRange tr = resultTimeRangeTree.floor(t1TimeRange);

		if (tr != null)
		{
			if(tr.fullRangeEndSec <= fullRangeMinTimeSecs) 
				tr = resultTimeRangeTree.higher(tr);
			else
				// if the previous time range encompasses the range we are going to
				// add
				if (tr.fullRangeEndSec > fullRangeMaxTimeSecs)
					return;
		}
		else if (!resultTimeRangeTree.isEmpty())
			tr = resultTimeRangeTree.first();
		
		AreaTimeRange ltr = null;
		
		//while still within the new area to be added
		while(tr != null && tr.fullRangeStartSec < fullRangeMaxTimeSecs)
		{
			if(tr.fullRangeStartSec < fullRangeMinTimeSecs)
				fullRangeMinTimeSecs = tr.fullRangeStartSec;
			if(tr.fullRangeEndSec > fullRangeMaxTimeSecs)
				fullRangeMaxTimeSecs = tr.fullRangeEndSec;
			if(tr.startTimeSec < cutStartTimeSec)
				cutStartTimeSec = tr.startTimeSec;
			if(tr.endTimeSec > cutEndTimeSec)
				cutEndTimeSec = tr.endTimeSec;

			ltr = tr;
			tr = resultTimeRangeTree.higher(tr);
			resultTimeRangeTree.remove(ltr);
			
		}
		
		//resuse the last tr that we removed if possible
		if(ltr == null)
			ltr = new AreaTimeRange(fullRangeMinTimeSecs,
					fullRangeMaxTimeSecs, cutStartTimeSec,
					cutEndTimeSec);
		else
		{
			ltr.fullRangeStartSec = fullRangeMinTimeSecs;
			ltr.fullRangeEndSec = fullRangeMaxTimeSecs;
			ltr.startTimeSec = cutStartTimeSec;
			ltr.endTimeSec = cutEndTimeSec;
		}

		resultTimeRangeTree.add(ltr);
	}

	private boolean runBackgroundTaskForTimeRange() {
		if (resultAreas.isEmpty()
				|| resultAreas.get(0) != requestedAreas.get(0)
				|| resultIncomplete) {
			resultTimeRangeTree = new TreeSet<AreaTimeRange>();

			updateTimeRangeTree(requestedStartTimeSec, requestedEndTimeSec);

			if (workerInterrupted) {
				//note that we don't set upToDate to be true
				workerInterrupted = false;
				return false; //if we we're up to date before and we're not null
				//it doesn't really affect the viewer
			}

			copyRequestedInputToResultInput(true);

			//note that we can't synchronize here because this could cause an rwtm to upToDateLock deadlock
			upToDate = true;
			return true;
		}

		if (!resultTimeRangeTree.isEmpty()) {
			//we need to clear out time ranges outside of the range. Technically we 
			//could keep them as a cache, but it makes listing them out more complex...
			//"How many time ranges in the list?" would be hard to determine for example.
			//We do keep long time ranges alone if they extend beyond the start and end
			//of the requested time. However, they at least need to overlap the requested
			// start and end time somewhat to survive this.

			//check if all items should be removed
			if (resultTimeRangeTree.first().fullRangeStartSec >= requestedEndTimeSec
					|| resultTimeRangeTree.last().fullRangeEndSec <= requestedStartTimeSec) {
				resultTimeRangeTree = new TreeSet<AreaTimeRange>();

				updateTimeRangeTree(requestedStartTimeSec, requestedEndTimeSec);

				if (workerInterrupted) {
					//note that we don't set upToDate to be true
					workerInterrupted = false;
					return false; //if we we're up to date before and we're not null
					//it doesn't really affect the viewer
				}

			} else {
				TreeSet<AreaTimeRange> newResultTimeRangeTree = new TreeSet<AreaTimeRange>();

				t1TimeRange.startTimeSec = requestedStartTimeSec;
				t2TimeRange.startTimeSec = requestedEndTimeSec;

				newResultTimeRangeTree.addAll(resultTimeRangeTree.subSet(
						t1TimeRange, t2TimeRange));

				AreaTimeRange leftEdge = resultTimeRangeTree.lower(t1TimeRange);

				if (leftEdge != null
						&& leftEdge.endTimeSec > requestedStartTimeSec)
					newResultTimeRangeTree.add(leftEdge);

				resultTimeRangeTree = newResultTimeRangeTree;
			}
		} else
			//this is to make sure that any old references floating out there won't
			//be changed by this new calculation (there currently aren't any, but you know...)
			resultTimeRangeTree = new TreeSet<AreaTimeRange>();

		if (requestedStartTimeSec < resultStartTime) {
			updateTimeRangeTree(requestedStartTimeSec, resultStartTime);

			if (workerInterrupted) {
				//note that we don't set upToDate to be true
				workerInterrupted = false;
				return false; //if we we're up to date before and we're not null
				//it doesn't really affect the viewer
			}

		}

		if (requestedEndTimeSec > resultEndTime)
			updateTimeRangeTree(resultEndTime, requestedEndTimeSec);

		copyRequestedInputToResultInput(true);

		upToDate = true;
		return true;
	}

	private void copyRequestedInputToResultInput(boolean isTimeTree) {
		lastTimeRange = null;
		resultAreas.clear();
		resultAreas.addAll(requestedAreas);
		resultStartTime = requestedStartTimeSec;
		resultEndTime = requestedEndTimeSec;

		if (isTimeTree) {
			isResultTimeTree = true;
			resultPaths = null;
		} else {
			isResultTimeTree = false;
			resultTimeRangeTree = null;
		}
	}

	/**
	 * If upToDate is true, and the result is paths,
	 * results the resultPaths, otherwise null. The
	 * caller can assume the resultPaths will never
	 * be modified by the sas thread 
	 * 
	 * @return
	 */
	public ArrayList<Path> getResultPaths() {
		synchronized (upToDateLock) {
			if (upToDate && !isResultTimeTree)
				return resultPaths;

			return null;
		}
	}

	public int getTotalTimeSecs() {
		rwtm.registerReadingThread();
		int totalTime = 0;
		
		for(TimeRange tr : resultTimeRangeTree)
		{
			int startTimeSec = Math.max(tr.startTimeSec, requestedStartTimeSec);
			int endTimeSec = Math.min(tr.endTimeSec, requestedEndTimeSec);
			
			if(startTimeSec > endTimeSec)
				throw new IllegalStateException("Why tr out of range? "+this+" tr: "+tr);
			
			totalTime += endTimeSec - startTimeSec;
		}
		
		rwtm.unregisterReadingThread();
		return totalTime;
	}

	public double getTotalDistM() {
		rwtm.registerReadingThread();
		double res = 0;
		
		for(TimeRange tr : resultTimeRangeTree)
			res += tr.dist;
		
		rwtm.unregisterReadingThread();
		return res;
	}

	public int getTimesInArea() {
		rwtm.registerReadingThread();
		try {
			return resultTimeRangeTree.size();
		}
		finally {
			rwtm.unregisterReadingThread();
		}
	}

	@Override
	public String toString() {
		return "SelectedAreaSet [isRunning=" + isRunning + ", isShutdown="
				+ isShutdown + ", rwtm=" + rwtm + ", activity=" + activity
				+ ", requestedAreas=" + requestedAreas
				+ ", requestedStartTimeSec=" + requestedStartTimeSec
				+ ", requestedEndTimeSec=" + requestedEndTimeSec
				+ ", resultPaths=" + resultPaths + ", resultTimeRangeTree="
				+ resultTimeRangeTree + ", resultAreas=" + resultAreas
				+ ", resultEndTime=" + resultEndTime + ", resultStartTime="
				+ resultStartTime + ", upToDate=" + upToDate
				+ ", distCalculated=" + distCalculated + ", timeZone="
				+ timeZone + ", p1=" + p1 + ", p2=" + p2 + ", lastTimeRange="
				+ lastTimeRange + ", lastIndex=" + lastIndex + ", observers="
				+ observers + ", getTimeRangeTr=" + getTimeRangeTr
				+ ", workerInterrupted=" + workerInterrupted
				+ ", resultIncomplete=" + resultIncomplete + ", upToDateLock="
				+ upToDateLock + ", t1TimeRange=" + t1TimeRange
				+ ", t2TimeRange=" + t2TimeRange + ", resultId=" + resultId
				+ ", isResultTimeTree=" + isResultTimeTree + "]";
	}

}
