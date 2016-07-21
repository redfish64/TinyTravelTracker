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
package com.rareventure.gps2.database.cachecreator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import android.database.Cursor;
import android.util.Log;

import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.android.DbUtil;
import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;
import com.rareventure.gps2.GpsTrailerCrypt;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;
import com.rareventure.gps2.database.cache.TimeTree;
import com.rareventure.gps2.database.cachecreator.ViewNode.VNStatus;
import com.rareventure.gps2.reviewer.map.GpsTrailerOverlay;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;
import com.rareventure.gps2.reviewer.map.ViewLine;
import com.rareventure.util.ReadWriteThreadManager;

//TODO 3: 3d view with time being z (or id)
/**
 * Creates the cache which is used to display the ui map in GpsTrailerOverlayDrawer.
 * Note, this is a thread because it takes a long time to commit, and we really can't
 * block the drawer thread while this is going on. Even though the user shouldn't be too
 * far out of sync, it can take quite awhile even to process a few days worth of points,
 * so we need this.
 *
 * It uses GTG.rwtm for locking purposes. Anytime a thread wants to read from
 * apCache or viewNode it must lock itself through GTG.rwtm. In addition, when
 * a thread is waiting on the lock, this thread will quit processing as soon as possible
 */
public class GpsTrailerCacheCreator extends Thread {

	private static final int SOFT_COMMITS_PER_ROUND = 25;

	/**
	 * Number of gps points to process before giving a progress update
	 */
	private static final int NUM_GPS_PROCESSING_PER_PROGRESS_UPDATE = 250;

	/**
	 * bit flag for calcViewNodes. Indicates all the nodes haven't finished
	 * being calculated
	 */
	public static final int CALC_VIEW_NODES_STILL_MORE_NODES_TO_CALC = 1;

	/**
	 * bit flag for calcViewNodes. Indicates some view nodes have been altered
	 * in a way for them to require lines to/from them to be recalculated
	 */
	public static final int CALC_VIEW_NODES_LINES_NEED_RECALC = 2;

	/**
	 * Amount of distance in meters used as a constant to determine how much of
	 * the average vs the current point to use
	 */
	private static final int GPS_JITTER_METERS = 99;

	private static final double AVERAGE_DECAY_PERC = 0.1;
	
	public static Preferences prefs = new Preferences();

	private ViewNode headVn;
	
	//note that we don't use a SparseIntArray because it is inefficient when adding or deleting,
	// in fact I don't see why its so hyped at all??
	//not that hashmap is that great either, but if we really want to improve efficiency we should
	//make an integer hashmap
	//note that we put these in cache creator because headVn is in cache creator. If we put these
	// in classes related to the main screen, and the user leaves and reenters the screen,
	//then the view nodes would still be present, but the view lines would not
	//TODO 2.5 possibly fix the above??? 
	public HashMap<Integer,ViewLine> startTimeToViewLine = new HashMap<Integer,ViewLine>();
	public HashMap<Integer,ViewLine> endTimeToViewLine = new HashMap<Integer,ViewLine>();
	
	public ReadWriteThreadManager viewNodeThreadManager = new ReadWriteThreadManager();
	
    //we use the drawer of this to alert the map view that its viewnodes have changed
    //after we load some stuff in our cache

	//whether the medialoctimemap is up to date with the gallery
	private boolean mediaDirty = true;
	
	public int minTimeSec;

	public int maxTimeSec;

	public OsmMapGpsTrailerReviewerMapActivity gtum;

	public boolean isShutdown;
	
	public GpsTrailerCacheCreator() {
//		Log.d(GTG.TAG, "GpsTrailerCacheCreator: created! "+this);
		headVn = ViewNode.createHeadViewNode();
		
		//we never really die, just pause or unpause.
		setDaemon(true);
		
		if(GTG.apCache.hasGpsPoints())
		{
			AreaPanel ap = GTG.apCache.getTopRow();
			
			minTimeSec = ap.getTimeTree().getMinTimeSecs(); 
			maxTimeSec = ap.getTimeTree().getMaxTimeSecs(); 
		}
		else
		{
			minTimeSec = maxTimeSec = (int) (System.currentTimeMillis()/1000);
		}
		
		if(!GTG.apCache.isDbFilled())
		{
			// create top area panel
			
			AreaPanel topAreaPanel = GTG.apCache.newRow();
			topAreaPanel.setData(0, 0, AreaPanel.DEPTH_TO_WIDTH.length - 1);
	
			try {
				GTG.timmyDb.beginTransaction();
				GTG.apCache.writeDirtyRows();
				GTG.timmyDb.setTransactionSuccessful();
				GTG.timmyDb.endTransaction();
				//we clear the dirty rows only after the transaction is committed so we
				//don't attempt to read an updated or inserted row while the commit is
				//being done, which is unsupported
				GTG.apCache.clearDirtyRows();
			}
			catch(IOException e)
			{
				throw new IllegalStateException(e);
			}
		}
		
	}
	
	public void setGtum(OsmMapGpsTrailerReviewerMapActivity gtum)
	{
		this.gtum = gtum;
	}
	
