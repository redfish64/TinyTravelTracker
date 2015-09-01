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
package com.rareventure.gps2.database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TimeZone;

import android.database.Cursor;
import android.util.Log;

import com.rareventure.android.DbUtil;
import com.rareventure.android.Util;
import com.rareventure.android.Util.LongComparator;
import com.rareventure.android.database.Cache;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerCrypt;

/**
 * Set of all time zone data points
 */
public class TimeZoneTimeSet {
	
	private static final LongComparator<TimeZoneTimeRow> TIME_COMPARATOR = new Util.LongComparator<TimeZoneTimeRow>() {

		@Override
		public int compare(TimeZoneTimeRow t, long key) {
			if(t.getTime() < key)
				return -1;
			else if(t.getTime() > key)
				return 1;
				
			return 0;
		}
	};;

	ArrayList<TimeZoneTimeRow> data = new ArrayList<TimeZoneTimeRow>();

	private DbDatastoreAccessor<TimeZoneTimeRow> dbA;

	private int nextRowId = -1;

	public TimeZoneTimeSet() {
		dbA = new DbDatastoreAccessor<TimeZoneTimeRow>(
						TimeZoneTimeRow.TABLE_INFO);
	}

	/**
	 * This will also delete items that are unnecessary repeats.
	 */
	public void loadSet()
	{
		data.clear();
		
		GTG.db.beginTransaction();
		try {
			boolean isFirst = true;
			TimeZoneTimeRow lastTztr = null;
			
			Cursor c = dbA.query(null, "_id");
			
			while(c.moveToNext())
			{
				TimeZoneTimeRow tztr = new TimeZoneTimeRow();
				dbA.readRow(tztr, c);
				
				if(!isFirst)
				{
					if(tztr.isTimeZoneEqual(lastTztr))
					{
						/* ttt_installer:remove_line */Log.d(GTG.TAG,"Deleting duplicate tztr "+lastTztr+", "+tztr);
						GTG.db.execSQL("delete from time_zone_time where _id = ?", new Object [] {tztr.id});
						continue;
					}
				}
				
				isFirst =false;
				data.add(tztr);
				
				lastTztr = tztr;
				
			}
			
			GTG.db.setTransactionSuccessful();
		}
		finally {
			GTG.db.endTransaction();
		}
		
	}
	
	/**
	 * Adds a row to the database and the set. Does not start a transaction.
	 * Sets an id into the tzt
	 */
	public void addRowToEnd(TimeZoneTimeRow tzt)
	{
		if(nextRowId == -1)
			nextRowId = dbA.getNextRowId();
		
		if(!data.isEmpty()
				&& tzt.getTime() < getLatestRow().getTime())
			throw new IllegalStateException("cant add a row before end");
		
		tzt.id = nextRowId++;

		data.add(tzt);
		
//		Log.d(GTG.TAG,"adding tzt to end, "+tzt);
		
		dbA.insertRow(tzt);
	}
	
	public TimeZoneTimeRow getLatestRow() {
		if(data.isEmpty())
			return null;
		return data.get(data.size() -1);
	}

	public Iterator<TimeZoneTimeRow> getIterator() {
		return data.iterator();
	}
	
	public TimeZone getTimeZoneTimeOrNullIfUnknwonOrLocalTime(int timeSec)
	{
		TimeZoneTimeRow tztr = GTG.tztSet.getTimeZoneCovering(timeSec);
		
		if(tztr != null && tztr.getTimeZone() != null && !tztr.isLocalTimeZone())
			return tztr.getTimeZone();
		
		return null;
	}

	public TimeZoneTimeRow getTimeZoneCovering(int timeSec) {
		int index = Util.binarySearch(data, timeSec*1000l, TIME_COMPARATOR);
		
		if(index < 0)
			//if less than zero, x = -(insertion point) -1, so
			// insertion point = -x - 1
			// the one before the insertion point is the time zone we're looking
			// for (with a time that is the greatest and also less than timeSec)
		{
			//the time is before the first timezone
			if(-index -2 < 0)
				return null;
			else 
				return data.get(-index -2);
		}
		//it exactly matches a timezone
		return data.get(index);
	}

}
