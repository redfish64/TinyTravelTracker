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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

import com.rareventure.gps2.reviewer.map.OsmMapView;

/**
 * In memory cache of tiles
 */
//TODO 3: we should store a tiny bitmap along with the bitmap and this second bitmap
// will then contain the subtiles that are covered (saved to the file system), so
// the user doesn't have to guess which layers have been loaded and which haven't
// when working without wifi. note, we'd update the upper layers after remotely loading
// which happens infrequently so it wouldn't be that much of a perf drain.
public class MemoryCache {
	private static final int TILE_SIZE = 256;
	
	/**
	 * This is the maximum zoom level that memory cache can support (and a very much
	 * overkill value anyway). If you need to increase this, make sure to update
	 * getKey() to shift around the bits or change the object
	 */
	public static final int MAX_ZOOM_LEVEL = 18;

	private int rectX;
	private int rectY;
	private int rectWidth;
	private int rectHeight;

	private int DEFAULT_CACHE_SIZE = 32;
	
	private LRUMap<Long, Bitmap> cache = new LRUMap<Long, Bitmap>(DEFAULT_CACHE_SIZE, DEFAULT_CACHE_SIZE);

	private FileCache fileCache;

	private RemoteLoader remoteLoader;

	private OsmMapView mapView;
	
	public MemoryCache(OsmMapView mapView)
	{
		this.mapView = mapView;
	}
	
	public void setFileCache(FileCache fileCache)
	{
		this.fileCache = fileCache;
	}
	
	public void setRemoteCache(RemoteLoader remoteLoader)
	{
		this.remoteLoader = remoteLoader;
	}
	
	public void setWidthAndHeight(int width, int height)
	{
		//at a minimum, the cache should at least hold enough panels to all show on the screen at once
		 cache.setMaxCapacity(Math.max(DEFAULT_CACHE_SIZE, ((TILE_SIZE - 1 + width) /TILE_SIZE+ 1) 
				 * ((TILE_SIZE - 1+ height) / TILE_SIZE+ 1)));
	}

	/**
	 * Draws tiles for the given area and requests that they be loaded into the 
	 * memory cache if they are not already.
	 */
	public void requestAndDrawRect(Canvas canvas, int pxX, int pxY, int pxWidth,
			int pxHeight, long zoom, int zoomLevel) {
		double zoomToZoomLevelRatio = ((double)(1 << (zoomLevel +8)))/zoom; 
		int zlX = (int) (pxX * zoomToZoomLevelRatio);
		int zlY = (int) (pxY * zoomToZoomLevelRatio);
		int zlWidth = (int) (pxWidth * zoomToZoomLevelRatio);
		int zlHeight = (int) (pxHeight * zoomToZoomLevelRatio);
		
		rectX = zlX/TILE_SIZE;
		rectY = zlY/TILE_SIZE;
		rectWidth = (zlX+zlWidth + (TILE_SIZE-1))/TILE_SIZE - rectX;
		rectHeight = (zlY+zlHeight + (TILE_SIZE-1))/TILE_SIZE - rectY;
		
		for(int tx = 0; tx < rectWidth; tx++)
		{
			for(int ty = 0; ty < rectHeight && ty + rectY < (1<<zoomLevel); ty++) 
			{
				int tileX = (tx + rectX) % (1<<zoomLevel);
				int tileY = ty+rectY;
				
				int score = drawBitmap(canvas, tileX, tileY,
							(int)(tileX*TILE_SIZE/zoomToZoomLevelRatio - pxX), 
							(int)(tileY * TILE_SIZE/zoomToZoomLevelRatio - pxY),
							//compute the exact width and height as will be rounded to, to avoid 
							//lines between the tiles
							((int)((tileX+1)*TILE_SIZE/zoomToZoomLevelRatio))
							-  ((int)(tileX*TILE_SIZE/zoomToZoomLevelRatio)),
							((int)((tileY+1)*TILE_SIZE/zoomToZoomLevelRatio))
							-  ((int)(tileY*TILE_SIZE/zoomToZoomLevelRatio)),
							zoomLevel);
				
				//if we don't have the perfect tile
				if(score > 0)
				{
					//we can only load up to the max zoom level, so we reduce ti if
					//necessary here
					int loadZoomLevel = Math.min(zoomLevel, RemoteLoader.MAX_ZOOM_LEVEL);
					
					int loadTileX = (tileX >> (zoomLevel - loadZoomLevel));
					int loadTileY = (tileY >> (zoomLevel - loadZoomLevel));
					
					//requesting multiple times won't hurt the loaders any
					//they'll just load the last one requested

					fileCache.requestTile(loadZoomLevel, loadTileX, loadTileY);

					//note that if the tile already exists in the file cache, the remote
					//loader won't try to reload it.
					
					//TODO 3: reload out of date dates for tiles in the file system
					remoteLoader.requestTile(loadZoomLevel, loadTileX, loadTileY);
				}
			}
		}
	}

	/**
	 * 
	 * @param canvas
	 * @param tileX
	 * @param tileY
	 * @param xDest
	 * @param yDest
	 * @param zoomLevel
	 * @return an int specifying the suitability of the tile that we have. If zero, means the tile
	 * is perfectly suitable and nothing needs to be done.
	 */
	private int drawBitmap(Canvas canvas, int tileX, int tileY, int xDest, int yDest,
			int width, int height,
			int zoomLevel) {
		Rect src = new Rect(0,0,TILE_SIZE,TILE_SIZE);
		Rect dst = new Rect(xDest, yDest, xDest+width, yDest+height);
		//keep searching upwards until we find a bitmap to scale and draw
		for(int i = 
				zoomLevel;
				i>= 0; i--)
		{
			Bitmap bitmap;
			
			//since other threads add to cache, we have to be thread safe
			synchronized (cache)
			{
				bitmap = cache.get(getKey(i, tileX,tileY));
			}
			
			if(bitmap != null)
			{
				canvas.drawBitmap(bitmap, src, dst, null);
				return zoomLevel - Math.min(i, RemoteLoader.MAX_ZOOM_LEVEL);
			}

			//we cut in half because we're zooming out, and based on the relationship
			//to the parent we either add 128 or 0
			src.left = src.left >> 1;
			src.right = src.right >> 1;
			if((tileX & 1) == 1)
			{
				src.left += 128;
				src.right += 128;
			}
			
			src.top = src.top >> 1;
			src.bottom = src.bottom >> 1;
			if((tileY & 1) == 1)
			{
				src.top += 128;
				src.bottom += 128;
			}
			
			//since we are going up a level, we need to reduce the tile coordinates by half
			tileX = tileX >> 1;
			tileY = tileY >> 1;
			
		}
		
		return MAX_ZOOM_LEVEL;
	}
	
	public void putBitmapInCache(int zoomLevel, int tileX, int tileY,
			Bitmap bitmap) {
		synchronized (cache) {
			cache.put(getKey(zoomLevel, tileX, tileY), bitmap);
		}
		
		mapView.notifyNewBitmapInCache();
	}

	

	public static long getKey(int zoomLevel, int tileX, int tileY) {
		// LSB --> HSB
		// zoomLevel (16 bits) | tileX | tileY
		return ((long)zoomLevel)|( (((long)tileX) | (((long)tileY)<<MAX_ZOOM_LEVEL)) << 16);
	}
	
	public boolean containsExactBitmap(int zoomLevel, int tileX, int tileY) {
		return cache.containsKey(getKey(zoomLevel, tileX, tileY));
	}

	public void shutdown() {
	}

}
