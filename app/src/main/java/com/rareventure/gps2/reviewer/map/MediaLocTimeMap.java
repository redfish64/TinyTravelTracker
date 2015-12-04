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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;

import rtree.AABB;
import rtree.BoundedObject;
import rtree.RTree;
import rtree.RTree.Processor;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;
import android.view.View;

import com.rareventure.android.DbUtil;
import com.rareventure.android.Util;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.android.database.timmy.TimmyDatastoreAccessor;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;
import com.rareventure.gps2.database.cache.MediaLocTime;
import com.rareventure.gps2.database.cachecreator.GpsTrailerCacheCreator;

public class MediaLocTimeMap {

	private static final int MEDIA_SQL_LIMIT = 50;

	public RTree rTree = new RTree(2,12);

	private ArrayList<MediaLocTime> futureMltArray = new ArrayList<MediaLocTime>();

	public AreaPanelSpaceTimeBox lastApStBox;

	private static Preferences prefs = new Preferences();

	public HashSet<ViewMLT> displayedViewMlts = new HashSet<ViewMLT>();

	public int currViewMltWidth;

	private ArrayList<MediaLocTime> tempLocMltArray = new ArrayList<MediaLocTime>();

	private ArrayList<MediaLocTime> mltsToDelete = new ArrayList<MediaLocTime>();

	private void resetAllMedia(ArrayList<MediaLocTime> media) 
	{
		synchronized (this)
		{
			this.rTree = new RTree(2, 12);
		
			futureMltArray.clear();
			tempLocMltArray.clear();
			
			int latestTime = GTG.cacheCreator.maxTimeSec;
			
			for(MediaLocTime mlt : media)
			{
	//			Log.d(GTG.TAG,"insert: "+mlt);
				if(mlt.getTimeSecs() > latestTime + prefs.maxFutureTimeForPlacingTempMlt)
					futureMltArray.add(mlt);
				else 
				{
					if(mlt.isTempLoc())
						tempLocMltArray.add(mlt);
					rTree.insert(mlt);
				}
			}
			
			//sort in reverse chronological order
			Collections.sort(futureMltArray, new Comparator<MediaLocTime>() {

				@Override
				public int compare(MediaLocTime lhs, MediaLocTime rhs) {
					return rhs.getTimeSecs() - lhs.getTimeSecs();
				}
			});
			
			//clear the variables used to determine data that doesn't need to be recalcuated
			//assuming the rtree hasn't changed
			this.lastApStBox = null;
			this.displayedViewMlts.clear();
		}
	}

	public synchronized void remove(MediaLocTime mlt) {
		rTree.remove(mlt);
	}

	public synchronized void insert(MediaLocTime mlt) {
		rTree.insert(mlt);
	}
	
	public synchronized void query(ArrayList<MediaLocTime> mediaResults, AABB box)
	{
		rTree.query((ArrayList)mediaResults, box);
		/* ttt_installer:remove_line */Log.d(GTG.TAG,"query against "+box+" returned "+mediaResults.size()+" rows");
	}
	
	/**
	 * Loads all media from the database and resets the data held by the map
	 * @return
	 */
	public void loadFromDb() {
		resetAllMedia(MediaLocTime.loadAllMediaLocTime());
	}

