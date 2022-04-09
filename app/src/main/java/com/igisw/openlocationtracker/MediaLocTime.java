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
package com.igisw.openlocationtracker;

import java.io.File;
import java.util.ArrayList;

import rtree.AABB;
import rtree.BoundedObject;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import com.rareventure.android.DbUtil;
import com.igisw.openlocationtracker.Util;
import com.igisw.openlocationtracker.Cache;
import com.igisw.openlocationtracker.DbDatastoreAccessor;
import com.igisw.openlocationtracker.TableInfo;
import com.igisw.openlocationtracker.TimmyDatastoreAccessor;
import com.igisw.openlocationtracker.EncryptedRow;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.MediaThumbnailCache;
import com.igisw.openlocationtracker.ViewMLT;

public class MediaLocTime extends EncryptedRow implements BoundedObject {
	//x and y are absolute coordinates of the earth in ap panel format
	public static final Column X = new Column("X", Integer.class);
	public static final Column Y = new Column("Y", Integer.class);

	//foreign key into android media table. This depends on what type of media file it is
	public static final Column FK = new Column("FK",Integer.class);
	
	//type of media... image, video, etc. and the temp loc bit indicates
	//whether the x y position is temporary and might be changed if we get
	//more gps positions
	public static final Column FLAGS = new Column("FLAGS", Integer.class);
	
	public static final Column TIME_SECS = new Column("TIME_SECS",Integer.class); 
	
	public static final int TYPE_IMAGE = 1;
	public static final int TYPE_VIDEO = 2;

	public static final int TEMP_LOC_BIT = 1<<16;

	private static final int ORIENTATION_BIT_START_INDEX = 8;
	
	private static final int TYPE_BIT_START_INDEX = 0;

	//orientation is a 2 bit integer
	public static final int ORIENTATION_BITS = 3<<ORIENTATION_BIT_START_INDEX;
	
	private static final int TYPE_BITS = 255;

	public static final Column[] COLUMNS = new Column[] { Y, X, FK, FLAGS, TIME_SECS};
	
	public static final Preferences prefs = new Preferences();
	
	public static final String TABLE_NAME = "media_loc_time";
	
	public static final String INSERT_STATEMENT = DbDatastoreAccessor.createInsertStatement(TABLE_NAME);
	public static final String UPDATE_STATEMENT = DbDatastoreAccessor.createUpdateStatement(TABLE_NAME);
	public static final String DELETE_STATEMENT = DbDatastoreAccessor.createDeleteStatement(TABLE_NAME);
	
	public static final TableInfo TABLE_INFO = new TableInfo(TABLE_NAME, COLUMNS, INSERT_STATEMENT, UPDATE_STATEMENT,
			DELETE_STATEMENT);

	/**
	 * size of data
	 */
	public static final int DATA_LENGTH = 
		EncryptedRow.figurePosAndSizeForColumns(COLUMNS);
	
	//transient variables
	//try to keep these at a minimum because all medialoctimes are loaded into memory
	
	//used to position it in RTree
	private AABB aabb;
	
	//the current viewmlt associated with the mlt
	public ViewMLT viewMlt;
	
	/**
	 * This indicates the last time the mlt was checked to make
	 * sure the media still exists. The value is referenced against
	 * GTG.reviewerMapResumeId; 
	 */
	public int cleanAsOfReviewerMapResumeId = Integer.MIN_VALUE;
	
	public MediaLocTime() {
		super();

	}
	
	public int getDataLength()
	{
		return DATA_LENGTH;
	}
	
	
	
	public int getX()
	{
		return getInt(X);
	}
	
	public int getY()
	{
		return getInt(Y);
	}
	
	public int getFk()
	{
		return getInt(FK);
	}
	
	public int getTimeSecs()
	{
		return getInt(TIME_SECS);
	}

	public int getType()
	{
		return getInt(FLAGS)&(TYPE_BITS);
	}
	
	public boolean isTempLoc()
	{
		return (getInt(FLAGS)&TEMP_LOC_BIT) != 0;
	}

	public void setData(int x, int y, int fk, int timeSecs, int type, boolean isTempLoc,
			int orientation) {
		data2 = new byte[DATA_LENGTH];
		
		setInt(X.pos,x);
		setInt(Y.pos,y);
		setInt(FK.pos,fk);
		setInt(TIME_SECS.pos,timeSecs);
		setInt(FLAGS.pos,type | (isTempLoc ? TEMP_LOC_BIT : 0)
				| convertOrientationToBits(orientation));
	}
	

	private int convertOrientationToBits(int orientation) {
		return (((orientation%360)/90) >> ORIENTATION_BIT_START_INDEX);
	}

	private int convertOrientationBitsToValue(int flags) {
		return (flags & ORIENTATION_BITS) << ORIENTATION_BIT_START_INDEX;
	}
	
	public int getOrientation()
	{
		return convertOrientationBitsToValue(getInt(FLAGS));
	}

	public String toStringFieldsOnly()
	{
		return 
		String.format("MediaLocTime(id=%d,x=%d,y=%d,fk=%d," +
				"timeSecs=%d,tempLoc=%d,viewMlt=%s,isVideo=%s)",
				this.id, getX(), getY(), getFk(),
				getTimeSecs(), isTempLoc() ? 1 : 0, String.valueOf(viewMlt),
						Boolean.toString(isVideo())
					);
	}
	
	public String toString()
	{
		return toStringFieldsOnly();
	}

	

	public static class Preferences
	{
	}


	@Override
	public Cache getCache() {
		return null;
	}

