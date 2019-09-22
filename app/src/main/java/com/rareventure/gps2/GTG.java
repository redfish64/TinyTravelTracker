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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import org.acra.ACRA;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.rareventure.android.AndroidPreferenceSet;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.android.Util;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.android.database.timmy.RollBackTimmyDatastoreAccessor;
import com.rareventure.android.database.timmy.TimmyDatabase;
import com.rareventure.android.database.timmy.TimmyTable;
import com.rareventure.gps2.database.GpsLocationCache;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.gps2.database.TimeZoneTimeSet;
import com.rareventure.gps2.database.UserLocationCache;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.AreaPanelCache;
import com.rareventure.gps2.database.cache.TimeTree;
import com.rareventure.gps2.database.cache.TimeTreeCache;
import com.rareventure.gps2.database.cachecreator.GpsTrailerCacheCreator;
import com.rareventure.gps2.reviewer.map.MediaLocTimeMap;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;
import com.rareventure.gps2.reviewer.map.OsmMapView;
import com.rareventure.util.BackgroundRunner;
import com.rareventure.util.ReadWriteThreadManager;

/**
 * Gps Trailer Globals... The reason we stick everything
 * here is 1) to make it easy to find, and 2) we might
 * have generic things like AndroidPreferenceSet which we
 * need to stick somewhere. 3) We have different apps using
 * the same globals but initialize them in different parts of
 * code
 */
public class GTG {
	//TODO 2 put autozoom and gps buttons behind location/time window
	//TODO 2 create blog and make a lot of interesting articles... get a following, and make an article on ttt, maybe
	// even what if you build it and they never come?
	//TODO 2.1 add wifi back
	//TODO 2.1 add magnetic readings
	//TODO 2.1 add visualization of altitude, magnetic readings, etc.
	//TODO 2 add file locations in manual
	//TODO 2 after premium is install and moved over, add a little dialog indicating that the trial version can be removed
	//TODO 2 add version number in about
	//TODO 2.1 sas isn't updated when cache is updated and it includes the latest time
	//TODO 2.1 when a line is really long and zoomed in really closely, antialiasing doesn't seem to work (at least on google nexus)
	//TODO 4 sometimes after taking pictures immediately, they refuse to show up in TTT. It seems that id's are being reused. They won't be reused unless the 
	// *last* id was deleted. Maybe use date taken instead.. can't reproduce????
	//TODO 2.5: work with phones without google maps? Look at ApiDemos manifest file for how to install without maps library
	//TODO 2.5: how to deal with errors because of device differences?
	//TODO 4: Maybe allow user to click on and label current position if available
	//TODO 2.5: highlight current position (we can do this by looking at last gps row, and if it was added within
	// a few minutes we'll consider it the current pos)
	//TODO 2.5 get rid of validation for restore gpx????
	//TODO 2 where to put "(C) Rareventure LLC, All rights reserved"? In the manual, for instance? The about screen
	//TODO 2.5 make timeview oval drawer more accurate by drawing solid colors and empty spots
	//TODO 2.1 on most errors from cache, we should delete it as corrupted. This is incase there was some sort of powerfailure
	// which caused our code to fail, or the hardware failed. At least they can get into settings, probably and export the data
	//we should turn off cache creation in this case and notify user rather than just bombing
	//TODO 2.1 notify when time is not correct (ie points in the future) and that gps will be disabled until it is fixed
	//TODO 2.01 add pinch zooming and swipe to switch pictures in picture viewer maybe
	//TODO 2.5 rows created jsut as cache forms them are really big
	//TODO 3 make point location less "gridy" by looking at children locations
	//TODO 2.5 previous line from italy to argentina is being drawed for may 10th
	//TODO 2.5 when calculating paths, should not display any view nodes
	//TODO 2.5 maybe make the whole tree unknown when a path is calculated?
	//TODO 2.5 why doesn't the view nodes get recalcuated sometimes when a path is calcuated?
	//TODO 2.5 handle pictures wrt paths
	//TODO 2.5 put a number in areas 
	//TODO 2.5 try not using wake lock during gps tracking 
	//TODO 3 all videos sideloaded are at latest time
	//TODO 2.1 no tool tip for pressing on screen???
	//TODO 2.5 get rid of number scroller in restore
	//TODO 2.1 canceling during timmy journal rollback causes freeze 
	//TODO 3 collect gps data is slow when processing gps points are running
	//TODO 3 MyProgressDialog fails when flipping horizontally
	//TODO 2.1 either disable selecting areas or make them show distance (and maybe export gpx)
	//TODO 2.1 use bouncy castle for all, that way we are sure that it won't break when different
	// versions of java crypto are used
	
	//TODO 2.3 backup and restore. Backup and restore should use a common opensource format, maybe xml or something, or csv would be
	//the best I would think.It won't be password protected because that would be a pain for the user to enter a password 
	// every single time they want to backup. Its up to the user to secure at that point. Then we could import and export this data at will.
	
	//TODO 2.5 daylight wasting time shows 1 am forever when zoomed far in in timeview. See Nov 6, 2011 1am
	//TODO 2.5 make sure we can turn on premium without a user having to pay... we'd do this with our own
	// store app
	
	//TODO 2.5 possibly use time encoded in video filename to determine date time of file. remove all letters and look
	// for dates beginning with 20xx (indicating a year)
	
	
	//TODO 2.1 make trial invoke regular app if both are installed, don't want both accessing the db at the same time,
	// note that this work has been started but not finished. The big task here is shutting down the db when
	// we detect the premium version is installed. We need to do two things:
	//  1. when trial is started and premium is installed, launch premium
	//  2. when premium is installed and trial is running, shutdown trial before loading premium
	// These must be done to avoid db cache corruption 
	
	//TODO 2 Z make an explicit git version before release. Note the proguard mapping from class to obsfucated class
	// in proguard. Also store the whole translated tree, after ttt_install.pl, because the line numbers seem off
	
	//TODO 2.1 turn off get tasks permission
	
	//TODO 2 test everything: 
	//   battery low, 
	//   gps off, 
	//   sd card unmount,
	//   ttt server down
	//   select area
	//   gps service alerts
	//   different phones
	//   backup
	
	//TODO 2.2 put toast when the current location isn't known and button is pressed
	//TODO 2.1 setup a very sparse AreaPanel cache tile of just the first few tiles
	// to get the user going
	//TODO 2.1 pinch zoom and swipe in photo viewer
	//TODO 3 try to speed up point loading
	//TODO 2.2 use barber pole when still thinking for point location bar in timeview
	//TODO 2.1 use speed and direction of movement for location center
	//TODO 3 solve the problem where for a time gap, there is a visible dot
	// at a large x/y area panel, and it disappears when we zoom in somehow 
	//TODO 4 auto zoom when selecting time range by increasing size only, no panning?????
	//TODO 3 put a check in apcache to make sure that rwtm is working. Make it check
	//that anything accessing it while the writing thread is active is actually the
	//writing thread
	//TODO 2.2 prevent photos from changing size everytime we zoom in or zoom out
	//TODO 2.1 when going back to ttt, reset end time to latest gps time
	//TODO 4 only load points when plugged in into gtcache
	//TODO 2.1 option for darkening map so points stand out better?
	//TODO 2.1 facebook integration
	//TODO 4 slow down database point logging
	//TODO 2.1 bind to service so that we can notify it when 
	// we are show location enabled
	
