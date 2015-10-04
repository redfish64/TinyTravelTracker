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
package com.rareventure.gps2;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.provider.MediaStore.Video.Thumbnails;
import android.util.Log;

import com.rareventure.android.Util;
import com.rareventure.android.database.CachableRow;
import com.rareventure.android.database.Cache;
import com.rareventure.android.database.Cache.DatastoreAccessor;
import com.rareventure.gps2.database.cache.MediaLocTime;

public class MediaThumbnailCache {
	private Cache<MediaThumbnailCache.BitmapWrapper> cache; 
	
	public static class MediaThumbnailDatastoreAccessor implements
			DatastoreAccessor<BitmapWrapper> {

		private Context context;
		private int width;
		private int height;

		public MediaThumbnailDatastoreAccessor(Context context, int width,
				int height) {
			this.context = context;
			this.width = width;
			this.height = height;
		}

		@Override
		public int getNextRowId() {
			return 0;
		}

		@Override
		public void updateRow(BitmapWrapper row) {
			throw new IllegalStateException();
		}

		@Override
		public void insertRow(BitmapWrapper row) {
			throw new IllegalStateException();
		}

		@Override
		public void softUpdateRow(BitmapWrapper row) {
			throw new IllegalStateException("Soft update not needed");
		}

		@Override
		public boolean needsSoftUpdate() {
			return false;
		}

		@Override
		public boolean getRow(BitmapWrapper outRow, int id) {
			Bitmap fullThumb;
			
			if((id & VIDEO_BIT) != 0)
			{
				//first check if the media exists at all
				//note, even if the media doesn't exist, the thumbnail might
				//still exist, so we have to do this separate check to avoid 
				//showing the user deleted media
				if(!Util.mediaExists(context, id - VIDEO_BIT,false))
					return false;
				
				fullThumb = MediaStore.Video.Thumbnails.getThumbnail(
						context.getContentResolver(),
						id - VIDEO_BIT,
						MediaStore.Video.Thumbnails.MICRO_KIND, null);

				//if the gallery hasn't created a thumbnail yet, we relunctantly create
				//our own, which is only stored in memory. We aren't caching our own images
				//because it seems creepy to create our own thumbnails of everything
				if(fullThumb == null)
				{
					Log.d(GTG.TAG,"creating own thumbnail for video id "+id);
					fullThumb = ThumbnailUtils.createVideoThumbnail(
							Util.getDataFilepathForMedia(context.getContentResolver(), id - VIDEO_BIT,
									false), Thumbnails.MICRO_KIND);
				}

			}
			else if((id & IMAGE_BIT) != 0)
			{
				//first check if the media exists at all
				//note, even if the media doesn't exist, the thumbnail might
				//still exist, so we have to do this separate check to avoid 
				//showing the user deleted images
				if(!Util.mediaExists(context, id- IMAGE_BIT,true))
					return false;

				fullThumb = MediaStore.Images.Thumbnails.getThumbnail(
						context.getContentResolver(),
						id - IMAGE_BIT,
						MediaStore.Images.Thumbnails.MICRO_KIND, null);
				
				//if the gallery hasn't created a thumbnail yet, we relunctantly create
				//our own, which is only stored in memory. We aren't caching our own images
				//because it seems creepy to create our own thumbnails of everything
				if(fullThumb == null)
				{
					Log.d(GTG.TAG,"creating own thumbnail for image id "+id);
					fullThumb = ThumbnailUtils.extractThumbnail(Util.getBitmap(context, id - IMAGE_BIT, true), width, height);
				}

			}
			else
				throw new IllegalStateException();
			
			if(fullThumb == null)
			{
				//sometimes the extractThumbnail returns null, in which case, we just return false, indicating to delete
				//the media item
				return false;
			}
			outRow.bitmap = Bitmap.createScaledBitmap(fullThumb, width, height, false);
			outRow.id = id;
			
			return true;
		}

	}



	public static class BitmapWrapper extends CachableRow
	{

		public Bitmap bitmap;
		
	}



	/**
	 * This is used to signify whether a vide or image should be retrieved
	 */
	public static final int VIDEO_BIT = 1<<30;
	public static final int IMAGE_BIT = 1<<29;

	public MediaThumbnailCache(Context context, int width, int height) {
		cache = new Cache<MediaThumbnailCache.BitmapWrapper>(new MediaThumbnailDatastoreAccessor(context, width, height), prefs.maxCache)
		{
			@Override
			public BitmapWrapper allocateRow() {
				return new BitmapWrapper();
			}
		};
	}
	
	
	public BitmapWrapper getBitmapWrapper(int id)
	{
		return cache.getRowNoFail(id);
	}

	public void clear() {
		cache.clear();
	}

	
	public static Preferences prefs = new Preferences();

	
	public static class Preferences
	{
		/**
		 * Max number of items to cache
		 */
		public int maxCache = 16;
	}



}
