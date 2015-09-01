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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTGActivity;
import com.rareventure.gps2.R;
import com.rareventure.gps2.gpx.CreateGpxBackup;

public class TrialExpiredActivity extends GTGActivity
{
	public TrialExpiredActivity()
	{
	}
	

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.trial_expired_activity);
		//HACK
		throw new IllegalStateException();
	}


	@Override
	public void doOnResume()
	{
		super.doOnResume();
		
		if(GTG.calcDaysBeforeTrialExpired() != 0)
		{
			finish();
		}
		
	}

	public void onBuyFullVersion(View view) {
		startActivity(GTG.BUY_PREMIUM_INTENT);
	}
	
	public void onCreateBackup(View view) {
		startInternalActivity(new Intent(this, CreateGpxBackup.class));
	}
	
	public void onExit(View view)
	{
		exitFromApp();
	}


	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_TRIAL_EXPIRED_ACTIVITY;
	}
	

}
