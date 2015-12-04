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
package com.rareventure.gps2.reviewer.map;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.Gallery;
import android.widget.Gallery.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.rareventure.gps2.R;
import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.cache.MediaLocTime;
import com.rareventure.gps2.reviewer.SettingsActivity;
import com.rareventure.gps2.reviewer.imageviewer.ViewImage;

public class MediaGalleryFragment extends Fragment
{

	private ArrayList<MediaLocTime> mlts;
	private OsmMapGpsTrailerReviewerMapActivity gtum;
	private Gallery gallery;
	private MltAdapter adapter;
	
	private int lastGalleryPosition;
	
	public MediaGalleryFragment(OsmMapGpsTrailerReviewerMapActivity gtum, ArrayList<MediaLocTime> mlts) {
		this.gtum = gtum;
		this.mlts = mlts;
	}
	
	public MediaGalleryFragment()
	{
		//TODO 2.5 how to handle no arg constructor calls
		
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		if(gtum == null)
			return null;
		
		
		View v = inflater.inflate(R.layout.media_gallery, container, false);
		
		View glassPane = (View) v.findViewById(R.id.glass_pane);
		
		glassPane.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				finishBrowsing();
			}
		});
		
        // Reference the Gallery view
        gallery = (Gallery) v.findViewById(R.id.gallery);
        
        //remove the alphaness
        gallery.setUnselectedAlpha(1.0f);
        
        gallery.setSpacing((int) Util.convertDpToPixel(10, this.getActivity()));
        
        // Set a item click listener, and just Toast the clicked position
        gallery.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView parent, View v, int position, long id) {
            	MediaLocTime mlt = mlts.get((int)id);
            	
            	if(!mlt.isClean(gtum))
            		return;
            	
            	ViewImage.mAllImages = mlts;
            	ViewImage.mCurrentPosition = (int) id;
            	
                Intent intent = new Intent(getActivity(), ViewImage.class);  
                gtum.startInternalActivity(intent); 
//            	FullMediaGalleryActivity.setFullMediaGalleryActivityData(mlts, (int)id);
//            	startActivity(new Intent(getActivity(), FullMediaGalleryActivity.class));
//            	
//            	if(mlt.getType() == MediaLocTime.TYPE_IMAGE)
//            		Util.viewMediaInGallery(gtum, mlt.getFilename(), true);
//            	else
//            		Util.viewMediaInGallery(gtum, mlt.getFilename(), false //video
//            				);
            }
        });
        
        final TextView index = (TextView) v.findViewById(R.id.index);
        final TextView timeAndTimezone = (TextView) v.findViewById(R.id.time_and_timezone);
        
        final String indexTextFormat = getResources().getText(R.string.x_of_x_items_format).toString(); 
        final String nothingTextFormat = getResources().getText(R.string.x_items_format).toString(); 
        final String timeWithTimezoneFormat = getResources().getText(R.string.media_gallery_strip_time_with_timezone).toString(); 
        
        final SimpleDateFormat timeAndDateSdf = new SimpleDateFormat(getString(R.string.time_and_date_format));

        
        gallery.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				int secs = mlts.get(position).getTimeSecs();
				
				/* ttt_installer:remove_line */Log.d(GTG.TAG,"Mlt selected: "+mlts.get(position));

				TimeZone tz = GTG.tztSet.
						getTimeZoneTimeOrNullIfUnknwonOrLocalTime(secs);
				
				if(tz != null)
					timeAndDateSdf.setTimeZone(tz);
				else
					timeAndDateSdf.setTimeZone(Util.getCurrTimeZone());
				
				String dateStr = timeAndDateSdf.format(new Date(secs * 1000l));
				
				index.setText(String.format(indexTextFormat,(position+1), mlts.size()));
				
				if(tz != null)
					timeAndTimezone.setText(String.format(timeWithTimezoneFormat, 
						dateStr, tz.getDisplayName()));
				else
					timeAndTimezone.setText(dateStr);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				index.setText(String.format(nothingTextFormat,mlts.size()));
				
				timeAndTimezone.setText("");
				
			}
		});
        ;
       ((Gallery) v.findViewById(R.id.gallery)).setAdapter(adapter = 
    	   new MltAdapter(getActivity(), inflater, mlts));
        
        // Inflate the layout for this fragment
        return v;
	}
	
	
	
