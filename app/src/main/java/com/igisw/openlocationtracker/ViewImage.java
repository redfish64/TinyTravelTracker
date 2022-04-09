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
/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igisw.openlocationtracker;


import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;
import android.widget.ZoomButtonsController;

import com.igisw.openlocationtracker.GTGActivity;
import com.rareventure.gps2.reviewer.imageviewer.RotateBitmap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

// This activity can display a whole picture and navigate them in a specific
// gallery. It has two modes: normal mode and slide show mode. In normal mode
// the user view one image at a time, and can click "previous" and "next"
// button to see the previous or next image. In slide show mode it shows one
// image after another, with some transition effect.
public class ViewImage extends GTGActivity implements View.OnClickListener {
    private static final String TAG = "ViewImage";

    private ImageGetter mGetter;
 
    // Choices for what adjacents to load.
    private static final int[] sOrderAdjacents = new int[] {0, 1, -1};

    final GetterHandler mHandler = new GetterHandler();

    private boolean mFullScreenInNormalMode;

    public static int mCurrentPosition = 0;

    private View mNextImageView;
    private View mPrevImageView;
    private final Animation mHideNextImageViewAnimation =
            new AlphaAnimation(1F, 0F);
    private final Animation mHidePrevImageViewAnimation =
            new AlphaAnimation(1F, 0F);
    private final Animation mShowNextImageViewAnimation =
            new AlphaAnimation(0F, 1F);
    private final Animation mShowPrevImageViewAnimation =
            new AlphaAnimation(0F, 1F);

    public static final String KEY_IMAGE_LIST = "image_list";

    /**
     * These are the indexes of images that have been deleted.
     * We pretend to delete images (but don't actually) and then
     * when we pause, only then do we actually delete them.
     * TODO 3 I don't understand the code well enough to do this another
     * way. This whole thing should probably be rewritten.
     */
    private ArrayList<Integer> deletedImageIndexes = new ArrayList<Integer>();

    public static ArrayList<MediaLocTime> mAllImages;

    GestureDetector mGestureDetector;
    private ZoomButtonsController mZoomButtonsController;

    // The image view displayed for normal mode.
    private ImageViewTouch mImageView;
    // This is the cache for thumbnail bitmaps.
    private BitmapCache mCache;
    
    private final Runnable mDismissOnScreenControlRunner = new Runnable() {
        public void run() {
            hideOnScreenControls();
        }
    };

	private View mPlayVideo;

    private void updateNextPrevControls() {
        boolean showPrev = !isAtBeginning();
        boolean showNext = !isAtEnd();

        boolean prevIsVisible = mPrevImageView.getVisibility() == View.VISIBLE;
        boolean nextIsVisible = mNextImageView.getVisibility() == View.VISIBLE;

        if (showPrev && !prevIsVisible) {
            Animation a = mShowPrevImageViewAnimation;
            a.setDuration(500);
            mPrevImageView.startAnimation(a);
            mPrevImageView.setVisibility(View.VISIBLE);
        } else if (!showPrev && prevIsVisible) {
            Animation a = mHidePrevImageViewAnimation;
            a.setDuration(500);
            mPrevImageView.startAnimation(a);
            mPrevImageView.setVisibility(View.GONE);
        }

        if (showNext && !nextIsVisible) {
            Animation a = mShowNextImageViewAnimation;
            a.setDuration(500);
            mNextImageView.startAnimation(a);
            mNextImageView.setVisibility(View.VISIBLE);
        } else if (!showNext && nextIsVisible) {
            Animation a = mHideNextImageViewAnimation;
            a.setDuration(500);
            mNextImageView.startAnimation(a);
            mNextImageView.setVisibility(View.GONE);
        }
    }

    private void hideOnScreenControls() {
        if (mNextImageView.getVisibility() == View.VISIBLE) {
            Animation a = mHideNextImageViewAnimation;
            a.setDuration(500);
            mNextImageView.startAnimation(a);
            mNextImageView.setVisibility(View.INVISIBLE);
        }

        if (mPrevImageView.getVisibility() == View.VISIBLE) {
            Animation a = mHidePrevImageViewAnimation;
            a.setDuration(500);
            mPrevImageView.startAnimation(a);
            mPrevImageView.setVisibility(View.INVISIBLE);
        }

        mZoomButtonsController.setVisible(false);
    }

