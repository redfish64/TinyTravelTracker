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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.rareventure.gps2.R.string;
import com.rareventure.android.SuperThread;
import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;
import com.rareventure.gps2.reviewer.map.OsmMapView;

/**
 * Loads from remote url
 */
//TODO 3 shut self off if continually get bad data (to avoid being rude to server)
// (this is a 3 because we already sleep for a certain period if we get a bad response)

public class RemoteLoader {
	
	//private String urlBaseGoogle            = "http://mt1.google.com/vt/x=1&y=0&z=1";

	static final int MAX_ZOOM_LEVEL = 18; //this is the maximum zoom level that open street
	//maps has
	
	byte[] buffer                           = new byte[2048];

	private FileCache fileCache;
	private int nextZoomLevel=-1;
	private int nextTileX;
	private int nextTileY;
	
	private Preferences prefs = new Preferences();
	private OsmMapView mapView;
	private OsmMapGpsTrailerReviewerMapActivity context;
	private RemoteLoaderTask remoteLoaderTask;
	
	public RemoteLoader(OsmMapGpsTrailerReviewerMapActivity context, OsmMapView mapView, FileCache fileCache, SuperThread st) 
	{
		this.context = context;
		this.mapView = mapView;
		this.fileCache = fileCache;

		st.addTask(remoteLoaderTask = new RemoteLoaderTask());
	}

	public synchronized void requestTile(int zoomLevel, int tileX, int tileY) {
		if(zoomLevel > MAX_ZOOM_LEVEL)
			return;
		
		this.nextZoomLevel = zoomLevel;
		this.nextTileX = tileX;
		this.nextTileY = tileY;
		
		remoteLoaderTask.stNotify(this);
		
		return;
	}
	
	/**
	 * Loads the bitmap and saves to the file cache
	 * @param url
	 * @param file
	 * @return 
	 * @return
	 */
	private boolean loadBitmap(int zoomLevel, int tileX, int tileY, File file)
	{
		InputStream in = null;
		
		//we write to a temp file and then move it over so that we don't end
		//up reading a partial file in the fileCache
		File tempFile = new File(file+".tmp");
		
		OutputStream out;
		
		try {
			out = new FileOutputStream(tempFile);
		} catch (FileNotFoundException e2) {
			//if the sdcard is messed up we've got big problems,
			// note we already checked to see if the sdcard was mounted 
			throw new IllegalStateException("cannot open cache file "+file);
		}
		
		try 
		{
			in = GTG.tttClient.getTile(remoteLoaderTask,context, zoomLevel, tileX, tileY);
			
			//sometimes the ttt client will hang if it can't get access to the server.
			//in this case, we have no way of stopping our thread, so we promise to
			//shutdown later without causing any damage
			
			this.remoteLoaderTask.promiseToDieWithoutBadEffect(true);
			
			if(in == null)
				throw new IOException("can't get data");

			//if we couldn't write to output stream 
			if(Util.copy(buffer, in, out) != null)
				//if the sdcard is messed up we've got big problems
				throw new IllegalStateException("cannot write to cache file "+file);

			try {
				out.close();
			} 
			catch (IOException e) 
			{
				throw new IllegalStateException("cannot close cache file "+file, e);
			}
		
			if(FileCache.getAndTestBitmap(tempFile) == null)
				throw new IOException("Got bad bitmap from server");
			
			this.remoteLoaderTask.promiseToDieWithoutBadEffect(false);
			
			if(remoteLoaderTask.superThread.manager.isShutdown)
				return false;
			
			if(!tempFile.renameTo(file))
				throw new IllegalStateException("cannot rename cache file "+file);
		} 
		catch (IOException e) 
		{
			//this catch only gets invoked when ioexception came from input stream
			
			this.remoteLoaderTask.promiseToDieWithoutBadEffect(true);
			try {
				Thread.sleep(prefs.timeToRetryConnectionMs);
			} catch (InterruptedException e1) {
			}
			
			this.remoteLoaderTask.promiseToDieWithoutBadEffect(false);
			return false;
		}
		return true;
	}

	private boolean loadTile(int zoomLevel, int tileX, int tileY) {
		/* ttt_installer:remove_line */Log.d("RemoteLoader","Loading tile, zoomLevel: "+zoomLevel+" tileX: "+tileX+" tileY: "+tileY);
		
		File file = fileCache.getFileName(zoomLevel, tileX, tileY);
		
		if(!file.getParentFile().exists() && !file.getParentFile().mkdirs())
		{
			throw new IllegalStateException("cannot make cache dirs "+file.getParent());
		}

		//this copies the data to the filesystem cache
		if(loadBitmap(zoomLevel, tileX, tileY, file))
		{
			mapView.notifyNewBitmapInCache();
			return true;
		}
		
		return false;
	}


	private class RemoteLoaderTask extends SuperThread.Task
	{
		public RemoteLoaderTask() {
			super(GTG.REMOTE_LOADER_TASK_PRIORITY);
		}

		public void doWork()
		{
			//we need local copies incase things change
			int zoomLevel, tileX, tileY;
			
			synchronized(RemoteLoader.this)
			{
				//if there is work to be done
				if(nextZoomLevel == -1)
				{
					stWait(0, RemoteLoader.this);
					return;
				}
				
				zoomLevel = nextZoomLevel;
				tileX = nextTileX;
				tileY = nextTileY;
			}

			//check that we didn't already load it.. 
			if(!fileCache.containsExactUnexpiredBitmap(zoomLevel, tileX, tileY))
			{
				//make sure we aren't running out of room, the sdcard is writable, etc
				if(!GTG.checkSdCard(RemoteLoader.this.context))
					return;

				//now we know we have work to do, so do it
				loadTile(zoomLevel,tileX,tileY);
			}
			
			//now we know we loaded it or tried our best to do so
			//note that loadTile may fail to load anything, due to an ioexception reading from the url
			//but we tried our best and there is no reason to try again for this tile
			
			synchronized (RemoteLoader.this) {
				//if they are still requesting the same tile, at this point, we believe that we are good,
				//so we clear it
				if(zoomLevel == nextZoomLevel && tileX == nextTileX && tileY == nextTileY)
					nextZoomLevel = -1;
			}
			
		}

	}

	public static class Preferences 
	{
		/**
		 * Time to wait when we get an exception trying to load a tile, so we don't go crazy.
		 */
		public long timeToRetryConnectionMs = 10000;
	}

}
