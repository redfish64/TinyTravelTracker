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
package com.rareventure.gps2;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import com.rareventure.gps2.R;
import com.rareventure.gps2.reviewer.SettingsActivity;

import android.app.Application;
import android.content.Intent;

/**
 * Does stuff that is common to all gps trailer reviewer type things
 */
@ReportsCrashes(
//		formKey="dFp0X2pTTTV1am5kNHczbk1lTE5rYVE6MQ",
//		formKey="",
		formUri = "http://rareventure.com:3127/reportCrash",
//		formUri = "http://10.32.13.200:3127/reportCrash",
//        mode = ReportingInteractionMode.DIALOG,
		//we need silent here because of the way we forward from one activity to another in case of an
		//error. If mode is TOAST or DIALOG, for some reason it ends up in an infinite loop constantly
		// repeating the exception (when the exception occurs without user input)
        mode = ReportingInteractionMode.SILENT,
        resToastText = R.string.crash_toast_text, // optional, displayed as soon as the crash occurs, before collecting data which can take a few seconds
        resDialogText = R.string.crash_dialog_text,
        resDialogIcon = android.R.drawable.ic_dialog_info, //optional. default is a warning sign
        resDialogTitle = R.string.crash_dialog_title, // optional. default is your application name
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. when defined, adds a user text field input with this text resource as a label
        resDialogOkToast = R.string.crash_dialog_ok_toast // optional. displays a Toast message when the user accepts to send a report.
        )
public class GpsTrailerReviewerApplication extends Application
{
    @Override
    public void onCreate() {
        // The following line triggers the initialization of ACRA
        ACRA.init(this);
        
        super.onCreate();
    }
}