    private void showOnScreenControls() {
//        // If the view has not been attached to the window yet, the
//        // zoomButtonControls will not able to show up. So delay it until the
//        // view has attached to window.
//        if (mActionIconPanel.getWindowToken() == null) {
//            mHandler.postGetterCallback(new Runnable() {
//                public void run() {
//                    showOnScreenControls();
//                }
//            });
//            return;
//        }
        updateNextPrevControls();

        MediaLocTime image = mAllImages.get(mCurrentPosition);
        if (image.isVideo()) {
            mZoomButtonsController.setVisible(false);
            mPlayVideo.setVisibility(View.VISIBLE);
        } else {
            updateZoomButtonsEnabled();
            mZoomButtonsController.setVisible(true);
            mPlayVideo.setVisibility(View.GONE);
        }

    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mZoomButtonsController.isVisible()) {
            scheduleDismissOnScreenControls();
        }
        return super.dispatchTouchEvent(m);
    }

    private void updateZoomButtonsEnabled() {
        ImageViewTouch imageView = mImageView;
        float scale = imageView.getScale();
        mZoomButtonsController.setZoomInEnabled(scale < imageView.mMaxZoom);
        mZoomButtonsController.setZoomOutEnabled(scale > 1);
    }

    @Override
    protected void onDestroy() {
        // This is necessary to make the ZoomButtonsController unregister
        // its configuration change receiver.
        if (mZoomButtonsController != null) {
            mZoomButtonsController.setVisible(false);
        }
        super.onDestroy();
    }

    private void scheduleDismissOnScreenControls() {
        mHandler.removeCallbacks(mDismissOnScreenControlRunner);
        mHandler.postDelayed(mDismissOnScreenControlRunner, 2000);
    }

    private void setupOnScreenControls(View rootView, View ownerView) {
        mNextImageView = rootView.findViewById(R.id.next_image);
        mPrevImageView = rootView.findViewById(R.id.prev_image);
        mPlayVideo = rootView.findViewById(R.id.play);
        
        mNextImageView.setOnClickListener(this);
        mPrevImageView.setOnClickListener(this);
        mPlayVideo.setOnClickListener(this);

        setupZoomButtonController(ownerView);
        setupOnTouchListeners(rootView);
    }

    private void setupZoomButtonController(final View ownerView) {
        mZoomButtonsController = new ZoomButtonsController(ownerView);
        mZoomButtonsController.setAutoDismissed(false);
        mZoomButtonsController.setZoomSpeed(100);
        mZoomButtonsController.setOnZoomListener(
                new ZoomButtonsController.OnZoomListener() {
            public void onVisibilityChanged(boolean visible) {
                if (visible) {
                    updateZoomButtonsEnabled();
                }
            }

            public void onZoom(boolean zoomIn) {
                if (zoomIn) {
                    mImageView.zoomIn();
                } else {
                    mImageView.zoomOut();
                }
                mZoomButtonsController.setVisible(true);
                updateZoomButtonsEnabled();
            }
        });
    }

    private void setupOnTouchListeners(View rootView) {

        mGestureDetector = new GestureDetector(this, new MyGestureListener());

        // If the user touches anywhere on the panel (including the
        // next/prev button). We show the on-screen controls. In addition
        // to that, if the touch is not on the prev/next button, we
        // pass the event to the gesture detector to detect double tap.
        final OnTouchListener buttonListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                scheduleDismissOnScreenControls();
                return false;
            }
        };

        OnTouchListener rootListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                buttonListener.onTouch(v, event);
                mGestureDetector.onTouchEvent(event);

                // We do not use the return value of
                // mGestureDetector.onTouchEvent because we will not receive
                // the "up" event if we return false for the "down" event.
                return true;
            }
        };

        mNextImageView.setOnTouchListener(buttonListener);
        mPrevImageView.setOnTouchListener(buttonListener);
        rootView.setOnTouchListener(rootListener);
    }

    private class MyGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            ImageViewTouch imageView = mImageView;
            if (imageView.getScale() > 1F) {
                imageView.postTranslateCenter(-distanceX, -distanceY);
            }
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            showOnScreenControls();
            scheduleDismissOnScreenControls();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            ImageViewTouch imageView = mImageView;

            // Switch between the original scale and 3x scale.
            if (imageView.getScale() > 2F) {
                mImageView.zoomTo(1f);
            } else {
                mImageView.zoomToPoint(3f, e.getX(), e.getY());
            }
            return true;
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
        MenuInflater mi = getMenuInflater();
        mi.inflate(R.menu.view_image_menu, menu);
        
        return true;
	}

	
	
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getTitle().equals(getText(R.string.share)))
        {
        	startShareMediaActivity(mAllImages.get(mCurrentPosition));
            return true;
        }
        if(item.getTitle().equals(getText(R.string.delete)))
        {
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			alert.setTitle(getText(R.string.delete));
			alert.setMessage(R.string.view_image_delete_file_query);
			alert.setPositiveButton(R.string.delete,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							deletedImageIndexes.add(mCurrentPosition);
							
							//if there are no images left
							if(mAllImages.size() == deletedImageIndexes.size())
							{
								finish();
							}
							//we have to get off the image we deleted
							else if(isAtBeginning())
								moveNextOrPrevious(1);
							else moveNextOrPrevious(-1);

						}
					});
			alert.setNegativeButton(R.string.cancel, null);
			alert.show();
			
            return true;
        }
        if(item.getTitle().equals(R.string.view_image_menu_view_in_gallery))
        {
        	startShareMediaActivity(mAllImages.get(mCurrentPosition));
            return true;
        }
        
        
        return super.onOptionsItemSelected(item);
    }

    void setImage(int pos, boolean showControls) {
        mCurrentPosition = pos;

        Bitmap b = mCache.getBitmap(pos);
        if (b != null) {
            MediaLocTime image = mAllImages.get(pos);
            mImageView.setImageRotateBitmapResetBase(
                    new RotateBitmap(b, 0), true);
            updateZoomButtonsEnabled();
        }

        ImageGetterCallback cb = new ImageGetterCallback() {
            public void completed() {
            }

            public boolean wantsThumbnail(int pos, int offset) {
                return !mCache.hasBitmap(pos + offset);
            }

            public boolean wantsFullImage(int pos, int offset) {
                return offset == 0;
            }

            public int fullImageSizeToUse(int pos, int offset) {
                // this number should be bigger so that we can zoom.  we may
                // need to get fancier and read in the fuller size image as the
                // user starts to zoom.
                // Originally the value is set to 480 in order to avoid OOM.
                // Now we set it to 2048 because of using
                // native memory allocation for Bitmaps.
                final int imageViewSize = 2048;
                return imageViewSize;
            }

            public int [] loadOrder() {
                return sOrderAdjacents;
            }

            public void imageLoaded(int pos, int offset, RotateBitmap bitmap,
                                    boolean isThumb, MediaLocTime media) {
                // shouldn't get here after onPause()

                // We may get a result from a previous request, or
            	// get a thumbnail after we deleted an item
                if (pos != mCurrentPosition || pos >= mAllImages.size() || 
                		media != mAllImages.get(pos)) {
                    bitmap.recycle();
                    return;
                }

                if (isThumb) {
                    mCache.put(pos + offset, bitmap.getBitmap());
                }
                if (offset == 0) {
                    // isThumb: We always load thumb bitmap first, so we will
                    // reset the supp matrix for then thumb bitmap, and keep
                    // the supp matrix when the full bitmap is loaded.
                    mImageView.setImageRotateBitmapResetBase(bitmap, isThumb);
                    updateZoomButtonsEnabled();
                }
            }
        };

        // Could be null if we're stopping a slide show in the course of pausing
        if (mGetter != null) {
            mGetter.setPosition(pos, cb, mAllImages, mHandler);
        }
       if (showControls) showOnScreenControls();
        scheduleDismissOnScreenControls();
    }

	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI;
	}
	
	@Override 
	protected void requestWindowFeatureHook()
	{
        requestWindowFeature(Window.FEATURE_NO_TITLE);
	}

    @Override
    public void doOnCreate(Bundle instanceState) {
        super.doOnCreate(instanceState);

        Intent intent = getIntent();
        mFullScreenInNormalMode = intent.getBooleanExtra(
                MediaStore.EXTRA_FULL_SCREEN, true);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        setContentView(R.layout.viewimage);

        mImageView = (ImageViewTouch) findViewById(R.id.image);
        mImageView.setEnableTrackballScroll(true);
        mCache = new BitmapCache(3);
        mImageView.setRecycler(mCache);

        makeGetter();

        if (mFullScreenInNormalMode) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setupOnScreenControls(findViewById(R.id.rootLayout), mImageView);
    }


    private void makeGetter() {
        mGetter = new ImageGetter(getContentResolver());
    }

    @Override
    public void doOnResume() {
        super.doOnResume();

        // normally this will never be zero but if one "backs" into this
        // activity after removing the sdcard it could be zero.  in that
        // case just "finish" since there's nothing useful that can happen.
        int count = mAllImages == null ? 0 : mAllImages.size();
        if (count == 0) {
            finish();
            return;
        } else if (count <= mCurrentPosition) {
            mCurrentPosition = count - 1;
        }

        if (mGetter == null) {
            makeGetter();
        }

        setImage(mCurrentPosition, true);
    }

    

    @Override
	public void finish() {
		super.finish();
	}
    
    @Override
    public void doOnPause(boolean resumeCalled)
    {
    	super.doOnPause(resumeCalled);
    	if(!resumeCalled)
    		return;

    	//tim - note that we moved this from onStop because onStop is called after
    	// the next activities onResume(). So the mediagalleryfragment was displaying
    	//the images after we had marked the deleted

        // mGetter could be null if we call finish() and leave early in
        // onStart().
        if (mGetter != null) {
            mGetter.cancelCurrent();
            mGetter.stop();
            mGetter = null;
        }

        // removing all callback in the message queue
        mHandler.removeAllGetterCallbacks();

        hideOnScreenControls();
        
        deleteMarkedImages();
        
        mImageView.clear();
        mCache.clear();
    }

	/**
	 * We actually delete the images here. Should not be called when ImageGetter is running
	 * or activity at all, actually
	 */
    private void deleteMarkedImages() {
    	Collections.sort(deletedImageIndexes);
    	
    	ContentResolver cr = getContentResolver();
    	
    	for(int i = deletedImageIndexes.size()-1; i >= 0; i--)
    	{
    		int index = deletedImageIndexes.get(i).intValue();
    		
    		if(index <= mCurrentPosition)
    			mCurrentPosition --;
    		
    		MediaLocTime media = mAllImages.remove(index);
    		
    		String filename = media.getFilename(cr);

    		if(media.isVideo())
	    		cr.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media._ID + "=?", 
	    				new String[]{ Long.toString(media.getFk()) } );
    		else
        		cr.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media._ID + "=?", 
        				new String[]{ Long.toString(media.getFk()) } );
    			
			if(filename != null)
				new File(filename).delete();

			GTG.mediaLocTimeMap.notifyMltNotClean(media);
    	}
    	
    	deletedImageIndexes.clear();
	}

	private void startShareMediaActivity(MediaLocTime image) {
        boolean isVideo = image.isVideo();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType(image.getMimeType(getContentResolver()));
        intent.putExtra(Intent.EXTRA_STREAM, image.getUri(this));
        try {
            startActivity(Intent.createChooser(intent, getText(
                    isVideo ? R.string.sendVideo : R.string.sendImage)));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, isVideo
                    ? R.string.no_way_to_share_image
                    : R.string.no_way_to_share_video,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startPlayVideoActivity() {
        MediaLocTime image = mAllImages.get(mCurrentPosition);
        Intent intent = new Intent(
                Intent.ACTION_VIEW);
        intent.setDataAndType(image.getUri(this), "video/*");
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + image.getUri(this), ex);
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.play:
                startPlayVideoActivity();
                break;
//            case R.id.share: {
//                IImage image = mAllImages.getImageAt(mCurrentPosition);
//                if (!MenuHelper.isWhiteListUri(image.fullSizeImageUri())) {
//                    return;
//                }
//                startShareMediaActivity(image);
//                break;
//            }
            case R.id.next_image:
                moveNextOrPrevious(1);
                break;
            case R.id.prev_image:
                moveNextOrPrevious(-1);
                break;
        }
    }
    
    private boolean isAtBeginning()
    {
    	int priorImagePos = mCurrentPosition - 1;
    	while(deletedImageIndexes.contains(priorImagePos))
    		priorImagePos --;
    	
    	if(priorImagePos < 0)
    		return true;
    	
    	return false;
    }

    private boolean isAtEnd()
    {
    	int nextImagePos = mCurrentPosition + 1;
    	while(deletedImageIndexes.contains(nextImagePos))
    		nextImagePos ++;
    	
    	if(nextImagePos >= mAllImages.size())
    		return true;
    	
    	return false;
    }

    private void moveNextOrPrevious(int delta) {
    	int nextImagePos = mCurrentPosition;
    	
    	while(delta != 0)
    	{
    		if(delta < 0)
    		{
    			nextImagePos --;
    		}
    		else {
    			nextImagePos ++;
    		}
    		
    		if(deletedImageIndexes.contains(nextImagePos))
    			continue;
    		
    		delta += (delta < 0 ? 1 : -1);
    	}

    	if ((0 <= nextImagePos) && (nextImagePos < mAllImages.size())) {
            setImage(nextImagePos, true);
            showOnScreenControls();
        }
    }

}

