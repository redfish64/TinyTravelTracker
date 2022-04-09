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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class GpsTrailerReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("GTG", "GpsTrailerReceiver called");

        ContextCompat.startForegroundService(context,new Intent(context,
                GpsTrailerService.class));
//		Toast.makeText(context, "Good morning! " +intent.getAction(),
//				Toast.LENGTH_SHORT).show();
	}
}