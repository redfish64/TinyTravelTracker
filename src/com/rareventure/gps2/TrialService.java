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

import java.util.Map;

import org.acra.ACRA;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class TrialService extends Service
{
    private static final String TAG = "TrialPremiumService";
	

    private final ITrialService.Stub mBinder = new ITrialService.Stub() {
		@Override
		public Map giveMePreferences() throws RemoteException {
			//note that giveMePreferences and transferDbAndCache are separate steps, so that
			//the premium app can save the preferences between getting the files. Otherwise
			//if a crash occurs, we may have transferred the files but not the shared prefs
			//(which includes the private key)
			
			SharedPreferences sp = TrialService.this.getSharedPreferences(GTG.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
			
			return sp.getAll();
		}
		
		@Override
		public void notifyReplaced() throws RemoteException {
			//if the premium version is ever uninstalled, we will want to start over in a fresh state if the user
			//opens us again
			GTG.prefs.initialSetupCompleted = false;
			
			GTG.prefSet.saveSharedPrefs(TrialService.this);
			
		}
		
		@Override
		public void shutdown() throws RemoteException {
			//get the cache creator to pause itself
			GTG.ccRwtm.registerReadingThread();
			
			if(GTG.service != null)
			{
				//we explicity turn off the notification, because we are going to do hard
				//exit. Otherwise it will remain there and there will be two frogs
				// when we use this to swtich from trial to premium
				GTG.service.turnOffNotification();
				GTG.service.shutdown();
			}
			
			//close the database
			if(GTG.db != null)
				GTG.db.close();
			
			//we don't worry about the cache because it'll repair itself and it doesn't have
			//many points, so it should happen quickly
			
			//just do a hard exit, We don't have to worry about the gps service restarting
			//because if premium is installed, it will shut itself down immediately
			GTG.killSelf();
		}

		@Override
		public String getExtFileDir() throws RemoteException {
			return GTG.getExternalStorageDirectory().toString();
		}
    };
    
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return Service.START_NOT_STICKY;
	}
	
	

    	
	@Override
	public void onCreate() {
		super.onCreate();
		
		GTG.initRwtm.registerWritingThread();
		try {

			GTG.requireInitialSetup(this, true);
			GTG.requirePrefsLoaded(this);
	
	
			if (!GTG.requireSdcardPresent(this)) {
				Log.e(GTG.TAG,"sdcard not present when moving trial to premium");
				stopSelf();
				return;
			}
	
			if (!GTG.requireSystemInstalled(this)) {
				Log.e(GTG.TAG,"system not installed when moving trial to premium");
				stopSelf();
				return;
			}
		}
		finally {
			GTG.initRwtm.unregisterWritingThread();
		}

	}




	@Override
    public void onDestroy() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

 
}