	/**
	 * Loads media from gallery and updates mediaLocTemps in database and in memory. Note
	 * that this method turns on GTG.alert(GTGEvent.LOADING_MEDIA), but doesn't turn it
	 * off. If the caller is just going to loop and call us again, it can leave this alert
	 * alone, otherwise it should turn it off 
	 * @return true if media is no longer dirty
	 */
	public boolean updateFromGallery(GpsTrailerCacheCreator gtcc, ContentResolver contentResolver)
	{
		//TODO 3 we should be taking this from gps location table, although if there really is no data at all
		// then we are still stuck with saying the media is not dirty because we have no data at all
		if(!GTG.apCache.hasGpsPoints())
			return true;
		
		if(GTG.timmyDb.getProperty("lastImageDateMs") == null)
		{
			try {
				GTG.timmyDb.beginTransaction();
				//note that we use the date taken value rather than the id because id's will be reused
				//if the media with the highest id is deleted
				//note also that the dates are seperate. This is because we limit the number of images/videos
				// we grab at a time. So we might grab videos up to Dec 2012, but images only up to Jan 2012,
				// simply because there are less videos than images
				GTG.timmyDb.setProperty("lastImageDateMs","0");
				GTG.timmyDb.setProperty("lastVideoDateMs","0");
				GTG.timmyDb.saveProperties();
				GTG.timmyDb.setTransactionSuccessful();
				GTG.timmyDb.endTransaction();
			}
			catch(IOException e)
			{
				throw new IllegalStateException(e);
			}
		}
		
		//we need to delete the marked mlts from the database first, because
		//since they are not in the rtree, we would then assume they we're already
		// deleted, reuse them and then when they actually did get deleted, they
		//would write over our handywork
		GTG.mediaLocTimeMap.deleteMarkedMltsFromDb();
		
		// The latest modification time we read the last time we 
		// updated from the gallery 
		long lastImageDateMs = Long.parseLong(GTG.timmyDb.getProperty("lastImageDateMs"));
		long lastVideoDateMs = Long.parseLong(GTG.timmyDb.getProperty("lastVideoDateMs"));
		
		//TxODO 1 HACK!!!!
//		lastMltImageId = 2586;

		long startTimeMs = GTG.cacheCreator.minTimeSec * 1000l;
		
		//we load all the current photo ids from the cache into memory
		//This is so we can delete photo ids from the database which are
		//no longer present in the users gallery
		
		//We load the whole set of ids into memory, because they're can't
		//be more than 10000 or so pictures I would guess and otherwise
		//we'd need to make a tree system that we could update (delete
		// and insert) dynamically which seems to be too much work for
		//such a small set of ids
		// 10,000 MLTs take about 400K of memory
		
		//Furthermore, we are not using areapanels to do this, because
		//we'd need to somehow link the media to time trees, (since
		//more than one image could be associated with an area panel)
		//note that the fact that we will show a "card pile" for images
		//that are too close together doesn't help here, because if the
		//time interval is adjusted, we'd need to be able to show single
		//images again.
		
		//Furthermore we'd then have to delete time trees or join them
		//back up depending how we represent images, and that would
		//also be a lot of work.
		
		ArrayList<MediaLocTime> media = new ArrayList<MediaLocTime>(rTree.count()+futureMltArray.size());
		
		ArrayList<Integer> deletedMediaIds = new ArrayList<Integer>();
		
		TimmyDatastoreAccessor<MediaLocTime> dataAccessor = 
			new TimmyDatastoreAccessor(GTG.mediaLocTimeTimmyTable);
		
		
		Cursor cursor = null;

		boolean mediaUpToDate = true;
		
		
		try {
			GTG.timmyDb.beginTransaction();
			
			int nextRowId = dataAccessor.getNextRowId();
			
			boolean mediaNodesRetreived = false;
			
//			//HACK
//			getAllNodesAndFindDeletedIds(media, deletedMediaIds, dataAccessor.getNextRowId());
//			mediaNodesRetreived = true;
//			for(MediaLocTime mlt : media)
//			{
//				Log.d(GTG.TAG,"MediaLocTime: "+mlt);
//			}
//			Log.d(GTG.TAG,"Deleted ids: "+deletedMediaIds);
			
			//note that we go through all the data, every time. This allows us to find cases
			//where an image or video was deleted
			
			for(int type : new int [] {MediaLocTime.TYPE_IMAGE, MediaLocTime.TYPE_VIDEO} )
			{
				if(type == MediaLocTime.TYPE_IMAGE)
				{
					String[] columns = new String[] {
					                ImageColumns._ID,
					                ImageColumns.DATA, //filename
					                ImageColumns.DATE_TAKEN
					                ,ImageColumns.LONGITUDE
					                ,ImageColumns.LATITUDE
					                ,ImageColumns.ORIENTATION
					                };
					cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					                columns, ImageColumns.DATE_TAKEN+" >= ?", 
					                new String [] { String.valueOf(Math.max(startTimeMs,lastImageDateMs+1) )} , 
					                ImageColumns.DATE_TAKEN+" limit "+MEDIA_SQL_LIMIT);
				}
				else
				{
					String [] columns = new String[] {
			                VideoColumns._ID
			                ,VideoColumns.DATA //filename
			                ,VideoColumns.DATE_TAKEN
			                ,VideoColumns.LONGITUDE
			                ,VideoColumns.LATITUDE
			                };
					cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
			                columns, VideoColumns.DATE_TAKEN+" >= ?", 
			                new String [] { String.valueOf(Math.max(startTimeMs,lastVideoDateMs+1) )} , 
			                ImageColumns.DATE_TAKEN+" limit "+MEDIA_SQL_LIMIT);
				}
				
				if(gtcc.isShutdown)
					break;

				if(!cursor.moveToFirst())
					continue;
				
				GTG.alert(GTGEvent.LOADING_MEDIA);
				
				if(!mediaNodesRetreived)
				{
					getAllNodesAndFindDeletedIds(media, deletedMediaIds, dataAccessor.getNextRowId());
					mediaNodesRetreived = true;
				}
				
				int highestMediaIdForTypeInMlts = Integer.MIN_VALUE;
				
				for(MediaLocTime mlt : media)
				{
					if(mlt.getType() == type)
						highestMediaIdForTypeInMlts = Math.max(mlt.getFk(), highestMediaIdForTypeInMlts);
				}
				
				int mediaQueried = 0;
				
				while (!cursor.isAfterLast()) {
					int mediaId = cursor.getInt(0);
					String data = cursor.getString(1);
					
					mediaQueried++;
					
					//I'm not positive this happens but I suspect that if a file was deleted outside of the
					//gallery it may still report that it exists on certain os's, so I check for it here
					if(!new File(data).exists())
					{
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"File "+data+" returned from gallery but doesn't really exist");
						cursor.moveToNext();
						continue;
					}
					
					//note, unfortunately, there is an android bug, and this is just set to
					// the last modified date (by android), so we try to get the exif
					//information if available
					long dateTakenMs = cursor.getLong(2);
					int orientation = 0;
					
					double lon = cursor.isNull(3) ? 0 : cursor.getDouble(3);
					double lat = cursor.isNull(4) ? 0 : cursor.getDouble(4);
					
					if(!Util.isLonLatSane(lon,lat))
					{
						lon = lat = 0;
					}
					
					//note that we use the androids dateTaken value since we are only
					//using lastMediaDateMs to get new images
					if(type == MediaLocTime.TYPE_IMAGE)
						lastImageDateMs = dateTakenMs;
					else if(type == MediaLocTime.TYPE_VIDEO)
						lastVideoDateMs = dateTakenMs;
						
					//if its an image we can peer into the exif data which seems to be the most accurate as
					//to when the picture was taken
					if(type == MediaLocTime.TYPE_IMAGE)
					{
						orientation = cursor.getInt(5);
						
						ExifInterface ei;
						
						try {
							ei = new ExifInterface(data);

							long exifDate = Util.getExifDateInUTC(ei);
							
							//the exif date is in local time, so we need to convert it. We do this by looking
							// at the timezone set. This may be off by a few hours, so if they are hiking around
							// timezone lines, they'll get in trouble but its the best I can do
							TimeZoneTimeRow tz = GTG.tztSet.getTimeZoneCovering((int)(exifDate/1000l));
							
							if(tz != null && tz.getTimeZone() != null)
							{
								//we subtract the timezone because exif date is in the timezone time
								int offset = tz.getTimeZone().getOffset(exifDate - tz.getTimeZone().getRawOffset());
								exifDate -= offset;
								/* ttt_installer:remove_line */Log.d(GTG.TAG, "adjusting exifDate by "+offset+" to "+exifDate);
							}
							
							/* ttt_installer:remove_line */Log.d(GTG.TAG, "exifDate is "+exifDate+" android date is "+dateTakenMs);
							
							//TODO 3: get exif lon and lat to make sure that we tried everything
							
							if(exifDate != 0)
							{
								dateTakenMs = exifDate;						
							
								//skip images that have an exif date that is less than the
								//earliest date of gps points
								if(exifDate < startTimeMs)
								{
									//get the next item from the db
									cursor.moveToNext();

									continue;
								}
							}
							
						} catch (IOException e1) {
							Log.d(GTG.TAG,"No exif data for image "+data);
						}
						
					}
					
					
					/* ttt_installer:remove_line */Log.d(GTG.TAG, "type "+type+", mediaId "+mediaId+" timeMs "+dateTakenMs+" data "+data);
					
					//since we're changing existing mlts we have to synchronize
					synchronized (this)
					{
						//if the media item might have been updated
						if(mediaId <= highestMediaIdForTypeInMlts)
						{
							int currMltItemIndex = findMlt(mediaId, type, media);
							if(currMltItemIndex != -1)
							{
								/* ttt_installer:remove_line */Log.d(GTG.TAG,"Updating media "+mediaId+" type "+type);
								//if the media item was updated, delete it
								//and we'll add it back
								// note. not sure when this would ever happen,
								// but the alternative, to ignore it, would cause
								// the same fk to be represented by two mlts which
								// would be bad
								MediaLocTime mlt = media.get(currMltItemIndex);
								mlt.markDeleted();
								deletedMediaIds.add(mlt.id);
								dataAccessor.updateRow(mlt);
								media.remove(currMltItemIndex);
							}
						}
						
						//add the item to the cache
						MediaLocTime mlt = createMediaLocTime(mediaId, dateTakenMs, type, orientation,
								lon, lat);
						
						if(deletedMediaIds.isEmpty())
						{
							mlt.id = nextRowId++;
							dataAccessor.insertRow(mlt);
						}
						
						else
						{
							mlt.id = deletedMediaIds.remove(deletedMediaIds.size()-1);
							dataAccessor.updateRow(mlt);
						}

						//throw it on the end of our current set of mlts
						media.add(mlt);
						
						//we want to get the gallery to create all the small bitmaps so that we won't
						//have to worry about it slowing down the strip media gallery (on the main map screen)
						
						//TODO 2.5 is this threadsafe? If no?
						Bitmap x = mlt.getThumbnailBitmap(contentResolver, true);
						if(x != null)
							x.recycle();
					}
					
					//get the next item from the db
					cursor.moveToNext();

				}
				