class ImageViewTouch extends ImageViewTouchBase {
    private final ViewImage mViewImage;
    private boolean mEnableTrackballScroll;

    public ImageViewTouch(Context context) {
        super(context);
        mViewImage = (ViewImage) context;
    }

    public ImageViewTouch(Context context, AttributeSet attrs) {
        super(context, attrs);
        mViewImage = (ViewImage) context;
    }

    public void setEnableTrackballScroll(boolean enable) {
        mEnableTrackballScroll = enable;
    }

    protected void postTranslateCenter(float dx, float dy) {
        super.postTranslate(dx, dy);
        center(true, true);
    }

    private static final float PAN_RATE = 20;

    // This is the time we allow the dpad to change the image position again.
    private long mNextChangePositionTime;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        // Don't respond to arrow keys if trackball scrolling is not enabled
        if (!mEnableTrackballScroll) {
            if ((keyCode >= KeyEvent.KEYCODE_DPAD_UP)
                    && (keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
                return super.onKeyDown(keyCode, event);
            }
        }

        int current = mViewImage.mCurrentPosition;

        int nextImagePos = -2; // default no next image
        try {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: {
                    if (getScale() <= 1F && event.getEventTime()
                            >= mNextChangePositionTime) {
                        nextImagePos = current - 1;
                        mNextChangePositionTime = event.getEventTime() + 500;
                    } else {
                        panBy(PAN_RATE, 0);
                        center(true, false);
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    if (getScale() <= 1F && event.getEventTime()
                            >= mNextChangePositionTime) {
                        nextImagePos = current + 1;
                        mNextChangePositionTime = event.getEventTime() + 500;
                    } else {
                        panBy(-PAN_RATE, 0);
                        center(true, false);
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    panBy(0, PAN_RATE);
                    center(false, true);
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    panBy(0, -PAN_RATE);
                    center(false, true);
                    return true;
                }
            }
        } finally {
            if (nextImagePos >= 0
                    && nextImagePos < mViewImage.mAllImages.size()) {
                synchronized (mViewImage) {
                    mViewImage.setImage(nextImagePos, true);
                }
           } else if (nextImagePos != -2) {
               center(true, true);
           }
        }

        return super.onKeyDown(keyCode, event);
    }
}

// This is a cache for Bitmap displayed in ViewImage (normal mode, thumb only).
class BitmapCache implements ImageViewTouchBase.Recycler {
    public static class Entry {
        int mPos;
        Bitmap mBitmap;
        public Entry() {
            clear();
        }
        public void clear() {
            mPos = -1;
            mBitmap = null;
        }
    }

