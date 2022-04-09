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
package com.igisw.openlocationtracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

public class SimpleEula {

	private String EULA_PREFIX = "eula_";
	private Activity mActivity;
	private EulaListener eulaListener;
	private String message;
	
	private Dialog dialog;
	
	public static interface EulaListener
	{
		public void onEulaDecision(boolean thumbsUp);
	}

	public SimpleEula(Activity context, EulaListener eulaListener, String message) {
		mActivity = context;
		this.eulaListener = eulaListener;
		this.message = message;
	}

	private PackageInfo getPackageInfo() {
		PackageInfo pi = null;
		try {
			pi = mActivity.getPackageManager().getPackageInfo(
					mActivity.getPackageName(), PackageManager.GET_ACTIVITIES);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		return pi;
	}

	public void show() {
		PackageInfo versionInfo = getPackageInfo();

		// the eulaKey changes every time you increment the version number in
		// the AndroidManifest.xml
		// final String eulaKey = EULA_PREFIX + versionInfo.versionCode;
		final SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(mActivity);
		// boolean hasBeenShown = prefs.getBoolean(eulaKey, false);
		// if(hasBeenShown == false){

		// Show the Eula
		String title = mActivity.getString(R.string.app_name) + " v"
				+ versionInfo.versionName;

		AlertDialog.Builder builder = new AlertDialog.Builder(mActivity)
				.setTitle(title)
				.setMessage(message)
				.setPositiveButton(R.string.accept,
						new Dialog.OnClickListener() {

							@Override
							public void onClick(
									DialogInterface dialogInterface, int i) {
								// // Mark this version as read.
								// SharedPreferences.Editor editor =
								// prefs.edit();
								// editor.putBoolean(eulaKey, true);
								// editor.commit();
								eulaListener.onEulaDecision(true);
							}
						})
				.setNegativeButton(R.string.decline,
						new Dialog.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								// Close the activity as they have declined the
								// EULA
								dialog.dismiss();
								eulaListener.onEulaDecision(false);
							}

						});
		builder.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode,
					KeyEvent event) {
				if ((keyCode == KeyEvent.KEYCODE_HOME))
					return false;
				if (keyCode == KeyEvent.KEYCODE_BACK)
				{
					// Close the activity as they have declined the
					// EULA
					dialog.dismiss();
					eulaListener.onEulaDecision(false);
				}
				
				return true;
			}
		});
		builder.setCancelable(false);
		(dialog = builder.create()).show();
		// }
	}

	public void dismiss() {
		if(dialog != null && dialog.isShowing())
			dialog.dismiss();
	}
}