	public static ArrayList<MediaLocTime> loadAllMediaLocTime() {
		TimmyDatastoreAccessor<MediaLocTime> da = 
			new TimmyDatastoreAccessor<MediaLocTime>(GTG.mediaLocTimeTimmyTable);

		int maxSize = da.getNextRowId();
		
		ArrayList<MediaLocTime> mltArray = new ArrayList<MediaLocTime>(maxSize);
		
		for(int i = 0; i < maxSize; i++)
		{
			MediaLocTime row = new MediaLocTime();
			try {
				da.getRow(row, i);
			}
			catch(Exception e)
			{
				Log.e(GTG.TAG,"Corruption in media loc database? "+i);
				//note that we don't need to do anything here, because
				//when we insert new pictures, this row will assumed to be
				//an empty deleted one and will be written over.
				
			}

			if(!row.isDeleted())
				mltArray.add(row);
		}
		
		return mltArray;
	}

	@Override
	public AABB getBounds() {
		if(aabb == null)
		{
			aabb = new AABB();
			aabb.setMinCorner(getX(), getY(), getTimeSecs());
			aabb.setMaxCorner(getX()+1, getY()+1, getTimeSecs()+1);
		}
		
		return aabb;
	}

	public void setX(int x) {
		setInt(X.pos, x);
		aabb = null;
	}

	public void setY(int y) {
		setInt(Y.pos, y);
		aabb = null;
	}

	public void setIsTempLoc(boolean tempLoc) {
		setInt(FLAGS.pos, (getInt(FLAGS) & (~TEMP_LOC_BIT)) | (tempLoc ? TEMP_LOC_BIT : 0));
	}

	public void setFk(int fk) {
		setInt(FK.pos, fk);
	}

	public void setTimeSec(int timeSec) {
		setInt(TIME_SECS.pos, timeSec);
		aabb = null;
	}
	
	public void setType(int type)
	{
		setInt(FLAGS.pos, getInt(FLAGS) & (~TYPE_BITS)  | ( type << TYPE_BIT_START_INDEX) );
	}
//	public static long getImageTime(String file) {
//		
//	}

	/**
	 * Id used to store bitmaps in cache (incorporates video and image bit to keep
	 * them separate)
	 * 
	 */
	public int getCacheId() {
		if (getType() == MediaLocTime.TYPE_IMAGE) {
			return getFk()
					|MediaThumbnailCache.IMAGE_BIT;
		}
		if (getType() == MediaLocTime.TYPE_VIDEO) {
			return getFk()
			|MediaThumbnailCache.VIDEO_BIT;
		}
		
		throw new IllegalStateException();
	}

	public void setOrientation(int orientation) {
		setInt(FLAGS.pos, getInt(FLAGS) & (~ORIENTATION_BITS)  | convertOrientationToBits(orientation));
	}

	public Bitmap getActualBitmap(Context context) {
		return Util.getBitmap(context, getFk(), getType() == TYPE_IMAGE);
	}
	
	public Bitmap getThumbnailBitmap(ContentResolver cr, boolean isMicroKind) {
		if(isDeleted())
			return null;
		if(getType() == TYPE_IMAGE)
	        return MediaStore.Images.Thumbnails.getThumbnail(
					cr,
					getFk(),
					isMicroKind ? MediaStore.Images.Thumbnails.MICRO_KIND :
						MediaStore.Images.Thumbnails.MINI_KIND, null);
		else
	        return MediaStore.Video.Thumbnails.getThumbnail(
					cr,
					getFk(),
					isMicroKind ? MediaStore.Images.Thumbnails.MICRO_KIND :
						MediaStore.Images.Thumbnails.MINI_KIND, null);
	}

	public String getFilename(ContentResolver cr)
	{
		Cursor cursor = null;
		
		try {
			if(getType() == TYPE_IMAGE)
			{
				String[] columns = new String[] {
						ImageColumns.DATA, // filename
				};
				cursor = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						columns, ImageColumns._ID + " = ?",
						new String[] { String.valueOf(getFk()) }, null);
			}
			else 
			{
				String[] columns = new String[] {
						VideoColumns.DATA, // filename
				};
				cursor = cr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
						columns, VideoColumns._ID + " = ?",
						new String[] { String.valueOf(getFk()) }, null);
			}
			
			if(!cursor.moveToFirst())
				return null;
			
			return cursor.getString(0);
		}
		finally {
			DbUtil.closeCursors(cursor);
		}
	}

	public void markDeleted() {
		setInt(FK.pos, -1);
	}
	
	public boolean isDeleted()
	{
		return getFk() == -1;
	}
	
	/**
	 * 
	 * @return true if the media associated with the mlt is actually there
	 */
	public boolean isClean(Context context)
	{
		if(cleanAsOfReviewerMapResumeId == GTG.reviewerMapResumeId)
			return true;
		
		if(Util.mediaExists(context, getFk(), getType() == TYPE_IMAGE))
		{
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"expensive isClean for "+id);
			cleanAsOfReviewerMapResumeId = GTG.reviewerMapResumeId;
			return true;
		}
		
		return false;
	}

	public boolean isVideo() {
		return getType()==TYPE_VIDEO;
	}

	public Bitmap getLargeBitmap(int minSideLength, int maxNumberOfPixels,
            ContentResolver cr) {
		String filePath = Util.getDataFilepathForMedia(cr, getFk(), !isVideo());
		
		if(filePath == null)
			return null;
		
		return com.igisw.openlocationtracker.ImageUtil.makeBitmap(minSideLength, maxNumberOfPixels,
                Uri.fromFile(new File(filePath)), cr, true);
	}

	public Uri getUri(Context context) {
		return Uri.fromFile(new File(Util.getDataFilepathForMedia(context.getContentResolver(), 
				getFk(), !isVideo())));
	}

	public String getMimeType(ContentResolver cr) {
		return Util.getMimeTypeForMedia(cr,
				getFk(), !isVideo());
	}

}