	//TODO 3 tool tip when "+ sas" button is checked
	//TODO 9 feedback $1 off
	//TODO 2.1 see about making red frog appear immediately when gps is turned off, or at least
	// when another reading is attempted (rather than when phone is turned on)
	
	//TODO 2.1 smoozy web page
	
	//TODO 2.5: what if timmy files are corrupted?
	
	//TODO 2.1: don't let screen go below south pole or north of north pole
	
	//TODO 2.1: maybe use startForeground() to show gps service notification rather than manually doing this
	
    //TODO 3: make landscape work decently 

	//TODO 3: remove dbHack when we're sure that loading points and viewing them at the same
	// time works
	
	//TODO 3: remove log messages that appear way too much
	
	//TODO 2.5: encrypt background tiles (or have an option to do so)
	
	//TODO 2.1: make next/done button appear when entering passwords, etc.
	
	//TODO 2.5: make nicer frog??
	//TODO 3: Link this app to facebook
	//TODO 2.5: tutorial
	
	//TODO 2.5: get rid of magnifying glass in autozoom button, because we aren't
	//exactly zooming
	
	//TODO 3: We should maybe have an option to save a limited set of data
	// (ie excluding AreaPanel et al) to save room. Then we could probably
	// send the data in an email for example.	
	
	//TODO 3: add ability to merge an old backup with a current database
	// you will need to handle overlapping gps coords for this
	
	//TODO 2.5: add password timeout

	//TODO 2.5: wrap the world on the osm map view
	//TODO 2.5: allow option for password to be rechecked everytime the task is relaunched
	
	//TODO 2.5: make the gps service run when we want it to, immediately. Then we can
	// display the latest known point *and* it will go directly into the gps database
	
	//TODO 3: review code and clean up unnecessary classes/libs
	
	//TODO 3.5: histogram of speed to time spent at that speed
	
	//TODO 2.5: use proguard to optimize and obsfuscate code
	
	//TODO 2.5: possibly have ACRA display a troubleshooting link if things go wonky

	//TODO 2.5: check for whether there is GPS on phone
	//TODO 2.5: consider making a long standing background task for creating the database and setting up keys
	// while configuration is being done. If it's not finished by the time initial setup is done, then
	// put up a waiting dialog.
	
	//TODO 4: handle time range not changing for new GPS location row points while in map mode. (as long
	// as they're not in the app for days at a time, we'll never not be able to see new points the 
	// way it's currently set up)
	
	//TODO 3: have a auto zoom time button?
	//TODO 2.5: split the database into a cache database and a normal database. To backup, we just save the 
	//normal database
	//TODO 2.6: automatic backups
	//TODO 2.6: automatic backups and normal backups should prompt for passwords if there is no user password
	// (also have a "Change automatic password" box)
	
	//TODO 2.5: pan should wrap around in lon and hit the top and bottom of the earth in lat
		
	//TODO 2.1 turn this off if possible   <uses-permission android:name="android.permission.GET_TASKS" />
	
	public static final int IS_PREMIUM = /* ttt_installer:premium_neg42 */-42; //-42 is premium 

	public static final String PREMIUM_APPLICATION_PACKAGE = /* ttt_installer:premium_package */"com.rareventure.gps2_foobar";
	
	public static final String TRIAL_APPLICATION_PACKAGE = /* ttt_installer:trial_package */"com.rareventure.gps2_trial";


	public static SQLiteDatabase db;
	
	/**
	 * synchronization rules... only the gtgcachecreator can 
	 * perform transactions on the timmy db
	 */
	public static TimmyDatabase timmyDb;

	public static Intent BUY_PREMIUM_INTENT = new Intent(Intent.ACTION_VIEW);
	
	public static ReadWriteThreadManager initRwtm = new ReadWriteThreadManager();

	/**
	 * The timestamp of when the user last did an action where we would want them to
	 * reenter their password normally.
	 *
	 * Used by password timeout functionality.
	 */
	public static long lastGtgClosedMS;


	//TODO 2.01 Z make video??
	
	//TODO 2 Z make sure that market link works for BUY_PREMIUM_INTENT	
	static {
		BUY_PREMIUM_INTENT.setData(Uri.parse("market://details?id="+GTG.PREMIUM_APPLICATION_PACKAGE));
	}
	
	/**
	 * Requirements indicate system services and environment states that need to
	 * be a certain way for an activity to run.
	 * 
	 * Each requirement has an associated require...() method. These methods should
	 * be run in the same order listed (but some may be skipped if unnecessary)
	 * 
	 * The main reason we have requirements is that some activities require things that other
	 * activities don't. We want to load in all the requirements for all the back activities
	 * at once, so we have to OR requirements of all back pages together. This is opposed to
	 * having simple "setup" methods to setup the system at once for different system modes. 
	 */
	public static enum Requirement
	{
		INITIAL_SETUP,
		PREFS_LOADED(INITIAL_SETUP), 
		NOT_IN_RESTORE(INITIAL_SETUP),
		NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE(PREFS_LOADED,INITIAL_SETUP),
		NOT_TRIAL_EXPIRED(PREFS_LOADED,INITIAL_SETUP),
		SDCARD_PRESENT(INITIAL_SETUP), 
		
		/**
		 * True if the system has been already installed (the external directory
		 * created, the db created, initial paramaeters set, etc.)
		 */
		SYSTEM_INSTALLED(PREFS_LOADED,INITIAL_SETUP), 
		DB_READY(PREFS_LOADED,INITIAL_SETUP, SDCARD_PRESENT),
		DECRYPT(SYSTEM_INSTALLED,DB_READY,PREFS_LOADED,INITIAL_SETUP),
		ENCRYPT(SYSTEM_INSTALLED,DB_READY,PREFS_LOADED,INITIAL_SETUP),
		/**
		 * If this is not fulfilled, a password will be requested regardless if we need one to
		 * decrypt or not.
		 * This allows us to keep the system working for background processes (like
		 * GpsTrailerCacheCreator) while still requesting a password if the user did
		 * something that would normally lock the application.
		 * 
		 * Note, be careful that you don't ask for this as a requirement, and later have the
		 * user navigate to a screen that requires full decryption, because then the user
		 * will have to enter their password again
		 */
		PASSWORD_ENTERED(PREFS_LOADED,INITIAL_SETUP), 
		TIMMY_DB_READY(ENCRYPT, DECRYPT, PREFS_LOADED,INITIAL_SETUP),
		
		;
		
		public int bit;
		
		public int requiredPriorRequirementsBitmap;
		
		private Requirement(Requirement ... requiredPriorRequirements)
		{
			if(ordinal() > Integer.SIZE -1)
				throw new IllegalStateException("too many states");
			
			bit = 1 << ordinal();
			
			for(Requirement r : requiredPriorRequirements)
				requiredPriorRequirementsBitmap = 
				(requiredPriorRequirementsBitmap | r.bit | r.requiredPriorRequirementsBitmap);
		}
		
		/**
		 * bitmap of all required prior requirements and current requirement
		 */
		public int priorAndCurrentBitmap()
		{
			return bit|requiredPriorRequirementsBitmap;
		}

		public void fulfill() {
			fulfilledRequirements = (fulfilledRequirements | bit);
			
		}
		
