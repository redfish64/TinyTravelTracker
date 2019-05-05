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

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.mapzen.tangram.CameraPosition;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.android.DbUtil;
import com.rareventure.android.FatalErrorActivity;
import com.rareventure.android.ProgressDialogActivity;
import com.rareventure.android.SuperThread;
import com.rareventure.android.SuperThreadManager;
import com.rareventure.android.Util;
import com.rareventure.android.widget.InfoNoticeStatusFragment;
import com.rareventure.android.widget.OngoingProcessStatusFragment;
import com.rareventure.android.widget.ToolTipFragment;
import com.rareventure.android.widget.ToolTipFragment.UserAction;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;
import com.rareventure.gps2.GTG.GTGEventListener;
import com.rareventure.gps2.GTG.Requirement;
import com.rareventure.gps2.GpsTrailerService;
import com.rareventure.gps2.R;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.cache.AreaPanelSpaceTimeBox;
import com.rareventure.gps2.reviewer.EnterFromDateToToDateActivity;
import com.rareventure.gps2.reviewer.SettingsActivity;
import com.rareventure.gps2.reviewer.ShowManual;
import com.rareventure.gps2.reviewer.TrialExpiredActivity;
import com.rareventure.gps2.reviewer.map.sas.TimeRange;
import com.rareventure.gps2.reviewer.timeview.TimeView;
import com.rareventure.gps2.reviewer.timeview.TimeView.Listener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

//TODO 4: Satellite, street view??, traffic
public class OsmMapGpsTrailerReviewerMapActivity extends ProgressDialogActivity implements OnClickListener,  Listener,
GTGEventListener
{
	private ImageButton menuButton;
	private GpsLocationOverlay locationOverlay;
	private LocationManager locationManager;
	private boolean userDoesntWantGpsOn;

	public void setupLocationUpdates(GpsLocationOverlay gpsLocationOverlay) {
		//the user may have disabled us from reading location data
		if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED)
		{
			Criteria criteria = new Criteria();
			criteria.setSpeedRequired(false);
			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			criteria.setAltitudeRequired(false);
			criteria.setBearingRequired(false);
			criteria.setCostAllowed(false);

			String locationProviderName = locationManager.getBestProvider(criteria, true);
			locationManager.requestLocationUpdates(locationProviderName, 0, 0, gpsLocationOverlay, getMainLooper());
		}
	}

	public void removeLocationUpdates(GpsLocationOverlay gpsLocationOverlay) {
		locationManager.removeUpdates(gpsLocationOverlay);
	}


	private static enum SasPanelState { GONE, TAB, FULL;
	
	};
	
	private SasPanelState sasPanelState = SasPanelState.GONE;
	private TranslateAnimation slideSasFullToTab;
	private TranslateAnimation slideSasTabToFull;
	private TranslateAnimation slideSasFullToNone;
	private TranslateAnimation slideSasNoneToFull;
	private TranslateAnimation slideSasNoneToTab;
	private TranslateAnimation slideSasTabToNone;

	private TextView distanceView;

	@Override
	public void doOnCreate(Bundle savedInstanceState)
    {
        super.doOnCreate(savedInstanceState);

		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		ContextCompat.startForegroundService(this,new Intent(this, GpsTrailerService.class));

        timeAndDateSdf = new SimpleDateFormat(getString(R.string.time_and_date_format));
        
        GTG.cacheCreatorLock.registerReadingThread();
        try {
        	/* ttt_installer:remove_line */Log.d(GTG.TAG,"OsmMapGpsTrailerReviewerMapActivity.onCreate()");
	        
	        //sometimes onDestroy forgets to be called, so we need to check for this and cleanup after the last instance
	        if(reviewerStillRunning)
	        {
	        	Log.w(GTG.TAG,"OsmMapGpsTrailerReviewerMapActivity: onDestroy() forgot to be called!");
	        	cleanup();
	        }
	        
	        reviewerStillRunning = true;
	        
            setContentView(R.layout.osm_gps_trailer_reviewer);

			osmMapView = (OsmMapView) findViewById(R.id.osmmapview);
			osmMapView.onCreate(savedInstanceState);

			initUI();
            
    		ViewTreeObserver vto = this.findViewById(android.R.id.content).getViewTreeObserver(); 
    	    vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() { 
    	    @Override 
    	    public void onGlobalLayout() { 
    	    	initWithWorkingGetWidth();
    	    	findViewById(android.R.id.content).getViewTreeObserver().removeGlobalOnLayoutListener(this); 
    	    	osmMapView.initAfterLayout();
    	    } 
    	    }); 
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}

	protected void initWithWorkingGetWidth() {
        slideSasFullToTab = new TranslateAnimation(0, sasPanelButton.getWidth() - sasPanel.getWidth(), 0, 0);
        slideSasTabToFull = new TranslateAnimation(sasPanelButton.getWidth() - sasPanel.getWidth(), 0, 0, 0);
        slideSasTabToNone = new TranslateAnimation(sasPanelButton.getWidth() - sasPanel.getWidth(), - sasPanel.getWidth(), 0, 0);
		slideSasFullToNone = new TranslateAnimation(0, -sasPanel.getWidth(), 0, 0);
		slideSasNoneToFull = new TranslateAnimation(-sasPanel.getWidth(), 0, 0, 0);
		slideSasNoneToTab = new TranslateAnimation(-sasPanel.getWidth(),
				sasPanelButton.getWidth() - sasPanel.getWidth(), 0, 0);
		
		slideSasFullToTab.setDuration(500);
		slideSasTabToFull.setDuration(500);
		slideSasTabToNone.setDuration(200);
		slideSasFullToNone.setDuration(500);
		slideSasNoneToFull.setDuration(500);
		slideSasNoneToTab.setDuration(200);
		
		osmMapView.setZoomCenter(osmMapView.getWidth()/2,
				findViewById(R.id.timeview_layout).getTop()/2);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
        
        return true;
	}

	
	
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
    	menu.add(R.string.settings);