    private final Entry [] mCache;

    public BitmapCache(int size) {
        mCache = new Entry [size];
        for (int i = 0; i < size; i++) {
            mCache[i] = new Entry();
        }
    }

    // Given the position, find the associated entry. Returns null if there is
    // no such entry.
    private Entry findEntry(int pos) {
        for (Entry e : mCache) {
            if (pos == e.mPos) {
                return e;
            }
        }
        return null;
    }

    // Returns the thumb bitmap if we have it, otherwise return null.
    public synchronized Bitmap getBitmap(int pos) {
        Entry e = findEntry(pos);
        if (e != null) {
            return e.mBitmap;
        }
        return null;
    }

    public synchronized void put(int pos, Bitmap bitmap) {
        // First see if we already have this entry.
        if (findEntry(pos) != null) {
            return;
        }

        // Find the best entry we should replace.
        // See if there is any empty entry.
        // Otherwise assuming sequential access, kick out the entry with the
        // greatest distance.
        Entry best = null;
        int maxDist = -1;
        for (Entry e : mCache) {
            if (e.mPos == -1) {
                best = e;
                break;
            } else {
                int dist = Math.abs(pos - e.mPos);
                if (dist > maxDist) {
                    maxDist = dist;
                    best = e;
                }
            }
        }

        // Recycle the image being kicked out.
        // This only works because our current usage is sequential, so we
        // do not happen to recycle the image being displayed.
        if (best.mBitmap != null) {
            best.mBitmap.recycle();
        }

        best.mPos = pos;
        best.mBitmap = bitmap;
    }

    // Recycle all bitmaps in the cache and clear the cache.
    public synchronized void clear() {
        for (Entry e : mCache) {
            if (e.mBitmap != null) {
                e.mBitmap.recycle();
            }
            e.clear();
        }
    }

    // Returns whether the bitmap is in the cache.
    public synchronized boolean hasBitmap(int pos) {
        Entry e = findEntry(pos);
        return (e != null);
    }

    // Recycle the bitmap if it's not in the cache.
    // The input must be non-null.
    public synchronized void recycle(Bitmap b) {
        for (Entry e : mCache) {
            if (e.mPos != -1) {
                if (e.mBitmap == b) {
                    return;
                }
            }
        }
        b.recycle();
    }
    
    
}
