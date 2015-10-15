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
package com.rareventure.gps2.reviewer;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import com.rareventure.android.SeekBarDialogPreference;
import com.rareventure.android.widget.SeekBarWithText;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGAction;
import com.rareventure.gps2.GTGPreferenceActivity;
import com.rareventure.gps2.GpsTrailerCrypt;
import com.rareventure.gps2.GpsTrailerGpsStrategy;
import com.rareventure.gps2.GpsTrailerService;
import com.rareventure.gps2.IGTGActivity;
import com.rareventure.gps2.R;
import com.rareventure.gps2.gpx.CreateGpxBackup;
import com.rareventure.gps2.gpx.RestoreGpxBackup;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;
import com.rareventure.gps2.reviewer.password.EnterPasswordActivity;

import java.util.Arrays;

public class SettingsActivity extends GTGPreferenceActivity implements OnPreferenceChangeListener, OnPreferenceClickListener, OnDismissListener {
	private final Runnable SAVE_PREFS_AND_RESTART_COLLECTOR = new Runnable() 
				{	
					public void run() {
						GTG.savePreferences(SettingsActivity.this);
						runOnUiThread(new Runnable ()
						{
							public void run() {
								//we need to restart the service because its preferences changed
								stopService(new Intent(SettingsActivity.this, GpsTrailerService.class));
								startService(new Intent(SettingsActivity.this, GpsTrailerService.class));
							}
						});
					}
					
				};
				
	private CheckBoxPreference isCollectData;
	private CheckBoxPreference enablePassword;
	private Preference changePassword;
	private SeekBarDialogPreference percTimeGpsOn;
	private SeekBarDialogPreference minBatteryLife;
	private SeekBarDialogPreference passwordTimeout;

	private CheckBoxPreference useMetricUnits;

	private CheckBoxPreference enableToolTips;

	private PreferenceScreen createBackupFilePref;

	private PreferenceScreen restoreBackupFilePref;

	private PreferenceScreen aboutPref;

	private PreferenceScreen colorblindSettings;

	private CheckBoxPreference allowErrorReporting;

	private SeekBarDialogPreference mapFontSize;

	private static final String[] passwordTimeoutStrs =
			{ "Off" ,
			"30 seconds",
					"1 minute",
			"3 minutes",
					"5 minutes",
					"10 minutes",
					"15 minutes",
					"30 minutes",
					"1 hour"};
	private static final long[] passwordTimeoutValues =
			{ 0 ,
					30*1000,
					60*1000,
			180*1000,
					300 * 1000,
					10 * 60 * 1000,
					15 * 60 * 1000,
					30 * 60 * 1000,
					60 * 60 * 1000};


	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);

		//we do this programatically because we have some dialog preferences
		//that can't easily be created using xml
        createPreferenceHierarchy();
    }

    private void createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        this.setPreferenceScreen(root);

        isCollectData = new CheckBoxPreference(this);
        isCollectData.setTitle(R.string.collect_gps_data);
        isCollectData.setSummary(R.string.collect_gps_data_desc);
        root.addPreference(isCollectData);
        isCollectData.setOnPreferenceClickListener(this);
        
        percTimeGpsOn = new SeekBarDialogPreference(this,
        		getText(R.string.title_perc_time_gps_on), 
        		getText(R.string.desc_perc_time_gps_on),
    			getResources().getInteger(R.dimen.perc_time_gps_on_min_value), 
    			getResources().getInteger(R.dimen.perc_time_gps_on_max_value), 
    			getResources().getInteger(R.dimen.perc_time_gps_on_steps), 
    			getResources().getInteger(R.dimen.perc_time_gps_on_log_scale),
    			getText(R.string.seekbar_perc_printf_format).toString(),
				null);
        percTimeGpsOn.setOnPreferenceChangeListener(this);
        root.addPreference(percTimeGpsOn);
        
        minBatteryLife = new SeekBarDialogPreference(this,
        		getText(R.string.title_min_battery_life_perc), 
        		getText(R.string.desc_min_battery_life_perc),
    			getResources().getInteger(R.dimen.min_battery_level_min_value), 
    			getResources().getInteger(R.dimen.min_battery_level_max_value), 
    			getResources().getInteger(R.dimen.min_battery_level_steps), 
    			getResources().getInteger(R.dimen.min_battery_level_log_scale),
    			getText(R.string.seekbar_perc_printf_format).toString(),
				null);
        minBatteryLife.setOnPreferenceChangeListener(this);
        root.addPreference(minBatteryLife);
        
        enableToolTips = new CheckBoxPreference(this);
        enableToolTips.setTitle(R.string.enable_tooltips);
        enableToolTips.setKey("enable_tooltips");
        root.addPreference(enableToolTips);
        enableToolTips.setOnPreferenceClickListener(this);
        
        useMetricUnits = new CheckBoxPreference(this);
        useMetricUnits.setTitle(R.string.use_metric_units);
		useMetricUnits.setChecked(GTG.prefs.useMetric);
        root.addPreference(useMetricUnits);
        
        //note that we can't use setIntent() for these preferences, since that would
        // be interpreted as an outside action causing us to lose the password is set flag
        
        colorblindSettings = getPreferenceManager().createPreferenceScreen(this);
