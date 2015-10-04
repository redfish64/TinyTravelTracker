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

import java.io.File;
import java.io.IOException;
import java.io.SyncFailedException;

import com.rareventure.android.DbUtil;
import com.rareventure.android.database.timmy.RollBackTimmyTable;
import com.rareventure.android.database.timmy.TimmyDatabase;
import com.rareventure.android.database.timmy.TimmyTable;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.MediaLocTime;
import com.rareventure.gps2.database.cache.TimeTree;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

//TODO 3: The whole database stuff is kind of crappy. Rewrite for greater code efficiency, or even use templates?
public class GpsTrailerDbProvider {
	private static final String TAG = "GpsTrailerDbProvider";

	public static final String DB_FILE_NAME = "gps.db3";

	public static boolean isDatabasePresent()
	{
		return getDbFile(false).isFile();
	}
	
	public static File getDbFile(boolean isTmpFile)
	{
		return new File(GTG.getExternalStorageDirectory()+"/"+DB_FILE_NAME+(isTmpFile ? ".tmp" : ""));
	}
	
	public static SQLiteDatabase createNewDbFile(File dbFile)
	{
		if(GTG.getExternalStorageDirectory() == null)
			throw new IllegalStateException("why is external storage directory null?");
			
		if (!GTG.getExternalStorageDirectory().exists())
		{
			throw new IllegalStateException("directory doesn't exist");
		}
		
		deleteDbFiles(dbFile);

		SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.toString(), null,
				SQLiteDatabase.OPEN_READWRITE
						+ SQLiteDatabase.CREATE_IF_NECESSARY);
		GpsTrailerDb.initializeNewDb(db);		
		return db;
	}

	/**
	 * Deletes database and related journal file
	 */
	public static void deleteDbFiles(File dbFile) {
		dbFile.delete();
		
		new File(dbFile+"-journal").delete();
	}

	public static SQLiteDatabase openDatabase(File dbFile) {
		Log.d(GTG.TAG, "Using db " + dbFile);

		SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.toString(), null,
				SQLiteDatabase.OPEN_READWRITE);
		
		GpsTrailerDb.upgradeDbIfNecessary(db);
		
		//cleanup after failed restore if necessary
		
		//if there is no gps_location_row or it has zero rows
		GpsTrailerDb.replaceTableWithBakIfNecessary(db, GpsLocationRow.TABLE_NAME);
		GpsTrailerDb.replaceTableWithBakIfNecessary(db, TimeZoneTimeRow.TABLE_NAME);
		
		return db;
	}

	public static final String APCACHE_TIMMY_TABLE_FILENAME = "AreaPanel.tt";


	public static final String TIME_TREE_TIMMY_TABLE_FILENAME = "TimeTree.tt";

	public static final String MEDIA_LOC_TIME_TIMMY_TABLE_FILENAME = "MediaLocTime.tt";

	public static final String MEDIA_LOC_TIME_PLUS_TIMMY_TABLE_FILENAME = "MediaLocTimePlus.tt";

	public static final String TIMMY_DB_FILENAME = "cache.td";
	
	public static TimmyDatabase createTimmyDb() throws SyncFailedException, IOException {
		TimmyDatabase timmyDb = new TimmyDatabase(GTG.getExternalStorageDirectory()+"/"+TIMMY_DB_FILENAME);
		timmyDb
		.addRollBackTimmyTable(
				GTG.getExternalStorageDirectory()+ "/"
						+ APCACHE_TIMMY_TABLE_FILENAME,
				GTG.crypt.crypt
						.getNumOutputBytesForEncryption(AreaPanel.DATA_LENGTH));
		timmyDb
				.addRollBackTimmyTable(
						GTG.getExternalStorageDirectory()+ "/"
								+ TIME_TREE_TIMMY_TABLE_FILENAME,
								GTG.crypt.crypt
								.getNumOutputBytesForEncryption(TimeTree.DATA_LENGTH));
		timmyDb
		.addTimmyTable(
				GTG.getExternalStorageDirectory()+ "/"
						+ MEDIA_LOC_TIME_TIMMY_TABLE_FILENAME,
						GTG.crypt.crypt
						.getNumOutputBytesForEncryption(MediaLocTime.DATA_LENGTH));
//		timmyDb
//		.addTimmyTable(
//				context.getExternalFilesDir(null) + "/"
//						+ MEDIA_LOC_TIME_PLUS_TIMMY_TABLE_FILENAME,
//				crypt.crypt
//						.getNumOutputBytesForEncryption(MediaLocTime.DATA_LENGTH)
//						+ EncryptedRow.EXTRA_BYTES_FOR_USER_DATA_KEY);
		return timmyDb;
	}

	public static void deleteUnopenedCache() {
		//TODO 2.5 this is weird to have here!!
		for(String file : new String [] { APCACHE_TIMMY_TABLE_FILENAME, TIME_TREE_TIMMY_TABLE_FILENAME,
				MEDIA_LOC_TIME_TIMMY_TABLE_FILENAME, MEDIA_LOC_TIME_PLUS_TIMMY_TABLE_FILENAME, TIMMY_DB_FILENAME})
		{
			file = GTG.getExternalStorageDirectory()+ "/" + file;
			new File(file).delete();
			new File(file+TimmyTable.ROLLFORWARD_EXTENSION).delete();
			new File(file+RollBackTimmyTable.ROLLBACK_EXTENSION).delete();
		}
	}

	// TODO 4: Insert into rest immediately when we have a stop, and update it
	// later when we move, so we have real timeness
	// in the display of rest periods.. Is this even going to work? If they take
	// it out of their pocket, it will shake.. and
	// in the event of a system crash, we'll need to clean up this handiwork.

}
