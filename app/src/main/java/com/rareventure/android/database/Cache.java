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
package com.rareventure.android.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.rareventure.android.DbUtil;
import com.rareventure.android.SuperThread;
import com.rareventure.android.database.Cache.DatastoreAccessor;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.gps2.database.cache.AreaPanelCache;
import com.rareventure.util.CircularList;
import com.rareventure.util.Pair;
import com.rareventure.util.ReadWriteThreadManager;

public abstract class Cache<T extends CachableRow> {
	public static interface DatastoreAccessor<T extends CachableRow>
	{
		/**
		 *
		 * @return the next row id for inserting,
		 *   usually one plus maximum id
		 */
		int getNextRowId();

		void updateRow(T row);
		
		void insertRow(T row);

		/**
		 * 
		 * @param outRow
		 * @param id
		 * @return true if the row was retrieved
		 */
		boolean getRow(T outRow, int id);

		void softUpdateRow(T row);

		boolean needsSoftUpdate();
	}
	
	private HashMap<Integer, T> idToCache;
	
	private HashMap<Integer, T> idToDirtyRow;
	
	public int misses;
	public int hits;
	private int maxCacheSize;
	
	private int nextRowId = Integer.MIN_VALUE;
	
	private CircularList<T> cache;
	
	private DatastoreAccessor<T> da;
	
	public Cache(DatastoreAccessor<T> da, int maxCacheSize)
	{
		this.da = da;
		this.maxCacheSize = maxCacheSize;
		cache = new CircularList<T>(maxCacheSize);
		clear();
	}
	
	public synchronized void clear() {
		cache.clear();
		//PERF: convert to SparseArray(), note we will need to implement entrySet()
		idToCache = new HashMap<Integer, T>();
		idToDirtyRow = new HashMap<Integer, T>();
		nextRowId = da.getNextRowId();
	}

	public synchronized int getDirtyRowCount() {
		return this.idToDirtyRow.size();
	}


	public synchronized boolean isEmpty()
	{
		return idToCache.isEmpty();
	}
	
	public final synchronized T newRow()
	{
		T row = allocateRow();
		row.isDirty = true;
		row.isInserted = false;
		row.id = nextRowId++;

		idToDirtyRow.put(row.id, row);

		return row;
	}
	
	public synchronized void notifyRowUpdated(T row)
	{
		if(!row.isDirty)
		{
			//ignore rows that don't have an id yet
			if(row.id == -1)
				return;
			
			idToDirtyRow.put(row.id, row);
			
			row.isDirty = true;
			row.referencedRecently = true;
		}
	}
	
	//TODO 4: implement delete (if you want)
	
	public void writeDirtyRows() {
		writeDirtyRows(null);
	}

	/**
	 * Commits dirty rows to the database.
	 * This is meant only to be called by the same thread that is inserting or
	 * updating the rows
	 * 
	 * WARNING: Again, writing to the data while calling this method is not thread safe
	 */
	public void writeDirtyRows(ReadWriteThreadManager rwtm)
	{
		/* ttt_installer:remove_line */Log.d(GTG.TAG,"Commiting "+idToDirtyRow.size()+" rows for "+this);
		
		ArrayList<Entry<Integer, T>> al = new ArrayList<Entry<Integer, T>>(idToDirtyRow.entrySet());
		
		//sort it by id because timmy tables demand that inserts be done in ascending 
		// consecutive order
		Collections.sort(al, new Comparator<Entry<Integer, T>>() {

			@Override
			public int compare(Entry<Integer, T> lhs, Entry<Integer, T> rhs) {
				return lhs.getKey() - rhs.getKey();
			}
		});
		
		if(da.needsSoftUpdate())
		{
			for(Map.Entry<Integer,T> e : al)
			{
				SuperThread.abortOrPauseIfNecessary();
	
				T r = e.getValue();
				
				if(r.isInserted)
				{
//					Log.d(GTG.TAG,"soft update for "+r.id);
					da.softUpdateRow(r);
				}
				
				if(rwtm != null && rwtm.isReadingThreadsActive())
				{
					rwtm.pauseForReadingThreads();
				}
			}
		}
		
		for(Map.Entry<Integer,T> e : al)
		{
			SuperThread.abortOrPauseIfNecessary();
			
			T r = e.getValue();
			
			if(r.isInserted)
			{
//				Log.d(GTG.TAG,"hard update for "+r.id);
				da.updateRow(r);
			}
			else
				da.insertRow(r);
			
			synchronized (this)
			{
				r.isDirty = false;
				r.isInserted = true;
				
				//Log.d("GTS", "i = "+e.getKey()+" id is "+r.id);
				
				//put the dirty rows in the cache, because they probably will be used
				
				r.referencedRecently = true;
			
				if(!idToCache.containsKey(r.id))
					putRowInCache(r);
			}
			
			if(rwtm != null && rwtm.isReadingThreadsActive())
			{
				rwtm.pauseForReadingThreads();
			}
		}
		
		//TODO 4 the below explanation is no longer the case, fix?
		//note that we don't clear the rows here. This is to work well with timmy tables, which
		//don't support access to row being committed
	}
	
