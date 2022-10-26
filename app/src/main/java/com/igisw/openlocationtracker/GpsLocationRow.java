/** 
    Copyright 2022 Igor Calì <igor.cali0@gmail.com>

    This file is part of Open Travel Tracker.

    Open Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Open Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Open Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.igisw.openlocationtracker;

import com.igisw.openlocationtracker.Util;
import com.rareventure.android.database.CachableRow;
import com.igisw.openlocationtracker.DbDatastoreAccessor;
import com.igisw.openlocationtracker.TableInfo;
import com.igisw.openlocationtracker.EncryptedRow;

public class GpsLocationRow extends EncryptedRow
{
	public static final Column TIME = new Column("TIME",Long.class);
	public static final Column LATM = new Column("LATM",Integer.class);
	public static final Column LONM = new Column("LONM",Integer.class);
	public static final Column ALT = new Column("ALT",Double.class);
	
	public static final Column [] COLUMNS = new Column [] {
		TIME, LATM, LONM, ALT
		
	};
	
	public static final int DATA_LENGTH = EncryptedRow.figurePosAndSizeForColumns(COLUMNS);

	public static final int ENC_BLOB_LENGTH = GTG.crypt.crypt.getNumOutputBytesForEncryption(DATA_LENGTH);

	public static final String TABLE_NAME = "gps_location_time";
	
	public static final String INSERT_STATEMENT = DbDatastoreAccessor.createInsertStatement(TABLE_NAME);
	public static final String UPDATE_STATEMENT = DbDatastoreAccessor.createUpdateStatement(TABLE_NAME);
	public static final String DELETE_STATEMENT = DbDatastoreAccessor.createDeleteStatement(TABLE_NAME);
	
	public static final TableInfo TABLE_INFO = new TableInfo(TABLE_NAME, COLUMNS, INSERT_STATEMENT, UPDATE_STATEMENT,
			DELETE_STATEMENT);
	
	public GpsLocationRow()
	{
		super();
	}
	
	public int getDataLength()
	{
		return DATA_LENGTH;
	}
	


	public void setData(long time, int latm, int lonm, double alt) {
//		Log.d(GTG.TAG,"Creating gps location blob for id "+id);
		data2 = new byte[DATA_LENGTH];
		setLong(TIME.pos,time);
		setInt(LATM.pos,latm);
		setInt(LONM.pos,lonm);
		setDouble(ALT.pos,alt);
	}

	public long getTime() {
		return getLong(TIME);
	}

	public String toString()
	{
		return String.format("GpsLocationRow(id=%d,timeMs=%20s,latm=%20d,lonm=%20d,timeSec=%d)"
//				+",timeZone=%d)",
				,id,
				GTG.sdf.format(getLong(TIME)),
				getInt(LATM),
				getInt(LONM),
				getLong(TIME)/1000
//				,getByte(TIMEZONE)
				);
	}

	public String toString(long relTime, int latm1, int lonm1) {
		return String.format("GpsLocationRow(id=%8d,              t=%8d, latm=%8d, lonm=%8d)",id,
				getLong(TIME) - relTime,
				getInt(LATM)-latm1, getInt(LONM) - lonm1);
		
	}
	
//	public long hackRandomize(Random r, long minTime, long startTime, int timeWalk, int mdWalk, int timePower, int mdWalkPower) {
//		while(true)
//		{
//			startTime += randomWalk(r,timeWalk, timePower);
//			if(startTime < minTime)
//			{
//				startTime = minTime + timeWalk/2;
//			}
//			else
//				break;
//		}
//		
//		setLong(TIME.pos,startTime);
//		setInt(LATM.pos,getInt(LATM) + randomWalk(r,mdWalk, mdWalkPower));
//		setInt(LONM.pos,getInt(LONM) + randomWalk(r,mdWalk, mdWalkPower));
//		
//		return startTime;
//	}
//
//	private int randomWalk(Random r, int amount, int power) {
//		float answer = 1;
//		
//		while(power-- > 0)
//		{
//			answer *= r.nextFloat();
//		}
//		
//		//we do it this way, because we want unbiased results. We don't want to walk from -5 to 4 for a
//		//range of 5 for example
//		if(r.nextFloat() >= .5)
//			return (int) (answer * amount);
//		return - (int) (answer * amount);
//	}
//	
	public String plotTo(GpsLocationRow o)
	{
		return Util.gnuPlot3DIt(getInt(LATM),getInt(LONM),getLong(TIME),
				o.getInt(LATM),o.getInt(LONM),o.getLong(TIME));
	}

	public int getLonm() {
		return getInt(GpsLocationRow.LONM);
	}

	public int getLatm() {
		return getInt(GpsLocationRow.LATM);
	}



	@Override
	public Cache<CachableRow> getCache() {
		return (Cache)GTG.gpsLocCache;
	}



	public double getAltitude() {
		return getDouble(ALT);
	}

}
