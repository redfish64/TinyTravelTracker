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

import java.io.IOException;
import java.io.SyncFailedException;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.rareventure.android.DbUtil;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.UserLocationRow;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.MediaLocTime;
import com.rareventure.gps2.database.cache.TimeTree;

public final class GpsTrailerDb {
    // This class cannot be instantiated
    private GpsTrailerDb() {}
    
    public static final int DATABASE_VERSION = 18;
	public static final int GPS_LOCATION_TIME_FLAG_EST_SPEED = 1;
	public static final int GPS_LOCATION_TIME_TYPE_GPS = 1;

	/**
	 * Drops and recreates encryption tables and tables relating to encryption
	 * @param db
	 */
	public static void dropAndRecreateEncryptedTables(SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS USER_DATA_KEY");
		db.execSQL("CREATE TABLE USER_DATA_KEY (_id integer primary key, APP_ID INTEGER NOT NULL,encrypted_key blob);");
		
		for(String table : new String [] { GpsLocationRow.TABLE_NAME, UserLocationRow.TABLE_NAME,
				TimeZoneTimeRow.TABLE_NAME})
		{
			dropAndRecreateEncryptedTable(db, table);
		}
	}
	
	public static void dropAndRecreateEncryptedTable(SQLiteDatabase db, String table)
	{
		Log.d(GTG.TAG,"dropping table "+table+"...");
		db.execSQL("drop table if exists "+table);
		Log.d(GTG.TAG, "done dropping "+table+" table...");
		db.execSQL("create table "+table+" (" +
        		"_ID INTEGER PRIMARY KEY," +
        		"USER_DATA_KEY_FK INTEGER NOT NULL, " +
        		"DATA BLOB NOT NULL)" );
	}
	
	//TODO 3: investigate faster file formats. Since we're never deleting rows, we can get away with a flat file I think.. note that sqlite3 does
	// not have performance improvements for fixed witdth columns (according to what I read)

	public static void initializeNewDb(SQLiteDatabase db) {
		//very important.. without this we'll get locking errors all over the place
		// and cache population while cache reading is going on won't work
		//this weird bit is to get around an android bug
		//co: because we now use timmy tables and evo is acting funny with this
//		Cursor c1 = db.rawQuery("PRAGMA journal_mode=wal", null); c1.moveToNext(); c1.close();

		if(DbUtil.runQueryWithStrings(db, "select exists (SELECT 1 FROM sqlite_master WHERE " +
				"type='table' AND upper(name)='ANDROID_METADATA')") != 1)
			db.execSQL("CREATE TABLE android_metadata (locale TEXT);");

		dropAndRecreateEncryptedTables(db);
		
		//co: these tables were used as an experiment... might need them later
		//CREATE TABLE WIFI_SCAN_RESULT (_ID INTEGER PRIMARY KEY,TIME LONG NOT NULL,SSID TEXT NOT NULL,BSSID TEXT NOT NULL,CAPABILITIES TEXT NOT NULL,LEVEL INTEGER NOT NULL,FREQUENCY INTEGER NOT NULL);
		//CREATE TABLE rest (_id INTEGER PRIMARY KEY,begin_time LONG NOT NULL,end_time LONG NOT NULL);
		//CREATE TABLE compass (_id INTEGER PRIMARY KEY,begin_time LONG NOT NULL,end_time LONG NOT NULL,direction FLOAT NOT NULL);

		db.setVersion(DATABASE_VERSION);
	}

	public static void upgradeDbIfNecessary(SQLiteDatabase db) {
		if(db.getVersion() < 14)
		{
			Log.d(GTG.TAG,"upgrading db to version 14");
			dropAndRecreateEncryptedTable(db, MediaLocTime.TABLE_NAME);
			
			db.setVersion(14);
		}
		else if(db.getVersion() == 14)
		{
			Log.d(GTG.TAG,"upgrading db to version 15");
			Log.d(GTG.TAG,"dropping table media_loc_time...");
			db.execSQL("drop table if exists media_loc_time");
			
			db.setVersion(15);
		}
		else if(db.getVersion() == 15)
		{
			Log.d(GTG.TAG,"upgrading db to version 16");
			Log.d(GTG.TAG, "Removing cache");
			GpsTrailerDbProvider.deleteUnopenedCache();
			
			db.setVersion(16);
		}
		else if(db.getVersion() == 16)
		{
			Log.d(GTG.TAG,"upgrading db to version 17");
			Log.d(GTG.TAG, "creating time zone time table");
			dropAndRecreateEncryptedTable(db, TimeZoneTimeRow.TABLE_NAME);
			
			db.setVersion(17);
		}
		else if(db.getVersion() == 17)
		{
			Log.d(GTG.TAG,"upgrading db to version 18");
			Log.d(GTG.TAG, "removing android_prefs table");
			db.execSQL("drop table if exists android_prefs");
			
			db.setVersion(18);
		}
	}

	public static void moveToBakAndRecreateTable(String tableName) {
		GTG.db.execSQL("DROP TABLE IF EXISTS "+tableName+"_BAK");
		
		if(DbUtil.runQueryWithStrings(GTG.db, "select exists (SELECT 1 FROM sqlite_master WHERE type='table' AND upper(name)=?)", tableName.toUpperCase()) == 1)
		{
			GTG.db.execSQL("ALTER TABLE "+tableName+" RENAME TO "+tableName+"_BAK");
		}
		
		GpsTrailerDb.dropAndRecreateEncryptedTable(GTG.db, tableName);

	}

	public static void dropTable(String tableName) {
		GTG.db.execSQL("DROP TABLE IF EXISTS "+tableName);
	}

	public static void dropBakTable(String tableName) {
		GTG.db.execSQL("DROP TABLE IF EXISTS "+tableName+"_BAK");
	}

	public static void replaceTableWithBakIfNecessary(SQLiteDatabase db,
			String tableName) {
		if(
				DbUtil.runQueryWithStrings(db, "select exists (SELECT 1 FROM sqlite_master WHERE type='table' AND upper(name)=?)", tableName.toUpperCase()) != 1
			|| 	DbUtil.runQueryWithStrings(db, "select exists (SELECT 1 FROM "+tableName+" LIMIT 1)") != 1)
		{
			if(DbUtil.runQueryWithStrings(db, "select exists (SELECT 1 FROM sqlite_master WHERE type='table' AND upper(name)=?)", tableName.toUpperCase()+"_BAK") == 1)
			{
				db.execSQL("DROP TABLE IF EXISTS "+tableName);
				db.execSQL("ALTER TABLE "+tableName+"_BAK RENAME TO "+tableName);
			}
		}
		db.execSQL("DROP TABLE IF EXISTS "+tableName+"_BAK");
		
	}
	
    
}
