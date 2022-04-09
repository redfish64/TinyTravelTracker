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

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.Gallery.LayoutParams;
import android.widget.ImageView;

import com.igisw.openlocationtracker.GTGActivity;
import com.igisw.openlocationtracker.Util;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.MediaLocTime;

public class FullMediaGalleryActivity extends GTGActivity
{

	private static ArrayList<MediaLocTime> mlts;
	private static int mltIndex;
	
	public static void setFullMediaGalleryActivityData(ArrayList<MediaLocTime> mlts, int mltIndex)
	{
		FullMediaGalleryActivity.mlts = mlts;
		FullMediaGalleryActivity.mltIndex = mltIndex;
	}
	
	private FragmentActivity gtum;
	private Gallery gallery;
	private MltAdapter adapter;

	public FullMediaGalleryActivity() {
	}

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);

		setContentView(R.layout.full_media_gallery);
		
        // Reference the Gallery view
        gallery = (Gallery) findViewById(R.id.gallery);
        
        //remove the alphaness
        gallery.setUnselectedAlpha(1.0f);
        
        gallery.setSpacing((int) Util.convertDpToPixel(10, this));
        
        // Set a item click listener, and just Toast the clicked position
        gallery.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView parent, View v, int position, long id) {
            	MediaLocTime mlt = mlts.get((int)id);
            	
            	if(!mlt.isClean(gtum))
            		return;
            	
            	if(mlt.getType() == MediaLocTime.TYPE_IMAGE)
            		Util.viewMediaInGallery(gtum, mlt.getFilename(getContentResolver()), true);
            	else
            		Util.viewMediaInGallery(gtum, mlt.getFilename(getContentResolver()), false //video
            				);
            }
        });
        
       gallery.setAdapter(adapter = new MltAdapter(this, mlts));
	}
	
	
	
//	public void setMlts(ArrayList<MediaLocTime> mltArray)
//	{
//		this.mlts = mltArray;
//		
//        // Set the adapter to our custom adapter (below)
//		 ((Gallery) getView().findViewById(R.id.gallery)).setAdapter(new MltAdapter(getActivity(), mltArray));
//	}
	
	@Override
	public void doOnResume() {
		super.doOnResume();
		adapter.notifyDataSetChanged();
	}



	private static class MltAdapter extends BaseAdapter
	{
		private ArrayList<MediaLocTime> mltArray;
		private Context context;

		public MltAdapter(Context context, ArrayList<MediaLocTime> mltArray)
		{
			this.context = context;
			this.mltArray = mltArray;
		}

		@Override
		public int getCount() {
			return mltArray.size();
		}

		@Override
		public Object getItem(int position) {
			return position;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			MediaLocTime mlt = mltArray.get(position);
			
            ImageView i = new ImageView(context);
            
			if(mlt.isClean(context))
			{
	            Bitmap b = mltArray.get(position).getActualBitmap(context);

	            i.setImageBitmap(b);
	            i.setScaleType(ImageView.ScaleType.CENTER);
	            
//	            LayoutParams layoutParams = new Gallery.LayoutParams((int) (b.getWidth() * WEIRD_LAYOUT_WIDTH_MULTIPLIER),
//	            		b.getHeight());
//	            i.setLayoutParams(layoutParams);
			}
			else
			{
	            LayoutParams layoutParams = new Gallery.LayoutParams(0, 100);
	            i.setLayoutParams(layoutParams);
	            
	            if(!mlt.isDeleted())
	            	GTG.mediaLocTimeMap.notifyMltNotClean(mlt);
			}
            
            return i;
		}
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI;
	}
}
