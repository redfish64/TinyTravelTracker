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
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;

import com.igisw.openlocationtracker.GTGActivity;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GpsTrailerDbProvider;
import com.igisw.openlocationtracker.GpsTrailerService;
import com.igisw.openlocationtracker.GTG.Requirement;

public class TurnOnGpsPage extends GTGActivity {
	
	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.wizard_turn_on_gps);
		
		((RadioButton) findViewById(R.id.yes)).setChecked(true);
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
		boolean isChecked = ((RadioButton)findViewById(R.id.yes)).isChecked();
		GTG.prefs.isCollectData = isChecked;
		GTG.savePreferences(this);

		if(isChecked)
		{
			startInternalActivity(new Intent(this, BatteryLifePage.class));
		}
		else
		{
			startInternalActivity(new Intent(this, HelpDeveloperPage.class));
		}
		
	}

	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_WIZARD;
	}
}