//        screenPref.setKey("screen_preference");
//        colorblindSettings.setIntent(new Intent(this, ChooseColorsScreen.class));
        colorblindSettings.setTitle(R.string.colorblind_title);
        colorblindSettings.setSummary(R.string.colorblind_summary);
        colorblindSettings.setOnPreferenceClickListener(this);
        
        root.addPreference(colorblindSettings);
        
        mapFontSize = new SeekBarDialogPreference(this,
        		getText(R.string.title_map_font_size), 
        		getText(R.string.desc_map_font_size),
    			getResources().getInteger(R.dimen.map_font_size_min_value), 
    			getResources().getInteger(R.dimen.map_font_size_max_value), 
    			getResources().getInteger(R.dimen.map_font_size_steps), 
    			getResources().getInteger(R.dimen.map_font_size_log_scale),
    			"%1.0f",
				null);
        mapFontSize.setOnPreferenceChangeListener(this);
        root.addPreference(mapFontSize);
        

        enablePassword = new CheckBoxPreference(this);
        enablePassword.setTitle(R.string.enable_password);
        enablePassword.setKey("enable_password");
        root.addPreference(enablePassword);
        enablePassword.setOnPreferenceClickListener(this);
        
        changePassword = new Preference(this);
        changePassword.setTitle(R.string.change_password);
        root.addPreference(changePassword);
        changePassword.setDependency("enable_password");
        changePassword.setOnPreferenceClickListener(this);

		passwordTimeout = new SeekBarDialogPreference(this,
				getText(R.string.title_password_timeout),
				getText(R.string.desc_password_timeout),
				0,
				passwordTimeoutStrs.length-1,
				passwordTimeoutStrs.length,
				0,
				null,
				new SeekBarWithText.CustomUpdateTextView() {
					@Override
					public String updateText(float value) {
						return passwordTimeoutStrs[(int)value];
					}
				});
		passwordTimeout.setOnPreferenceChangeListener(this);
		root.addPreference(passwordTimeout);

		createBackupFilePref = getPreferenceManager().createPreferenceScreen(this);
        createBackupFilePref.setTitle(R.string.create_backup_pref);
        root.addPreference(createBackupFilePref);
        createBackupFilePref.setOnPreferenceClickListener(this);

        restoreBackupFilePref = getPreferenceManager().createPreferenceScreen(this);
        restoreBackupFilePref.setTitle(R.string.restore_backup_pref);
        root.addPreference(restoreBackupFilePref);
        restoreBackupFilePref.setOnPreferenceClickListener(this);

        allowErrorReporting = new CheckBoxPreference(this);
        allowErrorReporting.setTitle(R.string.allow_error_reporting);
        allowErrorReporting.setSummary(R.string.allow_error_reporting_summary);
        allowErrorReporting.setChecked(GTG.isAcraEnabled(this));
        root.addPreference(allowErrorReporting);

        aboutPref = getPreferenceManager().createPreferenceScreen(this);
        aboutPref.setOnPreferenceClickListener(this);
        aboutPref.setTitle(R.string.about);
        root.addPreference(aboutPref);
    }
    

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
    	menu.add(R.string.go_to_main);
    	
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        GTG.ccRwtm.registerReadingThread();
        try {
        if(item.getTitle().equals(getText(R.string.go_to_main)))
        {
			startInternalActivity(new Intent(this, OsmMapGpsTrailerReviewerMapActivity.class));
        	finish();
            return true;
        }
        	
        return super.onOptionsItemSelected(item);
        }
        finally {
        	GTG.ccRwtm.unregisterReadingThread();
        }
    }

	

    @Override
    public void doOnResume() {
        super.doOnResume();
        enablePassword.setChecked(!GpsTrailerCrypt.prefs.isNoPassword);
        isCollectData.setChecked(GTG.prefs.isCollectData);
        percTimeGpsOn.setValue(GpsTrailerGpsStrategy.prefs.batteryGpsOnTimePercentage * 100);
        minBatteryLife.setValue(GTG.prefs.minBatteryPerc*100);
        enableToolTips.setChecked(OsmMapGpsTrailerReviewerMapActivity.prefs.enableToolTips);
		mapFontSize.setValue(OsmMapGpsTrailerReviewerMapActivity.prefs.panelScale);
		passwordTimeout.setValue(getPasswordTimeoutFromMS(GTG.prefs.passwordTimeoutMS));

		passwordTimeout.setEnabled(enablePassword.isChecked());

        //co: we no longer allow access to application after expiry (so we can justify not
        // serving tiles without a valid license)
//        if(GTG.isTrialPeriodExpired())
//        {
//        	isCollectData.setEnabled(false);
//        	isCollectData.setChecked(false);
//        }

        if(GTG.lastSuccessfulAction == GTGAction.TOOL_TIP_CLICKED)
        {
        	GTG.lastSuccessfulAction = null;
        	
        	//scroll done to tool tip option
	        ViewTreeObserver vto = getListView().getViewTreeObserver(); 
		    vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() { 
		    @Override 
		    public void onGlobalLayout() {
		    	
	        	PreferenceScreen screen = getPreferenceScreen();
	        	int i;
	        	for(i = 0; i < screen.getPreferenceCount(); i++) {
	        	   Preference p = screen.getPreference(i);
	        	   
	        	   if(p == enableToolTips)
	        		   break;
	        	}

	        	getListView().setSelection(i);
	        	
	        	getListView().getViewTreeObserver().removeGlobalOnLayoutListener(this); 
		    } 
		    }); 

        }

    }

	private float getPasswordTimeoutFromMS(long passwordTimeoutMS) {
		int res = Arrays.binarySearch(passwordTimeoutValues, passwordTimeoutMS);
		if(res >= 0)
			return res;
		return 0;
	}


	private void savePrefs()
	{
		//note, everything else (passwords, is collect gps) is saved immediately
		
		if(allowErrorReporting.isChecked() != GTG.isAcraEnabled(this))
		{
			GTG.enableAcra(this, allowErrorReporting.isChecked());
		}
		
		if(percTimeGpsOn.getValue() != GpsTrailerGpsStrategy.prefs.batteryGpsOnTimePercentage*100
			|| minBatteryLife.getValue() != GTG.prefs.minBatteryPerc*100)
		{
			GTG.prefs.useMetric = useMetricUnits.isChecked();
			
			GTG.runBackgroundTask(SAVE_PREFS_AND_RESTART_COLLECTOR);
		}
		else if (GTG.prefs.useMetric != useMetricUnits.isChecked() ||
				OsmMapGpsTrailerReviewerMapActivity.prefs.enableToolTips != enableToolTips.isChecked())
		{
			GTG.prefs.useMetric = useMetricUnits.isChecked();
			OsmMapGpsTrailerReviewerMapActivity.prefs.enableToolTips = enableToolTips.isChecked();
			GTG.savePreferences(SettingsActivity.this);
		}
	}

	@Override
	public void doOnPause(boolean doOnResumeCalled) {
		super.doOnPause(doOnResumeCalled);
		
		if(!doOnResumeCalled)
			return;
					
		savePrefs();
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if(preference == minBatteryLife)
		{
			GTG.prefs.minBatteryPerc = minBatteryLife.getValue()/100;
			GTG.runBackgroundTask(SAVE_PREFS_AND_RESTART_COLLECTOR);
		}
		else if(preference == percTimeGpsOn)
		{
			GpsTrailerGpsStrategy.prefs.batteryGpsOnTimePercentage = percTimeGpsOn.getValue()/100;
			GTG.runBackgroundTask(SAVE_PREFS_AND_RESTART_COLLECTOR);
		}
		else if(preference == mapFontSize)
		{
			OsmMapGpsTrailerReviewerMapActivity.prefs.panelScale = (int)(mapFontSize.getValue()+.5);
			//GTG.savePreferences(SettingsActivity.this);
		}
		else if(preference == passwordTimeout)
		{
			GTG.prefs.passwordTimeoutMS = passwordTimeoutValues[(int)(passwordTimeout.getValue())];
			//GTG.savePreferences(SettingsActivity.this);
		}
		return false;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		
		if(id == 0 || id == 1)
			dialog = new PasswordDialog(this, GpsTrailerCrypt.prefs.isNoPassword);
		else if(id == 2 || id == 3)
			dialog = new PasswordDialog(this, true);
		else
			return super.onCreateDialog(id);
		
		dialog.setOnDismissListener(this);
		
		return dialog;
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		enablePassword.setChecked(!GpsTrailerCrypt.prefs.isNoPassword);
		passwordTimeout.setEnabled(enablePassword.isChecked());
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if(preference == enablePassword)
		{
			if(enablePassword.isChecked())
				showDialog(0);
			else
				showDialog(1);
		}
		else if(preference == changePassword)
		{
			if(GpsTrailerCrypt.prefs.isNoPassword)
				showDialog(2);
			else showDialog(3);
		}
		else if(preference == isCollectData)
		{
			GTG.prefs.isCollectData = isCollectData.isChecked();
			GTG.runBackgroundTask(SAVE_PREFS_AND_RESTART_COLLECTOR);
		}
		else if(preference == createBackupFilePref)
		{
			startInternalActivity(new Intent(this, CreateGpxBackup.class));
		}
		else if(preference == restoreBackupFilePref)
		{
			//we don't require the backstack, because NOT_IN_RESTORE Requirement is
			//required by most pages, except that it can't be required for RestoreGpxBackup
			startInternalActivity(new Intent(this, RestoreGpxBackup.class), false);
		}
		else if(preference == colorblindSettings)
			startInternalActivity(new Intent(this, ChooseColorsScreen.class));
		else if(preference == aboutPref)
			startInternalActivity(new Intent(this, AboutScreen.class));
		else
			return false;
		
		return true;
	}

	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI;
	}

	

}
