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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;

import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GTGActivity;

public class FatalErrorActivity extends GTGActivity {

	public static final String MESSAGE_RESOURCE_ID = FatalErrorActivity.class.getName()+".MESSAGE_RESOURCE_ID";

	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FATAL_ERROR;
	}

	@Override
	public void doOnResume() {
		super.doOnResume();
		
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setMessage(getText(getIntent().getIntExtra(MESSAGE_RESOURCE_ID, 0)));
		alert.setPositiveButton(R.string.exit,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int whichButton) {
						exitFromApp();
					}
				});
		alert.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				exitFromApp();
			}
		});
		alert.show();
	}
	
	

}
