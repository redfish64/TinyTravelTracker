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

import java.util.TimeZone;

import android.text.GetChars;
import android.util.Log;

import com.rareventure.android.Util;
import com.rareventure.android.database.CachableRow;
import com.rareventure.android.database.Cache;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.android.database.TableInfo;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.GTG;

public class TimeZoneTimeRow extends EncryptedRow
{
	private static final int MAX_TIMEZONE_LENGTH = 255;

	public static final Column TIME = new Column("TIME",Long.class);
	public static final Column TIMEZONE = new Column("TIMEZONE",MAX_TIMEZONE_LENGTH);
	
	public static final Column [] COLUMNS = new Column [] {
		TIME, TIMEZONE
		
	};
	
	public static final int DATA_LENGTH = GTG.crypt.crypt.getNumOutputBytesForDecryption(EncryptedRow.figurePosAndSizeForColumns(COLUMNS));

	public static final int ENC_BLOB_LENGTH = GTG.crypt.crypt.getNumOutputBytesForEncryption(DATA_LENGTH);

	
	public static final String TABLE_NAME = "time_zone_time";
	
	public static final String INSERT_STATEMENT = DbDatastoreAccessor.createInsertStatement(TABLE_NAME);
	public static final String UPDATE_STATEMENT = DbDatastoreAccessor.createUpdateStatement(TABLE_NAME);
	public static final String DELETE_STATEMENT = DbDatastoreAccessor.createDeleteStatement(TABLE_NAME);
	
	public static final TableInfo TABLE_INFO = new TableInfo(TABLE_NAME, COLUMNS, INSERT_STATEMENT, UPDATE_STATEMENT,
			DELETE_STATEMENT);
	private TimeZone tz;

	private boolean tzKnown;
	
	public TimeZoneTimeRow()
	{
		super();

	}
	


	public TimeZoneTimeRow(TimeZone currTimeZone, long currentTimeMillis) {

		setData(currentTimeMillis, currTimeZone);
	}

	public int getDataLength()
	{
		return DATA_LENGTH;
	}
	


	public void setData(long time, TimeZone tz) {
		data2 = new byte[DATA_LENGTH];
//		Log.d(GTG.TAG,"Creating gps location blob for id "+id);
		setLong(TIME.pos,time);
		if(tz == null)
			//empty string indicates unknown (we default to the current users timezone
			setString(TIMEZONE.pos,"", MAX_TIMEZONE_LENGTH);
		else
			setString(TIMEZONE.pos,tz.getID(), MAX_TIMEZONE_LENGTH);
	}

	public long getTime() {
		return getLong(TIME);
	}
	
	public TimeZone getTimeZone()
	{
		if(!tzKnown)
		{
			String tzId = getString(TIMEZONE);
			
			if(tzId.length() == 0)
				tz = null;
			else
				tz = TimeZone.getTimeZone(tzId);
			
			tzKnown = true;
		}
		
		return tz;
	}

	public String toString()
	{
		return String.format("TimeZoneTime(id=%d,timeMs=%d,timeDate=%20s,tzStr=%s,tz=%s",
				id,
				getLong(TIME),
				GTG.sdf.format(getLong(TIME)),
				getString(TIMEZONE),
				String.valueOf(getTimeZone()));
	}


	@Override
	public Cache<CachableRow> getCache() {
		return null;
	}



	public static TimeZoneTimeRow createTztForNow() {
		return new TimeZoneTimeRow(Util.getCurrTimeZone(), System.currentTimeMillis());
	}



	public void setTime(long timeMs) {
		setLong(TIME.pos, timeMs);
	}



	public boolean isTimeZoneEqual(TimeZoneTimeRow o) {
		return getString(TIMEZONE).equals(o.getString(TIMEZONE));
	}



	public boolean isLocalTimeZone() {
		return getTimeZone() == null || getTimeZone().equals(Util.getCurrTimeZone());
	}


}
