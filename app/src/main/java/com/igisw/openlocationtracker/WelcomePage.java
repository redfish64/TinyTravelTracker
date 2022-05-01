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

import java.io.File;
import java.util.Map;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import com.igisw.openlocationtracker.GpsReader;
import com.igisw.openlocationtracker.ProgressDialogActivity;
import com.igisw.openlocationtracker.SimpleEula;
import com.igisw.openlocationtracker.SimpleEula.EulaListener;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GpsTrailerService;
import com.igisw.openlocationtracker.ITrialService;
import com.igisw.openlocationtracker.GpsTrailerReceiver;

public class WelcomePage extends ProgressDialogActivity implements EulaListener
{
	private boolean eulaAgreedTo;
	
	private boolean isMoveTrialRunning;

	private SimpleEula eulaDialog;
	
	public WelcomePage()
	{
	}
	

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.wizard_welcome);
	}


	@Override
	public void doOnResume()
	{
		super.doOnResume();
	
		startWelcomePage(false);
	}
	
	
	
	@Override
	public void doOnPause(boolean doOnResumeCalled) {
		super.doOnPause(doOnResumeCalled);
		
		if(eulaDialog != null)
			eulaDialog.dismiss();
	}


	private void startWelcomePage(boolean triedMovingTrialData)
	{
		//prevents the initial setup screens from showing when the system is already set up
		if(GTG.prefs.initialSetupCompleted)
		{
			finish();
			return;
		}

		/*
		//check if we are premium and the trial version is installed. In this case we 
		//just copy it's data and prefs over to us
		if(GTG.IS_PREMIUM == -42)
		{
			if(GTG.getGTGAppStart(this, GTG.TRIAL_APPLICATION_PACKAGE) != null && !triedMovingTrialData)
			{
				WTask wTask = new WTask() {
					
					@Override
					public void cancel() {
						
					}
				};
				
	        	super.openDialogForWTask(wTask, false, true, R.string.dialog_long_task_title,
	        			R.string.moving_trial_data_to_premium_long_task);
	        	
	        	//move the trial data into the premium package
	        	moveTrialDataToPremiumDir(wTask);
	        	return;
			}
		}
		*/

		if(!eulaAgreedTo)
			(eulaDialog = new SimpleEula(this, this, getString(R.string.eula))).show();
	}

	public void onPrev(View view) {
		exitFromApp();
		eulaAgreedTo = false;
	}
	
	

	@Override
	public void onBackPressed() {
		eulaAgreedTo = false;
		super.onBackPressed();
	}


	public void onNext(View view) {
		//if we are moving the trial to premium, this will handle
		//moving the app forward and back. Otherwise bad things can
		//occur if this was pressed at the wrong time
//		E/ACRA    (23459): java.lang.IllegalStateException: Could not execute method of the activity
//		E/ACRA    (23459):      at android.view.View$1.onClick(View.java:3591)
//		E/ACRA    (23459):      at android.view.View.performClick(View.java:4084)
//		E/ACRA    (23459):      at android.view.View$PerformClick.run(View.java:16966)
//		E/ACRA    (23459):      at android.os.Handler.handleCallback(Handler.java:615)
//		E/ACRA    (23459):      at android.os.Handler.dispatchMessage(Handler.java:92)
//		E/ACRA    (23459):      at android.os.Looper.loop(Looper.java:137)
//		E/ACRA    (23459):      at android.app.ActivityThread.main(ActivityThread.java:4745)
//		E/ACRA    (23459):      at java.lang.reflect.Method.invokeNative(Native Method)
//		E/ACRA    (23459):      at java.lang.reflect.Method.invoke(Method.java:511)
//		E/ACRA    (23459):      at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:786)
//		E/ACRA    (23459):      at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:553)
//		E/ACRA    (23459):      at dalvik.system.NativeStart.main(Native Method)
//		E/ACRA    (23459): Caused by: java.lang.reflect.InvocationTargetException
//		E/ACRA    (23459):      at java.lang.reflect.Method.invokeNative(Native Method)
//		E/ACRA    (23459):      at java.lang.reflect.Method.invoke(Method.java:511)
//		E/ACRA    (23459):      at android.view.View$1.onClick(View.java:3586)
//		E/ACRA    (23459):      ... 11 more
//		E/ACRA    (23459): Caused by: java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
//		E/ACRA    (23459):      at android.support.v4.app.FragmentManagerImpl.checkStateLoss(FragmentManager.java:1299)
//		E/ACRA    (23459):      at android.support.v4.app.FragmentManagerImpl.enqueueAction(FragmentManager.java:1310)
//		E/ACRA    (23459):      at android.support.v4.app.BackStackRecord.commitInternal(BackStackRecord.java:541)
//		E/ACRA    (23459):      at android.support.v4.app.BackStackRecord.commit(BackStackRecord.java:525)
//		E/ACRA    (23459):      at android.support.v4.app.DialogFragment.show(DialogFragment.java:123)
//		E/ACRA    (23459):      at com.rareventure.android.ProgressDialogActivity.showDialog(ProgressDialogActivity.java:47)
//		E/ACRA    (23459):      at com.rareventure.android.ProgressDialogActivity.runLongTask(ProgressDialogActivity.java:55)
//		E/ACRA    (23459):      at com.rareventure.gps2.reviewer.wizard.WelcomePage.onNext(WelcomePage.java:94)
//		E/ACRA    (23459):      ... 14 more
		if(isMoveTrialRunning)
			return;
		
		startInternalActivity(new Intent(WelcomePage.this, ShouldHavePasswordPage.class));
	}


	@Override
	public void onEulaDecision(boolean thumbsUp) {
		if(!thumbsUp)
			this.exitFromApp(); //exit setup
		else
			eulaAgreedTo = true;
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_WIZARD;
	}

	/*
	public void moveTrialDataToPremiumDir(final WTask wTask) {
    	isMoveTrialRunning = true;
		Intent i = new Intent(ITrialService.class.getName()).setPackage(GTG.TRIAL_APPLICATION_PACKAGE);

		boolean boundSuccessfully = true;
		
		ServiceConnection serviceConnection = null;
		
		try {
			
	        if(!bindService(i ,serviceConnection = new ServiceConnection() {
				
	        	private boolean serviceAlreadyConnected;
	        	
				@Override
				public void onServiceDisconnected(ComponentName name) {
				}
				
				@Override
				public void onServiceConnected(ComponentName name, IBinder service) {
					try {
						Log.d(GTG.TAG, "welcome page: service connected");
						if(serviceAlreadyConnected)
							return;
						serviceAlreadyConnected = true;
						ITrialService mService = ITrialService.Stub.asInterface(service);
						Map<String,Object> prefsMap = mService.giveMePreferences();
						String trialDirString = mService.getExtFileDir();
		
						Log.d(GTG.TAG, "welcome page: got ext file dir");
						// we don't worry about synchronization here, because we only do this during
						// initial setup
						GTG.prefSet.loadAndroidPreferencesFromMap(WelcomePage.this, prefsMap);
		
						GTG.prefSet.saveSharedPrefs(WelcomePage.this);
						
						mService.notifyReplaced();
						
						//shutdown the trial app commpletely before we start moving the files
						try {
							mService.shutdown();
						}
						catch(Exception e)
						{
							//an exception is thrown because it kills the process
							Log.i(GTG.TAG,"Trial shutdown");
						}
						
						File trialDir = new File(trialDirString);
						
						File myFileDir = GTG.getExternalStorageDirectory();
						
						for(File subFile : trialDir.listFiles())
						{
							//we don't freak out on failure here. Hopefully gps.db3 will be moved at least.
							//if not, a new db will be created with no points
							if(!subFile.renameTo(new File(myFileDir+"/"+subFile.getName())))
								Log.e(GTG.TAG,"Couldn't move file "+subFile+" to "+myFileDir);
						}
					}
					catch(Exception e)
					{
						Log.e(GTG.TAG,"Error trying to move trial data", e);
					}
					
					wTask.notifyFinish();
					//restart startReviewer now that we may have trial data
		        	runOnUiThread(new Runnable () { public void run() {startWelcomePage(true); }});
		        	unbindService(this);
		        	
		        	//we restart the trial app, so that it will shut off the notification frog. Since we kill it before we
		        	//moved the files away, it didn't have time to shut it off itself.
		        	Intent i = new Intent(GpsTrailerReceiver.class.getName()).setPackage(GTG.TRIAL_APPLICATION_PACKAGE);
		        	
		        	Log.d(GTG.TAG, "Sending broadcast to "+i+", name "+GpsTrailerReceiver.class.getName());
		        	sendBroadcast(i);
				}
			}
	                , Context.BIND_AUTO_CREATE))
	        	boundSuccessfully = false;
		}
		catch(Exception e)
		{
			Log.e(GTG.TAG,"Exception trying to bind service", e);
			boundSuccessfully = false;
		}
		finally
		{
		}
		
		if(!boundSuccessfully)
        {
			Log.d(GTG.TAG, "welcome page: could not bind service");
        	wTask.notifyFinish();
        	isMoveTrialRunning = false;
        	startWelcomePage(true);
        	
        	//even if we don't bind the service, we still have to unbind or android complains about a leaked service connection
			if(serviceConnection != null)
				unbindService(serviceConnection);
        }
	}
	*/
}
