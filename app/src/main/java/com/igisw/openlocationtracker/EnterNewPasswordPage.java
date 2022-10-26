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
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.igisw.openlocationtracker.GpsTrailerDbProvider;
import com.igisw.openlocationtracker.ProgressDialogActivity;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GTG.Requirement;
import com.rareventure.gps2.database.TAssert;
import com.igisw.openlocationtracker.GpsTrailerCrypt;

public class EnterNewPasswordPage extends ProgressDialogActivity {

	private OnEditorActionListener onEditorActionListener = new OnEditorActionListener() {
		
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			((Button)findViewById(R.id.next)).performClick();
			return false;
		}
	};
	
	public static String passwordInitializedWith;
	
	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		
		setContentView(R.layout.wizard_enter_password);
		
		((EditText) findViewById(R.id.reenter_new_password)).setOnEditorActionListener(onEditorActionListener);

	}
	
	@Override
	public void doOnResume()
	{
		super.doOnResume();
		//prevents the initial setup screens from showing when the system is already set up
		if(GTG.prefs.initialSetupCompleted)
			finish();
	}

	public void onPrev(View view) {
		finish();
	}

	public void onNext(View view) {
		EditText enterNewPassword = (EditText) findViewById(R.id.enter_new_password); 
		EditText reenterNewPassword = (EditText) findViewById(R.id.reenter_new_password); 
		
		if(enterNewPassword.getText().toString().equals
				(reenterNewPassword.getText().toString()))
		{
			if(enterNewPassword.getText().length() == 0)
			{
				Toast.makeText(this, R.string.error_password_cant_be_empty, Toast.LENGTH_LONG).show();
				return;
			}
			
			final String password = enterNewPassword.getText().toString(); 

			enterNewPassword.getEditableText().clear();
			reenterNewPassword.getEditableText().clear();
			
			super.runLongTask(new Task()
			{

				@Override
				public void doIt() {
					//incase the user visited the enter password page, and hit back and changed their
					//mind, the db may be open and ready with the old crypt and db data
					//so we close it to make sure it isn't the case
					GTG.closeDbAndCrypt();
					
					GpsTrailerCrypt.setupPreferencesForCrypt(EnterNewPasswordPage.this, password);
					GTG.createAndInitializeNewDbFile();
					
					//we then store the passoword temporarily in a static variable
					//this is ok from a security perspective because we're holding it only in
					//memory, and only this one time, and there really isn't much difference 
					//between holding it for a short time and a long time (across several pages) 
					//and we want to not prompt for a password after the initial setup is completed.
					passwordInitializedWith = password;
					
				}

				@Override
				public void doAfterFinish() {
					startInternalActivity(new Intent(EnterNewPasswordPage.this, TurnOnGpsPage.class));
				}
				
			}, false, true, R.string.dialog_long_task_title,
			R.string.wizard_setup_encryption_for_database);
		}
		else
		{
			Toast.makeText(this, R.string.new_passwords_dont_match, Toast.LENGTH_LONG).show();
			findViewById(R.id.enter_new_password).requestFocus();
		}
	}


	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_WIZARD;
	}
}
