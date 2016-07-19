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

import org.acra.ErrorReporter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteCallbackList;
import android.provider.Settings;
import android.util.Log;

import com.rareventure.gps2.GTG.Requirement;
import com.rareventure.gps2.IGpsTrailerService;
import com.rareventure.gps2.IGpsTrailerServiceCallback;
import com.rareventure.gps2.R;
import com.rareventure.android.AndroidPreferenceSet;
import com.rareventure.android.Util;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.gps2.GTG.GTGEvent;
import com.rareventure.gps2.GTG.GTGEventListener;
import com.rareventure.gps2.GTG.SetupState;
import com.rareventure.gps2.database.GpsLocationCache;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.gps2.reviewer.SettingsActivity;
import com.rareventure.gps2.reviewer.map.osm.RemoteLoader;
import com.rareventure.gps2.reviewer.wizard.WelcomePage;

public class GpsTrailerService extends Service {
	private static final String TAG = "GpsTrailerService";

	final RemoteCallbackList<IGpsTrailerServiceCallback> mCallbacks = new RemoteCallbackList<IGpsTrailerServiceCallback>();

	/**
	 * The IRemoteInterface is defined through IDL
	 */
	private final IGpsTrailerService.Stub mBinder = new IGpsTrailerService.Stub() {
		public void registerCallback(IGpsTrailerServiceCallback cb) {
			if (cb != null)
				mCallbacks.register(cb);
		}

		public void unregisterCallback(IGpsTrailerServiceCallback cb) {
			if (cb != null)
				mCallbacks.unregister(cb);
		}
	};

	private Handler mHandler;

	public class LocalBinder extends Binder {
		//   	private static final String DESCRIPTOR = "com.rareventure.tapmusic.ITapServiceCallback";

		public LocalBinder() {
			//    	this.attachInterface(this, DESCRIPTOR);
		}

		GpsTrailerService getService() {
			return GpsTrailerService.this;
		}
	}

	private GpsTrailerManager gpsManager;

	private BroadcastReceiver batteryReceiver;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		/* ttt_installer:remove_line */Log.d(GTG.TAG, "Starting on intent " + intent);
		//if we were already running and had a gpsManager, notify that we woke up
		if (gpsManager != null)
			gpsManager.notifyWoken();

		mHandler = new Handler();

		GTG.service = this;