				//if we reached the limit than there may be more media items
				if(mediaQueried == MEDIA_SQL_LIMIT)
					mediaUpToDate = false;

				/* ttt_installer:remove_line */Log.d(GTG.TAG,"Media queried for type "+type+" is "+mediaQueried);
				
				cursor.close();
			} //for each type of media (image,video)
			
			GTG.timmyDb.setProperty("lastImageDateMs", String.valueOf(lastImageDateMs));
			GTG.timmyDb.setProperty("lastVideoDateMs", String.valueOf(lastVideoDateMs));
			GTG.timmyDb.saveProperties();

			GTG.timmyDb.setTransactionSuccessful();
			
			//if we processed any mlts at all
			if(mediaNodesRetreived)
			{
				resetAllMedia(media);

				//TODO 3 hack to redraw when we show media 
				final OsmMapGpsTrailerReviewerMapActivity localGtum = GTG.cacheCreator.gtum;

				//notify the listeners (sort of) that the data has changed
				//note, localizing incase drawer is changed between check for null and call
				if(localGtum != null)
				{
					localGtum.notifyMediaChanged();
				}
			}

		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		finally {
			DbUtil.closeCursors(cursor);
			try {
				GTG.timmyDb.endTransaction();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		//TODO 3 notify drawer when loaded.. make sure to update GTG.mediaLocTimeMap appropriately

		return mediaUpToDate;
	}

	private int findMlt(int mediaId, int type, ArrayList<MediaLocTime> media) {
		for(int i = media.size()-1; i >= 0; i--)
		{
			if(media.get(i).getType() == type && media.get(i).getFk() == mediaId)
				return i;
		}
		return -1;
	}

	/**
	 * Gets all the nodes and figures the locations of the deleted mlts based on the 
	 * absense of a node
	 */
	private void getAllNodesAndFindDeletedIds(final ArrayList<MediaLocTime> result, ArrayList<Integer> deletedIds,
												int nextRow) {
		result.ensureCapacity(rTree.count()+futureMltArray.size());
		
		//put all non deleted mlts into result
		rTree.query(new Processor() {
			
			@Override
			public boolean process(BoundedObject bo) {
				MediaLocTime mlt = (MediaLocTime)bo;
				
				result.add(mlt);
				return true;
			}
		}, null);
		
		result.addAll(futureMltArray);
		
		//sort by id
		Collections.sort(result, new Comparator<MediaLocTime>() {

			@Override
			public int compare(MediaLocTime lhs, MediaLocTime rhs) {
				return lhs.id - rhs.id;
			}
		}
		);
		
		int mltIndex = 0;
		
		//find deleted mlts by going through the arrays of the mlts we have against
		//a counter
		for(int i = 0; i < nextRow; i++)
		{
			if(mltIndex >= result.size())
			{
				deletedIds.add(i);
			}
			else {
				int mltId = result.get(mltIndex).id;
				if(mltId > i)
				{
					deletedIds.add(i);
				}
				else if(mltId == i) { mltIndex++; }
				else 
					throw new IllegalStateException("why two mlts have same id? "+result.get(mltIndex)+result.get(mltIndex-1));
			}
		}
		
	}

	/**
	 * 
	 * @param mediaId
	 * @param timeMs
	 * @param type
	 * @param orientation
	 * @param lon if known, otherwise 0
	 * @param lat if known, otherwise 0
	 * @return
	 */
	private MediaLocTime createMediaLocTime(int mediaId, long timeMs, int type, int orientation, 
			double lon, double lat) {
		int timeSec = (int) (timeMs / 1000);
		
		
		MediaLocTime row = new MediaLocTime();
		
		row.setData(0, 0, mediaId, timeSec, type, false, orientation);
		
		//if we don't already know the lon and lat from the picture information
		if(lon == 0 && lat == 0)
			updateMediaLocTimeLoc(row);
		else
		{
			int x = AreaPanel.convertLonmToX((int) (lon*1000000));
			int y = AreaPanel.convertLatmToY((int) (lat*1000000));

			row.setX(x);
			row.setY(y);
			row.setIsTempLoc(false);
		}
		
		return row;
	}
	
	private void updateMediaLocTimeLoc(MediaLocTime row)
	{
		int startX, startY, endX, endY;
		float perc;
		
		//true if the location might need to change later (ie the person took a picture
		// recently and the next gps point wasn't read yet)
		boolean isTempLoc = false;
		
		int timeSec = row.getTimeSecs();
		
		//TODO 4  this would find the location between two ap's, but it stopped working
		//when ap's timetree's were changed to overlap, maybe fix this?
		//PERF we could combine these two calls and return an array or something
		AreaPanel prevAp = AreaPanel.findAreaPanelForTime(timeSec, true);
		AreaPanel nextAp = AreaPanel.findAreaPanelForTime(timeSec, false);
		
		//if the picture was taken before 
		if(prevAp == null)
		{
			//co: we want the pictures to have their actual time although we don't know
			//where they are exactly. So if the user goes back to that time, they can
			//still see the picture (although in the wrong location)
			//TODO 3 do we really like it like this?
//			timeSec = nextAp.getStartTimeSec();
			prevAp = nextAp;
		}
		
		if(nextAp == null)
		{
			nextAp = prevAp;
			isTempLoc = true;
		}
		
		if(prevAp.getDepth() != 0)
			throw new IllegalStateException("prevAp depth... it's wrong! "+prevAp);
		if(nextAp.getDepth() != 0)
			throw new IllegalStateException("nextAp depth... it's wrong! "+nextAp);
		
		
		//there is a slight chance that a picture was taken at the exact second that 
		// a gps location was taken
		if(prevAp == nextAp) perc = 0;
		else
		{
			//PERF, we could report this from findAreaPanelForTime, because it already knows
			int prevTime = prevAp.getTimeTree().getNearestTimePoint(timeSec, true);
			int nextTime = nextAp.getTimeTree().getNearestTimePoint(timeSec, false);
			
			//note, in general these ap's will always be 1 second long because they are at the
			//minimum depth
			perc = ((float)timeSec - prevTime)/(nextTime - prevTime); 
		}

		//TODO 2.1 handle overlapping of the world, otherwise if line happens to go from new zealand
		// to california, the photo will show up in the middle
		startX = prevAp.getCenterX();
		endX = nextAp.getCenterX();
	
		startY = prevAp.getCenterY();
		endY = nextAp.getCenterY();
		
		synchronized (this)
		{
			int x;
			
			if(endX - startX > AreaPanel.MAX_AP_UNITS>>1)
			{
				x = (int) (startX - (AreaPanel.MAX_AP_UNITS - endX + startX) * perc);
				if(x < 0) 
					x += AreaPanel.MAX_AP_UNITS;
			}
			else if(startX - endX > AreaPanel.MAX_AP_UNITS >>1)
			{
				x = (int) (startX + (AreaPanel.MAX_AP_UNITS - startX + endX) * perc);
				if(x > AreaPanel.MAX_AP_UNITS) 
					x -= AreaPanel.MAX_AP_UNITS;
			}
			else
				x = (int) (startX + (endX-startX) * perc);
			
			
			int y = (int) (startY + (endY-startY) * perc);
			
			row.setX(x);
			row.setY(y);
			row.setIsTempLoc(isTempLoc);
		}
	}
	
	

	
	/**
	 * Calculates the current viewable nodes (each node may contain multiple MLT's)
	 * based on the provided stbox 
	 */
	public synchronized void calcViewableMediaNodes(Context context,
			AreaPanelSpaceTimeBox localApStbox) {
		int newViewMltWidth = (int)(localApStbox.getWidth() * prefs.multiMLTAreaWidthToApBoxWidth);
		
		if(newViewMltWidth != currViewMltWidth)
			lastApStBox = null;
		
		
		currViewMltWidth = newViewMltWidth;
		
		//create an ap box which is bigger than the requested one so that we process nodes
		//correctly that oerlap the edge
		AreaPanelSpaceTimeBox adjustedApStBox = new AreaPanelSpaceTimeBox(localApStbox);
		
		//note, since in certain circumstances, a viewmlt can grow in size (when time is increased,
		// more nodes can become part of the viewmlt and shift the center of it), we 
		// use the full width of the areas, rather than half of it
		adjustedApStBox.addBorder(currViewMltWidth);
		
		ArrayList<MediaLocTime> mltsToClearOut = new ArrayList<MediaLocTime>();
		
		//now add points for the new areas created
		Processor addViewNodeProcessor = createAddViewNodeProcessor(context, adjustedApStBox,
				mltsToClearOut);
		
		for(MediaLocTime mlt : mltsToClearOut)
		{
			notifyMltNotClean(mlt);
		}
		
		//if we are completely outside of the last box
		if(lastApStBox == null || adjustedApStBox.minZ >= lastApStBox.maxZ 
				|| adjustedApStBox.maxZ <= lastApStBox.minZ 
				|| adjustedApStBox.minX >= lastApStBox.maxX 
				|| adjustedApStBox.maxX <= lastApStBox.minX 
				|| adjustedApStBox.minY >= lastApStBox.maxY 
				|| adjustedApStBox.maxY <= lastApStBox.minY
				)
		{
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"querying box");
			rTree.query(addViewNodeProcessor, adjustedApStBox);
		}
		else {
			//there are 6 sides that may have new points, top, bottom, left, right, future, past
			AABB temp = adjustedApStBox.clone();
			
			//handle future
			if(adjustedApStBox.maxZ > lastApStBox.maxZ)
			{
				temp.minZ = lastApStBox.maxZ;
				rTree.query(addViewNodeProcessor, temp);
				temp.minZ = adjustedApStBox.minZ;
			}
			//handle past
			if(adjustedApStBox.minZ < lastApStBox.minZ)
			{
				temp.maxZ = lastApStBox.minZ;
				rTree.query(addViewNodeProcessor, temp);
				temp.maxZ = adjustedApStBox.maxZ;
			}
		
			//handle left
			if(adjustedApStBox.minX < lastApStBox.minX)
			{
				temp.maxX = lastApStBox.minX;
				rTree.query(addViewNodeProcessor, temp);
				temp.maxX = adjustedApStBox.maxX;
			}
			//handle right
			if(adjustedApStBox.maxX > lastApStBox.maxX)
			{
				temp.minX = lastApStBox.maxX;
				rTree.query(addViewNodeProcessor, temp);
				temp.minX = adjustedApStBox.minX;
			}

			//handle bottom (in y direction)
			if(adjustedApStBox.minY < lastApStBox.minY )
			{
				temp.maxY = lastApStBox.minY;
				rTree.query(addViewNodeProcessor, temp);
				temp.maxY = adjustedApStBox.maxY;
			}
			//handle top
			if(adjustedApStBox.maxY > lastApStBox.maxY)
			{
				temp.minY = lastApStBox.maxY;
				rTree.query(addViewNodeProcessor, temp);
				temp.minY = adjustedApStBox.minY;
			}
		}//if the last ap stbox and the current one overlaps
		
		
		//remove viewmlts that are before or after the current time or out of range in the x,y
		//dimensions
		for (Iterator<ViewMLT> i = GTG.mediaLocTimeMap.displayedViewMlts.iterator(); i.hasNext();) {
			//it is our responsibility to cull out view mlts that should be cleared out
			ViewMLT viewMlt = i.next();
			
			//now remove viewmlts that aren't displayable anymore
			if(viewMlt.totalNodes == 0 || viewMlt.width != GTG.mediaLocTimeMap.currViewMltWidth
					|| viewMlt.minZ < adjustedApStBox.minZ || viewMlt.maxZ > adjustedApStBox.maxZ)
			{
				i.remove();
				continue;
			}
			
			int centerX = viewMlt.getCenterX(); 
			int centerY = viewMlt.getCenterY();
			
			//if we're out of bounds, take it out of the displayed list
			if(centerX < adjustedApStBox.minX || centerX > adjustedApStBox.maxX ||
					centerY < adjustedApStBox.minY || centerY > adjustedApStBox.maxY)
			{
				i.remove();
			}
			
		}
		
		lastApStBox = adjustedApStBox;
	}
	
	private Processor createAddViewNodeProcessor(final Context context, 
			final AreaPanelSpaceTimeBox adjustedApStBox, final ArrayList<MediaLocTime> mltsToClearOut) {
		final AABB tempAABB = adjustedApStBox.clone();
		
		return new RTree.Processor()
		{
			public boolean process(BoundedObject bo)
			{
				final MediaLocTime mlt = (MediaLocTime)bo;
				
				/* ttt_installer:remove_line */Log.d(GTG.TAG, "processing mlt "+mlt);
				
				//if the mlt is already associated with a view node
				if(mlt.viewMlt != null)
				{
					//if the view mlt no longer applies to the current width
					if(!isViewMltUsable(mlt.viewMlt, currViewMltWidth, adjustedApStBox))
					{
						//if the view mlt can't remove the mlt, because its using
						//the picture of the mlt
						if(!mlt.viewMlt.removeMlt(mlt))
						{
							//mark it invalid to destroy it as far as its other mlt's
							//are concerned.. its already at a different width
							//so it won't be in the final displayed view mlts anyway
							mlt.viewMlt.width = -1;
						}
					}
					//otherwise if it's already associated to a valid view node, skip it
					else 
					{
						displayedViewMlts.add(mlt.viewMlt);
						
						return true;
					}
				}
				
				//find other mlt's in range so that we can find a view node to stick this
				//mlt to
				tempAABB.minX = mlt.getX() - currViewMltWidth / 2;
				tempAABB.minY = mlt.getY() - currViewMltWidth / 2;
				tempAABB.maxX = tempAABB.minX + currViewMltWidth;
				tempAABB.maxY = tempAABB.minY + currViewMltWidth;
				
				//if we went through them all without finding a viewnode to attach to
				if(rTree.query(new RTree.Processor() {

					@Override
					public boolean process(BoundedObject bo) {
						MediaLocTime otherMlt = (MediaLocTime)bo;

						//if there already is an mlt that has a valid
						// mlt, then add it
						if(otherMlt.viewMlt != null && otherMlt.viewMlt.width == currViewMltWidth)
						{
							otherMlt.viewMlt.addMlt(mlt);
							displayedViewMlts.add(otherMlt.viewMlt);
							return false;
						}
						
						return true;
					}}, tempAABB))
				{
					//skip mlts that are to be displayed and are unclean
					//note that we don't affect the rtree here because we are in
					//the middle of a query and it would compromise the results
					if(!mlt.isClean(context))
					{
						mltsToClearOut.add(mlt);
						return true;
					}
					
					//create a new viewmlt
					mlt.viewMlt = new ViewMLT(currViewMltWidth, mlt);
					displayedViewMlts.add(mlt.viewMlt);
				}
				
				return true; 
			}


		};
	}

	private boolean isViewMltUsable(ViewMLT viewMlt,
			int currViewMltWidth, AreaPanelSpaceTimeBox adjustedApStBox) {
		return viewMlt.width == currViewMltWidth &&
			viewMlt.minZ >= adjustedApStBox.minZ && viewMlt.maxZ <= adjustedApStBox.maxZ;
	}
	
	/**
	 * Should be called by gtgcachecreator thread to periodically delete mlts that 
	 * are marked for deletion
	 */
	public void deleteMarkedMltsFromDb()
	{
		synchronized (this) {
			if(mltsToDelete.isEmpty())
			{
				return;
			}
		}
		
		try {
			GTG.timmyDb.beginTransaction();
			
			TimmyDatastoreAccessor<MediaLocTime> da = 
				new TimmyDatastoreAccessor<MediaLocTime>(GTG.mediaLocTimeTimmyTable);
		
			synchronized (this)
			{
				for(MediaLocTime mlt : mltsToDelete)
				{
					mlt.markDeleted();
					da.updateRow(mlt);
				}
				mltsToDelete.clear();
			}
			
			GTG.timmyDb.setTransactionSuccessful();
			
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		finally
		{
			try {
				GTG.timmyDb.endTransaction();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}
			
	/**
	 * Updates temp location for all mlt's with a temp loc.
	 * A temp loc indicates that the final position of the mlt hasn't been calculated
	 * yet. This is used so that if a user takes a picture, it will immediately appear
	 * on their trail, rather than not appear until the next gps point.
	 */
	public void updateTempLocs()
	{
		synchronized (this)
		{
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"updateTempLocs: futureMltArray: "+futureMltArray.size()
			/* ttt_installer:remove_line */		+" tempLocMltArray: "+tempLocMltArray.size());
			if(GTG.apCache.getTopRow() == null)
				return;
			
			int latestApTime = GTG.cacheCreator.maxTimeSec; 
			
			//first move the items from future mlt array to temp array for all items that are
			//no longer in the future
			for(int i = futureMltArray.size()-1; i >= 0; i--)
			{
				MediaLocTime mlt = futureMltArray.get(i);
			
				//items are in chronological order in reverse, so break as soon as we're still in 
				//the future
				if(mlt.getTimeSecs() > latestApTime + prefs.maxFutureTimeForPlacingTempMlt)
					break;
				
				tempLocMltArray.add(mlt);
				futureMltArray.remove(i);
			}
				
			
			if(!tempLocMltArray.isEmpty())
			{
				try {
					GTG.timmyDb.beginTransaction();
					
					TimmyDatastoreAccessor<MediaLocTime> dataAccessor = 
						new TimmyDatastoreAccessor<MediaLocTime>(GTG.mediaLocTimeTimmyTable);
			
					for(int i = tempLocMltArray.size()-1; i >= 0; i--)
					{
						MediaLocTime mlt = tempLocMltArray.get(i);
						
						GTG.mediaLocTimeMap.remove(mlt);
					
						updateMediaLocTimeLoc(mlt);
						GTG.mediaLocTimeMap.insert(mlt);
						
						if(!mlt.isTempLoc())
							tempLocMltArray.remove(i);
						
						dataAccessor.updateRow(mlt);
					}
					
					GTG.timmyDb.setTransactionSuccessful();
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
				finally
				{
					try {
						GTG.timmyDb.endTransaction();
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				}
	
				lastApStBox = null;
				
				/* ttt_installer:remove_line */Log.d(GTG.TAG,"updateTempLocs end: futureMltArray: "+futureMltArray.size()
						/* ttt_installer:remove_line */		+" tempLocMltArray: "+tempLocMltArray.size());
			}
		}
	}
	
	public static class Preferences
	{
		/**
		 * The maximum amount of time an mlt can be in the future to
		 * place it 
		 */
		public int maxFutureTimeForPlacingTempMlt = 60 * 60 * 24;


		/**
		 * Percentage of screen width to width of area where multiple photos and videos
		 * are combined into one node
		 */
		public float multiMLTAreaWidthToApBoxWidth = .15f;
		
	}

	public synchronized void notifyMltNotClean(MediaLocTime mlt) {
		if(mlt.viewMlt != null)
		{
			if(!mlt.viewMlt.removeMlt(mlt))
			{
				mlt.viewMlt.width = -1;
				
				//this view mlt is destroyed so we have to reload the whole
				//display
				lastApStBox = null;
			}
		}
		
		rTree.remove(mlt);
		mlt.markDeleted();
		mltsToDelete.add(mlt);
	}

	public void notifyResume() {
		/* ttt_installer:remove_line */Log.d(GTG.TAG,"medialoctimemap notified resume");
		lastApStBox = null;
	}

}
