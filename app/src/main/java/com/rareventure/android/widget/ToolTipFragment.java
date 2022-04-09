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

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.rareventure.gps2.IGTGActivity;
import com.rareventure.gps2.R;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGAction;
import com.rareventure.gps2.reviewer.SettingsActivity;

/**
 * Reports status of ongoing processes
 */
public class ToolTipFragment extends Fragment {
	private TextView status;
	private View view;
	private ImageView relatedImage;
	private boolean enabled = true;
	
	public static enum UserAction
	{
		PAN_TO_LOCATION_BUTTON(	R.string.pan_to_location_button_tooltip, R.drawable.pan_to_location_white), 
		AUTOZOOM_BUTTON(R.string.autozoom_button_tooltip, R.drawable.autozoom_white), DATE_PICKER, 
		TIME_VIEW_CHANGE(R.string.timeview_tooltip), ZOOM_IN, ZOOM_OUT, MAP_VIEW_MOVE, MAP_VIEW_PINCH_ZOOM, SELECTED_AREA_ADD_UNLOCKED, SELECTED_AREA_ADD_LOCKED;
		
		private int textId = -1;
		private int imageId = -1;
		
		private UserAction()
		{
		}

		private UserAction(int textId)
		{
			this.textId = textId;
		}
		
		private UserAction(int textId, int imageId)
		{
			this.textId = textId;
			this.imageId = imageId;
		}
	}
	
	public ToolTipFragment()
	{
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {		
		view = inflater.inflate(R.layout.tool_tip, container, false);
		status = (TextView) view.findViewById(R.id.status);
		relatedImage = (ImageView) view.findViewById(R.id.relatedImage);

		view.setVisibility(View.GONE);
		view.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
	        	GTG.lastSuccessfulAction = GTGAction.TOOL_TIP_CLICKED;
				((IGTGActivity)getActivity()).startInternalActivity(new Intent(getActivity(), SettingsActivity.class));
			}
		});

		return view;
	}
	

	@Override
	public void onPause() {
		super.onPause();
	}


	@Override
	public void onResume() {
		super.onResume();
	}


	public void setAction(UserAction action) {
		if(!enabled)
			return;
		
		if(action.textId != -1)
		{
			status.setText(action.textId);
			view.setVisibility(View.VISIBLE);
			
			if(action.imageId != -1)
			{
				relatedImage.setVisibility(View.VISIBLE);
				relatedImage.setImageResource(action.imageId);
			}
			else
				relatedImage.setVisibility(View.GONE);
		}
		else
		{
			status.setText("");
			view.setVisibility(View.GONE);
		}
	}

	public void setEnabled(boolean enableToolTips) {
		if(!enableToolTips)
			view.setVisibility(View.GONE);
		this.enabled = enableToolTips;
	}
	
}