	public static double calcTrDist(int startTime, int endTime, ReadWriteThreadManager writingRwtm) {
		GTG.ccRwtm.registerReadingThread();

		try {
			//now we need to find the biggest ap with a start time equal to the best start time,
			// and an end time that is less or equal to the minimum of the tr end time and the 
			// stb end time, unless the lowest level ap's time tree extends beyond the end time,
			// in which case we use that
			// (which indicates that its dist is the distance of the travel to the ap plus the distance
			// within the time range and the ap itself and also within the stb.... because time ranges
			// can extend beyond the ends of the stb... However, at the lowest level, the time tree might
			// extend beyond the stb edge (because tt's overlap to the next ap), and if so, we must use
			// that, since the distance of that tt is always dist from prev point to current ap, so it
			// would be correct to use that.

			//the tt of the largest ap with a start time that is closest to startTime without exceeding it. 
			//note that at there should be many ap's with the same start time that encapsulate each other.
			// We want the largest one so we can advance the time as fast as possible
			//note also that this won't always be start time in the case that a time range is cut off by the start of the stb
			
			int bestStartTime = Integer.MIN_VALUE;
			TimeTree bestTtInPath = null;
			AreaPanel bestApInPath = null;
			
			double dist = 0;

			AreaPanel ap = GTG.apCache.getTopRow();
			
			if(ap == null || ap.getTimeTree() == null)
				return 0;
			
			//we limit the range to the area actually calculated so that we have conitinous
			//coverage over all times (to avoid triggering some sanity checks)
			startTime = Math.max(ap.getStartTimeSec(), startTime);
			endTime = Math.min(ap.getEndTimeSec(), endTime);
			
			if(startTime >= endTime)
				return 0;
			
			//the ap used in the last time range calculation
			//we use this so that we don't need to go down all the way to zero
			//depth to determine the biggest ap that is entered at the start time
			//(if the ap doesn't overlap the lastTimeAp, then we know it will be the
			// ap the current start time first entered)
			AreaPanel lastTimeAp = null;

			for (;;) {
				int i;
				
				//note, we need the following because when we are determining the initial
				//area panel in the time range, we may hit either the prior ap or the next
				//ap (since the time ranges overlap). We are only interested in the next
				//ap after the startTime, so we traverse all of them.
				TimeTree bestCurrLevelChildTt = null;
				int bestCurrLevelStartTime = Integer.MIN_VALUE;
				AreaPanel bestCurrLevelChildAp = null;

				//find the child that has a bottom level tt that encompasses the startTime (there should be only one)
				for (i = AreaPanel.NUM_SUB_PANELS-1; i >= 0; i--) {
					AreaPanel childAp = ap.getSubAreaPanel(i);

					if (childAp == null)
						continue;

					if(writingRwtm != null)
					{
						writingRwtm.pauseForReadingThreads();
						if (writingRwtm.isWritingHoldingUpWritingThreads()) {
							return -1;
						}
					}
					
					TimeTree childTt = childAp.getTimeTree()
							.getBottomLevelEncompassigTimeTree(startTime);
					
					if(childTt != null && childTt.getMinTimeSecs() >= bestStartTime && 
							childTt.getMinTimeSecs() > bestCurrLevelStartTime)
					{
						bestCurrLevelChildAp = childAp;
						bestCurrLevelChildTt = childTt;
						bestCurrLevelStartTime = childTt.getMinTimeSecs();
						
						//if we know the ap just prior to the start time, then
						//we can be sure we have hit the next tt rather than the prior one
						if(lastTimeAp != null && !childAp.overlaps(lastTimeAp))
							break;
					}
					
				}

				
				//note that there should always be an encompassing child because startTime is taken from a time range
				//which means that we have already determined that this time is within some ap somewhere
				if (bestCurrLevelChildTt == null)
					throw new IllegalStateException(
							"Couldn't find a child ap encompassing time "
									+ startTime);

				//note, if at the lowest level, the distance won't be anything greater than the distance from the
				//prev ap to the current ap, so we don't have to worry about end time at that point
				//(note that the reason we care about end time is we don't want an ap that extends beyond
				// the requested end time, since that would have a distance that includes a time that is after
				// the requested time.
				if (bestCurrLevelChildTt != null
						&& bestCurrLevelChildTt.getMinTimeSecs() >= bestStartTime) {
					
					//notice we are only including child tt's that have a later start time and still
					//are valid, > vs the >= in the above if
					if (bestCurrLevelChildTt.getMinTimeSecs() > bestStartTime
							&& (bestCurrLevelChildTt.getMaxTimeSecs() <= endTime || bestCurrLevelChildAp
									.getDepth() == 0)) {
						bestStartTime = bestCurrLevelChildTt.getMinTimeSecs();
						bestTtInPath = bestCurrLevelChildTt;
						bestApInPath = bestCurrLevelChildAp;
					}

					//we need to go to depth zero on the first try, because we need to subtract the distance between
					//the prior ap and the current one. After that, we add the distances between the ap's together
					//so we only need to find an ap that exists outside of the last one we visited
					if(bestCurrLevelChildAp.getDepth() == 0 || bestCurrLevelChildAp == bestApInPath 
							&& lastTimeAp != null && !lastTimeAp.overlaps(bestCurrLevelChildAp))
					{
						dist += bestTtInPath.getPrevAndCurrDistM();
						
						if(lastTimeAp == null)
						{
							//considering that the best ap may not be zero level and getPrevAndCurrDistM returns
							//the distance within the area panel added to the distance from the prior ap
							//to the current ap, we need to remove this first part.
							//in order to do so, we find the bottom level ap, whos getPrevAndCurrDistM will
							//return just the distance from the prior ap to the current ap and is contained
							//within the ap
							dist -= bestCurrLevelChildTt.getPrevAndCurrDistM();
						}
						
						//if we've reached the end of the tr
						if (bestTtInPath.getMaxTimeSecs() >= endTime)
							return dist; //we're finished, so return the distance

						lastTimeAp = bestApInPath;
						
						ap = GTG.apCache.getTopRow();
						
						startTime = bestTtInPath.getMaxTimeSecs();
						
						//note, we need to reset bestStartTime even though we are on to 
						//the next start time which is after the current area panel.
						//This is because a large ap that encompasses both the previous
						//ap and the next one will have an encompassing tt with a  start 
						//time that may be less then the prior ap
						bestStartTime = Integer.MIN_VALUE;
						bestTtInPath = null;
						bestApInPath = null;
					}
					else //otherwise, we need to go to the child that encompasses the start time, recursively,
						//until we find one that has an encompassing time range that doesn't extend beyond the
						//end time
					{
						ap = bestCurrLevelChildAp;
					}
				}

			} // end for(;;) while we haven't reached end time
		} finally {
			GTG.ccRwtm.unregisterReadingThread();
		}
	}

