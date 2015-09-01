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
package com.rareventure.gps2.reviewer.wizard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;

import com.rareventure.android.ProgressDialogActivity;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerCrypt;
import com.rareventure.gps2.R;
import com.rareventure.gps2.GTG.Requirement;

public class ShouldHavePasswordPage extends ProgressDialogActivity {
	
	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		
		setContentView(R.layout.wizard_should_have_password);
		
		((RadioButton) findViewById(R.id.no)).setChecked(true);
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
		if(((RadioButton)findViewById(R.id.yes)).isChecked())
			startInternalActivity(new Intent(this, EnterNewPasswordPage.class));
		else
		{
			super.runLongTask(new Task()
			{

				@Override
				public void doIt() {
					//incase the user visited the enter password page, and hit back and changed their
					//mind, the db may be open and ready with the old crypt and db data
					//so we close it to make sure it isn't the case
					GTG.closeDbAndCrypt();

					GpsTrailerCrypt.setupPreferencesForCrypt(ShouldHavePasswordPage.this, null);
					GTG.createAndInitializeNewDbFile();
					
					EnterNewPasswordPage.passwordInitializedWith = null;
				}

				@Override
				public void doAfterFinish() {
					startInternalActivity(new Intent(ShouldHavePasswordPage.this, TurnOnGpsPage.class));
				}
				
			}, false, true, R.string.dialog_long_task_title,
			R.string.wizard_setup_encryption_for_database);
		}
	}

	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_WIZARD;
	}

}