		public boolean isPriorRequirementsFulfilled()
		{
			return (requiredPriorRequirementsBitmap & (~ fulfilledRequirements)) == 0;
		}

		public void assertPriorRequirementsFulfilled()
		{
			if(!isPriorRequirementsFulfilled())
			{
				throw new IllegalStateException("Prior requirements not fulfilled, "+requiredPriorRequirementsBitmap+
						", got "+fulfilledRequirements);
			}
		}

		public boolean isFulfilled() {
			return (fulfilledRequirements & bit) != 0;
		}

		public boolean isFulfilledAndAssertPriorRequirements() {
			// we make sure we are in write mode so the gps trailer service doesn't interfere
			// with the ui (since they both share the same application space and both
			// need to be initted)
			initRwtm.assertInWriteMode();
			assertPriorRequirementsFulfilled();
			return isFulfilled();
		}
		
		public void reset() {
			fulfilledRequirements = (fulfilledRequirements & (~bit));
		}

		public boolean isOn(int bitmap) {
			return (bitmap & bit) != 0;
		}
	}
	
	/**
	 * When we just start out, none of the requirements are fulfilled
	 */
	public static int fulfilledRequirements = 0;
	
	/**
	 * Initial setup should always be called first
	 */
	public static void requireInitialSetup(Context context, boolean inUi )
	{
		if(Requirement.INITIAL_SETUP.isFulfilledAndAssertPriorRequirements())
			return;

		Requirement.INITIAL_SETUP.fulfill();
	}
	
	
	public static boolean requireNotInRestore()
	{
		if(Requirement.NOT_IN_RESTORE.isFulfilledAndAssertPriorRequirements())
			return true;

		if(GTG.GTGEvent.DOING_RESTORE.isOn)
			return false;
		
		Requirement.NOT_IN_RESTORE.fulfill();
		
		return true;
	}
	
	public static void requirePrefsLoaded(Context context)
	{
		if(Requirement.PREFS_LOADED.isFulfilledAndAssertPriorRequirements())
			return;

		loadPreferences(context);
		
		Requirement.PREFS_LOADED.fulfill();
	}
	
	/**
	 * @return true if not expired, false otherwise
	 */
	public static boolean requireNotTrialExpired()
	{
		if(Requirement.NOT_TRIAL_EXPIRED.isFulfilledAndAssertPriorRequirements())
			return true;

		if(calcDaysBeforeTrialExpired() == 0)
			return false;
		
		Requirement.NOT_TRIAL_EXPIRED.fulfill();
		return true;
	}
	
	
	/**
	 * In the case where we're trial and premium package is installed
	 * returns intent to go to premium package
	 */
	public static Intent requireNotTrialWhenPremiumIsAvailable(Context context)
	{
		if(Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.isFulfilledAndAssertPriorRequirements())
			return null;

		//if trial
		if(GTG.IS_PREMIUM != -42)
		{
			Intent premiumStart = GTG.getGTGAppStart(context, GTG.PREMIUM_APPLICATION_PACKAGE);
				
			if(premiumStart != null)
			{
				return premiumStart;
			}
			
		}
		
		Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.fulfill();
		return null; 
	}
	
	public static boolean requireSdcardPresent(Context context)
	{
		if(Requirement.SDCARD_PRESENT.isFulfilledAndAssertPriorRequirements())
			return true;

		externalFilesDir =  context.getExternalFilesDir(null);

		if(!sdCardMounted(context))
		{
			return false;
		}

		Requirement.SDCARD_PRESENT.fulfill();
		
		return true;
	}
	
	public static boolean requireSystemInstalled(Context context)
	{
		if(Requirement.SYSTEM_INSTALLED.isFulfilledAndAssertPriorRequirements())
			return true;
		
		if(!prefs.initialSetupCompleted)
			return false;
		
		Requirement.SYSTEM_INSTALLED.fulfill();
		
		return true;
			
	}
	
	public static final int REQUIRE_DB_READY_OK = 0;
	public static final int REQUIRE_DB_READY_DB_DOESNT_EXIST = 1;
	
	/**
	 * Opens the database. 
	 */
	public static int requireDbReady()
	{
		//WARNING make sure to update RestoreGpxBackup if you add anything here
		
		if(Requirement.DB_READY.isFulfilledAndAssertPriorRequirements())
			return REQUIRE_DB_READY_OK;
		
		File dbToUse = GpsTrailerDbProvider.getDbFile(false);
		
		if (!dbToUse.exists()) {
			return REQUIRE_DB_READY_DB_DOESNT_EXIST;
		}
		
//		If db is corrupt we simply throw an exception
//		and treat as an internal error. The reason being we are unsure if 
//		an exception is a temporary occurrence or not. So if we prompt the
//		user to destroy an existing database but seemingly corrupt database
//		it may be a bad idea
		GTG.db = GpsTrailerDbProvider.openDatabase(dbToUse);
		
		Requirement.DB_READY.fulfill();
		
		return REQUIRE_DB_READY_OK;
	}
	
	public static final int REQUIRE_DECRYPT_OK = 0;
	public static final int REQUIRE_DECRYPT_BAD_PASSWORD = 1;
	public static final int REQUIRE_DECRYPT_NEED_PASSWORD = 2;

	/**
	 * Require both encrypt and decrypt requirements.
	 * 
	 * @param password if there is a password, this must be set, otherwise may be null
	 * @return 
	 */
	public static int requireEncryptAndDecrypt(String password)
	{
		if(Requirement.DECRYPT.isFulfilledAndAssertPriorRequirements())
			return REQUIRE_DECRYPT_OK;
		
		
		if(GpsTrailerCrypt.prefs.isNoPassword || password != null)
		{
			if(!GpsTrailerCrypt.initialize(GpsTrailerCrypt.prefs.isNoPassword ? null : password))
			{
				return REQUIRE_DECRYPT_BAD_PASSWORD;
			}

			GTG.userLocationCache = new UserLocationCache();
			GTG.gpsLocDbAccessor = new DbDatastoreAccessor<GpsLocationRow>(GpsLocationRow.TABLE_INFO);
			GTG.gpsLocCache = new GpsLocationCache(GTG.gpsLocDbAccessor, 10);
			GTG.tztSet = new TimeZoneTimeSet();
			tztSet.loadSet();
		}
		else
			return REQUIRE_DECRYPT_NEED_PASSWORD;
		
		Requirement.DECRYPT.fulfill();
		Requirement.ENCRYPT.fulfill();
		Requirement.PASSWORD_ENTERED.fulfill();
		
		return REQUIRE_DECRYPT_OK;
	}
	
	public static void requireEncrypt()
	{
		if(Requirement.ENCRYPT.isFulfilledAndAssertPriorRequirements())
			return;
		
		//TODO 4 we no longer need app id
		GpsTrailerCrypt.initializeWithoutPassword(MASTER_APP_ID);

		GTG.gpsLocDbAccessor = new DbDatastoreAccessor<GpsLocationRow>(GpsLocationRow.TABLE_INFO);
		GTG.gpsLocCache = new GpsLocationCache(GTG.gpsLocDbAccessor, 10);
		GTG.tztSet = new TimeZoneTimeSet();
				//co: we can't load the set here because we lack the private key to decrypt it with
		//note that we could still decrypt this if there is no set password, but I'd rather
		//keep the flow the same regardless if the user set the password or not
//		tztSet.loadSet();

		Requirement.ENCRYPT.fulfill();
		return;
		
	}
	
