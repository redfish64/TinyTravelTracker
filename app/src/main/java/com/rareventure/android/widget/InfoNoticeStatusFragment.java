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
package com.rareventure.android.widget;

import java.util.Map;
import java.util.TreeMap;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.rareventure.gps2.R;

/**
 * Reports status of ongoing processes
 */
public class InfoNoticeStatusFragment extends StatusFragment {

	public static final Integer FREE_VERSION = 1;
	public static final Integer NO_GPS_POINTS = 2;
	public static final Integer LOW_FREE_SPACE = 3;
	public static final Integer UNLICENSED = 4;

	@Override
	protected int getLayoutId() {
		
		return R.layout.info_notice_status_fragment;
	}
	
}