//    	if(prefs.showPhotos)
//    	{
//    		menu.add(R.string.turn_off_photos);
//    	}
//    	else
//    	{
//    		menu.add(R.string.turn_on_photos);
//    	}
//    	menu.add(R.string.help);
    	
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
        if(item.getTitle().equals(getText(R.string.settings)))
        {
        	startInternalActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        else if(item.getTitle().equals(getText(R.string.turn_off_photos)))
        {
        	prefs.showPhotos = false;
        	gpsTrailerOverlay.notifyViewNodesChanged();
        	
        	if(mediaGalleryFragment != null)
        		mediaGalleryFragment.finishBrowsing();
        	return true;
        }
        else if(item.getTitle().equals(getText(R.string.turn_on_photos)))
        {
        	prefs.showPhotos = true;
        	gpsTrailerOverlay.notifyViewNodesChanged();
        	return true;
        }
        else if(item.getTitle().equals(getText(R.string.help)))
        {
        	startInternalActivity(new Intent(this, ShowManual.class));
        	return true;
        }
        	
        return super.onOptionsItemSelected(item);
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
    }

    private static final int DIALOG_DELETE_NAMED_LOCATION = 1;
	private static final int INITIAL_SETUP_REQUEST = 0;
	private static final long MAX_SANE_PERIOD_MS = Util.MS_PER_YEAR * 10;
	public final Runnable NOTIFY_HAS_DRAWN_RUNNABLE = new Runnable() {
		
		@Override
		public void run() {
			notifyHasDrawn();
		}
	};
	
	private ImageView autoZoom;

	public GpsTrailerOverlay gpsTrailerOverlay;
	private long minRecordedTimeMs;
	private long maxRecordedTimeMs;
	public static Preferences prefs = new Preferences();
	OsmMapView osmMapView;
	MapScaleWidget scaleWidget;
	private GpsClickData gpsClickData;
	private Dialog currentDialog;
	
	public ImageView panToLocation;
	private boolean timeViewDisplayed;
	
	TimeView timeView;
	View datePicker;
	private OngoingProcessStatusFragment gtgStatus;
	ToolTipFragment toolTip;
	ZoomControls zoomControls;
	private InfoNoticeStatusFragment infoNotice;
	
//	private CheckBox selectedAreaAddLock;
	private View sasPanel;
	private ImageButton sasPanelButton;
//	private ImageButton selectedAreaView;
	//this is static because sometimes onDestroy from the last time this
	//activity was run isn't called. In this case we want to make sure to
	//be able to kill the threads before starting again
	// and it does create a completely new instance of this class
	private static SuperThreadManager superThreadManager;
	
	private static boolean reviewerStillRunning;
	
	
	/**
	 * Notifies the activity that the selected areas have been
	 * changed by the user.
	 * @param isSet true if there are any selected areas set after
	 *   the operation.
	 */
	public void notifySelectedAreasChanged(boolean isSet)
	{
		if(isSet)
		{
			if(sasPanelState == sasPanelState.GONE)
			{
				slideSas(SasPanelState.FULL);
			}
		}
		else
		{
			slideSas(SasPanelState.GONE);
		}
	}
	
	private void slideSas(final SasPanelState newState) {
		final TranslateAnimation anim;
		
		if(newState == SasPanelState.FULL)
		{
			if(sasPanelState == SasPanelState.GONE)
				anim = slideSasNoneToFull;
			else if (sasPanelState == SasPanelState.TAB)
			{
				anim = slideSasTabToFull;
			}
			else
				return; //nothing to do
		}
		else if(newState == SasPanelState.TAB)
		{
			if(sasPanelState == SasPanelState.GONE)
				anim = slideSasNoneToTab;
//				throw new IllegalStateException("not supported");
			else if (sasPanelState == SasPanelState.FULL)
				anim = slideSasFullToTab;
			else
				return; //nothing to do
		}
		else //if(newState == SasPanelState.GONE)
		{
			if(sasPanelState == SasPanelState.TAB)
				anim = slideSasTabToNone;
			else if (sasPanelState == SasPanelState.FULL)
				anim = slideSasFullToNone;
			else
				return; //nothing to do
		}
		
		if(sasPanelState == SasPanelState.TAB)
			sasPanel.findViewById(R.id.sas_main_panel).setVisibility(View.VISIBLE);
        sasPanel.startAnimation(anim);
        anim.setAnimationListener(new AnimationListener() {
			
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				if(newState == SasPanelState.TAB)
				{
					sasPanel.findViewById(R.id.sas_main_panel).setVisibility(View.GONE);
			        sasPanelButton.setBackgroundResource(R.drawable.sas_tab_out);
				}
				else
					sasPanelButton.setBackgroundResource(R.drawable.sas_tab_in);
				
				sasPanel.setVisibility(newState != SasPanelState.GONE ? View.VISIBLE : View.INVISIBLE);
				
				sasPanelState = newState;
			}
		});
        
	}
	


	protected void notifyHasDrawn() {
		
		TimeZoneTimeRow newTimeZone = GTG.tztSet.getTimeZoneCovering(gpsTrailerOverlay.closestToCenterTimeSec);
		
		timeView.updateTimeZone(newTimeZone);
		
		osmMapView.invalidate();
	}

	private void initUI()
	{
		TextView ty = (TextView) findViewById(R.id.thankyou);
	    ty.setMovementMethod(LinkMovementMethod.getInstance());

        sasPanelButton = (ImageButton) findViewById(R.id.sas_open_close_button);
        
        sasPanelButton.setOnClickListener(this);
		
        gtgStatus = (OngoingProcessStatusFragment) getSupportFragmentManager().
    	findFragmentById(R.id.status);

        toolTip = (ToolTipFragment) getSupportFragmentManager().
    	findFragmentById(R.id.tooltip);

        infoNotice = (InfoNoticeStatusFragment) getSupportFragmentManager().
    	findFragmentById(R.id.infoNotice);
    
        superThreadManager = new SuperThreadManager();
        SuperThread cpuThread = new SuperThread(superThreadManager);
        SuperThread fileIOThread = new SuperThread(superThreadManager);

        fileIOThread.start();
        cpuThread.start();

        //co: this has been replaced by notifyProcessing() method        
//        superThreadManager.setSleepingThreadsListener(new SuperThreadManager.SleepingThreadsListener() {
//			
//			@Override
//			public void notifySleepingThreadsChanged(final boolean allThreadsAreSleeping) {
//				runOnUiThread(new Runnable() {
//					
//					@Override
//					public void run() {
//						setProgressBarIndeterminateVisibility(!allThreadsAreSleeping);
//					}
//				});
//			}
//		});
        
        zoomControls = (ZoomControls)this.findViewById(R.id.zoomControls);
        zoomControls.setOnZoomInClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				osmMapView.zoomIn();

				toolTip.setAction(UserAction.ZOOM_IN);
			}
		});
        zoomControls.setOnZoomOutClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				osmMapView.zoomOut();

				toolTip.setAction(UserAction.ZOOM_OUT);
			}
		});

		osmMapView.addOverlay(gpsTrailerOverlay = new GpsTrailerOverlay(this, cpuThread, osmMapView));
		osmMapView.addOverlay(locationOverlay = new GpsLocationOverlay(this));
        osmMapView.init(fileIOThread, this);

        scaleWidget = (MapScaleWidget) this.findViewById(R.id.scaleWidget);
        
		osmMapView.setScaleWidget(scaleWidget);
		
        panToLocation = (ImageView)this.findViewById(R.id.pan_to_location);
        panToLocation.setOnClickListener(this);

        autoZoom = (ImageView)this.findViewById(R.id.AutoZoom);
        autoZoom.setOnClickListener(this);

		menuButton = (ImageButton) findViewById(R.id.menu_button);
		menuButton.setOnClickListener(this);

		datePicker = findViewById(R.id.date_picker);
		datePicker.setOnClickListener(this);

        timeView = (TimeView) findViewById(R.id.timeview);
		
		timeView.setListener(this);
		
		timeView.setActivity(this);
		
