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

import java.io.IOException;

import android.os.Bundle;
import android.view.View;

import com.igisw.openlocationtracker.ProgressDialogActivity;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GpsTrailerDbProvider;
import com.igisw.openlocationtracker.GTG.Requirement;

public class TimmyCorruptActivity extends ProgressDialogActivity
{
	public TimmyCorruptActivity()
	{
	}
	

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.timmy_corrupt);
	}


	@Override
	public void doOnResume()
	{
		super.doOnResume();
	}

	public void onOk(View view) {
		//TODO 3.5 make a blank screen appear before showing dialog to make it look better
		super.runLongTask(new Task()
		{

			@Override
			public void doIt() {
				try {
					if(GTG.timmyDb != null)
						GTG.timmyDb.close();
					
					GpsTrailerDbProvider.deleteUnopenedCache();
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
				GTG.timmyDb = null;
				Requirement.TIMMY_DB_READY.reset();
			}
			
			@Override
			public void doAfterFinish() {
				finish();
			}
			
			
		}, false, true, R.string.dialog_long_task_title,
		R.string.please_wait);
	}


	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_BASIC_PASSWORD_PROTECTED_UI;
	}
	
}
