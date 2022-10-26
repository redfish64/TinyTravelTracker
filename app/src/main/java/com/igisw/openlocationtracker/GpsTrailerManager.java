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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.wifi.ScanResult;
import android.os.Looper;

import com.rareventure.android.DbUtil;
import com.rareventure.android.TestUtil;
import com.rareventure.android.ThreadedBufferedOutputStream;
import com.rareventure.android.WriteConstants;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class GpsTrailerManager implements GpsReader.GpsProcessor, WirelessAdapter.Listener 
{
	
	private GpsReader gpsReader;
	private GpsTrailerDataHandler dataHandler;
	private GpsTrailerGpsStrategy strategy;
	private ProcessThread processThread;
	private DataOutputStream os;
//	private CompassDirectionCalculator compassDirectionCalculator;
	private WirelessAdapter wirelessAdapter;
	
	public GpsTrailerManager(File dataFile, Context ctx, String tag, Looper looper) throws FileNotFoundException
	{
		//TODO 4: deal with debugging crap
		TestUtil.modesToSuppress.add(WriteConstants.MODE_WRITE_SENSOR_DATA);
		
		//for saving data to a test file to replay later
		if(dataFile != null)
		{
			//the threaded buffer output stream guarantees the write won't block 
			os = new DataOutputStream(new ThreadedBufferedOutputStream(2048,1024,
					new FileOutputStream(dataFile)));
			//os = new DataOutputStream(new FileOutputStream(dataFile));
		}
		
		gpsReader = new GpsReader(os, ctx, this, tag, looper);
		
//		compassDirectionCalculator = new CompassDirectionCalculator();
		
		dataHandler = new GpsTrailerDataHandler();
		
		IntentTimer intentTimer = new IntentTimer(ctx, GpsTrailerReceiver.class);
		
		//TODO 3: it sucks passing os everywhere, and catching io exceptions and specifying a mode for everything. wah!
		strategy = new GpsTrailerGpsStrategy(os, gpsReader, intentTimer);
		
		processThread = new ProcessThread(ctx, tag, gpsReader);
		
		//co because I think this uses a lot of batteries
		//wirelessAdapter = new WirelessAdapter(ctx, this);
	}
	
	public void start()
	{
		strategy.start();
		processThread.start();
	}

	@Override
	public void processGpsData(double lon, double lat, double alt, long time) 
	{
		strategy.gotReading();
		dataHandler.processGpsData((int)Math.round(lon*1000000), (int)Math.round(lat*1000000), alt, time);
		
		
		//notify the cache creator that we got another point
		GpsTrailerCacheCreator localCacheCreator = GTG.cacheCreator;
		
		if(localCacheCreator != null)
		{
			synchronized(localCacheCreator)
			{
				localCacheCreator.notify();
			}
		}
				
		if(wirelessAdapter != null)
			wirelessAdapter.doScan();
	}

	public void shutdown()
	{
		strategy.shutdown();
		processThread.notifyShutdown();
		
		processThread.waitUntilShutdown();
		
		if(wirelessAdapter != null)
			wirelessAdapter.shutdown();
		
		if(os != null)
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	@Override
	public void notifyWifiScan(long timeMs, List<ScanResult> scanResults) {
		SQLiteDatabase db = GTG.db;
		
		SQLiteStatement stmt = DbUtil.createOrGetStatement(db,
			"insert into WIFI_SCAN_RESULT (time,ssid,bssid,capabilities,level,frequency) values (?,?,?,?,?,?)");
		
		//it's a little faster in a transaction
		
		db.beginTransaction();
		try {
			for(ScanResult sr : scanResults)
			{
				stmt.bindLong(1, timeMs);
				stmt.bindString(2, sr.SSID);
				stmt.bindString(3, sr.BSSID);
				stmt.bindString(4, sr.capabilities);
				stmt.bindLong(5, sr.level);
				stmt.bindLong(6, sr.frequency);
				
				stmt.execute();
			}
			
			db.setTransactionSuccessful();
		}
		finally {
			if(db.inTransaction())
					db.endTransaction();
		}
	}

	public void notifyWoken() {
		strategy.notifyWoken();
	}
}
