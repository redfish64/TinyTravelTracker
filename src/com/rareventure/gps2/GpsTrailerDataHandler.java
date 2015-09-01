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

import java.util.TimeZone;

import android.content.Context;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.rareventure.android.DbUtil;
import com.rareventure.android.Util;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.cachecreator.GpsTrailerCacheCreator;

/**
 * Handles accelerometer and sensor data. Provides plumbing for saving data to the 
 * database and detecting calmness
 */
public class GpsTrailerDataHandler
{
	/**
	 * When the user last stopped moving.
	 */
	private long startOfRestMs;

	private TimeZone currTimeZone;
	
	public GpsTrailerDataHandler()
	{
		GTG.initRwtm.registerReadingThread();
		try {
			//we try to get the latest time zone data if we can. Otherwise, (if the db is encrypted and 
			//we don't have the key), we'll just use null. This'll just create an extra row in the TimeZoneTime
			//table but this shouldn't happen that often
			if(GTG.crypt.canDecrypt())
			{
				TimeZoneTimeRow tztr = GTG.tztSet.getLatestRow();
				if(tztr != null && tztr.getTimeZone() != null)
					currTimeZone = tztr.getTimeZone();
			}
		}
		finally {	 	
			GTG.initRwtm.unregisterReadingThread();
		}
	}
	
	public void processGpsData(int lonm, int latm, double alt, long time) {
		GTG.initRwtm.registerReadingThread();
		
		GpsLocationRow lr = GTG.gpsLocCache.newRow();
		
		lr.setData(time, latm, lonm, alt);
		
		GTG.db.beginTransaction();
		
		try {
			//WARNING, if this is changed to no longer writes using gpsLocCache, we will need to fix
			// GTG.lockGpsCaches
			GTG.gpsLocCache.writeDirtyRows();
			GTG.gpsLocCache.clearDirtyRows();
			
			TimeZone tz = Util.getCurrTimeZone();
			
			if(currTimeZone == null || !tz.getID().equals(currTimeZone.getID()))
			{
				TimeZoneTimeRow tzt = TimeZoneTimeRow.createTztForNow();
				tzt.setData(time, tz);
				GTG.tztSet.addRowToEnd(tzt);

				/* ttt_installer:remove_line */Log.d(GTG.TAG,"Inserted tzt "+tzt.id);
				
				currTimeZone = tz;
			}
			
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"Inserted row "+lr.id);
			GTG.db.setTransactionSuccessful();
		}
		finally
		{
			GTG.db.endTransaction();
			GTG.initRwtm.unregisterReadingThread();
		}
	}
	
	/**
	 * If we just stopped. Should not be called when system starts, only when actually stopped.
	 */
	public void stopped(long startOfRestMs)
	{
		this.startOfRestMs = startOfRestMs;
	}
	
	/**
	 * If we just started moving.
	 * @param startOfMoving
	 */
	public void moving(long startOfMoving)
	{
		GTG.initRwtm.registerReadingThread();

		try {
			//if we haven't stopped yet
			if(startOfRestMs == 0)
				return;
			
			SQLiteStatement s = DbUtil.createOrGetStatement(GTG.db, 
			"insert into Rest (begin_time, end_time) values (?,?);");
			s.bindLong(1, startOfRestMs);
			s.bindLong(2, startOfMoving);
			
			s.execute();
		} finally {
			GTG.initRwtm.unregisterReadingThread();
		}
 	}

	public void compassDirectionChange(float direction,
			long startTimeMs, long endTimeMs) {
		GTG.initRwtm.registerReadingThread();

		try {
			SQLiteStatement s = DbUtil.createOrGetStatement(GTG.db, 
					"insert into compass (begin_time, end_time, direction) values (?,?,?);");
			
			synchronized(s)
			{
				s.bindLong(1, startTimeMs);
				s.bindLong(2, endTimeMs);
				s.bindDouble(3, direction);
				
				s.execute();
			}
		} finally {
			GTG.initRwtm.unregisterReadingThread();
		}
	}

}