	/**
	 * Require that the password be entered since the last time the app was entered
	 * or there is none. 
	 *
	 * @param password if user entered a password, the password that was entered, otherwise
	 *   should be null
	 * @param lastGtgClosedMS
	 */
	public static boolean requirePasswordEntered(String password, long lastGtgClosedMS)
	{
		if(Requirement.PASSWORD_ENTERED.isFulfilledAndAssertPriorRequirements())
			return true;

		boolean status;
		if(GTG.prefs.passwordTimeoutMS != 0 && lastGtgClosedMS + GTG.prefs.passwordTimeoutMS > System.currentTimeMillis())
			status = true;
		else if(password == null)
			status = GpsTrailerCrypt.prefs.isNoPassword;
		else
			status = GpsTrailerCrypt.verifyPassword(password);
		
		if(status)
		{
			Requirement.PASSWORD_ENTERED.fulfill();
		
			return true;
		}
		
		return false;
	}
	
	public static int REQUIRE_TIMMY_DB_OK = 0;
	public static int REQUIRE_TIMMY_DB_IS_CORRUPT = 1;
	public static int REQUIRE_TIMMY_DB_NEEDS_UPGRADING = 2;
	public static int REQUIRE_TIMMY_DB_NEEDS_PROCESSING_TIME = 3;
	