	public void run()
	{
		try {
			long nextTimeToLoadPointsMs = 0;
			long nextTimeToLoadMediaMs = 0;
			
			for(;;)
			{
				if(isShutdown)
					break;
				
		
				//make sure we aren't running out of room
				if(!GTG.checkSdCard(gtum)) 
				{
					return;
				}
				
				long currTime = System.currentTimeMillis();
				
				//look for new gps points to be cached
				if(nextTimeToLoadPointsMs < currTime )
				{
					GTG.alert(GTGEvent.LOADING_MEDIA,false);
					if(loadNextPoints())
						nextTimeToLoadPointsMs = System.currentTimeMillis() + prefs.areaPanelUpdateGpsLocSpinLockMs;
				}
		
				//update the media (images and videos) r-tree based on the media
				// urls 
				if(this.mediaDirty && gtum != null)
				{
					GTG.alert(GTGEvent.PROCESSING_GPS_POINTS,false);
					//if we finished updating all the images
					if(GTG.mediaLocTimeMap.updateFromGallery(this, gtum.getContentResolver()))
						this.mediaDirty = false;
				}
		
				//there are two things to wait for. First, for gps service to collect more points,
				// and second, for the drawer thread to finish calculating its view nodes so they are
				// no longer dirty (to make it easy we don't update view nodes until they are all clean)
				//TODO 3: we should use a push mechanism for gps updates.
				
				//TODO 3: we are using the fact that this always runs to clear out deleted mlts
				// periodically. we should probably have a notification mechanism
				GTG.mediaLocTimeMap.deleteMarkedMltsFromDb();
		
				//we wait until we think there may be more points if we're fully updated, or
				// for a short while if we were not fully finished since last time
				long waitTime = ((mediaDirty && gtum != null) ? 0 : nextTimeToLoadPointsMs - System.currentTimeMillis());
//				Log.d(GTG.TAG, "Sleeping for "+waitTime);
				
				if(isShutdown)
					break;

				synchronized(this)
				{
					if(waitTime > 0)
					{
						GTG.alert(GTGEvent.LOADING_MEDIA,false);
						GTG.alert(GTGEvent.PROCESSING_GPS_POINTS,false);
						
						//wait for more gps data. the gps service will notify us if it processes
						//a point
						try {
							wait(waitTime);
						} catch (InterruptedException e) {
							// we should never be interrupted
							throw new IllegalStateException(e);
						}
					}
				}
			}
		}
		catch(RuntimeException e)
		{
			Util.printAllStackTraces();
			throw e;
		}
		catch(Error e)
		{
			Util.printAllStackTraces();
			throw e;
		}
			
	}

	// public static void createFakeDataHack(int hackStop) {
	// int lastGpsLocId = prefs.lastPointCachedId;
	//
	// if(lastGpsLocId >= hackStop)
	// {
	// Log.e("HACK","Already reached hackStop, "+hackStop);
	// return;
	// }
	//
	// if(!GTG.USE_TEST_DB)
	// throw new
	// IllegalStateException("you don't want to use the real database, I think");
	//
	// int startId = 9604;
	// int repeats = 300;
	//
	// Random r = new Random(1000);
	//
	// SQLiteDatabase db = GTG.db;
	//
	// //first we load the data in memory.
	// ArrayList<GpsLocationRow> rows = new ArrayList<GpsLocationRow>();
	//
	// GpsLocationRow currGpsLocRow = GpsTrailerCrypt.allocateGpsLocationRow();
	//
	// SQLiteCursor c = (SQLiteCursor) currGpsLocRow.query(db,
	// GpsLocationRow.TABLE_NAME, "_id >= ?", "_id",
	// new String [] { String.valueOf(startId) });
	//
	// //CTODO 2: keeps drawing overlay over and over when the bubble is up in a
	// busy loop
	// //try for closing the cursor in a finally
	// try {
	//
	// //while processing this query
	// while(c.moveToNext())
	// {
	// currGpsLocRow = GpsTrailerCrypt.allocateGpsLocationRow();
	// currGpsLocRow.readRow(c);
	// rows.add(currGpsLocRow);
	// }
	// }
	// finally {
	// DbUtil.closeCursors(c);
	// }
	//
	// Log.i(GpsTrailer.TAG,
	// "creating cache HACK for "+startId+" to "+currGpsLocRow.id+" repeats "+repeats);
	//
	// GpsLocationRow lastGpsLocRow = GpsTrailerCrypt.allocateGpsLocationRow();
	// currGpsLocRow = GpsTrailerCrypt.allocateGpsLocationRow();
	//
	// long time = 0;
	//
	// boolean firstTime = true;
	// int lastIndex = 0;
	//
	// int currGpsLocId = rows.get(rows.size()-1).id;
	//
	// while(repeats-- >= 0)
	// {
	// Log.i(GpsTrailer.TAG, "repeats "+repeats);
	//
	// db.beginTransaction();
	//
	// try {
	// Log.i(GpsTrailer.TAG, "fake id is "+currGpsLocId);
	//
	// int i = rows.size()-1;
	// boolean forwards = false;
	//
	// while(i < rows.size())
	// {
	// if(!firstTime)
	// {
	// lastGpsLocRow.copyRow2(currGpsLocRow);
	// lastGpsLocRow.id = currGpsLocRow.id;
	// currGpsLocRow.copyRow2(rows.get(i));
	// currGpsLocRow.id = rows.get(i).id;
	// time = currGpsLocRow.hackRandomize(r, time,
	// time + Math.abs(currGpsLocRow.getTime() - rows.get(lastIndex).getTime()),
	// 120 * 60 * 1000, 500, 3, 3);
	//
	// currGpsLocRow.id = currGpsLocId;
	//
	// if(currGpsLocId % 50 == 0)
	// {
	// GTG.turnOnMethodTracing = true;
	// }
	//
	// if(currGpsLocId <= lastGpsLocId)
	// Log.d("GTG","Skipping "+currGpsLocId);
	// else
	// processGpsPoint(lastGpsLocRow, currGpsLocRow);
	//
	// if(currGpsLocId % 50 == 0)
	// {
	// GTG.turnOnMethodTracing = false;
	// }
	// }
	// else
	// {
	// currGpsLocRow.copyRow2(rows.get(i));
	// currGpsLocRow.id = rows.get(i).id;
	// time = rows.get(i).getTime();
	// firstTime = false;
	// }
	//
	// lastIndex = i;
	// i += forwards ? 1 : -1;
	//
	// if(i < 0)
	// {
	// i = 0;
	// forwards = true;
	// }
	//
	// if(currGpsLocId >= GTG.HACK_STOP)
	// break;
	// currGpsLocId++;
	//
	// } // while going through the rows in both directions
	//
	// prefs.lastPointCachedId = currGpsLocRow.id;
	//
	// //save the position we've gotten to in androidprefs
	// GTG.prefSet.saveIndividualPrefToDatabase( db, prefs,
	// "lastPointCachedId");
	//
	// GTG.apCache.commitDirtyRows();
	// db.setTransactionSuccessful();
	// }
	// finally
	// {
	// db.endTransaction();
	// }
	// Log.i(GpsTrailer.TAG, "finished!!!!!!!!!!!!!!!!!!!!!!!!");
	// }
	//
	// }

