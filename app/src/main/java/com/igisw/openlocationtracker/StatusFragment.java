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

import java.util.Map.Entry;
import java.util.TreeMap;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rareventure.gps2.IGTGActivity;
//import com.rareventure.gps2.R;

/**
 * Reports one or more generic messages that can be turned on/off independently
 */
public abstract class StatusFragment extends Fragment {
	
	private static class TextData
	{
		CharSequence text;
		TextView textView;
		Intent internalGTGIntent;
		Runnable runnable;
	}
	
	private TreeMap<Integer, TextData> map = new TreeMap<Integer, TextData>();
	private LinearLayout textViews;
	private Activity activity;
	private Runnable updateTextDatas = new Runnable() {
		
		@Override
		public void run() {
			
			synchronized (StatusFragment.this)
			{
				textViews.removeAllViews();
				
				boolean addedView = false;
				
				for(Entry<Integer, TextData> e : map.entrySet())
				{
					final TextData td = e.getValue();
					
					if(td.text == null)
						continue;
					
					if(td.textView == null)
					{
						td.textView = new TextView(activity);
						td.textView.setTextColor(Color.WHITE);
					}
					
					td.textView.setText(td.text);
					
					if(td.internalGTGIntent != null || td.runnable != null)
					{
						td.textView.setOnClickListener(new OnClickListener() {
							
							@Override
							public void onClick(View v) {
								if(td.internalGTGIntent != null) 
									((IGTGActivity)activity).startInternalActivity(td.internalGTGIntent);
								else 
									td.runnable.run();
							}
						});
					}
					else td.textView.setOnClickListener(null);
					
					textViews.addView(td.textView);
					
					addedView = true;
				}
				
				view.setVisibility(addedView ? View.VISIBLE : View.GONE);
			}
			
		}
	};
	private View view;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {		
		view = inflater.inflate(getLayoutId(), container, false);
		textViews = (LinearLayout) view.findViewById(R.id.textViews);
		
		activity = getActivity();
		
		view.setVisibility(View.GONE);
		
		return view;
	}
	
	protected abstract int getLayoutId();
	
	/**
	 * @param internalGTGIntent don't use an external intent here, or the password will not be asked for on return
	 */
	public synchronized void registerProcess(Integer key, final CharSequence val, Intent internalGTGIntent, Runnable r)
	{
		TextData textData = map.get(key);
		
		if(textData == null)
		{
			textData = new TextData();
			
			map.put(key, textData);
		}
		
		if(textData.text == null || !textData.text.equals(val))
		{
			textData.text = val;
			
			textData.internalGTGIntent = internalGTGIntent;
			textData.runnable = r;
	
			activity.runOnUiThread(updateTextDatas);
		}
	}
	

	public synchronized void unregisterProcess(Integer key)
	{
		final TextData td = map.get(key);
		
		if(td != null)
		{
			td.text = null;
			activity.runOnUiThread(updateTextDatas );
		}
	}
	
}
