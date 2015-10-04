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

import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTGActivity;
import com.rareventure.gps2.R;

public class HelpDeveloperPage extends GTGActivity
{
	public HelpDeveloperPage()
	{
	}

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.wizard_help_developer);
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

	public void onPrev(View target) {
		finish();
	}

	public void onNext(View target) {
		GTG.enableAcra(this,((RadioButton)findViewById(R.id.yes)).isChecked());
		GTG.savePreferences(this);
		
		startInternalActivity(new Intent(this, ConclusionPage.class));		
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_WIZARD;
	}

}
