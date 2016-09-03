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

import android.util.Log;

import com.rareventure.android.Util;
import com.rareventure.android.database.CachableRow;
import com.rareventure.android.database.Cache;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.android.database.TableInfo;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.GTG;

public class GpsLocationRow extends EncryptedRow
{
	public static final Column TIME = new Column("TIME",Long.class);
	public static final Column LATM = new Column("LATM",Integer.class);
	public static final Column LONM = new Column("LONM",Integer.class);
	public static final Column ALT = new Column("ALT",Double.class);

	//accuracy of reading. 0 indicates accuracy unknown
	public static final Column ACCURACY = new Column("ACCURACY",Float.class);

	public static final Column [] COLUMNS = new Column [] {
		TIME, LATM, LONM, ALT, ACCURACY
		
	};
	
	public static final int DATA_LENGTH = EncryptedRow.figurePosAndSizeForColumns(COLUMNS);

	public static final int ENC_BLOB_LENGTH = GTG.crypt.crypt.getNumOutputBytesForEncryption(DATA_LENGTH);

	public static final String TABLE_NAME = "gps_location_time";
	
	public static final String INSERT_STATEMENT = DbDatastoreAccessor.createInsertStatement(TABLE_NAME);
	public static final String UPDATE_STATEMENT = DbDatastoreAccessor.createUpdateStatement(TABLE_NAME);
	public static final String DELETE_STATEMENT = DbDatastoreAccessor.createDeleteStatement(TABLE_NAME);
	
	public static final TableInfo TABLE_INFO = new TableInfo(TABLE_NAME, COLUMNS, INSERT_STATEMENT, UPDATE_STATEMENT,
			DELETE_STATEMENT);

	/**
	 * Estimation of gps_hdop to accuracy value
	 * TODO: use the actual nmea data rather than hardcode it
	 */
	public static final float GPS_HDOP_TO_ACCURACY = 5.f;

	public GpsLocationRow()
	{
		super();
	}
	
	public int getDataLength()
	{
		return DATA_LENGTH;
	}
	


	public void setData(long time, int latm, int lonm, double alt, float accuracy) {
//		Log.d(GTG.TAG,"Creating gps location blob for id "+id);
		data2 = new byte[DATA_LENGTH];
		setLong(TIME.pos,time);
		setInt(LATM.pos,latm);
		setInt(LONM.pos,lonm);
		setDouble(ALT.pos,alt);
		setFloat(ACCURACY.pos,accuracy);
	}

	public long getTime() {
		return getLong(TIME);
	}

	public String toString()
	{
		return String.format("GpsLocationRow(id=%d,timeMs=%20s,latm=%20d,lonm=%20d,timeSec=%d,accuracy=%8.3f)"
//				+",timeZone=%d)",
				,id,
				GTG.sdf.format(getLong(TIME)),
				getInt(LATM),
				getInt(LONM),
				getLong(TIME)/1000,
				getFloat(ACCURACY)
//				,getByte(TIMEZONE)
				);
	}

	public String toString(long relTime, int latm1, int lonm1) {
		return String.format("GpsLocationRow(id=%8d,              t=%8d, latm=%8d, lonm=%8d)",id,
				getLong(TIME) - relTime,
				getInt(LATM)-latm1, getInt(LONM) - lonm1);
		
	}

	@Override
	public void decryptRow(int userDataKeyFk, byte[] encryptedData) {
		super.decryptRow(userDataKeyFk, encryptedData);

		//data made prior to android version 1.1.17 didn't include accuracy information, so we
		//add it if it isn't present
		if (data2.length < DATA_LENGTH) {
			byte[] data2Copy = new byte[DATA_LENGTH];
			System.arraycopy(data2, 0, data2Copy, 0, data2.length);
			setFloat(ACCURACY.pos, 0.0f);
			data2 = data2Copy;
		} else {
			float accuracy = getFloat(ACCURACY);
			if (accuracy < 1f || accuracy > 1000000f) //TODO 3, for some reason the old gps rows without this columns would
				//have extra data appended to them, so we have to check for a sane value rather
				//than just looking for 0.0. On my phone at least, the value that the accuracy
				//is set to is 0x8080808 which translates to a value near zero.
				//Since this data isn't so important, and a fix would be somewhat difficult, I
				//am just using this hack here.
				setFloat(ACCURACY.pos, 0f);
		}
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

	public float getAccuracy() {
		return getFloat(ACCURACY);
	}
}