//		selectedAreaAddLock = (CheckBox) findViewById(R.id.selected_areas_add_lock);
//		selectedAreaAddLock.setOnCheckedChangeListener(new OnCheckedChangeListener() {
//			
//			@Override
//			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//				GpsTrailerOverlayDrawer.doMethodTracing = true;
//				if(isChecked)
//				{
//					toolTip.setAction(ToolTipFragment.UserAction.SELECTED_AREA_ADD_LOCKED);
//					gpsTrailerOverlay.setSelectedAreaAddLock(true);
//				}
//				else 
//				{
//					toolTip.setAction(ToolTipFragment.UserAction.SELECTED_AREA_ADD_UNLOCKED);
//					gpsTrailerOverlay.setSelectedAreaAddLock(false);
//				}
//			}
//		});
//		
		sasPanel = findViewById(R.id.sas_panel);
		sasPanel.setVisibility(View.INVISIBLE);
		
		distanceView = (TextView)findViewById(R.id.dist_traveled);
		updateDistanceView(-1);
		
		initSasPanel();
	}

	
	private SimpleDateFormat timeAndDateSdf;
	private MediaGalleryFragment mediaGalleryFragment;
	private boolean locationKnown;
	
	
	private void initSasPanel() {
		ListView sasPanelList = (ListView) findViewById(R.id.sas_grid);

		sasPanelList.setOnItemClickListener(new OnItemClickListener() {
			
			private static final int MIN_TR_TIMESPAN_SEC = Util.SECONDS_IN_DAY;
			private static final int TR_TIMESPAN_MULTIPLIER = 3;

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				//note that getTimeRange reuses the same tr instance, so we have to be careful
				// when we call it twice
				TimeRange tr = gpsTrailerOverlay.sas.getTimeRange(position-1);

				int currStartSec = tr.startTimeSec;
				int currEndSec = tr.endTimeSec;
				
				//co: we just want the time inside the box
//				int timeSpan;
//				
//				if(tr.fullRangeEndSec - tr.fullRangeStartSec < MIN_TR_TIMESPAN_SEC / TR_TIMESPAN_MULTIPLIER)
//				{
//					timeSpan = MIN_TR_TIMESPAN_SEC;
//				}
//				else
//				{
//					timeSpan = (tr.fullRangeEndSec - tr.fullRangeStartSec) * TR_TIMESPAN_MULTIPLIER;
//				}
//				
//				int currStartSec = (int) Math.max(- timeSpan /2 + (tr.endTimeSec/2 + tr.startTimeSec/2), getStartTimeMs()/1000);
//				int currEndSec = (int) Math.min(timeSpan /2 + (tr.endTimeSec/2 + tr.startTimeSec/2), getEndTimeMs()/1000);
//				
//				if(position > 0)
//				{
//					TimeRange prevTr = gpsTrailerOverlay.sas.getTimeRange(position-1);
//					currStartSec = Math.max(prevTr.endTimeSec, currStartSec);
//				}
//				
//				if(position < gpsTrailerOverlay.sas.getTimeRangeCount() - 1)
//				{
//					TimeRange nextTr = gpsTrailerOverlay.sas.getTimeRange(position+1);
//					currEndSec = Math.min(nextTr.startTimeSec, currEndSec);
//				}
//				
				if(currEndSec - currStartSec < timeView.getMinSelectableTimeSec())
				{
					currEndSec = currStartSec + timeView.getMinSelectableTimeSec();
				}
				
				setStartAndEndTimeSec(currStartSec, currEndSec);
				updateTimeViewTime();
				
			}
		});
		
		sasPanelList.setAdapter(new ListAdapter() {
			
			@Override
			public void unregisterDataSetObserver(DataSetObserver observer) {
				gpsTrailerOverlay.sas.unregisterDataSetObserver(observer);
				
			}
			
			@Override
			public void registerDataSetObserver(DataSetObserver observer) {
				gpsTrailerOverlay.sas.registerDataSetObserver(observer);
			}
			
			@Override
			public boolean isEmpty() {
				return gpsTrailerOverlay.sas.isEmpty();
			}
			
			@Override
			public boolean hasStableIds() {
				return false; //because we might merge timeviews
			}
			
			@Override
			public int getViewTypeCount() {
				return 2;
			}
			
			public View getView(int position, View convertView, ViewGroup parent) {
				if(convertView == null)
				{
//					Log.d(GTG.TAG,"SASPanel: Creating new view");
					if(position == 0)
						convertView = getLayoutInflater().
							inflate(R.layout.selected_area_info_top_row, null);
					else
					convertView = getLayoutInflater().
						inflate(R.layout.selected_area_info_row, null);
				}
//				else
//					Log.d(GTG.TAG,"SASPanel: Reusing view");

				if(position == 0)
				{
					((TextView)convertView.findViewById(R.id.totalTime)).setText(Util.convertMsToText(getApplicationContext(), gpsTrailerOverlay.sas.getTotalTimeSecs()*1000l));
					((TextView)convertView.findViewById(R.id.totalDist)).setText(MapScaleWidget.calcLabelForLength(gpsTrailerOverlay.sas.getTotalDistM(),
							GTG.prefs.useMetric));
					((TextView)convertView.findViewById(R.id.timesInArea)).setText(String.valueOf(gpsTrailerOverlay.sas.getTimesInArea()));
					//((TextView)convertView.findViewById(R.id.timesInArea)).setText(String.valueOf(gpsTrailerOverlay.sas.getTimesInArea()));
					// line executed twice
					
					TimeZone tz = gpsTrailerOverlay.sas.timeZone;
					
					if(tz == null || tz.hasSameRules(Util.getCurrTimeZone()))
					{
						convertView.findViewById(R.id.timeZoneTableRow).setVisibility(View.GONE);
					}
					else
					{
						convertView.findViewById(R.id.timeZoneTableRow).setVisibility(View.VISIBLE);
						((TextView)convertView.findViewById(R.id.timezone)).setText(tz.getDisplayName());
					}
					
					return convertView;
				}
				
				TimeRange tr = gpsTrailerOverlay.sas.getTimeRange(position-1);
				
				timeAndDateSdf.setTimeZone(gpsTrailerOverlay.sas.timeZone);
				
				String startText = timeAndDateSdf.format(new Date(tr.startTimeSec * 1000l));
				String endText = timeAndDateSdf.format(new Date(tr.endTimeSec * 1000l));
				
				((TextView)convertView.findViewById(R.id.startTime)).setText(startText);
				((TextView)convertView.findViewById(R.id.endTime)).setText(endText);
				
				String distText;
				if(tr.dist == -1)
					distText = "--";
				else
				{
					distText = MapScaleWidget.calcLabelForLength(tr.dist, GTG.prefs.useMetric);
				}
				
				((TextView)convertView.findViewById(R.id.distance)).setText(distText);
				
				//this fixes a bug where sometimes if the last row is deleted and readded, it isn't
				//layedout again, causing the date/times to be cut off
				convertView.requestLayout();
				
//				((TextView)convertView.findViewById(R.id.timeLength)).setText("a certain amount of time");
				
				return convertView;
			}
			
			@Override
			public int getItemViewType(int position) {
				if(position == 0)
					return 0;
				return 1;
			}
			
			@Override
			public long getItemId(int position) {
				if(position == 0)
					return Long.MIN_VALUE;
				
				TimeRange tr = gpsTrailerOverlay.sas.getTimeRange(position-1);

				return tr.fullRangeStartSec;
			}
			
			@Override
			public Object getItem(int position) {
				if(position == 0)
					return null;
				return gpsTrailerOverlay.sas.getTimeRange(position-1);
			}
			
			@Override
			public int getCount() {
				int count = gpsTrailerOverlay.sas.getTimeRangeCount();
//				Log.d(GTG.TAG,"item count is "+count);
				
				if(count >= 1)
					return count+1;
				return 0;
			}
			
			@Override
			public boolean isEnabled(int position) {
				if(position == 0)
					return false;
				return true;
			}
			
			@Override
			public boolean areAllItemsEnabled() {
				return false;
			}
		});
	}

