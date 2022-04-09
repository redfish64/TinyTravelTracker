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

import java.io.IOException;

import android.os.Bundle;

import com.igisw.openlocationtracker.ProgressDialogActivity;
import com.igisw.openlocationtracker.GTG;

public class TimmyNeedsProcessingActivity extends ProgressDialogActivity
{
	public TimmyNeedsProcessingActivity()
	{
	}
	

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
	}


	@Override
	public void doOnResume()
	{
		super.doOnResume();

		super.runLongTask(new Task()
		{

			@Override
			public void doIt() {
				try {
					GTG.timmyDb.open();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
			
			@Override
			public void doAfterFinish() {
				finish();
				GTG.timmyDb.resetCancelOpen();
			}
			
			@Override 
			public void cancel()
			{
				GTG.timmyDb.cancelOpen();
			}
			
		}, false, true, R.string.dialog_long_task_title,
		R.string.rollforward_timmy_database);
	}
	


	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_BASIC_PASSWORD_PROTECTED_UI;
	}
}