	public static int requireTimmyDbReady(boolean canWaitAroundAwhile)
	{
		//WARNING!!! If you add anything here, make sure to update shutdownTimmyDb()
		
		if(Requirement.TIMMY_DB_READY.isFulfilledAndAssertPriorRequirements())
			return REQUIRE_TIMMY_DB_OK;
		
		//now setup timmy database, (only for the ui)
		if(timmyDb == null)
		{
			try {
				GTG.timmyDb = GpsTrailerDbProvider.createTimmyDb();
			} catch (IOException e) {
				Log.e(GTG.TAG,"Can't open timmy db", e);
				return REQUIRE_TIMMY_DB_IS_CORRUPT;
			}
		}
		
		//we check if timmy db is open outside of the timmyDb == null if
		// so that if we need more processing time, we don't need to null out timmy db
		// when we go back and try top open the db again
		if(!timmyDb.isOpen())
		{
			try {
				if(timmyDb.isCorrupt())
				{
					return REQUIRE_TIMMY_DB_IS_CORRUPT;
				}
				
				if(!canWaitAroundAwhile && timmyDb.needsProcessingTime())
					return REQUIRE_TIMMY_DB_NEEDS_PROCESSING_TIME;

				timmyDb.open();
	
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		
		//we need the database to be open before we mess with properties, so we 
		//check if the timmy db needs upgrade outside of the open if. We also do this
		//because if the user cancels out of the upgrade screen, or hits the home key,
		//this method would be run again, so timmydb would already be open (and not upgraded)
		//which would be bad if we didn't know to upgrade.
		if(timmyDb.isNew())
		{
			timmyDb.setProperty(CACHE_VERSION_NAME, String.valueOf(CACHE_VERSION));
		}
		else if(!GTG.isTimmyDbLatestVersion())
			return REQUIRE_TIMMY_DB_NEEDS_UPGRADING;

		if(apCache == null)
		{
			//note that we create these after open because they need to call
			// getNextRowId() which is only available after the database is opened.
			GTG.apCache = new AreaPanelCache(
					new RollBackTimmyDatastoreAccessor<AreaPanel>(
					GTG.timmyDb.getRollBackTable(GTG.getExternalStorageDirectory() + "/"
							+ GpsTrailerDbProvider.APCACHE_TIMMY_TABLE_FILENAME)));
			GTG.ttCache = new TimeTreeCache(
					new RollBackTimmyDatastoreAccessor<TimeTree>(
							GTG.timmyDb.getRollBackTable(GTG.getExternalStorageDirectory() + "/"
							+ GpsTrailerDbProvider.TIME_TREE_TIMMY_TABLE_FILENAME)));
			GTG.mediaLocTimeTimmyTable = GTG.timmyDb.getTable(GTG.getExternalStorageDirectory() + "/"
						+ GpsTrailerDbProvider.MEDIA_LOC_TIME_TIMMY_TABLE_FILENAME);
//			GTG.mlcpCache = new MediaLocTimePlusCache(
//					new TimmyDatastoreAccessor<MediaLocTimePlus>(
//					GTG.timmyDb.getTable(context.getExternalFilesDir(null) + "/"
//							+ MEDIA_LOC_TIME_PLUS_TIMMY_TABLE_FILENAME)));
			
			GTG.cacheCreator = new GpsTrailerCacheCreator();
			
			GTG.mediaLocTimeMap = new MediaLocTimeMap();
			GTG.mediaLocTimeMap.loadFromDb();		
			
			cacheCreator.start();
		}
		
		Requirement.TIMMY_DB_READY.fulfill();
		return REQUIRE_TIMMY_DB_OK;
		
	}

	public static AndroidPreferenceSet prefSet = new AndroidPreferenceSet() {

		@Override
		public void writePrefs(Map<String, Object> res) {
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.encryptedPrivateKey", 
					GpsTrailerCrypt.prefs.encryptedPrivateKey);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.publicKey", 
					GpsTrailerCrypt.prefs.publicKey);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.salt", 
					GpsTrailerCrypt.prefs.salt);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.initialWorkPerformed", 
					GpsTrailerCrypt.prefs.initialWorkPerformed);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.isNoPassword", 
					GpsTrailerCrypt.prefs.isNoPassword);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.aesKeySize", 
					GpsTrailerCrypt.prefs.aesKeySize);

			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.initialSetupCompleted", 
					GTG.prefs.initialSetupCompleted);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.isCollectData", 
					GTG.prefs.isCollectData);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.minBatteryPerc", 
					GTG.prefs.minBatteryPerc);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.compassData", 
					GTG.prefs.compassData);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.useMetric",
					GTG.prefs.useMetric);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.passwordTimeoutMS",
					GTG.prefs.passwordTimeoutMS);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.writeFileLogDebug",
					GTG.prefs.writeFileLogDebug);

			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.lastLon",
					OsmMapGpsTrailerReviewerMapActivity.prefs.lastLon);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.lastLat",
					OsmMapGpsTrailerReviewerMapActivity.prefs.lastLat);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.lastZoom",
					OsmMapGpsTrailerReviewerMapActivity.prefs.lastZoom);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.currTimePosSec",
					OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.currTimePeriodSec", 
					OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePeriodSec);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.showPhotos", 
					OsmMapGpsTrailerReviewerMapActivity.prefs.showPhotos);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.selectedColorRangesBitmap", 
					OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.enableToolTips", 
					OsmMapGpsTrailerReviewerMapActivity.prefs.enableToolTips);
			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.panelScale",
					OsmMapGpsTrailerReviewerMapActivity.prefs.panelScale);

			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapView.Preferences.mapStyle",
					OsmMapView.prefs.mapStyle);

			res.put(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerGpsStrategy.Preferences.batteryGpsOnTimePercentage", 
					GpsTrailerGpsStrategy.prefs.batteryGpsOnTimePercentage);
			
		}

		@Override
		protected void loadPreference(String name, String value) 
		{
			if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.encryptedPrivateKey"))
				GpsTrailerCrypt.prefs.encryptedPrivateKey = Util.toByte(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.publicKey"))
				GpsTrailerCrypt.prefs.publicKey = Util.toByte(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.salt"))
				GpsTrailerCrypt.prefs.salt = Util.toByte(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.initialWorkPerformed"))
				GpsTrailerCrypt.prefs.initialWorkPerformed = Boolean.parseBoolean(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.isNoPassword"))
				GpsTrailerCrypt.prefs.isNoPassword = Boolean.parseBoolean(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerCrypt.Preferences.aesKeySize"))
				GpsTrailerCrypt.prefs.aesKeySize = Integer.parseInt(value);
			
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.initialSetupCompleted"))
				GTG.prefs.initialSetupCompleted = Boolean.parseBoolean(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.isCollectData"))
				GTG.prefs.isCollectData = Boolean.parseBoolean(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.minBatteryPerc"))
				GTG.prefs.minBatteryPerc = Float.parseFloat(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.compassData"))
				GTG.prefs.compassData = Integer.parseInt(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.useMetric"))
				GTG.prefs.useMetric = Boolean.parseBoolean(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.passwordTimeoutMS"))
				GTG.prefs.passwordTimeoutMS = Long.parseLong(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GTG.Preferences.writeFileLogDebug"))
				GTG.prefs.writeFileLogDebug = Boolean.parseBoolean(value);

			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.lastLon"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.lastLon = Double.parseDouble(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.lastLat"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.lastLat = Double.parseDouble(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.lastZoom"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.lastZoom = Float.parseFloat(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.currTimePosSec"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec = Integer.parseInt(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.currTimePeriodSec"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePeriodSec = Integer.parseInt(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.showPhotos"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.showPhotos = Boolean.parseBoolean(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.selectedColorRangesBitmap"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap = Integer.parseInt(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.enableToolTips"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.enableToolTips = Boolean.parseBoolean(value);
			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity.Preferences.panelScale"))
				OsmMapGpsTrailerReviewerMapActivity.prefs.panelScale = Integer.parseInt(value);

			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.GpsTrailerGpsStrategy.Preferences.batteryGpsOnTimePercentage"))
				GpsTrailerGpsStrategy.prefs.batteryGpsOnTimePercentage = Float.parseFloat(value);

			else if(name.equals(/* ttt_installer:obfuscate_str */"com.rareventure.gps2.reviewer.map.OsmMapView.Preferences.mapStyle"))
				OsmMapView.prefs.mapStyle = Enum.valueOf(OsmMapView.Preferences.MapStyle.class, value);

			else
				Log.e(GTG.TAG,"ignoring pref: "+name);
		}};
	public static GpsTrailerCrypt crypt;
	
	//WARNING if you add anoter cache type, be sure to update lockGpsCaches
	public static AreaPanelCache apCache;
	public static TimeTreeCache ttCache;
	public static GpsLocationCache gpsLocCache;
	public static TimeZoneTimeSet tztSet;
	public static UserLocationCache userLocationCache;
	
	/**
	 * Use for debugging purposes only
	 */
	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
	
	public static final boolean CLEAR_OUT = false;
	public static final int HACK_FAIL_STOP = Integer.MAX_VALUE;
	public static final boolean DEBUG_SHOW_AREA_PANELS = false;
	public static final boolean COMMIT_TO_BEFORE_FAILURE = true;
	public static final boolean CHECK2 = false;
	
	public static final boolean FREE_VERSION = false;
	
	public static final boolean START_MAIN_APP = false;
	
	public static boolean HACK_MAKE_TT_CORRUPT = false;
	
	public static final boolean HACK_TURN_OFF_APCACHE_LOADING = false;
	
	public static boolean turnOnMethodTracing;
	public static Preferences prefs = new Preferences();
	
	/**
	 * A hack to communicate between activities
	 */
	public static GTGAction lastSuccessfulAction;
	
	public enum GTGAction { SET_FROM_AND_TO_DATES, TOOL_TIP_CLICKED };
	
	/**
	 * This lock is for the GpsTrailerCacheCreator. It prevents
	 * (ui) threads that read the cache to find points from
	 * interferring with writing to the cache. Since multiple threads
	 * commonly access the cache at the same time, we don't use
	 * regular synchronization, but instead this a ReadWriteThreadManager,
	 * which allows multiple readers at the same time.
	 */
	public static ReadWriteThreadManager cacheCreatorLock = new ReadWriteThreadManager();

//	public static BusyIndicationManager bim = new BusyIndicationManager();
	
	/**
	 * Named of default SharedPreferences object (persistent store)
	 */
	public static final String SHARED_PREFS_NAME = "GpsPrefs";
	
	/**
	 * ID for frog style notification pop up (for collecting data)
	 */
	public static final int FROG_NOTIFICATION_ID = -42;
	
	/**
	 * Signifies the user_data_key row contains key of the master application (the one which knows the password)
	 * All other keys will be removed and replaced by encrypting with this one. 
	 * (we have several keys because everytime we encrypt, we use a different key, signed by the master public key,
	 * this is how we prevent the app from asking for a password on boot, yet still have everything encrypted)
	 * 
	 */
	public static final int MASTER_APP_ID = 0;
	
	public static final int SETTINGS_APP_ID = 3;
	public static final int GPS_TRAILER_SERVICE_APP_ID = 99;

	/**
	 * True if we requested the user let us use gps, and they said no
	 */
	public static boolean userDoesntWantUsToHaveGpsPerm;

	//TODO 2.01 add instrumentation and automatic error reporting
	
	public static enum GTGEvent { 
		ERROR_LOW_FREE_SPACE,
		ERROR_SDCARD_NOT_MOUNTED,
		ERROR_LOW_BATTERY,
		ERROR_GPS_DISABLED,
		ERROR_GPS_NO_PERMISSION,

		ERROR_SERVICE_INTERNAL_ERROR,
		TRIAL_PERIOD_EXPIRED, TTT_SERVER_DOWN,
	 	ERROR_UNLICENSED, PROCESSING_GPS_POINTS, LOADING_MEDIA, DOING_RESTORE ;
								
		public boolean isOn;
		public Object obj;
	};

	public static interface GTGEventListener
	{
		/**
		 * Note that this will always be run in the UI thread
		 * 
		 * @return true if the event has been handled and can be turned off.
		 */
		public boolean onGTGEvent(GTGEvent event);

		public void offGTGEvent(GTGEvent event);
	}

	public static long MIN_FREE_SPACE_ON_SDCARD = 50l * 1024 * 1024; //WARNING: this is hardcoded into R.string.error_low_free_space,
	//if updated, update there as well

	public static GTGEventListener MAIN_APP_GTG_EVENT_LISTENER = new GTGEventListener() {
		
		@Override
		public boolean onGTGEvent(GTGEvent event) {
			return false;
		}

		@Override
		public void offGTGEvent(GTGEvent event) {
		}
	}; 
	
	/**
	 * If there is a serious problem that would prevent the application from running
	 * successfully, this will create an alert and return true.
	 * Otherwise if things are good to go, it returns false
	 */
	public static boolean checkSdCard(Context context)
	{
		if(!sdCardMounted(context))
		{
			GTG.alert( GTGEvent.ERROR_SDCARD_NOT_MOUNTED);
			return false;
		}
		
		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		long sdAvailSize = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();
		
		if(sdAvailSize < MIN_FREE_SPACE_ON_SDCARD)
		{
			GTG.alert( GTGEvent.ERROR_LOW_FREE_SPACE);
			return false;
		}
		
		return true;
		
	}
	
	public static boolean sdCardMounted(Context context) {
		String state = Environment.getExternalStorageState();
		
		//sometimes even though the external media is mounted, the external storage directory may not be present
		//for some reason
		return state.equals(Environment.MEDIA_MOUNTED) && (context == null ||  getExternalStorageDirectory() != null);
	}


	public static DbDatastoreAccessor<GpsLocationRow> gpsLocDbAccessor;

	/**
	 * A background process for loading data into the areapanel and timetree
	 * caches. We make it this way because it takes too long to commit 
	 * to the database to properly quit, so it often needs to run in the 
	 * background even when the user thinks they have exited the program
	 * 
	 * Also handles populating mediaLocTimeMap and updating it to reflect
	 * areapanels
	 * 
	 * Synchronization rules:
	 * any change to viewnodes, or areapanels must be synchronized against this
	 * TODO 3 possibly change this to synchronize against a more appropriate
	 * object, since cacheCreator is becoming a generic garbage man
	 */
	public static GpsTrailerCacheCreator cacheCreator;

	public static void notifyCollectDataServiceOfUpdate(Context context)
	{
		if(prefs.isCollectData)
			ContextCompat.startForegroundService(context,new Intent(context,
                GpsTrailerService.class));
		else
			context.stopService(new Intent(context,
	                GpsTrailerService.class));
	}
	
	private static ArrayList<GTGEventListener> localEventListeners = new ArrayList<GTGEventListener>();
	
	/**
	 * Called when a major event has occurred, such as an internal error.
	 * Is thread safe. Cannot be called before setupIfNecessary() is run.
	 * <p>
	 * This is used to notify the application of a specific condition
	 * with the environment. Note that events are sticky
	 * until handled. This can be used for two purposes, one is to 
	 * ignore an event until a screen comes up that can handle it. The
	 * other is to display a constant message to the user and not 
	 * remove it until the condition is lifted (such as for low
	 * free space). For the second case, the boolean isOn can
	 * turn off an event.
	 * <p> 
	 * Note that thread handling must be handled by the listener
	 * 
	 * @param obj object to set into event. If set, alert will always run, otherwise
	 *   it will be ignored if the event is already on
	 */
	public static void alert(final GTGEvent event, final boolean isOn, final Object obj) {
		int i;

//		Log.d(GTG.TAG,"GTGAlert: event: "+event+", isOn: "+isOn);

		synchronized(eventListeners)
		{
			if(event.isOn == isOn && obj == null) {
//				Log.d(GTG.TAG,"GTGAlert: event ignored");
				return;
			}

			event.isOn = isOn;
			event.obj = obj;
			
			localEventListeners.clear();
			localEventListeners.addAll(eventListeners.keySet());
			i = localEventListeners.size()-1;

//			Log.d(GTG.TAG,"GTGAlert: num listeners "+(i+1));
		}

		for(; i >= 0; i--)
		{
			GTGEventListener el;
			
			el = localEventListeners.get(i);
			
			if(event.isOn)
			{
				if(el.onGTGEvent(event))
				{
					//if the event handler turned off the event, alert everyone that its off
					//and restart
					alert(event, false);
					break;
				}
			}
			else
				el.offGTGEvent(event);
		}
		
		//make sure to clear so that we don't have a memory leak (eventListeners is a WeakHashMap)
		localEventListeners.clear();
	}
	
	
	public static void alert(GTGEvent event,
			boolean isOn) {
		alert(event, isOn, null);
	}
	

	public static void alert(final GTGEvent event) {
		alert(event, true, null);
	}
	
	public static enum SetupState
	{
		/**
		 * A password is necessary to setup the database (returned
		 * only if decryption is asked for)
		 */
		NEED_PASSWORD,
		/**
		 * Success
		 */
		SUCCESS,
		/**
		 * The database has not been initially setup. Note that this
		 * will *not* be called if the system expects the database to 
		 * be present, but its not (ie. prefs.initialSetupCompleted
		 * is true but the database isn't there). In this case,
		 * a GTGEvent will be sent to gtgEventListener
		 */
		BEFORE_INITIAL_SETUP, 
		
		
		BAD_PASSWORD, 
		
		/**
		 * The sdcard is not mounted, so we can't get the database
		 */
		SDCARD_NOT_MOUNTED, 
		NEED_PROCESSING_TIME, 
		TIMMY_DB_CORRUPT, TIMMY_DB_NEEDS_UPGRADE,
		
		/**
		 * app is in trial mode and the premium version is installed
		 */
		IS_TRIAL_AND_PREMIUM_INSTALLED
	}
	
	public static boolean outsideApplication = false;
	
	
	private static final long TIME_TO_WAIT_BEFORE_CHECKING_ACTIVITY = 5000;

	/**
	 * All intents must be under this package, or we won't be able to tell whether
	 * the user is within the application or not.
	 */
	protected static final String GTG_PACKAGE_PREFIX = /* ttt_installer:obfuscate_str */"com.rareventure.gps2";

	private static final String CACHE_VERSION_NAME = /* ttt_installer:obfuscate_str */"CACHE_VERSION";

	private static final int CACHE_VERSION = 1;

	public static final String ENCODED_GOOGLE_PLAY_PUBLIC_KEY = 
		/* ttt_installer:obfuscate_str */"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgDQPKbdJYuXJX4+RveoWle7vGL+YMm1tGjSa/rXtcDj04Su0QJCFaXF5I0cGGBQXwv7Y8lzFp/hsd34c8G0k9NyvPLacPVCLfBgWTt0WIINHKw8sWrwyDZO6Bph5V55My8J8Oi++ebkuSJFcEbSmblu/tI06CNmGd5uSBV2s8yVtv1eNjO+pQ3//ePONrKpehIASony9/gBQFT+vm3WYfNYIOyFsTP6f1mp5E+snNIdfp8H29jfxzNm1YwqQ2/AuFIMsXfzCmtD4zn/VWq5yaDlW2Rwh7pMNXs3FCthGFk88H9SUQew9ZReBHQaTl4uFUMlbJbP7l5oyEGGMg5Wv2QIDAQAB";

	public static boolean isTimmyDbLatestVersion() {
		
		int version = timmyDb.getIntProperty(CACHE_VERSION_NAME,0);
		return version == CACHE_VERSION; // && 1 == 0; //xODO 2 HACK
	}


	public static final String TAG = "GpsTrailer";

	public static final int FILE_CACHE_TASK_PRIORITY = 5;

	public static final int REMOTE_LOADER_TASK_PRIORITY = 5;

	//low priority for this so it doesn't slow down the gui too much
	public static final int GPS_TRAILER_CACHE_CREATOR_PRIORITY = 1;

	public static final int GPS_TRAILER_OVERLAY_DRAWER_PRIORITY = 5;

	public static final int SELECTED_AREA_SET_PRIORITY = 5;
	
	public static final String APP_NAME = "Tiny Travel Tracker";

	public static final int REQUIREMENTS_ENTER_PASSWORD = 
			Requirement.INITIAL_SETUP.priorAndCurrentBitmap()
			|Requirement.PREFS_LOADED.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.priorAndCurrentBitmap();

	public static final int REQUIREMENTS_FATAL_ERROR =  Requirement.INITIAL_SETUP.priorAndCurrentBitmap();
	
	public static final int REQUIREMENTS_BASIC_UI = 
			Requirement.INITIAL_SETUP.priorAndCurrentBitmap()
			|Requirement.NOT_IN_RESTORE.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_EXPIRED.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.priorAndCurrentBitmap();
	
	public static final int REQUIREMENTS_BASIC_PASSWORD_PROTECTED_UI = 
			Requirement.INITIAL_SETUP.priorAndCurrentBitmap()
			|Requirement.NOT_IN_RESTORE.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_EXPIRED.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.priorAndCurrentBitmap()
			|Requirement.DECRYPT.priorAndCurrentBitmap()
			|Requirement.ENCRYPT.priorAndCurrentBitmap()
			|Requirement.PASSWORD_ENTERED.priorAndCurrentBitmap();
			
	public static final int REQUIREMENTS_DB_DOESNT_EXIST_PAGE = 
			Requirement.INITIAL_SETUP.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_EXPIRED.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.priorAndCurrentBitmap();
	//co: although it's a little weird not to ask for a password here, we aren't allowing
	//the user any access to the gps points and if we do ask for a password and then
	//the user navigates to the main screen, they'd have to enter the password again
	// for the decrypt requirement
//			|Requirement.PASSWORD_ENTERED.priorAndCurrentBitmap();
			
			
	public static final int REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI = 
			 Requirement.DB_READY.priorAndCurrentBitmap()
			|Requirement.DECRYPT.priorAndCurrentBitmap()
			|Requirement.ENCRYPT.priorAndCurrentBitmap()
			|Requirement.INITIAL_SETUP.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_EXPIRED.priorAndCurrentBitmap()
			|Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.priorAndCurrentBitmap()
			|Requirement.PASSWORD_ENTERED.priorAndCurrentBitmap()
			|Requirement.PREFS_LOADED.priorAndCurrentBitmap()
			|Requirement.SDCARD_PRESENT.priorAndCurrentBitmap()
			|Requirement.SYSTEM_INSTALLED.priorAndCurrentBitmap()
			|Requirement.NOT_IN_RESTORE.priorAndCurrentBitmap()
			|Requirement.TIMMY_DB_READY.priorAndCurrentBitmap();

	public static final int REQUIREMENTS_TRIAL_EXPIRED_ACTIVITY = 
			GTG.REQUIREMENTS_BASIC_PASSWORD_PROTECTED_UI 
			& (~Requirement.NOT_TRIAL_EXPIRED.bit);
	
	/**
	 * Used by create backup so that an expired trial can create a backup
	 */ 
	public static final int REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI_EXPIRED_OK =
			REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI
			& (~Requirement.NOT_TRIAL_EXPIRED.bit);
	/**
	 * Initial setup wizard 
	 */
	public static final int REQUIREMENTS_WIZARD =
			Requirement.INITIAL_SETUP.priorAndCurrentBitmap()
			|Requirement.PREFS_LOADED.priorAndCurrentBitmap();

	public static final int REQUIREMENTS_RESTORE = REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI &
				(~Requirement.NOT_IN_RESTORE.bit);

	
	public static final Class START_ACTIVITY_CLASS = OsmMapGpsTrailerReviewerMapActivity.class;

			


	//these are the preferences we save.
	
	private static void loadPreferences(Context context) {
		//TODO 3: notify user if an exception is thrown here when the
		// preferences are corrupted. We then should reset to factory settings...
		// don't forget to load the password check field
		GTG.prefSet.loadAndroidPreferences(context);
		
		//colorRanges only gets updated from this method (it is an array, so that's why it's
		// not stored in prefs directly)
		OsmMapGpsTrailerReviewerMapActivity.prefs.updateColorRangeBitmap(OsmMapGpsTrailerReviewerMapActivity.prefs.selectedColorRangesBitmap);
	}
	
	//note must be threadsafe for TTTClient
	//TODO 3 get rid of this method
	public static void savePreferences(Context context) {
		GTG.prefSet.savePrefs(context);
	}
	
	public static void runBackgroundTask(Runnable r)
	{
		if(backgroundRunner == null)
		{
			backgroundRunner = new BackgroundRunner();
			backgroundRunner.setDaemon( true);
			backgroundRunner.start();
		}
		backgroundRunner.addRunnable(r);
	}
	
	private static BackgroundRunner backgroundRunner;

	/**
	 * 
	 * Synchronization rules:
	 * any change to rTree or the mlts should synchronize against this
	 */
	public static MediaLocTimeMap mediaLocTimeMap;

	public static TimmyTable mediaLocTimeTimmyTable;

	/**
	 * Everytime the reviewer map is resumed() this value is incremented
	 */
	public static int reviewerMapResumeId;

	private static Boolean isTrial;

	public static class Preferences implements AndroidPreferences
	{


		/**
		 * Prompts the user to enter initial setup
		 */
		public boolean initialSetupCompleted = false;
		
		/**
		 * If set, gps trailer service will run, otherwise no
		 */
		public boolean isCollectData;

		/**
		 * Minimum amount of battery life before gps collection automatically shuts itself off
		 */
		public float minBatteryPerc = .35f;

		/**
		 * True if we should use metric for scale and stuff
		 */
		public boolean useMetric;

		/**
		 * Shhhh... this is really the date in seconds when the product was installed xor'ed with 
		 * a static value
		 */
		public int compassData = COMPASS_DATA_XOR;

		/**
		 * If not zero, represents the amount of time before the password times out when
		 * not inside the app
		 */
		public long passwordTimeoutMS;

		/**
		 * If true will write to the gps wake lock debug file
		 */
		public boolean writeFileLogDebug = false;
	}
	
	public static int COMPASS_DATA_XOR = -1016932754;
	
	/**
	 * Time for the trial to exist 
	 */
	private static int TRIAL_TIME_LIMIT_SECS_XORED = Util.SECONDS_IN_MONTH ^ COMPASS_DATA_XOR;

	public static boolean isBillingSupported;

	/**
	 * Sets up encryption for a new database, deleting all data
	 * @param context
	 * @param password if null, then will be setup with default "no password" password
	 */
	public static void setupCryptForNewDatabase(
			Context context, String password) {
		if(password == null)
			GpsTrailerCrypt.prefs.isNoPassword = true;
		else
			GpsTrailerCrypt.prefs.isNoPassword = false;
		
		GpsTrailerCrypt.deleteAllDataAndSetNewPassword(context, password);
		if(!setupCrypt(password))
		{
			throw new IllegalStateException("What? created crypt with password and now it won't unlock");
		}
		
		savePreferences(context);
	}
	
	/**
	 * Does all the work for setuping encryption for the database
	 */
	private static boolean setupCrypt(String password) {
		if(!GpsTrailerCrypt.initialize(GpsTrailerCrypt.prefs.isNoPassword ? null : password))
		{
			return false;
		}

		//WARNING!!! make sure to check that RestoreGpxBackup resets these things properly
		GTG.userLocationCache = new UserLocationCache();
		GTG.gpsLocDbAccessor = new DbDatastoreAccessor<GpsLocationRow>(GpsLocationRow.TABLE_INFO);
		GTG.gpsLocCache = new GpsLocationCache(GTG.gpsLocDbAccessor, 10);
		GTG.tztSet = new TimeZoneTimeSet();
		tztSet.loadSet();
		
		return true;
	}


	/**
	 * If expired, returns 0, otherwise the days left before expiration rounded up
	 */
	public static int calcDaysBeforeTrialExpired() {
		return 999999;
//		int currTimeSec = (int) (System.currentTimeMillis() / 1000l);
//		int startDateSec = prefs.compassData ^ COMPASS_DATA_XOR;
//		int timeRemaining = (GTG.TRIAL_TIME_LIMIT_SECS_XORED  ^ COMPASS_DATA_XOR)- (currTimeSec - startDateSec);
//		
//		//is premium
//		if(IS_PREMIUM == -42)
//			return Integer.MAX_VALUE;
//		
//		//if start date was spoofed to future or trial is expired
//		if(startDateSec > currTimeSec || timeRemaining < 0)
//			return 0;
//		
//		return timeRemaining / Util.SECONDS_IN_DAY + 1;
//		
	}


	private static WeakHashMap<GTGEventListener, Object> eventListeners = new WeakHashMap<GTGEventListener, Object>();

	static GpsTrailerService service;

	private static File externalFilesDir;



	public static void addGTGEventListener(
			GTGEventListener eventListener) {
		synchronized(eventListeners)
		{
			if(eventListeners.containsKey(eventListener))
				return;

			for(GTGEvent event : GTGEvent.values()) {
				if(event.isOn)
					eventListener.onGTGEvent(event);
			}

			eventListeners.put(eventListener, Boolean.TRUE);
		}
	}

	public static void removeGTGEventListener(
			GTGEventListener eventListener) {
		synchronized(eventListeners)
		{
			eventListeners.remove(eventListener);
		}
	}



	public static File getExternalStorageDirectory() {
		return externalFilesDir;
	}
	
	private static boolean licenseCheckerBeingContacted;
	private static Object licenseCheckerBeingContactedLock = new Object();

	public static Intent getGTGAppStart(Context context, String appPackage) {
		Intent i = new Intent();
		i.setComponent(new ComponentName(appPackage, START_ACTIVITY_CLASS.getName()));
		
		//check if premium is installed
		if(Util.isCallable(context, i))
		{
			return i;
		}
		
		return null;
	}

	/**
	 * A hack to prevent gps and caches from being accessed when we are making
	 * major changes. Locks all the caches in a newly created thread and doesn't
	 * let go. In general if a change was made, killSelf() may
	 * prove useful.
	 */
	public static void lockGpsCaches(final Runnable myRunnable) {
		final boolean [] ready = new boolean[1];
		
		Runnable r = new Runnable() {
			
			@Override
			public void run() {
				synchronized (apCache)
				{
					synchronized (ttCache)
					{
						synchronized (gpsLocCache)
						{
							synchronized(this)
							{
								ready[0] = true;
								notify();
								
							}
							
							if(myRunnable != null)
								myRunnable.run();
							
							synchronized(this)
							{
								try {
									this.wait();
								} catch (InterruptedException e) {
								}
							}
						}
					}
				}
			}
		};
		
		new Thread(r).start();
		
		synchronized(r)
		{
			while(!ready[0])
				try {
					r.wait();
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
		}
	}
	
	public static void setAppPasswordNotEntered() {
		if(Requirement.PASSWORD_ENTERED.isFulfilled())
			GTG.lastGtgClosedMS = System.currentTimeMillis();

		Requirement.PASSWORD_ENTERED.reset();
	}


	/**
	 * Creates an initializes a db (including creating a user data encrypting key. Crypt in preferences must be already set up.
	 * Database is closed after being initialized
	 */
	public static void createAndInitializeNewDbFile() {
		//we create it as a temp file and then move it over so we are sure it 
		//is initialized and ready before we start using it (in case we crash
		// part way through)
		File dbTmpFile = GpsTrailerDbProvider.getDbFile(true);
		
		SQLiteDatabase newDb = GpsTrailerDbProvider.createNewDbFile(dbTmpFile);
		GpsTrailerCrypt.generateAndInitializeNewUserDataEncryptingKey(GTG.MASTER_APP_ID, newDb);
		newDb.close();
		
		dbTmpFile.renameTo(GpsTrailerDbProvider.getDbFile(false));
	}

	/**
	 * Should not be called when gps service is running
	 */
	public static void closeDbAndCrypt() {
		if(GTG.db != null)
		{
			GTG.db.close();
			GTG.db = null;
			Requirement.DB_READY.reset();
		}
		
		if(GTG.crypt != null)
		{
			GTG.crypt = null;
			Requirement.DECRYPT.reset();
			Requirement.ENCRYPT.reset();
		}
	}


	/**
	 * Sets whether to enable acra or not. Changes and saves to storedpreferences
	 */
	public static void enableAcra(Context context, boolean checked) {
	    //co: for use with ACRA 5+ (If I use it, it causes a problem acquiring wake locks)
		//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences sp = ACRA.getACRASharedPreferences();

		Editor editor = sp.edit();
		
		editor.putBoolean(ACRA.PREF_DISABLE_ACRA, !checked);

		if(!editor.commit())
			TAssert.fail("failed storing to shared prefs");
		
	}


	public static boolean isAcraEnabled(Context context) {
        //co: for use with ACRA 5+ (If I use it, it causes a problem acquiring wake locks)
		//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences sp = ACRA.getACRASharedPreferences();

		return !sp.getBoolean(ACRA.PREF_DISABLE_ACRA, false);
	}


	public static void setIsInRestore(boolean b) {
		Requirement.NOT_IN_RESTORE.reset();
		
		GTG.alert(GTGEvent.DOING_RESTORE, b);
	}

	private static Object shutdownTimmyDbLock = new Object();
	private static Thread shutdownTimmyDbThread;

	/**
	 * Note this may take awhile if the cache creator is busy. May be interrutped
	 * with interruptShutdownTimmyDb(). 
	 */
	public static void shutdownTimmyDb() {
		if(GTG.timmyDb == null)
			return;
		
		if(GTG.cacheCreator != null)
		{
			synchronized (shutdownTimmyDbLock)
			{
				shutdownTimmyDbThread = Thread.currentThread();
			}
			
			GTG.cacheCreator.shutdown();

			try {
				GTG.cacheCreator.join();
			}
			catch (InterruptedException e) {
				return;
			}
			
			synchronized (shutdownTimmyDbLock)
			{
				shutdownTimmyDbThread = null;
			}
			
			GTG.cacheCreator = null;
		}
		
		GTG.apCache.clear();
		GTG.apCache = null;

		GTG.ttCache.clear();
		GTG.ttCache = null;
		
		GTG.mediaLocTimeMap = null;
		try {
			GTG.timmyDb.close();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		GTG.timmyDb = null;
		GTG.mediaLocTimeTimmyTable = null;
		
		Requirement.TIMMY_DB_READY.reset();
	}

	public static void interruptShutdownTimmyDb()
	{
		synchronized (shutdownTimmyDbLock)
		{
			if(shutdownTimmyDbThread != null)
			{
				shutdownTimmyDbThread.interrupt();
			}
		}
	}


}