//    protected Dialog onCreateDialog(int id) {
//    	if(currentDialog != null)
//    		currentDialog.dismiss();
//    	
//        switch (id) {
//
//        case DIALOG_DELETE_NAMED_LOCATION:
//            this.currentDialog = new AlertDialog.Builder(this)
//                .setTitle(Util.varReplace(this.getString(R.string.delete_named_location), 
//                		"item", gpsClickData.name))
//                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int whichButton) {
//                    	deleteNamedLocation();
//                    }
//
//                })
//                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int whichButton) {
//                    }
//                })
//                .create();
//        }
//        
//        return null;
//    }
    
	//TODO 3: have a clearish dial. So the dial darkens only when you drape your finger over the screen
	// otherwise it is clear, or minimized so that it doesn't take up too much screen real estate.
	
	
	@Override
	public void onClick(View v) {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
        if(v == panToLocation)
		{
        	if(locationKnown)
        	{
				osmMapView.panTo(locationOverlay.getLastLoc());
				toolTip.setAction(ToolTipFragment.UserAction.PAN_TO_LOCATION_BUTTON);
        	}
        	else
        	{
        		Toast.makeText(OsmMapGpsTrailerReviewerMapActivity.this, R.string.location_unknown, Toast.LENGTH_SHORT).show();
        	}
		}
		else if(v == datePicker)
		{
			startInternalActivity(new Intent(this, EnterFromDateToToDateActivity.class));
			toolTip.setAction(ToolTipFragment.UserAction.DATE_PICKER);
			
		}
		else if(v == autoZoom)
		{
//			GpsTrailerOverlayDrawer.turnOnMethodTracing = true;
			
			doAutozoom(true);

			toolTip.setAction(ToolTipFragment.UserAction.AUTOZOOM_BUTTON);
        }
		else if(v == menuButton)
		{
			startInternalActivity(new Intent(this, SettingsActivity.class));
			//openOptionsMenu();
		}
		else if(v == sasPanelButton)
		{
			if(sasPanelState == SasPanelState.FULL)
				slideSas(SasPanelState.TAB);
			else
				slideSas(SasPanelState.FULL);
		}
//		else if(v == selectedAreaView)
//		{
//			datePicker.setVisibility(View.INVISIBLE);
//			timeView.setVisibility(View.INVISIBLE);
//			toolTip.getView().setVisibility(View.INVISIBLE);
//			
////			SelectedAreaInfoActivity.sas = gpsTrailerOverlay.sas;
////			startActivity(new Intent(this, SelectedAreaInfoActivity.class));
//		}
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }

	}

	private void doAutozoom(boolean showToastOnFailure) {
		AreaPanelSpaceTimeBox stBox;
		
		gpsTrailerOverlay.sas.rwtm.registerReadingThread();
		
		try {
	    	stBox = GTG.apCache.getAutoZoomArea(prefs.currTimePosSec, 
	    			prefs.currTimePosSec + prefs.currTimePeriodSec, gpsTrailerOverlay.sas.getResultPaths());

			if(stBox == null)
			{
				if(showToastOnFailure)
					Toast.makeText(OsmMapGpsTrailerReviewerMapActivity.this, getText(R.string.auto_zoom_no_data), Toast.LENGTH_SHORT).show();
				return; 
			}
			
			osmMapView.panAndZoom(stBox.minX,stBox.minY,stBox.maxX,stBox.maxY);
		}
		finally
		{
			gpsTrailerOverlay.sas.rwtm.unregisterReadingThread();
		}
	}
	
	private Float determineSpeed(long lastId, Integer lastLatM, Integer lastLonM, Long lastTime,
			Integer latm, Integer lonm, Long time) {
		Cursor c = null;
		
		try {
			if(lastLatM == null)
			{
				c = GTG.db.rawQuery(
						"select lat_md, lon_md, time from GPS_LOCATION_TIME where _id = ?", new String[] { String.valueOf(lastId) });
				if(!c.moveToNext()) //if we can't find the previous id
					return null;
				lastLatM = c.getInt(0);
				lastLonM = c.getInt(1);
				lastTime = c.getLong(2);
			}
			if(latm == null)
			{
				c = GTG.db.rawQuery(
						"select lat_md, lon_md, time from GPS_LOCATION_TIME where _id = ?", 
						new String[] { String.valueOf(lastId + 1) });
				if(!c.moveToNext()) //if we can't find the previous id
					return null;
				latm = c.getInt(0);
				lonm = c.getInt(1);
				time = c.getLong(2);
			}
			
			Location l1 = new Location("me");
			l1.setLatitude(lastLatM/1000000f);
			l1.setLongitude(lastLonM/1000000f);
			
			Location l2 = new Location("me");
			l2.setLatitude(latm/1000000f);
			l2.setLongitude(lonm/1000000f);
			
			long period = time - lastTime;
			if(period <=0) period = 1;
			
			return l1.distanceTo(l2) / period;
		}
		finally {
			DbUtil.closeCursors(c);
		}
	}

	public void redrawMap() {
		osmMapView.redrawMap();
	}		

	public static class Preferences implements AndroidPreferences
	{

		public long minTimePeriodMs = 300*1000;

		public boolean showPhotos = true;
		
		/**
		 * The amount of scaling of a panel, in terms of zoom level. ie. 2 would equal 2x, and would mean
		 * the smallest tile would be spread out over twice the area in x and y.
		 */
		public int panelScale = 2;

		public long lastCheckedGpsLocationIdForUIManager = Long.MIN_VALUE;
		/**
		 * The amount of padding
		 */
		public float zoomPaddingPerc = .2f;
		public int [] allColorRanges = 
			new int [] { 0xff000000, 0xff808080, 0xffff0000, 0xffff8000, 0xffffff00, 0xff80ff00, 0xff00ff00, 
				0xff00ff80,
        		0xff00ffff, 0xff0080ff, 0xff0000ff, 0xffffffff};
		
		//to change default colors, make sure to also update selectedColorRangesBitmap
		public int [] colorRange = 
			new int [] { 0xffff0000, 0xffff8000, 0xffffff00, 0xff80ff00, 0xff00ff00, 
				0xff00ff80,
        		0xff00ffff, 0xff0080ff, 0xff0000ff };

		public int currTimePosSec = (int)(System.currentTimeMillis()/1000l) - Util.SECONDS_IN_DAY, currTimePeriodSec = Util.SECONDS_IN_DAY*3;
	
		
		/**
		 * Last location of screen when TTT was last visited.
		 */
		public double lastLon, lastLat;
		public float lastZoom;

		/**
		 * Boolean map of color ranges that are in use in allColorRanges
		 */
		//no black, grey or white
		public int selectedColorRangesBitmap = ((1<<12)-1)&(~(2|1|(1<<11)));

		public boolean enableToolTips = true;

		public void updateColorRangeBitmap(int newColorRangesBitmap) {
			int [] newColorRange = new int[allColorRanges.length];
			
			int ncrIndex = 0;
			
			for(int i = 0; i < allColorRanges.length; i++)
			{
				if(((1<<i)&newColorRangesBitmap) != 0)
					newColorRange[ncrIndex++] = allColorRanges[i];
			}
			
			colorRange = new int[ncrIndex];
			System.arraycopy(newColorRange, 0, colorRange, 0, ncrIndex);
			
			this.selectedColorRangesBitmap = newColorRangesBitmap;
		}
		
	}
	
	

	public void editUserLocation(GpsClickData gpsClickData) {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
		this.gpsClickData = gpsClickData;
		
		turnOnSpeechBubble();
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}
	
	public void createNewUserLocation(int lastCalculatedGpsLatM, int lastCalculatedGpsLonM, float lastCalculatedRadius) {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
		
		gpsClickData = new GpsClickData();
		gpsClickData.latm = lastCalculatedGpsLatM;
		gpsClickData.lonm = lastCalculatedGpsLonM;
		gpsClickData.radius = lastCalculatedRadius;

		turnOnSpeechBubble();
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}

	private void turnOnSpeechBubble() {
		//TODO 3: make this work again
//		Point p = speechBubble.getPoint();
//
//		Point clickPoint = new Point();
//		
//		osmMapView.getProjection().toPixels(new MaplessGeoPoint(gpsClickData.latm,
//				gpsClickData.lonm), clickPoint);
//		
//		//we want to pan the screen over so the point is in the lower right corner and we type a name
//		int pixelXToPanTo = clickPoint.x + osmMapView.getView().getWidth()/2
//			- p.x;
//		int pixelYToPanTo = clickPoint.y + osmMapView.getView().getHeight()/2
//			- p.y;
//		
//		MaplessGeoPoint pointToPanTo = osmMapView.getProjection().fromPixels(pixelXToPanTo, pixelYToPanTo);
//		
//		osmMapView.getController().animateTo(pointToPanTo,new Runnable()
//		{
//
//			@Override
//			public void run() {
//				notifySpeechBubbleToBeDisplayed();
//			}
//		}
//		);
//		
	}

	public static class GpsClickData
	{
		public boolean isEdit;

		public int id;
		
		public int lonm;

		public int latm;

		/**
		 * The size of the location the user clicked on based on the points
		 * that were close by.
		 */
		public double radius;

		private String name;
		
		public static GpsClickData createUserLocation(int latm, int lonm, double radius)
		{
			GpsClickData gcd = new GpsClickData();
			
			gcd.id = Integer.MIN_VALUE;
			gcd.name = "";
			gcd.latm = latm;
			gcd.lonm = lonm;
			
			return gcd;
		}

		public static GpsClickData editUserLocation(int id, String name, int latm, int lonm) {
			GpsClickData gcd = new GpsClickData();
			gcd.id = id;
			gcd.name = name;
			gcd.lonm = lonm;
			gcd.latm = latm;
			gcd.isEdit = true;
			
			return gcd;
		}

	}



	
	@Override
	public void doOnResume() {
		super.doOnResume();

		datePicker.setVisibility(View.VISIBLE);
		timeView.setVisibility(View.VISIBLE);
		toolTip.getView().setVisibility(View.VISIBLE);
		
		
		int days = checkExpiry();
		
//		Log.d(GTG.TAG,"Days before expiry "+days);
		
		updateInfoNoticeForTrialInfo(days <= 0, days);

		
		
		toolTip.setEnabled(prefs.enableToolTips);
		
		scaleWidget.setUnitsToMetric(GTG.prefs.useMetric);
		
        GTG.cacheCreatorLock.registerReadingThread();
        try {
		
		GTG.reviewerMapResumeId++;
		
		//PERF: we should only notify media dirty when we didn't go
		// to settings or select time/date
		GTG.cacheCreator.setGtum(this);
		GTG.cacheCreator.notifyMediaDirty();
		
		GTG.mediaLocTimeMap.notifyResume();
		
		//note, this is with the gtg cache creator because drawer
		//won't pause while its holding onto a cache creator lock
		superThreadManager.resumeAllSuperThreads();
		
		osmMapView.onResume();
		
		// we do this so that if the user deleted a picture it will be 
		//removed from the display.. it may be null if we are actually going to the start
		//screen instead, or we haven't drawn at all
		if(gpsTrailerOverlay != null)
		{
			gpsTrailerOverlay.notifyViewNodesChanged();
			
			//just in case it was changed
			//TODO 1.5 FIXME
//			gpsTrailerOverlay.updateForColorRangeChange();
		}
		
		
		if(GTG.lastSuccessfulAction == GTG.GTGAction.SET_FROM_AND_TO_DATES)
		{
			updateTimeViewTime();
			doAutozoom(false);
			GTG.lastSuccessfulAction = null;
		}
		

		updateTimeViewMinMaxTime();
        
		timeView.ovalDrawer.updateColorRange();
		timeView.invalidate();
		
		
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
        
        //check all events and update accordingly
		updateStatusFromGTGEvent(null);

		GTG.addGTGEventListener(this);
		
		
   }

	/**
	 * Updates the time view min and max time. If minTime != maxTime (ie there are
	 * at least one point cached.) We also update the selected time view to be within
	 * the min max range
	 */
	private void updateTimeViewMinMaxTime() {
		GTG.cacheCreatorLock.registerReadingThread();
        try {
        	
        	if(GTG.cacheCreator.minTimeSec != GTG.cacheCreator.maxTimeSec)
        	{
	        	//if for whatever reason the selected times were outside of min/max
	        	//we need to update them
	        	//note that we updateTimeViewTime() here even though the TimeView may not be layed out and this
	        	//may not have any effect, because if the page is paused and then resumed, the normal way
	        	// of calling updateTimeViewTime() (on layout of the TimeView) will not happen
	        	boolean timeChanged = false;
	        	
	    		if(OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec >  GTG.cacheCreator.maxTimeSec)
	    		{
	    			OsmMapGpsTrailerReviewerMapActivity.prefs.currTimePosSec = GTG.cacheCreator.maxTimeSec;
	    			timeChanged = true;
	    		}
	    		if(prefs.currTimePosSec + prefs.currTimePeriodSec < GTG.cacheCreator.minTimeSec)
	    		{
	    			prefs.currTimePosSec -= GTG.cacheCreator.minTimeSec - (prefs.currTimePosSec + prefs.currTimePeriodSec);
	    			timeChanged = true;
	    		}
	    		
	    		if(timeChanged)
	    			updateTimeViewTime();
	    		
        	}

        	timeView.setMinMaxTime(GTG.cacheCreator.minTimeSec, GTG.cacheCreator.maxTimeSec);
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}

	/**
	 * Checks for and handles expiration of trial product
	 */
	private int checkExpiry() {
		if(GTG.IS_PREMIUM == -42)
			return Integer.MAX_VALUE;
		
		int days = GTG.calcDaysBeforeTrialExpired();

		if(days == 0)
		{
			//note, we set trial expired like this, because it depends on when the first point
			//was recorded. This information is not available if only the gps service is running
			//(and the gps service must shutoff if we're expired) so therefore we set it 
			//explicitly here.
			Requirement.NOT_TRIAL_EXPIRED.reset();
			GTG.alert(GTG.GTGEvent.TRIAL_PERIOD_EXPIRED);
			finish();

			startInternalActivity(new Intent(this, TrialExpiredActivity.class));
		}
		
		return days;
	}

	@Override
	public void doOnPause(boolean doOnResumeCalled) {
		super.doOnPause(doOnResumeCalled);

		GTG.removeGTGEventListener(this);
		
        GTG.cacheCreatorLock.registerReadingThread();
        try {

		if(currentDialog != null)
		{
			currentDialog.dismiss();
			currentDialog = null;
		}

		if(osmMapView != null) {
			osmMapView.onPause();

			//sometimes on pause gets called when we're not fully started up
			if(osmMapView.getMapController() != null) {
				CameraPosition cp = osmMapView.getMapController().getCameraPosition();

				prefs.lastLat = cp.latitude;
				prefs.lastLon = cp.longitude;
				prefs.lastZoom = cp.zoom;

				GTG.runBackgroundTask(new Runnable() {

					@Override
					public void run() {
						GTG.savePreferences(OsmMapGpsTrailerReviewerMapActivity.this);
					}
				});
			}
		}

		if(GTG.cacheCreator != null)
			GTG.cacheCreator.setGtum(null);

		if(superThreadManager != null)
			superThreadManager.pauseAllSuperThreads();
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
        
	}

	public void onDestroy() {
		/* ttt_installer:remove_line */Log.d(GTG.TAG,"OsmMapGpsTrailerReviewerMapActivity.onDestory() start");
        
		super.onDestroy();
		if(osmMapView != null)
			osmMapView.onDestroy();

        GTG.cacheCreatorLock.registerReadingThread();
        try {

			cleanup();
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"OsmMapGpsTrailerReviewerMapActivity.onDestory() end");
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		if(osmMapView != null)
			osmMapView.onLowMemory();
	}

	private void cleanup()
	{
		if(superThreadManager != null)
			superThreadManager.shutdownAllSuperThreads();

		//this happens when finish() has been called (or the system wants us dead), so
		//clear out the database and crypt instance, to force user to reenter password
		
		//co:we don't want to close and reopen the database because this takes forever
//		if(GTG.db != null)
//			GTG.db.close();
//		GTG.db = null;

		//TODO 2.5 why is this null sometimes?
		if(gpsTrailerOverlay != null)
			gpsTrailerOverlay.shutdown();
		reviewerStillRunning = false;
	}
	

	@Override
	public void notifyTimeViewChange() {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
			recalculateStartAndEndTimeFromTimeView();
			redrawMap();
			
			if(GTG.apCache.hasGpsPoints())
				toolTip.setAction(UserAction.TIME_VIEW_CHANGE);
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}
	
	/**
	 * May be called from another thread
	 */
	public void notifyMediaChanged()
	{
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				if(gpsTrailerOverlay != null)
					//PERF we could have a special method for pictures, because this
					//updates distance, too
					gpsTrailerOverlay.notifyViewNodesChanged();

				//TODO 2.2 we could update the fragment rather than dismissing it 
				//co: this causes the much worse problem of the pictures being dismissed every few seconds
				//when indexing a lot of pictures and videos (ie right after a restore)
//		    	if(mediaGalleryFragment != null)
//		    		mediaGalleryFragment.finishBrowsing();
			}
		});
	}
	
	@Override
	public void notifyTimeViewReady() {
		updateTimeViewTime();
	}

	private void updateInfoNoticeForTrialInfo(boolean expired, int daysBeforeExpiry) {
		infoNotice.unregisterProcess(InfoNoticeStatusFragment.NO_GPS_POINTS);
		infoNotice.unregisterProcess(InfoNoticeStatusFragment.FREE_VERSION);
		infoNotice.unregisterProcess(InfoNoticeStatusFragment.UNLICENSED);
		
		if(GTGEvent.ERROR_UNLICENSED.isOn)
		{
    		//co: we initially don't want to do anything if the user is unlicensed,
    		// just find out how much piracy is a problem
//			infoNotice.registerProcess(InfoNoticeStatusFragment.UNLICENSED, getString(R.string.info_notice_cant_verify_license), null, null);
//			return;
		}
		
		
		if(!GTG.gpsLocCache.hasGpsPoints())
		{
			if(!GTG.prefs.isCollectData)
			{
				Intent intent = new Intent(this, SettingsActivity.class);

				infoNotice.registerProcess(InfoNoticeStatusFragment.NO_GPS_POINTS, 
						getString(R.string.no_points_no_collect), intent, null);
			}
			else
				infoNotice.registerProcess(InfoNoticeStatusFragment.NO_GPS_POINTS, 
						getString(R.string.no_points_with_collect), null, null);
		}
		
		if(GTG.IS_PREMIUM != -42 )
		{
			//co : no more expiry
//			if(expired)
//				infoNotice.registerProcess(InfoNoticeStatusFragment.FREE_VERSION, 
//						getString(R.string.free_version_after_expiry), GTG.BUY_PREMIUM_INTENT, null);
//			else
//			{
//				infoNotice.registerProcess(InfoNoticeStatusFragment.FREE_VERSION, 
//					String.format(getString(daysBeforeExpiry != 1 ? R.string.free_version_before_expiry : 
//						R.string.free_version_before_expiry_1_day), daysBeforeExpiry), GTG.BUY_PREMIUM_INTENT, null);
//
//			}
		}	
	}

	/**
	 *  Should be called only by ui thread
	 */
	public void notifyMaxTimeChanged() {
		//note that this also updates the selected time if out of range. The reason that may happen,
		// is that we just did a restore (or for some reason the cache was deleted), and then
		// some points were finally cached (if there are no points cached then the selected times
		// are not updated
		updateTimeViewMinMaxTime();
	}
	
	private void updateTimeViewTime() {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
        	
        	if(timeView.getWidth() != 0)
				timeView.setSelectedStartAndEndTime(prefs.currTimePosSec, 
						prefs.currTimePosSec + prefs.currTimePeriodSec
						);
			
			//time view may adjust the end time if it is below the minimum (or above the maximum) that
			//it can display
			recalculateStartAndEndTimeFromTimeView();
	    }
	    finally {
	    	GTG.cacheCreatorLock.unregisterReadingThread();
	    }
	}

	private void recalculateStartAndEndTimeFromTimeView() {
		setStartAndEndTimeSec(timeView.selectedTimeStart, timeView.selectedTimeEnd );
	}

		
	public static synchronized void setStartAndEndTimeSec(int startTimeSec, int endTimeSec) {
		prefs.currTimePosSec = startTimeSec;
		prefs.currTimePeriodSec = endTimeSec - startTimeSec;
	}

	public void notifyLocationKnown() {
        GTG.cacheCreatorLock.registerReadingThread();
        try {
        	panToLocation.setBackgroundResource(R.drawable.pan_to_location);
        	locationKnown = true;
        }
        finally {
        	GTG.cacheCreatorLock.unregisterReadingThread();
        }
	}
	
	
	public static enum OngoingProcessEnum
	{
		PROCESS_GPS_POINTS(R.string.process_gps_points),
		DRAW_POINTS(R.string.drawing_points), LOADING_MEDIA(R.string.loading_media);
		
		Integer resourceId;
		
		private OngoingProcessEnum(int resourceId)
		{
			this.resourceId = resourceId;
		}
	};
	
	public void notifyProcessing(OngoingProcessEnum ope, String text) {
		gtgStatus.registerProcess(ope.resourceId, text, null, null);
	}

	public void notifyProcessing(OngoingProcessEnum ope) {
		gtgStatus.registerProcess(ope.resourceId, getText(ope.resourceId), null, null);
	}

	public void notifyDoneProcessing(OngoingProcessEnum ope) {
		gtgStatus.unregisterProcess(ope.resourceId);
	}

	@Override
	public boolean onGTGEvent(final GTGEvent event) {
		return updateStatusFromGTGEvent(event);
	}
	
	/**
	 * @param event if event is specified, only it will be updated, otherwise
	 *   all events are checked if they are on or off
	 */
	private boolean updateStatusFromGTGEvent(final GTGEvent event) {
		if (event == null || event == GTGEvent.ERROR_LOW_FREE_SPACE) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (GTGEvent.ERROR_LOW_FREE_SPACE.isOn)
						infoNotice
								.registerProcess(
										InfoNoticeStatusFragment.LOW_FREE_SPACE,
										getString(R.string.error_reviewer_low_free_space),
										null, new Runnable() {

											@Override
											public void run() {
												AlertDialog.Builder alert = new AlertDialog.Builder(
														OsmMapGpsTrailerReviewerMapActivity.this);
												alert.setMessage(getText(R.string.error_reviewer_low_free_space_help));
												alert.setPositiveButton(
														R.string.ok,
														new DialogInterface.OnClickListener() {
															public void onClick(
																	DialogInterface dialog,
																	int whichButton) {
																exitFromApp();
															}
														});
												alert.show();

											}
										});
					else
						infoNotice
								.unregisterProcess(InfoNoticeStatusFragment.LOW_FREE_SPACE);
					return;
				}
			});

			return false;
		}
		if (event == null || event == GTGEvent.ERROR_SDCARD_NOT_MOUNTED) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					startInternalActivity(new Intent(OsmMapGpsTrailerReviewerMapActivity.this,
							FatalErrorActivity.class).putExtra(
							FatalErrorActivity.MESSAGE_RESOURCE_ID,
							R.string.error_reviewer_sdcard_not_mounted));
				}
			});
			return false;
		}
		if (event == null || event == GTGEvent.PROCESSING_GPS_POINTS) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (GTGEvent.PROCESSING_GPS_POINTS.isOn) {
						timeAndDateSdf.setTimeZone(Util.getCurrTimeZone());

						long[] currDateAndExpectedDate = (long[]) event.obj;

						//this can sometimes be null because (I believe) that onGTGEvent
						// and offGTGEvent are called in rapid succession, and offGTGEvent
						// destroys the object associated to the event before we can
						// process the onGTGEvent message
						//TODO 2.5 fix the GTGEvent.obj race condition
						if (currDateAndExpectedDate != null)
							notifyProcessing(
									OngoingProcessEnum.PROCESS_GPS_POINTS,
									String.format(
											getString(R.string.process_gps_points_fmt),
											timeAndDateSdf
													.format(new Date(
															currDateAndExpectedDate[0]))));
					}
					else
						notifyDoneProcessing(OngoingProcessEnum.PROCESS_GPS_POINTS);
				}
			});
		}

		if (event == null || event == GTGEvent.LOADING_MEDIA) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (GTGEvent.LOADING_MEDIA.isOn)
						notifyProcessing(OngoingProcessEnum.LOADING_MEDIA);
					else
						notifyDoneProcessing(OngoingProcessEnum.LOADING_MEDIA);
				}
			});
		}

