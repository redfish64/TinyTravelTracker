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
package com.rareventure.gps2.reviewer.map.osm;

import java.io.File;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.rareventure.android.Crypt;
import com.rareventure.android.SuperThread;
import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;
import com.rareventure.gps2.reviewer.map.OsmMapView;


public class FileCache {

	private int nextZoomLevel = -1;
	private int nextTileX;
	private int nextTileY;
	
	private FileCacheTask fileCacheTask;
	private MemoryCache memCache;
	private Context context;
	
	private static final Preferences prefs = new Preferences();
	
	private static final int FILENAME_DATA_SIZE = Long.SIZE >> 8;
	private static final int ENCRYPT_FILENAME_SIZE = GTG.crypt.crypt.getNumOutputBytesForEncryption(FILENAME_DATA_SIZE);

	//TODO 4: maybe use interfaces for these arguments to be more javaie
	public FileCache(Context context, MemoryCache memCache, SuperThread superThread)
	{
		superThread.addTask(fileCacheTask = new FileCacheTask());
		
		this.context = context;
		this.memCache = memCache;
	}

	public synchronized void requestTile(int zoomLevel, int tileX, int tileY) {
		this.nextZoomLevel = zoomLevel;
		this.nextTileX = tileX;
		this.nextTileY = tileY;
				
		fileCacheTask.stNotify(this);
	}
	
	
	private byte [] encryptFilenameOutBytes = new byte[ENCRYPT_FILENAME_SIZE];
	private byte [] encryptFilenameInBytes = new byte[FILENAME_DATA_SIZE];

	File getFileName(int zoomLevel, int tileX, int tileY) {
		if(prefs.encryptTiles )
		{
			Util.longToByteArray2(MemoryCache.getKey(zoomLevel, tileX, tileY), encryptFilenameInBytes, 0);
			
			GTG.crypt.crypt.encryptData(encryptFilenameOutBytes, 0, encryptFilenameInBytes, 0, FILENAME_DATA_SIZE);
		
			return new File(String.format("%s/tile_cache/%s/%s/%s.tile", GTG.getExternalStorageDirectory().toString(),
					Util.toHex(encryptFilenameOutBytes, 0, 2),
					Util.toHex(encryptFilenameOutBytes, 2, 2),
					Util.toHex(encryptFilenameOutBytes, 4, encryptFilenameOutBytes.length-4)));
		}
		
		return new File(String.format("%s/tile_cache/%02d/%04d/%04d", GTG.getExternalStorageDirectory().toString(), zoomLevel, tileX, tileY));
	}
	

	public boolean containsExactUnexpiredBitmap(int zoomLevel, int tileX, int tileY) {
		File f = getFileName(zoomLevel, tileX, tileY);
		
		if(f.exists() && f.lastModified() > System.currentTimeMillis() - prefs.cacheExpiryMs)
			return true;
		
		return false;
	}


	private void loadBestTile(int zoomLevel, int tileX, int tileY) {
		for(int i = zoomLevel; i >= 0; i --)
		{
			//check if the best tile is already loaded  
			if(memCache.containsExactBitmap(i, tileX, tileY))
			{
				return;
			}
			
			File file = getFileName(i, tileX, tileY);
			
			if(file.exists())
			{
				loadTile(i, tileX, tileY, file);
				return;
			}
			
			tileX = tileX >> 1;
			tileY = tileY >> 1;
		}
	}

	/**
	 * 
	 * @param zoomLevel
	 * @param tileX
	 * @param tileY
	 * @param file
	 * @return true if we successfully loaded
	 */
	private boolean loadTile(int zoomLevel, int tileX, int tileY, File file) {
		/* ttt_installer:remove_line */Log.d("GTG","FC: Loading tile, zoomLevel: "+zoomLevel+" tileX: "+tileX+" tileY: "+tileY);
		
		Bitmap bitmap = getAndTestBitmap(file);

		if(bitmap != null)
		{
			memCache.putBitmapInCache(zoomLevel, tileX, tileY, bitmap);			
			return true;
		}

		return false;
	}

	private class FileCacheTask extends SuperThread.Task
	{
		
		public FileCacheTask() {
			super(GTG.FILE_CACHE_TASK_PRIORITY);
		}

		public void doWork()
		{
			//we need local copies incase things change
			int zoomLevel, tileX, tileY;
			
			synchronized(FileCache.this)
			{
				if(nextZoomLevel == -1)
				{
					stWait(0, FileCache.this);
					return;
				}
				zoomLevel = nextZoomLevel;
				tileX = nextTileX;
				tileY = nextTileY;
			}

			//make sure we aren't running out of room, the sdcard is writable, etc
			if(!GTG.checkSdCard(FileCache.this.context))
				return;

			//now we know we have work to do, so do it
			loadBestTile(zoomLevel,tileX,tileY);
			
			//note that loadBestTile may fail to load anything, due to a corrupt or unreadable file
			//but we tried our best. If this happens, the offending tile will have been deleted and just 
			//be re-requested during the next draw.
			
			//at this point we know the tile is loaded and in memory, or we can't do it--unless the mem cache is really small and it
			//got deleted right away. I do not want to hold a mem cache lock and a file lock at the same time
			//for fear of deadlocking, (although technically with the current code it shouldn't happen), 
			//so we ignore this condition (which would be really unlikely anyway)
			//note, that we could honestly put this in the above synchronization at the top, but it kind
			//of makes it a lot more confusing to read than it would otherwise be
			synchronized (FileCache.this) {
				//if they are still requesting the same tile, at this point, we believe that we are good,
				//so we clear it
				if(zoomLevel == nextZoomLevel && tileX == nextTileX && tileY == nextTileY)
					nextZoomLevel = -1;
			}
		}

	}

	public static class Preferences
	{

		public boolean encryptTiles = false;
		
		/**
		 * Time before panels in the file cache expire
		 */
		public long cacheExpiryMs = 1000 * 60 * 60 * 24 * 14; //two weeks
		
	}

	/**
	 * Retrieves a bitmap from file system, if the file appears corrupt,
	 * DELETES THE FILE, and returns null
	 * 
	 * @param file
	 * @return
	 */
	public static Bitmap getAndTestBitmap(File file) {
		Bitmap bitmap;
		
		try {
			bitmap = BitmapFactory.decodeFile(file.toString());
		}
		catch(Exception e)
		{
			Log.w("GTG", "Deleting cache file "+file, e);
			if(!file.delete())
			{
				//big problem
				throw new IllegalStateException("cannot delete cache file "+file);
			}
			
			return null;
		}
		
		//do some sanity checking
		if(bitmap != null && bitmap.getHeight() == OsmMapView.TILE_SIZE
				&& bitmap.getWidth() == OsmMapView.TILE_SIZE)
		{
			return bitmap;
		}

		//else delete the file, since it's no good
		Log.w("GTG", "Deleting cache file "+file);
		
		if(!file.delete())
		{
			throw new IllegalStateException("cannot delete cache file2 "+file);
		}
		
		return null;

	}
	
}