	public static void clearOutDbHack(boolean force) {

		if (GTG.CLEAR_OUT || force) {
			try {
				GTG.timmyDb.close();
				GTG.timmyDb.deleteDatabase();
				//TODO 3: WARNING!!!!!!! not sure if open works right after a close and delete
				GTG.timmyDb.open();
			}
			catch(IOException e)
			{
				throw new IllegalStateException(e);
			}
			
			Log.i("GTG", "Done clearing database");
		}
	}
	
	

	

	public synchronized void notifyMediaDirty()
	{
		/* ttt_installer:remove_line */Log.d(GTG.TAG,"notifying media dirty");
		mediaDirty = true;
		notify();
	}

	/**
	 * 
	 * @return true if all points have been calculated for the current set of gps points
	 */
	public boolean loadNextPoints() {
		
		if(GTG.timmyDb.getProperty("lastPointReadId") == null)
		{
			try {
				GTG.timmyDb.beginTransaction();
				GTG.timmyDb.setProperty("lastPointReadId","-1");
				GTG.timmyDb.setProperty("lastPointCachedId","-1");
				GTG.timmyDb.setProperty("lastAdjLonm","0");
				GTG.timmyDb.setProperty("lastAdjLatm","0");
				GTG.timmyDb.setProperty("avgLonm",Util.doubleToHex(0d));
				GTG.timmyDb.setProperty("avgLatm",Util.doubleToHex(0d));
				GTG.timmyDb.saveProperties();
				GTG.timmyDb.setTransactionSuccessful();
				GTG.timmyDb.endTransaction();
			}
			catch(IOException e)
			{
				throw new IllegalStateException(e);
			}
		}
		
		/*
		 * The last gps row we read from the database for caching purposes, 
		 * regardless if it was successful or not. 
		 * 
		 * Note, the reason for this is because if we try processing a whole
		 * block of points and for some reason they all failed, we would not
		 * want to start back at the last successful one, because then we'd get
		 * into an infinite loop. 
		 */
		int origLastPointReadId, lastPointReadId;
		
		origLastPointReadId = lastPointReadId = Integer.parseInt(GTG.timmyDb.getProperty("lastPointReadId"));

		/*
		 * The last point gps row we successfully processed
		 */
		int origLastPointCachedId, lastPointCachedId;
		
		origLastPointCachedId = lastPointCachedId = Integer.parseInt(GTG.timmyDb.getProperty("lastPointCachedId"));

		int lastAdjLatm = Integer.parseInt(GTG.timmyDb.getProperty("lastAdjLatm"));
		int lastAdjLonm = Integer.parseInt(GTG.timmyDb.getProperty("lastAdjLonm"));
		
		//note these have to be hex rather than just the printed representation because
		// some floating point numbers cannot be represented exactly accurately using the printed notation
		double avgLatm = Util.hexToDouble(GTG.timmyDb.getProperty("avgLatm"));
		double avgLonm = Util.hexToDouble(GTG.timmyDb.getProperty("avgLonm"));

		//true if this is the very first point were processing
		//TODO 2.5 hack, we should probably not have a top row at all if there are no points
		boolean isVeryFirst = GTG.apCache.getTopRow() == null || GTG.apCache.getTopRow().getTimeTree() == null;

		int count = 0;
		
		//note that we set the limit so that we will read past all the points that we failed on
		//plus the cache loading step. This way we guarantee we will make progress and not 
		//get stuck in an infinite loop ... we could skip the ones we tried to read before and
		//failed, but that would be kind of complicated
		//note, we multiply the difference times three over two, so for long stretches of corrupted points,
		//we will eventually get past them
		int limit = prefs.gpsCacheLoadingStep;

		GpsLocationRow currGpsLocRow = GpsTrailerCrypt.allocateGpsLocationRow();
		GpsLocationRow lastGpsLocRow = null;
		
		Cursor c = null;

		
		boolean atLeastOneToProcess = false;
		
		long [] currDateMsToLatestDateMs = new long[2];
		
		// this try is to close the transaction with a finally
		//and for removing the rwtm locks
		GTG.ccRwtm.registerWritingThread();

		try {
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"Started apCaching...");
						
			for(int softCommitCounter = 0; softCommitCounter < SOFT_COMMITS_PER_ROUND; softCommitCounter++)
			{
				if(isShutdown)
					break;

				//for the first time, we need to load the previous item because we don't have one
				if(lastGpsLocRow == null)
					c = GTG.gpsLocDbAccessor.query( "_id = ? or _id > ?", "_id limit " + 
						limit,
						new String[] { String.valueOf(lastPointCachedId), String.valueOf(lastPointReadId) });
				else
					c = GTG.gpsLocDbAccessor.query( "_id > ?", "_id limit " + 
							limit,
							new String[] { String.valueOf(lastPointReadId) });
					
		
//				Log.d(GTG.TAG, "Getting next batch of rows, lastPointReadId="+lastPointReadId
//						+", lastPointCachedId="+lastPointCachedId+" total limit="+limit+
//						", lastAdjLonm="+lastAdjLonm+", lastAdjLatm="+lastAdjLatm
//						+", avgLonm="+avgLonm+", avgLatm="+avgLatm);
				
				int currGpsLocId = lastPointReadId;;
				
				if(c.isAfterLast())
				{
					break; //break out of the soft commit loop
				}
	
				// while processing this query
				while (c.moveToNext() && !GTG.HACK_TURN_OFF_APCACHE_LOADING) {
					currGpsLocId = c.getInt(0);
	
					try {
						GTG.gpsLocDbAccessor.readRow(currGpsLocRow, c);
					} catch (Exception e) {
						// sometimes the encryption fails to work (not often)
						// TODO 3: figure out how to fail gracefully in these
						// situations
						Log.e("GTG", "Error reading row " + c.getInt(0)
								+ ", skipping", e);
						
						continue;
					}
					if (lastGpsLocRow == null) {
						lastGpsLocRow = currGpsLocRow;
						currGpsLocRow = GpsTrailerCrypt.allocateGpsLocationRow();
						
						//if this is the very first processable row
						if(lastAdjLatm == 0 && lastAdjLonm == 0)
						{
							lastAdjLonm = lastGpsLocRow.getLonm();
							lastAdjLatm = lastGpsLocRow.getLatm();
							avgLonm = lastAdjLonm;
							avgLatm = lastAdjLatm;
						}
						
						continue;
					}
	
					currDateMsToLatestDateMs[0] = currGpsLocRow.getTime();
					
					if(count % NUM_GPS_PROCESSING_PER_PROGRESS_UPDATE == 0)
						GTG.alert(GTGEvent.PROCESSING_GPS_POINTS, true, currDateMsToLatestDateMs);

					//if there are threads waiting to run, now is a good time to do it, since
					//we're at a discrete point of creating the apcaches
					//
					//Note that the drawer thread won't give up its reading lock until the view
					//is completely up to date, so we don't have to worry about the viewnodes
					//being half updated
					GTG.ccRwtm.pauseForReadingThreads();
					
					//note, we can use the headVn stbox because all vn's should be clean by this point
					//this is used strictly to populate the viewnode tree
					int minDepth = headVn.stBox == null ? 0 : GpsTrailerOverlay.getMinDepth(headVn.stBox);
					
					int latm = currGpsLocRow.getLatm();
					int lonm = currGpsLocRow.getLonm(); 
					
					double distFromAvg = Util.calcDistFromLonmLatm(lonm, latm, avgLonm, avgLatm);
					double k = GPS_JITTER_METERS / (Math.abs(distFromAvg + GPS_JITTER_METERS) + distFromAvg);
					
					int adjLonm = (int) (lonm*(1-k) + avgLonm * k);
					int adjLatm = (int) (latm*(1-k) + avgLatm * k);
					
					
//					//find out if we are too far away from the current location 
//					double distToLpf = Util.calcDistFromLonmLatm(currGpsLocRow.getLonm(), currGpsLocRow.getLatm(), 
//							lonm, latm);	
//					
//					if(distToLpf > MAX_LOW_PASS_FILTER_AFFECT_METERS)
//					{
//						//adjust to the current location
//						lonm = (int) (currGpsLocRow.getLonm() + (currGpsLocRow.getLonm() - lonm) * MAX_LOW_PASS_FILTER_AFFECT_METERS / distToLpf);
//						latm = (int) (currGpsLocRow.getLatm() + (currGpsLocRow.getLatm() - latm) * MAX_LOW_PASS_FILTER_AFFECT_METERS / distToLpf);
//					}
					
					double dist = Util.calcDistFromLonmLatm(lastAdjLonm, lastAdjLatm, adjLonm, 
							adjLatm);
					
					//xODO 2 HACK... note this messes up GpsTrailerService, since it affects the cache... caused a unique key exception, not sure why
//					currGpsLocRow.setData(currGpsLocRow.getTime(),(int) lpfLatm, (int)lpfLonm, currGpsLocRow.getAltitude());
					
					boolean wasSuccessful = processGpsPoint(dist, lastAdjLonm, lastAdjLatm, lastGpsLocRow, adjLonm, adjLatm,
							currGpsLocRow, minDepth, isVeryFirst);
					
					isVeryFirst = false;
	
	//					Log.d(GpsTrailer.TAG, "apcache misses: " + GTG.apCache.misses
	//							+ ", hits: " + GTG.apCache.hits + ", dirtyRows " + GTG.apCache.getDirtyRowCount());
	//	
	
//					Log.d(GpsTrailer.TAG, "Gps loc " + currGpsLocRow+" adjLonm= "+adjLonm+" adjLatm= "+adjLatm+" lastAdjLonm= "+lastAdjLonm+" lastAdjLatm= "+lastAdjLatm
//							+ " added " + count + " successful? "+wasSuccessful);

					if (wasSuccessful) {
						atLeastOneToProcess = true;
						
						GpsLocationRow temp = lastGpsLocRow;
						lastGpsLocRow = currGpsLocRow;
						currGpsLocRow = temp;
						count++;
						
						lastAdjLonm = adjLonm;
						lastAdjLatm = adjLatm;
							
						//note that we use the adjusted values here, which are partially based on the average
						//This creates a feedback loop so that if the phone is stationary, the noise will affect
						// it less and less
						//note also we use "k" which is shared by our calculation for adjustment. This makes the average
						// move more or less based on whether the movement was big enough
						//note also that we don't move the avg if we did not save the gps point. This is because we aren't moving 
						// the lastGpsLocRow either, which would change where we started next time in the condition that we
						//moved through the entire cursor *this* time. If we did change the averge for every reading, we'd
						//then be changing it again for the same points when we restarted, which would mean that we wouldn't
						//have consistent results depending on where we stopped in the data.
						avgLonm = k * avgLonm + (1-k) * adjLonm;
						avgLatm = k * avgLatm + (1-k) * adjLatm;
						
					} 
//					else
//						Log.d(GpsTrailer.TAG,
//								"Not switching last and current because we failed to process");
	
					if (lastGpsLocRow.id >= GTG.HACK_FAIL_STOP)
						TAssert.fail("failing cause you want me to, you know you do");
	
				} // while we are moving through the cursor
	
				//we save the last *successful* gps row. This is because we need to know
				// the time between the last and the current row
				if(lastGpsLocRow != null)
					lastPointCachedId = lastGpsLocRow.id;
				
				lastPointReadId = currGpsLocId;

				if(count > 0)
				{
					minTimeSec = GTG.apCache.getTopRow().getStartTimeSec();
					maxTimeSec = GTG.apCache.getTopRow().getEndTimeSec();
	
					final OsmMapGpsTrailerReviewerMapActivity localGtum = gtum;

					//notify the listeners (sort of) that the data has changed
					//note, localizing incase drawer is changed between check for null and call
					if(localGtum != null)
					{
						localGtum.runOnUiThread(new Runnable() {
							
							@Override
							public void run() {
								localGtum.notifyMaxTimeChanged();
							}
						});
						localGtum.gpsTrailerOverlay.notifyViewNodesChanged();
					}
				}
				
				if(!GTG.timmyDb.inTransaction())
				{
					GTG.timmyDb.beginTransaction();
				}


				GTG.apCache.writeDirtyRows(GTG.ccRwtm);
				GTG.ttCache.writeDirtyRows(GTG.ccRwtm);
				
				GTG.apCache.clearDirtyRows();
				GTG.ttCache.clearDirtyRows();
			}// while doing soft commits
			
		} catch (IOException e)
		{
			Log.e(GTG.TAG,"Exception",e);
			throw new IllegalStateException(e);
		}
		finally {
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"loadNextPoints, count is "+count);
			DbUtil.closeCursors(c);
			GTG.ccRwtm.unregisterWritingThread();