//		if (event == GTGEvent.ERROR_UNLICENSED) {
//			runOnUiThread(new Runnable() {
//
//				@Override
//				public void run() {
//					if (GTGEvent.ERROR_UNLICENSED.isOn)
//						updateInfoNoticeForTrialInfo();
//				}
//			});
//		}

		//co: we ignore it when the ttt server is down, it's kind of a non event
		//				if(event == GTGEvent.TTT_SERVER_DOWN)
		//				{
		//					return false;
		//				}

		return false;
	}

	@Override
	public void offGTGEvent(GTGEvent event) {
		updateStatusFromGTGEvent(event);
	}

	public void notifyPathsChanged() {
		if(gpsTrailerOverlay != null)
			gpsTrailerOverlay.notifyPathsChanged();
	}

	public void notifyDistUpdated(final double distance) {
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				updateDistanceView(distance);
			}
		});
	}

	protected void updateDistanceView(double distance) {
		distanceView.setText(String.format(getText(R.string.distance_traveled).toString(),
				distance == -1 ? "--" : 
					MapScaleWidget.calcLabelForLength(distance, GTG.prefs.useMetric)));
				
		
	}

	public void registerMediaGalleryFragment(
			MediaGalleryFragment mediaGalleryFragment) {
		this.mediaGalleryFragment = mediaGalleryFragment;
		
	}

	public void unregisterMediaGalleryFragment(
			MediaGalleryFragment mediaGalleryFragment2) {
		this.mediaGalleryFragment = null;
		
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI;
	}

}
