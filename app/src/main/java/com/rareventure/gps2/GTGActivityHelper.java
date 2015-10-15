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

import com.rareventure.android.FatalErrorActivity;
import com.rareventure.gps2.GTG.Requirement;
import com.rareventure.gps2.gpx.RestoreGpxBackup;
import com.rareventure.gps2.reviewer.DbDoesntExistActivity;
import com.rareventure.gps2.reviewer.TimmyCorruptActivity;
import com.rareventure.gps2.reviewer.TimmyNeedsProcessingActivity;
import com.rareventure.gps2.reviewer.TimmyNeedsUpgradeActivity;
import com.rareventure.gps2.reviewer.TrialExpiredActivity;
import com.rareventure.gps2.reviewer.password.EnterPasswordActivity;
import com.rareventure.gps2.reviewer.wizard.EnterNewPasswordPage;
import com.rareventure.gps2.reviewer.wizard.WelcomePage;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class GTGActivityHelper {
	private Activity activity;
	private IGTGActivity iGtgActivity;

	private Bundle doOnCreateBundle;

	/**
	 * All the requirements needed to run the activity in the current state. This will also
	 * include requirements inherited by the intent. (This is so if the app is killed and
	 * restarted and the page doesn't require, for example, a password, but the previous
	 * page does, it will still be asked for, in order that if the user hits the back button,
	 * it won't then ask for a password)
	 */
	private int neededRequirements;
	
	
	private static enum State {
		START,
		IN_DO_ON_CREATE,
		DO_CREATE_CALLED,
		IN_DO_ON_RESUME,
		DO_RESUME_CALLED,
		DO_PAUSE_CALLED
	}
	
	private boolean isNextActionInternal = false;
	
	private State state = State.START;
	
	/**
	 * Note this is static so that an activity in the stack can set the intent and
	 * then back out to the root which will then execute it.
	 */
	private static Intent exitingFromAppIntent;
	private boolean forwarded;
	
	/**
	 * true if we want to exit from the app completely
	 */
	private static boolean exitingFromApp;
	
	public static final String INTENT_REQUIREMENTS_BITMAP = GTGActivityHelper.class+".INTENT_REQUIREMENTS_BITMAP";
	

	/**
	 * Did the user hit back? If so we don't ever prompt for anything
	 * and simply assume the user doesn't want to enter
	 */
	private static boolean isBackAction;
	
	public GTGActivityHelper(Activity activity, int activityRequirements)
	{
		this.activity = activity;
		this.iGtgActivity = (IGTGActivity) activity;
		
		this.neededRequirements = activityRequirements;
	}

	/**
	 * We store requirements from the previous pages in the call stack
	 * in the intent itself. We make sure all these requirements are
	 * satisfied for the current page regardless if it requires them 
	 * or not, so if the user hits back, they won't be prompted for
	 * some weird requirements.
	 */
	private void loadRequirementsFromIntent()
	{
		Intent i = activity.getIntent();

		neededRequirements = (neededRequirements | i.getIntExtra(INTENT_REQUIREMENTS_BITMAP, 0));
	}
	
	public void onCreate(Bundle bundle)
	{
		//we always start the service incase the process was killed. If the service shouldn't
		//be running, it will stop itself
		//PERF maybe a little wasteful to keep restarting the service if it is not supposed to
		//be running
        activity.startService(new Intent(activity,
                GpsTrailerService.class));

        if(state != State.START)
			throw new IllegalStateException("Don't call me from doOnCreate " +
					"(or some other problem, why is state "+state+"?)");

		//load previous requirements from back stack
		loadRequirementsFromIntent();

		//if something is required that is not present but can be solved by user interaction
		// we do so here
		//note, it's really important that we don't call initAndForwardToHandlingScrenIfNecessary twice.
		//This is because when exitingFromApp is true, this value gets immediately shut off when 
		//we reach the top of the task. So if we let initAndForward be called again, it would try
		//to forward to fix the requirements that are unfulfilled
		if(initAndForwardToHandlingScreenIfNecessary())
		{
			this.doOnCreateBundle = bundle;
			this.forwarded = true;
			return;
		}
		
		state = State.IN_DO_ON_CREATE;
		
		iGtgActivity.doOnCreate(bundle);
		
		state = State.DO_CREATE_CALLED;
	}
	

	public void onResume()
	{	
		if(forwarded)
			return;
		
		loadRequirementsFromIntent();

		//if something is required that is not present but can be solved by user interaction
		// we do so here
		if(initAndForwardToHandlingScreenIfNecessary())
		{
			return;
		}
		
		//if we had to forwardToHandlingScreenIfNecessary() from onCreate(), we haven't yet called DO_ON_CREATE, 
		//so we do so now
		if(state == State.START)
		{
			state = State.IN_DO_ON_CREATE;
			
			iGtgActivity.doOnCreate(doOnCreateBundle);
			
			doOnCreateBundle = null;
			
			state = State.DO_CREATE_CALLED;
		}
		
		//I don't quite trust android to always get the states right (I have noticed that onDestroy()
		// sometimes is not called when screens are flipping in fast succession) so I don't worry
		// as long as it's not being called from doOnResume
		if(state == State.IN_DO_ON_RESUME)
			throw new IllegalStateException("Don't call me from doOnResume" +
					"(or some other problem, why is state "+state+"?)");


		state = State.IN_DO_ON_RESUME;
		
		//if we are going to actually display something, we no longer are backing out
		isBackAction = false;

		//we don't know what the next action will be.. if the user hits home or did some other action
		//that leaves the app, then when we get to onPause this value won't change and we know this
		isNextActionInternal = false;
		
		iGtgActivity.doOnResume();
		
		state = State.DO_RESUME_CALLED;		
	}
	
	/**
	 * Inits the system according to the requirements. If necessary, goes to a support
	 * activity for user interactive intialization (such as entering password, or reporting
	 * the db needs upgrading, etc.) 
	 */
	private boolean initAndForwardToHandlingScreenIfNecessary() {
		if (exitingFromApp) {
			activity.finish();
			if(activity.isTaskRoot())
			{
				if (exitingFromAppIntent != null)
					activity.startActivity(exitingFromAppIntent);
				
				exitingFromApp = false;
				isBackAction = false;
			}
			
			return true;
		}

		GTG.initRwtm.registerWritingThread();

		try {
			//these are the requirements that haven't already been fulfilled, but need to be
			int requirementsDiff = neededRequirements
					& (~GTG.fulfilledRequirements);

			GTG.requireInitialSetup(activity, true);

			if (Requirement.PREFS_LOADED.isOn(requirementsDiff))
				GTG.requirePrefsLoaded(activity);

			if (Requirement.NOT_IN_RESTORE.isOn(requirementsDiff)) {
				if (!GTG.requireNotInRestore()) {
					if(!isBackAction)
						//start an internal activity without requiring the back stack requirements
						startInternalActivity(new Intent(activity,
							RestoreGpxBackup.class), false);
					else
						activity.finish();

					return true;
				}
			}

			if (Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE
					.isOn(requirementsDiff)) {
				Intent i = GTG.requireNotTrialWhenPremiumIsAvailable(activity);

				if (i != null) {
					exitFromApp(i);

					return true;
				}
			}

			if (Requirement.NOT_TRIAL_EXPIRED.isOn(requirementsDiff)) {
				if (!GTG.requireNotTrialExpired()) {
					if(!isBackAction)
						//start an internal activity without requiring the back stack requirements
						startInternalActivity(new Intent(activity,
							TrialExpiredActivity.class), false);
					else
						activity.finish();

					return true;
				}
			}

			if (Requirement.SDCARD_PRESENT.isOn(requirementsDiff)) {
				if (!GTG.requireSdcardPresent(activity)) {
					if(!isBackAction)
						startInternalActivity(new Intent(activity,
							FatalErrorActivity.class).putExtra(
							FatalErrorActivity.MESSAGE_RESOURCE_ID,
							R.string.error_reviewer_sdcard_not_mounted), false);
					else
						activity.finish();

					return true;
				}
			}

			if (Requirement.SYSTEM_INSTALLED.isOn(requirementsDiff)) {
				if (!GTG.requireSystemInstalled(activity)) {
					if(!isBackAction)
						startInternalActivity(new Intent(activity,
							WelcomePage.class), false);
					else
						activity.finish();

					return true;
				}
			}

			if (Requirement.DB_READY.isOn(requirementsDiff)) {
				int status = GTG.requireDbReady();

				if (status == GTG.REQUIRE_DB_READY_DB_DOESNT_EXIST) {
					if(!isBackAction)
						startInternalActivity(new Intent(activity,
							DbDoesntExistActivity.class), false);
					else
						activity.finish();

					return true;
				}
			}

			if (Requirement.DECRYPT.isOn(requirementsDiff)) {
				//we don't want the user to have to retype their password right after creating it
				if (GTG.requireEncryptAndDecrypt(EnterNewPasswordPage.passwordInitializedWith) != GTG.REQUIRE_DECRYPT_OK) {
					if(!isBackAction)
						startInternalActivity(
							new Intent(activity, EnterPasswordActivity.class)
									.putExtra(
											EnterPasswordActivity.EXTRA_DECRYPT_OR_VERIFY_PASSWORD_BOOL,
											true), false);
					else
						activity.finish();

					return true;
				}
				
			}

			if (Requirement.ENCRYPT.isOn(requirementsDiff)) {
				GTG.requireEncrypt();
			}

			if (Requirement.PASSWORD_ENTERED.isOn(requirementsDiff)) {
				//we don't want the user to have to retype their password right after creating it
				if (!GTG.requirePasswordEntered(EnterNewPasswordPage.passwordInitializedWith, GTG.lastGtgClosedMS)) {
					if(!isBackAction)
						startInternalActivity(
							new Intent(activity, EnterPasswordActivity.class)
									.putExtra(
											EnterPasswordActivity.EXTRA_DECRYPT_OR_VERIFY_PASSWORD_BOOL,
											false), false);
					else
						activity.finish();

					return true;
				}
			}

			if (Requirement.TIMMY_DB_READY.isOn(requirementsDiff)) {
				int status = GTG.requireTimmyDbReady(false);

				if (status == GTG.REQUIRE_TIMMY_DB_IS_CORRUPT) {
					if(!isBackAction)
						startInternalActivity(new Intent(activity,
							TimmyCorruptActivity.class), false);
					else
						activity.finish();

					return true;
				}
				if (status == GTG.REQUIRE_TIMMY_DB_NEEDS_UPGRADING) {
					if(!isBackAction)
						startInternalActivity(new Intent(activity,
							TimmyNeedsUpgradeActivity.class), false);
					else
						activity.finish();

					return true;
				}
				if (status == GTG.REQUIRE_TIMMY_DB_NEEDS_PROCESSING_TIME) {
					if(!isBackAction)
						startInternalActivity(new Intent(activity,
							TimmyNeedsProcessingActivity.class), false);
					else
						activity.finish();

					return true;
				}
			}

			return false;
		}
		finally {
			GTG.initRwtm.unregisterWritingThread();
		}
	}

	/**
	 * Finishes the current activity and goes to the previous activity in the task.
	 */
	public void finish()
	{

		if(!activity.isTaskRoot())
			isNextActionInternal = true;
		
		iGtgActivity.superFinish();
	}
	
	/**
	 * Starts a new activity within the application
	 * @param requireBackStack true if the requirements from the activities in the 
	 *   back stack should be merged with the requirements for the current activity
	 */
	public void startInternalActivity(Intent intent, boolean requireBackStack)
	{
		isNextActionInternal = true;
		
		if(requireBackStack)
			addRequirementsToIntent(intent);
		
		activity.startActivity(intent);
		
	}
	
	public void startInternalActivity(Intent intent)
	{
		startInternalActivity(intent, true);
	}

	/**
	 * Adds requirements needed for this page and pages in the back stack
	 * to the intent
	 */
	private void addRequirementsToIntent(Intent intent) {
		intent.putExtra(INTENT_REQUIREMENTS_BITMAP, neededRequirements);
	}

	public void onPause() {
		forwarded = false;
		
		if(activity.isTaskRoot())
		{
			isBackAction = false;
			exitingFromApp = false;
		}
		
		if(!isNextActionInternal)
		{
			//we want to clear the initial password if the user leaves the app
			EnterNewPasswordPage.passwordInitializedWith = null;
			GTG.setAppPasswordNotEntered();
			
			//they might have went to buy the premium package
			Requirement.NOT_TRIAL_WHEN_PREMIUM_IS_AVAILABLE.reset();
		}
		
		iGtgActivity.doOnPause(state == State.DO_RESUME_CALLED);
	}

	public void onBackPressed() {
		isBackAction = true;
		
		finish();
	}

	public void performCancel() {
		isBackAction = true;
		
		finish();
	}

	public void exitFromApp()
	{
		exitFromApp(null);
	}
	
	public void exitFromApp(Intent i)
	{
		if(activity.isTaskRoot())
		{
			activity.startActivity(i);
			isBackAction = false;
		}
		else {
			exitingFromApp = true;
			exitingFromAppIntent = i;
		}
		activity.finish();
		return;
	}

	public void startInternalActivityForResult(Intent i, int s) {
		isNextActionInternal = true;
		
		activity.startActivityForResult(i, s);
	}

}
