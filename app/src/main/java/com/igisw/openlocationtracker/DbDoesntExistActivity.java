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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.igisw.openlocationtracker.ProgressDialogActivity;
import com.igisw.openlocationtracker.ProgressDialogActivity.Task;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GTG.Requirement;
import com.igisw.openlocationtracker.GTGActivity;
import com.igisw.openlocationtracker.GpsTrailerCrypt;
import com.igisw.openlocationtracker.GpsTrailerDbProvider;
import com.igisw.openlocationtracker.CreateGpxBackup;
import com.igisw.openlocationtracker.ShouldHavePasswordPage;
import com.igisw.openlocationtracker.WelcomePage;

public class DbDoesntExistActivity extends ProgressDialogActivity
{
	public DbDoesntExistActivity()
	{
	}
	

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.db_doesnt_exist);
	}


	@Override
	public void doOnResume()
	{
		super.doOnResume();
	}

	public void onRecreateDatabase(View view) {
		if(!GpsTrailerDbProvider.isDatabasePresent())
		{
			super.runLongTask(new Task()
			{

				@Override
				public void doIt() {
					if (!GTG.getExternalStorageDirectory().exists())
					{
						throw new IllegalStateException("external storage directory doesn't exist");
					}

					GTG.createAndInitializeNewDbFile();
				}
				
				@Override
				public void doAfterFinish() {
					finish();
				}
				
				
			}, false, true, R.string.dialog_long_task_title,
			R.string.create_database_dialog_message);
		}
	}
	
	public void onExit(View view)
	{
		exitFromApp();
	}


	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_DB_DOESNT_EXIST_PAGE;
	}
}