//	public void setMlts(ArrayList<MediaLocTime> mltArray)
//	{
//		this.mlts = mltArray;
//		
//        // Set the adapter to our custom adapter (below)
//		 ((Gallery) getView().findViewById(R.id.gallery)).setAdapter(new MltAdapter(getActivity(), mltArray));
//	}
	
	public void finishBrowsing() {
		FragmentManager fragmentManager = gtum.getSupportFragmentManager();
		fragmentManager.popBackStack();
	}

	@Override
	public void onResume() {
		super.onResume();

		if(adapter == null)
			return;

		//TODO 2.1 use startActivityForResult and use the picture selected in that screen as the position
		gallery.setSelection(lastGalleryPosition >= mlts.size() ? mlts.size()-1 : lastGalleryPosition);
		adapter.notifyDataSetChanged();
		gtum.registerMediaGalleryFragment(this);
		
		
	}
	
	



	@Override
	public void onPause() {
		super.onPause();

		//TODO 2.5 the gtum can be null if we get a no arg constructor call
		if(gtum != null)
			gtum.unregisterMediaGalleryFragment(this);
		
		//sometimes onCreateView isn't being called when gallery is paused (after kill and restart)
		if(gallery != null)
			lastGalleryPosition = gallery.getSelectedItemPosition();
	}





	private static class MltAdapter extends BaseAdapter
	{
//		/**
//		 * For some reason, the width seems not correspond to what
//		 * I put here..  I need to investigate how this will show
//		 * on different screens / why this happens
//		 */
//		private static final float WEIRD_LAYOUT_WIDTH_MULTIPLIER = 1.7f;
		
		private ArrayList<MediaLocTime> mltArray;
		private Context context;
		
		private FrameLayout fl;
		private ImageView imageView;
		private ImageView videoMarkerView;
		private LayoutInflater inflater;
		private ContentResolver contentResolver; 

		public MltAdapter(Context context, LayoutInflater inflater, ArrayList<MediaLocTime> mltArray)
		{
			this.context = context;
			this.mltArray = mltArray;
			this.inflater = inflater;
			
			fl = new FrameLayout(context);
			imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            
            videoMarkerView = new ImageView(context);
            videoMarkerView.setImageBitmap(((BitmapDrawable) context.getResources().
    				getDrawable(R.drawable.small_video_indicator)).getBitmap());
            videoMarkerView.setScaleType(ImageView.ScaleType.CENTER);
            
			
			fl.addView(imageView);
			fl.addView(videoMarkerView);
			
			contentResolver = context.getContentResolver();
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
			GTG.ccRwtm.registerReadingThread();
			try {
				MediaLocTime mlt = mltArray.get(position);
				
				View fl = convertView;

				if (fl == null) {

					fl = inflater.inflate(R.layout.media_gallery_media, parent,
							false);
				}

				ImageView imageView = (ImageView) fl.findViewById(R.id.image);
				ImageView videoMarkerView = (ImageView) fl
						.findViewById(R.id.video_indicator);

				if (!mlt.isDeleted() && mlt.isClean(context)) {
					Bitmap b = mltArray.get(position).getThumbnailBitmap(
							contentResolver, true);

					imageView.setImageBitmap(b);

					if (mlt.isVideo()) {
						videoMarkerView.setVisibility(View.VISIBLE);
					} else
						videoMarkerView.setVisibility(View.GONE);

					// LayoutParams layoutParams = new
					// Gallery.LayoutParams((int) (b.getWidth() *
					// WEIRD_LAYOUT_WIDTH_MULTIPLIER),
					// b.getHeight());
					// i.setLayoutParams(layoutParams);
				} else {
					Bitmap b = ((BitmapDrawable) context.getResources().
							getDrawable(R.drawable.white1x1)).getBitmap();
					
					imageView.setImageBitmap(b);

					
//					LayoutParams layoutParams = new Gallery.LayoutParams(0, 100);
//					imageView.setLayoutParams(layoutParams);
//					imageView.setImageBitmap(null);

					if (!mlt.isDeleted())
						GTG.mediaLocTimeMap.notifyMltNotClean(mlt);

					videoMarkerView.setVisibility(View.GONE);
				}

//				Log.d(GTG.TAG, "getView for " + position);
				return fl;
			} finally {
				GTG.ccRwtm.unregisterReadingThread();
			}
		}
	}

}