	public void clearDirtyRows() {
		idToDirtyRow.clear();
	}


	abstract protected T allocateRow();
	
	protected T allocateTopRow()
	{
		return allocateRow();
	}
	
	public synchronized T getRow(int id) {
		T row = getRowNoFail(id);
		
		if(row == null)
			TAssert.fail("can't find cache row for id "+id);
		
		return row;

	}


	public synchronized T getRowNoFail(int id) {
		T er = idToDirtyRow.get(id);
		
		if(er != null)
			return er;
		
		//now check the proper cache
		T cacheRow = idToCache.get(id);
		
		//if not in the cache
		if(cacheRow == null)
		{
			misses++;
			//get row from the db
			cacheRow = getCacheRowFromDb(id);
			
			if(cacheRow == null)
				return null;
			
			//put it into the cache
			putRowInCache(cacheRow);
		}
		else
		{
			hits++;
			cacheRow.referencedRecently = true;
		}
		
		return cacheRow;
	}
	
	private void putRowInCache(T cacheRow) {
		
//		Log.d(GpsTrailer.TAG,"putting "+cacheRow+" in cache");
		idToCache.put(cacheRow.id, cacheRow);
		cacheRow.referencedRecently = true;
		
		//
		// Now we need to update the cache and remove an item if we've gone over the limit
		//
		
		//remove old one if necessary
		if(cache.size() == maxCacheSize)
		{
			int count = 0;
			while(true)
			{
				CachableRow rowToReplace = cache.getLast();

				if(rowToReplace.referencedRecently)
				{
					rowToReplace.referencedRecently = false;
					cache.moveLastToFirst();
				}
				else
				{
					//TODO 2.5: Should we have ViewNodes start using ids so that 
					// we don't get a copy of the same row in the cache that is different
					// than the one in ViewNode?
//					if(rowToReplace.id == AreaPanelCache.TOP_ROW_ID)
//						Log.e(GTG.TAG, "What???? Why is top row being removed "+rowToReplace); //

					cache.replaceLast(cacheRow);

					//remove the item from the cache
					idToCache.remove(rowToReplace.id);
					
					break;
				}
				
				count++;
			}
			
			//Log.d(GTG.TAG, "set "+count+" nodes to referencedRecently=false for putRowInCache");
			
//			Log.d(GpsTrailer.TAG,"removing "+rowToRemove.id+" from cache");
			
		}
		else cache.addNoExpand(cacheRow);
	}
	
	

	/**
	 * Gets data from database, bypassing the cache
	 */
	private T getCacheRowFromDb(int id) {
		T cacheRow = allocateRow();
		if(!da.getRow(cacheRow, id))
			return null;
		
//		Log.d(GpsTrailer.TAG,"loaded "+cacheRow+" from db");
		
		cacheRow.isInserted = true;
		return cacheRow;
	}

	public int getNextRowId() {
		return da.getNextRowId();
	}


}