		//we want our service to restart if stopped because of memory constraints or whatever else
		return Service.START_STICKY;
	}

	public GTGEventListener gtgEventListener = new GTGEventListener() {

		@Override
		public boolean onGTGEvent(GTGEvent event) {

			if (event == GTGEvent.ERROR_GPS_DISABLED) {
				updateNotification(FLAG_GPS_ENABLED, false);
				stopSelf();
			}
			if (event == GTGEvent.ERROR_SDCARD_NOT_MOUNTED) {
				updateNotification(FLAG_ERROR_SDCARD_NOT_MOUNTED, true);
				stopSelf();
			}
			else if (event == GTGEvent.ERROR_LOW_FREE_SPACE) {
				updateNotification(FLAG_ERROR_LOW_FREE_SPACE, true);
				stopSelf();
			}
			else if (event == GTGEvent.ERROR_SERVICE_INTERNAL_ERROR) {
				updateNotification(FLAG_ERROR_INTERNAL, true);
				stopSelf();
			}
			else if (event == GTGEvent.ERROR_LOW_BATTERY) {
				updateNotification(FLAG_BATTERY_LOW, true);
				stopSelf();
			}
			else if (event == GTGEvent.TRIAL_PERIOD_EXPIRED) {
				updateNotification(FLAG_TRIAL_EXPIRED, true);
				stopSelf();
			}
			else if (event == GTGEvent.DOING_RESTORE) {
				updateNotification(FLAG_IN_RESTORE, true);
				stopSelf();
			}
			else if (event == GTGEvent.ERROR_UNLICENSED) {
				//co: we initially don't want to do anything if the user is unlicensed,
				// just find out how much piracy is a problem
				// we don't show a notification if we quit due to the app being unlicensed
				//	    		stopSelf();
			}

			return false;
		}

		@Override
		public void offGTGEvent(GTGEvent event) {
			if (event == GTGEvent.ERROR_GPS_DISABLED) {
				updateNotification(FLAG_GPS_ENABLED, true);
			}
		}

	};

	private static final int FLAG_GPS_ENABLED = 1;
	private static final int FLAG_BATTERY_LOW = 2;
	private static final int FLAG_FINISHED_STARTUP = 4;
	private static final int FLAG_ERROR_INTERNAL = 8;
	private static final int FLAG_ERROR_SDCARD_NOT_MOUNTED = 16;
	private static final int FLAG_ERROR_LOW_FREE_SPACE = 32;
	private static final int FLAG_COLLECT_ENABLED = 64;
	private static final int FLAG_ERROR_DB_PROBLEM = 128;
	private static final int FLAG_TRIAL_EXPIRED = 256;
	private static final int FLAG_IN_RESTORE = 512;


	private int notificationFlags = 0;

	private static class NotificationSetting {
		int iconId;
		int msgId;
		boolean sticky;
		int onFlags, offFlags;
		Intent intent;
		boolean isOngoing;

		public NotificationSetting(int iconId, int msgId, boolean sticky,
				int onFlags, int offFlags, Intent intent, boolean isOngoing) {
			super();
			this.iconId = iconId;
			this.msgId = msgId;
			this.sticky = sticky;
			this.onFlags = onFlags;
			this.offFlags = offFlags;
			this.intent = intent;
			this.isOngoing = isOngoing;
		}

		/**
		 * @return true if all of the this.onFlags are on and all of the this.offFlags are off
		 */
		public boolean matches(int notificationFlags) {
			return ((onFlags & notificationFlags) == onFlags)
					&& ((Integer.MAX_VALUE - offFlags) | notificationFlags) == (Integer.MAX_VALUE - offFlags);
		}

		@Override
		public String toString() {
			return "NotificationSetting [iconId=" + iconId + ", msgId=" + msgId
					+ ", sticky=" + sticky + ", onFlags=" + onFlags
					+ ", offFlags=" + offFlags + "]";
		}
	}

	/**
	 * Note, ordered by priority
	 */
	public static NotificationSetting[] NOTIFICATIONS = new NotificationSetting[] {
			//note, in the following, if FLAG_COLLECT_ENABLED is *off*, we
			//shut off the notification icon
			new NotificationSetting(-1, -1, false, 0, FLAG_COLLECT_ENABLED,
					null, false),
			new NotificationSetting(-1, -1, false, FLAG_IN_RESTORE, 0,
					null, false),
			new NotificationSetting(-1, -1, false, FLAG_TRIAL_EXPIRED, 0, null,
											false),
			new NotificationSetting(R.drawable.red_error,
					R.string.service_error_internal_error, true,
					FLAG_ERROR_INTERNAL, 0, null, false),

			new NotificationSetting(R.drawable.red_error,
					R.string.service_error_sdcard_not_mounted, true,
					FLAG_ERROR_SDCARD_NOT_MOUNTED, 0, null, false),

			new NotificationSetting(R.drawable.red_error,
					R.string.service_error_db_problem, true,
					FLAG_ERROR_DB_PROBLEM, 0, null, false),

			new NotificationSetting(R.drawable.red_error,
					R.string.service_error_low_free_space, true,
					FLAG_ERROR_LOW_FREE_SPACE, 0, null, false),

			new NotificationSetting(-1, -1, false, FLAG_BATTERY_LOW, 0, null,
							false),

			new NotificationSetting(R.drawable.red_error,
					R.string.service_gps_not_enabled, true, 0,
					FLAG_GPS_ENABLED, new Intent(
							Settings.ACTION_LOCATION_SOURCE_SETTINGS)

					, false),

			new NotificationSetting(R.drawable.green, R.string.service_active,
					false, FLAG_GPS_ENABLED | FLAG_FINISHED_STARTUP
							| FLAG_COLLECT_ENABLED, 0, null, true) };

	private NotificationSetting currentNotificationSetting = null;

	/**
	 * Updates the notification based on the current status of the system
	 */
	private void updateNotification(int flags, boolean isOn) {
		if (isOn)
			notificationFlags |= flags;
		else
			notificationFlags &= (Integer.MAX_VALUE ^ flags);

		NotificationSetting oldNotSetting = currentNotificationSetting;

		//choose first matching notification setting
		for (NotificationSetting ns : NOTIFICATIONS) {
			if (ns.matches(notificationFlags)) {
				currentNotificationSetting = ns;
				break;
			}
		}

		/* ttt_installer:remove_line */Log.d(GTG.TAG, "Flags is " + notificationFlags + ", current notset is "	+ currentNotificationSetting);

		if (currentNotificationSetting != oldNotSetting) {
			showCurrentNotification();
		}
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "GPS Service Startup");
		
		GTG.addGTGEventListener(gtgEventListener);

		//reset all flags 
		updateNotification(Integer.MAX_VALUE, false);
		
		updateNotification(FLAG_GPS_ENABLED, true);

		//read isCollectData first from shared prefs and if its false, quit immediately
		//we don't want to notify the user that we ran for any reason if this is false
		// (for example the db was corrupted)
		GTG.prefSet.loadAndroidPreferencesFromSharedPrefs(this);
		//co: we initially don't want to do anything if the user is unlicensed,
		// just find out how much piracy is a problem
		if (!GTG.prefs.isCollectData ) //|| GTGEvent.ERROR_UNLICENSED.isOn)
		{
			turnOffNotification();
			stopSelf();
			return;
		}

		GTG.initRwtm.registerWritingThread();
		try {

			try {
				GTG.requireInitialSetup(this, true);

				GTG.requirePrefsLoaded(this);
				
				if(!GTG.requireNotInRestore())
				{
					updateNotification(FLAG_IN_RESTORE, true);
					stopSelf();

					return;
				}

				Intent i = GTG.requireNotTrialWhenPremiumIsAvailable(this);

				if (i != null) {
					turnOffNotification();
					stopSelf();

					return;
				}

				if (!GTG.requireNotTrialExpired()) {
					updateNotification(FLAG_TRIAL_EXPIRED, true);
					stopSelf();

					return;
				}

				if (!GTG.requireSdcardPresent(this)) {
					updateNotification(FLAG_ERROR_SDCARD_NOT_MOUNTED, true);
					stopSelf();

					return;
				}

				if (!GTG.requireSystemInstalled(this)) {
					stopSelf();
					return;
				}

				int status = GTG.requireDbReady();

				if (GTG.requireDbReady() != GTG.REQUIRE_DB_READY_OK) {
					updateNotification(FLAG_ERROR_DB_PROBLEM, true);
					stopSelf();

					return;
				}

				GTG.requireEncrypt();
			}
			finally {
				GTG.initRwtm.unregisterWritingThread();
			}

			updateNotification(FLAG_COLLECT_ENABLED, true);

			//co because we don't want gps2data files in the root directory of sdcard. they take up too much space
			//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			//        File dataFile = new File(root, "gps2data"+sdf.format(new Date())+".txt");

			//			gpsManager = new GpsTrailerManager(dataFile, this, TAG, this.getMainLooper());
			gpsManager = new GpsTrailerManager(null, this, TAG,
					this.getMainLooper());

			//note that there is no way to receive the current status of the battery
			//However, the battery receiver will get a notification as soon as its registered
			// to the current status of the battery
			batteryReceiver = new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					float batteryLeft = ((float) intent.getIntExtra(
							BatteryManager.EXTRA_LEVEL, -1))
							/ intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

					/* ttt_installer:remove_line */Log.d(GTG.TAG, "Received battery info, batteryLeft: "+ batteryLeft);

					int status = intent.getIntExtra("status",
							BatteryManager.BATTERY_STATUS_UNKNOWN);

					if (batteryLeft < GTG.prefs.minBatteryPerc
							&& (status == BatteryManager.BATTERY_STATUS_DISCHARGING || status == BatteryManager.BATTERY_STATUS_UNKNOWN)) {
						updateNotification(FLAG_BATTERY_LOW, true);
						GTG.alert(GTGEvent.ERROR_LOW_BATTERY);
					}
					else
						updateNotification(FLAG_BATTERY_LOW, false);

					//we wait until now to finish startup 
					//because otherwise if the battery is low
					//and we are awoken for some other event, we would otherwise show our icon for
					//a brief period before we get the battery is low event
					updateNotification(FLAG_FINISHED_STARTUP, true);

					//just a hack, a reasonable place to check if the trial version has expired
					checkTrialExpired();
				}
			};

			IntentFilter batteryLevelFilter = new IntentFilter(
					Intent.ACTION_BATTERY_CHANGED);
			registerReceiver(batteryReceiver, batteryLevelFilter);

			gpsManager.start();

		}
		catch (Exception e) {
			shutdownWithException(e);
			ErrorReporter.getInstance().handleException(e);
		}
	}

	private boolean checkTrialExpired() {
		if (GTG.calcDaysBeforeTrialExpired() == 0) {
			Requirement.NOT_TRIAL_EXPIRED.reset();
			//this will stop self
			GTG.alert(GTGEvent.TRIAL_PERIOD_EXPIRED);
			return true;
		}
		return false;

	}

	private void shutdownWithException(Exception e) {
		Log.e(GTG.TAG, "Exception running gps service", e);
		updateNotification(FLAG_ERROR_INTERNAL, true);
		stopSelf();
	}

	/**
	 * Show a notification while this service is running.
	 * @param intent 
	 */
	private void showCurrentNotification() {
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		if (currentNotificationSetting.msgId == -1
				&& currentNotificationSetting.iconId == -1) {
			nm.cancel(GTG.FROG_NOTIFICATION_ID);
			return;
		}

		CharSequence text = getText(currentNotificationSetting.msgId);

		// Set the icon, scrolling text and timestamp
		Notification.Builder builder = new Notification.Builder(this);

		builder.setSmallIcon(currentNotificationSetting.iconId);
		builder.setOngoing(currentNotificationSetting.isOngoing);
		builder.setAutoCancel(!currentNotificationSetting.isOngoing);
		builder.setTicker(text);

		// The PendingIntent to launch our activity if the user selects this notification
		// TODO 2.5 make settings lite for notification bar only. Set it's task affinity
		// different from the main app so hitting back doesn't cause "enter password" to be asked
		Intent intent = new Intent(this, SettingsActivity.class);
		PendingIntent contentIntent = PendingIntent
				.getActivity(
						this,
						0,
						currentNotificationSetting.intent != null ? currentNotificationSetting.intent
								: intent, 
								PendingIntent.FLAG_UPDATE_CURRENT);

		builder.setContentIntent(contentIntent);

		Notification notification = builder.getNotification();

		nm.notify(GTG.FROG_NOTIFICATION_ID, notification);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "GPS Service Shutdown");

		GTG.removeGTGEventListener(gtgEventListener);

		if (currentNotificationSetting != null
				&& !currentNotificationSetting.sticky) {
			NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

			nm.cancel(GTG.FROG_NOTIFICATION_ID);
		}

		if (gpsManager != null)
			gpsManager.shutdown();

		if (batteryReceiver != null)
			unregisterReceiver(batteryReceiver);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	public void turnOffNotification()
	{
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(GTG.FROG_NOTIFICATION_ID);
		
	}

	public void shutdown() {

		Util.runOnHandlerSynchronously(mHandler, new Runnable() {

			@Override
			public void run() {
				
				stopSelf();

			}
		});

	}

}