			//co: the flickering is very annoying caused by turning the
			// processing gps points message on and off, so we just leave it on unless we are really
			// going to sleep
//			GTG.alert(GTGEvent.PROCESSING_GPS_POINTS, false);

		}

		//note that we can commit to the db while reading from the cache without problems
		
		try{
			if(origLastPointCachedId != lastPointCachedId ||
					origLastPointReadId != lastPointReadId)
			{
				if(!GTG.timmyDb.inTransaction())
					GTG.timmyDb.beginTransaction();
			
				GTG.timmyDb.setProperty("lastPointCachedId", lastPointCachedId);
				GTG.timmyDb.setProperty("lastPointReadId", lastPointReadId);
				GTG.timmyDb.setProperty("lastAdjLatm", lastAdjLatm);
				GTG.timmyDb.setProperty("lastAdjLonm", lastAdjLonm);
				GTG.timmyDb.setProperty("avgLatm", Util.doubleToHex(avgLatm));
				GTG.timmyDb.setProperty("avgLonm", Util.doubleToHex(avgLonm));

				GTG.timmyDb.saveProperties();
				
				GTG.timmyDb.setTransactionSuccessful();
				GTG.timmyDb.endTransaction(GTG.ccRwtm);
			}
			else if(GTG.timmyDb.inTransaction())
				GTG.timmyDb.endTransaction();
			
		} catch (IOException e)
		{
			/* ttt_installer:remove_line */Log.e(GTG.TAG,"Exception",e);
			throw new IllegalStateException(e);
		}
		finally {
			DbUtil.closeCursors(c);
		}
		
		
		//if we updated any gps locs at all, we may have affected the 
		//location of some of the images and video
		if(count != 0)
		{
			// note that we don't use GTG.rwtm for this because mediaLocTimeMap
			// doesn't use GTG.rwtm at all
			GTG.mediaLocTimeMap.updateTempLocs();
		}
		
		return !atLeastOneToProcess;
	}

	/**
	 * Adds one gps point to the apcache and ttcache.
	 * @param lpfLatm 
	 * @param lpfLonm 
	 * @param lastLpfLatm 
	 * @param lastLpfLonm 
	 */ 
	private boolean processGpsPoint(double dist, int lastLpfLonm, int lastLpfLatm, GpsLocationRow lastGpsLocRow,
			int lpfLonm, int lpfLatm, GpsLocationRow currGpsLocRow, int minDepth, boolean isVeryFirst) {
		int lastTimeSec = (int) (lastGpsLocRow.getTime() / 1000);
		int timeSec = (int) (currGpsLocRow.getTime() / 1000);

		// AreaPanels only keep time to the second, therefore we compare seconds
		// to check for equal times
		// In addition, I believe that the way that line calculations work, each
		// areapanel must overlap its time with the next area panel. Because so
		// we need at least 2 seconds of separation between them. In addition,
		// strangely (TODO 3) the code is only extending the previous ap to timeSec -1,
		// rather than timeSec, and I'm not sure why, but I don't want to tweak
		// it now. So I'm making them have to be at least 3 seconds apart for this
		// TODO 3: do we want to change that we store time as ints in AreaPanel?
		if (lastTimeSec >= timeSec -2) {
//			Log.d(GpsTrailer.TAG, "Skipping gps loc row " + currGpsLocRow.id
//					+ " since it has the same time as previous measurement");
			return false;
		}

		if (currGpsLocRow.getTime() < lastGpsLocRow.getTime()) {
			// TODO 3: What do we really want to do for this? It can be tricky
			// if the time is set to something far in
			// the future.. will we have to delete points? Maybe check them for
			// sanity beforehand? But how can we do that
			// if it's an on the fly thing?
//			Log.d(GpsTrailer.TAG,
//					"Skipping gps loc row "
//							+ currGpsLocRow.id
//							+ " since it has a previous time than that of the last successful row");
			return false;
		}

		//		Log.d(GpsTrailer.TAG, "Processing gps loc row " + currGpsLocRow
//				+ " last row time " + lastGpsLocRow.getTime());

		// we load the data in chunks to save memory (also if loading more than
		// 4000 rows, an i/o exception is thrown by
		// sqlite)

		int lastX = AreaPanel.convertLonmToX(lastLpfLonm);
		int lastY = AreaPanel.convertLatmToY(lastLpfLatm);
		int x = AreaPanel.convertLonmToX(lpfLonm);
		int y = AreaPanel.convertLatmToY(lpfLatm);
		
		if (lastX == x && lastY == y) {
//			Log.d(GpsTrailer.TAG, "Skipping gps loc row " + currGpsLocRow.id
//					+ " since it has the same location as previous measurement");
			return false;
		}
//		Log.d(GpsTrailer.TAG, "processing row lastX "+lastX+" "+"lastY "+lastY+" x "+x+" y "+y);

		// this will handle processing the item
		GTG.apCache.getTopRow().addPoint(currGpsLocRow.id,
				isVeryFirst ? null : GTG.apCache.getTopRow(), lastX, lastY, x, y, lastTimeSec, timeSec,
						dist);
		
		viewNodeThreadManager.registerWritingThread();
		//if head is unknown, we'll just let  calcViewableNodes handle it
		if(headVn.status != null)
			//add to the view node, so we can update the current view
			headVn.addPointToHead(x, y, lastTimeSec, timeSec, minDepth);
		viewNodeThreadManager.unregisterWritingThread();

		return true;
	}

	/**
	 * Calculate viewable nodes for a given space time box
	 *
	 * @param newLocalStBox
	 * @param minDepth
	 * @param earliestOnScreenPoint
	 * @param latestOnScreenPoint
     * @return
     */
	public int calcViewableNodes(AreaPanelSpaceTimeBox newLocalStBox,
			int minDepth, int earliestOnScreenPoint, int latestOnScreenPoint) {
//		Log.d("GPS", "calcViewableNodes start");
		
		
		viewNodeThreadManager.registerWritingThread();

		try {
			
			if (newLocalStBox != headVn.stBox) {
				/* ttt_installer:remove_line */Log.d("GPS", "Turning on all dirty flags");
				headVn.turnOnAllDirtyFlags(minDepth);
			}
	
			//if not dirty and kids aren't dirty
			if (headVn.dirtyDescendents == 0) {
				/* ttt_installer:remove_line */Log.d("GPS", "Finished because there are no more unkNodes in head");
				return 0;
			}
			
			if(headVn.dirtyDescendents < 0)
				TAssert.fail("why is dirtyDescendents below zero? " +headVn);
	
			ViewNode vn = headVn;
	
			// path from head node to current node
			ArrayList<ViewNode> parentsAndCurrent = new ArrayList<ViewNode>(
					AreaPanel.DEPTH_TO_WIDTH.length);
			
			boolean linesRecalcNeeded = false;
	
			// find an unknown guy with the smallest density (to work on next),
			// unless the stBox has changed since the
			// last time the vn has been visited, in which case, we check it
			// immediately.
	
			// PERF: it doesnt make sense to do only one child if its siblings also
			// are viewable in the display
			// area, since we can't really display it without knowing whether its
			// siblings are set or not
			while (!vn.needsProcessing(newLocalStBox)) {
				parentsAndCurrent.add(vn);
	
				// note, we know the vn has children, because a leaf node which is
				// not unk,
				// would have a zero dirtyDescendents
				// wouldn't be
				// chosen (ie, below selfOrDescendentsNeedProcessing() would return
				// false
				// for one its parents)
	
				int minDensity = Integer.MAX_VALUE;
				ViewNode bestChild = null;
				
				if (vn.ap().getDepth() == minDepth)
					TAssert.fail("Why does node say its dirty but doesn't need processing and is "
							+ " at the lowest level? " + vn+" min depth "+minDepth);
	
				// find child with fewest "on" dots with unknown descendents
				// remaining to process
				// (this allows us not work too hard on a big blob with a huge
				// number of points,
				// and concentrate on distinct features of small sets of points
				// first)
				for (ViewNode child : vn.children) {
					if(child == null)
						continue;
					
					// in order to display one child of a parent, all the other
					// children
					// of the same parent must not be unknown, so before we work on
					// any
					// of the children of the children of this node, we have to
					// process
					// all the children of the current parent
					if (child.needsProcessing(newLocalStBox)) {
						bestChild = child;
						break;
					}
					
					int childDensity;
					
					if (child.dirtyDescendents > 0
							&& (childDensity = child.getDensity(newLocalStBox)) < minDensity) {
						minDensity = childDensity;
						bestChild = child;
					}
				}
	
				// if we couldn't find any child to do work on
				if (bestChild == null) {
					TAssert.fail("why is parent dirty without a child: "
							+ parentsAndCurrent+" children "+Arrays.toString(vn.children));
				}
	
				vn = bestChild;
			}
	
			parentsAndCurrent.add(vn);
	
			// now that we know which one we're going to work on, process it
	
			// Log.d("GPS","Handling "+parentsAndCurrent);
	
			if (vn.stBox == newLocalStBox) // if the view hasn't changed at all
				TAssert.fail("Why are we trying to update a view node when the stBox hasn't changed???");
	
			boolean onStatus;
			
			int [] overlappingTimeRange = null;
			
			AreaPanel ap = vn.ap();;
	
			//note that vn.stBox can be null for new empty databases
			if (vn.status == null || vn.stBox == null ||
					vn.stBox.isPathsChanged(newLocalStBox)) {
				onStatus = ap.overlapsStbXY(newLocalStBox)
						&& (overlappingTimeRange = vn.checkTimeInterval(newLocalStBox)) != null;
			} 
			else // we have a previous calculation to start from
			{
				if (vn.status == ViewNode.VNStatus.SET) {
					if (ap.outsideOfXY(newLocalStBox)) {
						onStatus = false;
					}
					//if the time hasn't decreased, we know the point is still displayed. However,
					//if we are the tail or head point, the overlapping range might expand if 
					// the time range has expanded in the appropriate direction, so we fall through
					// in that case
					else if (!vn.timeDecreasedMeaningfully(newLocalStBox, ap)
							&& vn.overlappingRange[1] != latestOnScreenPoint
							&& vn.overlappingRange[0] != earliestOnScreenPoint)
					{
						onStatus = true;
						overlappingTimeRange = vn.overlappingRange;
					}
					else {
						// check the time interval and check if it overlaps
						onStatus = (overlappingTimeRange = vn.checkTimeInterval(newLocalStBox)) != null;
					}
				}
				// vn.status is EMPTY
				else {
	
					if (ap.outsideOfXY(newLocalStBox))
						onStatus = false;

					// if we would have turned on based on position last time, (but
					// were empty), then the
					// only way we can be on is if the time range increased
					// meaningfully relative to the areapanel
					 else if(vn.stBox != null && !vn.outsideOfXY(vn.stBox, ap) &&
					 !(vn.timeIncreasedMeaningfully(newLocalStBox, ap)))
						 onStatus = false;
					else {
						onStatus = (overlappingTimeRange = vn.checkTimeInterval(newLocalStBox)) != null;
					}
				}
			}
	
			if (onStatus) {
				vn.overlappingRange = overlappingTimeRange;
				
				boolean childrenDirty;
				
				//if we were not set, the point is mute, because there aren't any children
				//but there is no need to check as well
				if(vn.status != VNStatus.SET||
						vn.stBox.isPathsChanged(newLocalStBox))
				{
					childrenDirty = true;
					vn.clearLineCalcs();
					linesRecalcNeeded = true;
				}
				else if(vn.timeChangedMeaningfullyWithRegardsToChildren(newLocalStBox))
				{
					childrenDirty = true;
					vn.clearLineCalcs();
					linesRecalcNeeded = true;
				}
				else if(vn.xyChangedMeaningfullyWithRegardsToChildren(newLocalStBox))
				  childrenDirty = true;
				else childrenDirty = false;
	
				vn.setSetStatus(parentsAndCurrent, newLocalStBox, childrenDirty,
						minDepth);
				
			} else
				vn.setEmptyStatus(parentsAndCurrent, newLocalStBox);
	
			//note, we don't have to worry about clearing the line views for nodes where the chidren are
			//being changed from unknown to set for it, because the timesToLines will be the same
			//and will overwrite the line views that share the same timesToLines
			
			// we did some work
			return CALC_VIEW_NODES_STILL_MORE_NODES_TO_CALC | (linesRecalcNeeded ? CALC_VIEW_NODES_LINES_NEED_RECALC : 0);
		}
		finally {
			viewNodeThreadManager.unregisterWritingThread();
		}
	}

	public Iterator<ViewNode> getViewNodeIter() {
		return new Iterator<ViewNode>() {

			private ArrayList<ViewNode> path = new ArrayList<ViewNode>();
			private ViewNode nextNode = doNext();

			public boolean hasNext()
			{
				return nextNode != null;
			}
			
			public ViewNode next()
			{
				ViewNode localNextNode = nextNode;
				nextNode = doNext();
				return localNextNode;
			}

			public ViewNode doNext() {
				ViewNode previousSibling = null;
				boolean currentViewNodeWasDisplayed;

				if (path.size() == 0) {
					if (//headVn.needsProcessing(stBox) || 
							headVn.status != VNStatus.SET)
						return null;
					// start it off so we look at headVn's children
					currentViewNodeWasDisplayed = false;
					path.add(headVn);
				} else {
					// we displayed the last element of path already for the
					// previous call
					// and are now tasked to find the next one to display
					// (contrast this with moving up the path to the parent
					// nodes and
					// then drilling back downwards)
					currentViewNodeWasDisplayed = true;
				}

				// find the next node that has unknown children, which
				// represents the
				// best information we have so far (which we are continually
				// updating by reading the db)
				while (path.size() > 0) {
					ViewNode vn = path.get(path.size() - 1);

					if (currentViewNodeWasDisplayed) {
						// set the previous sibling, so we will display the next
						// one
						// next time and go downwards once again
						previousSibling = path.remove(path.size() - 1);
						currentViewNodeWasDisplayed = false;
						
						//co because if we only display points that are not dirty, we get random flickering
						// and large points (also, we commented out the needsProcessing below
//					} else if (!vn.needsProcessing(stBox) && vn.status == ViewNode.VNStatus.SET) {
//						// if we haven't explored vn's children yet
//						if (vn.hasUnknownChildren(stBox))
//							return vn;

					} else { 
						if(vn.children == null)
							return vn;
						
						// find the next "set" ViewNode
						int i = 0;

						// if we already returned an area panel from this group
						// of children
						// and we need to go to the next one
						if (previousSibling != null) {
							boolean foundSibling = false;

							// search for the one we previously returned
							for (i = 0; i < AreaPanel.NUM_SUB_PANELS; i++) {
								if (vn.children[i] == previousSibling) {
									i++;
									foundSibling = true;
									break;
								}
							}

							if (!foundSibling)
								TAssert.fail("couldn't find the previous sibling: "
										+ i + ", " + vn + " " + previousSibling);
						}

						// find the next set child and continue
						for (; i < AreaPanel.NUM_SUB_PANELS; i++) {
							if (vn.children[i] != null //&& !vn.children[i].needsProcessing(stBox) 
									&& vn.children[i].status == ViewNode.VNStatus.SET) {
								vn = vn.children[i];
								path.add(vn);

								break;
							}
						}

						// if there are no more children to go down to, we go up
						// and try the next one
						if (i == AreaPanel.NUM_SUB_PANELS)
							previousSibling = path.remove(path.size() - 1);
						else
							previousSibling = null;
					}
				}

				return null;
			}

			@Override
			public void remove() {
				throw new IllegalStateException("not implemented");
				
			}
		};
	}

	public static class Preferences implements AndroidPreferences {


		/**
		 * The amount of points to add each time before commiting
		 */
		public int gpsCacheLoadingStep = 500;

		/**
		 * The number of points within an area panel to search for matches
		 * before giving up when combining points together
		 */
		public int numPointsToSearchForMatching = 50;

		/**
		 * Number of milliseconds before we check the gps location table to see if it 
		 * has points added. If points have been added, area panel cache is then updated.
		 */
		public long areaPanelUpdateGpsLocSpinLockMs = 300*1000;

	}

	public boolean doViewNodesIntersect(int x1, int y1, int x2, int y2) {
		GTG.ccRwtm.registerReadingThread();
		viewNodeThreadManager.registerReadingThread();
		
		try {
			ArrayList<ViewNode> path = new ArrayList<ViewNode>();

			ViewNode previousSibling = null;

			if (headVn.status != VNStatus.SET)
				return false;
			path.add(headVn);
			
			boolean currentViewNodeWasChecked = false;

			// find the next node that has unknown children, which
			// represents the
			// best information we have so far (which we are continually
			// updating by reading the db)
			while (path.size() > 0) {
				ViewNode vn = path.get(path.size() - 1);

				if (currentViewNodeWasChecked) {
					// set the previous sibling, so we will display the next
					// one
					// next time and go downwards once again
					previousSibling = path.remove(path.size() - 1);
					currentViewNodeWasChecked = false;
					
					//co because if we only display points that are not dirty, we get random flickering
					// and large points (also, we commented out the needsProcessing below
//				} else if (!vn.needsProcessing(stBox) && vn.status == ViewNode.VNStatus.SET) {
//					// if we haven't explored vn's children yet
//					if (vn.hasUnknownChildren(stBox))
//						return vn;

				} else { 
					AreaPanel ap = vn.ap();
					if(ap.overlapsArea(x1, y1, x2, y2))
					{
					
						//if there are no children, then a visible node overlaps with area
						if(vn.children == null)
							return true;
					
						// find the next "set" ViewNode
						int i = 0;

						// if we already checked an area panel from this group
						// of children
						// and we need to go to the next one
						if (previousSibling != null) {
							boolean foundSibling = false;
	
							// search for the one we previously returned
							for (i = 0; i < AreaPanel.NUM_SUB_PANELS; i++) {
								if (vn.children[i] == previousSibling) {
									i++;
									foundSibling = true;
									break;
								}
							}
	
							if (!foundSibling)
								TAssert.fail("couldn't find the previous sibling: "
										+ i + ", " + vn + " " + previousSibling);
						}

						// find the next set child and continue
						for (; i < AreaPanel.NUM_SUB_PANELS; i++) {
							if (vn.children[i] != null //&& !vn.children[i].needsProcessing(stBox) 
									&& vn.children[i].status == ViewNode.VNStatus.SET) {
								vn = vn.children[i];
								path.add(vn);
	
								break;
							}
						}

						// if there are no more children to go down to, we go up
						// and try the next one
						if (i == AreaPanel.NUM_SUB_PANELS)
							previousSibling = path.remove(path.size() - 1);
						else
							previousSibling = null;
					}
					else //if the vn doesn't overlap the area, so we go up
					{
						previousSibling = path.remove(path.size() - 1);
					}
				}
			}

			return false;
		}
		finally
		{
			viewNodeThreadManager.unregisterReadingThread();
			GTG.ccRwtm.unregisterReadingThread();
		}
	}

	/**
	 * Shuts down the cache creator but does not join. Note that this may take awhile
	 * to finish.
	 */
	public void shutdown() {
		synchronized (GTG.cacheCreator)
		{
			GTG.cacheCreator.isShutdown = true;
			GTG.cacheCreator.notify();
		}
		
	}


}
