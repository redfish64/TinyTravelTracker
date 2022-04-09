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
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.provider.MediaStore;

import com.rareventure.gps2.reviewer.imageviewer.BitmapManager;
import com.rareventure.gps2.reviewer.imageviewer.RotateBitmap;

import java.util.ArrayList;

/*
 * Here's the loading strategy.  For any given image, load the thumbnail
 * into memory and post a callback to display the resulting bitmap.
 *
 * Then proceed to load the full image bitmap.   Three things can
 * happen at this point:
 *
 * 1.  the image fails to load because the UI thread decided
 * to move on to a different image.  This "cancellation" happens
 * by virtue of the UI thread closing the stream containing the
 * image being decoded.  BitmapFactory.decodeStream returns null
 * in this case.
 *
 * 2.  the image loaded successfully.  At that point we post
 * a callback to the UI thread to actually show the bitmap.
 *
 * 3.  when the post runs it checks to see if the image that was
 * loaded is still the one we want.  The UI may have moved on
 * to some other image and if so we just drop the newly loaded
 * bitmap on the floor.
 */

interface ImageGetterCallback {
    public void imageLoaded(int pos, int offset, RotateBitmap bitmap,
                            boolean isThumb,  MediaLocTime media);
    public boolean wantsThumbnail(int pos, int offset);
    public boolean wantsFullImage(int pos, int offset);
    public int fullImageSizeToUse(int pos, int offset);
    public void completed();
    public int [] loadOrder();
}

class ImageGetter {

    @SuppressWarnings("unused")
    private static final String TAG = "ImageGetter";

    // The thread which does the work.
    private Thread mGetterThread;

    // The current request serial number.
    // This is increased by one each time a new job is assigned.
    // It is only written in the main thread.
    private int mCurrentSerial;

    // The base position that's being retrieved.  The actual images retrieved
    // are this base plus each of the offets. -1 means there is no current
    // request needs to be finished.
    private int mCurrentPosition = -1;

    // The callback to invoke for each image.
    private ImageGetterCallback mCB;

    // The image list for the images.
    private ArrayList<MediaLocTime> mImageList;

    // The handler to do callback.
    private GetterHandler mHandler;

    // True if we want to cancel the current loading.
    private volatile boolean mCancel = true;

    // True if the getter thread is idle waiting.
    private boolean mIdle = false;

    // True when the getter thread should exit.
    private boolean mDone = false;

    private ContentResolver mCr;

    private class ImageGetterRunnable implements Runnable {

        private Runnable callback(final int position, final int offset,
                                  final boolean isThumb,
                                  final RotateBitmap bitmap,
                                  final int requestSerial,
                                  final MediaLocTime media) {
            return new Runnable() {
                public void run() {
                    // check for inflight callbacks that aren't applicable
                    // any longer before delivering them
                    if (requestSerial == mCurrentSerial) {
                        mCB.imageLoaded(position, offset, bitmap, isThumb, media);
                    } else if (bitmap != null) {
                        bitmap.recycle();
                    }
                }
            };
        }

        private Runnable completedCallback(final int requestSerial) {
            return new Runnable() {
                public void run() {
                    if (requestSerial == mCurrentSerial) {
                        mCB.completed();
                    }
                }
            };
        }

        public void run() {
            // Lower the priority of this thread to avoid competing with
            // the UI thread.
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            while (true) {
                synchronized (ImageGetter.this) {
                    while (mCancel || mDone || mCurrentPosition == -1) {
                        if (mDone) return;
                        mIdle = true;
                        ImageGetter.this.notify();
                        try {
                            ImageGetter.this.wait();
                        } catch (InterruptedException ex) {
                            // ignore
                        }
                        mIdle = false;
                    }
                }

                executeRequest();

                synchronized (ImageGetter.this) {
                    mCurrentPosition = -1;
                }
            }
        }
        private void executeRequest() {
            int imageCount = mImageList.size();

            int [] order = mCB.loadOrder();
            for (int i = 0; i < order.length; i++) {
                if (mCancel) return;
                int offset = order[i];
                int imageNumber = mCurrentPosition + offset;
                if (imageNumber >= 0 && imageNumber < imageCount) {
                    if (!mCB.wantsThumbnail(mCurrentPosition, offset)) {
                        continue;
                    }

                    MediaLocTime image = mImageList.get(imageNumber);
                    if (image == null) continue;
                    if (mCancel) return;

                    Bitmap b = image.getThumbnailBitmap(mCr, false);
                    if (b == null) continue;
                    if (mCancel) {
                        b.recycle();
                        return;
                    }

                    Runnable cb = callback(mCurrentPosition, offset,
                            true,
                            new RotateBitmap(b, 0),
                            mCurrentSerial, image);
                    mHandler.postGetterCallback(cb);
                }
            }

            for (int i = 0; i < order.length; i++) {
                if (mCancel) return;
                int offset = order[i];
                int imageNumber = mCurrentPosition + offset;
                if (imageNumber >= 0 && imageNumber < imageCount) {
                    if (!mCB.wantsFullImage(mCurrentPosition, offset)) {
                        continue;
                    }

                    MediaLocTime image = mImageList.get(imageNumber);
                    if (image == null) continue;
                    if (image.isVideo()) continue;
                    if (mCancel) return;

                    int sizeToUse = mCB.fullImageSizeToUse(
                            mCurrentPosition, offset);
                    Bitmap b = image.getLargeBitmap(sizeToUse, 3 * 1024 * 1024,
                            mCr);

                    if (b == null) continue;
                    if (mCancel) {
                        b.recycle();
                        return;
                    }

                    RotateBitmap rb = new RotateBitmap(b,
                            0);

                    Runnable cb = callback(mCurrentPosition, offset,
                            false, rb, mCurrentSerial, image);
                    mHandler.postGetterCallback(cb);
                }
            }

            mHandler.postGetterCallback(completedCallback(mCurrentSerial));
        }
    }

    public ImageGetter(ContentResolver cr) {
        mCr = cr;
        mGetterThread = new Thread(new ImageGetterRunnable());
        mGetterThread.setName("ImageGettter");
        mGetterThread.start();
    }

    // Cancels current loading (without waiting).
    public synchronized void cancelCurrent() {
        ImageUtil.Assert(mGetterThread != null);
        mCancel = true;
        BitmapManager.instance().cancelThreadDecoding(mGetterThread);
        MediaStore.Images.Thumbnails.cancelThumbnailRequest(mCr, -1);
    }

    // Cancels current loading (with waiting).
    private synchronized void cancelCurrentAndWait() {
        cancelCurrent();
        while (mIdle != true) {
            try {
                wait();
            } catch (InterruptedException ex) {
                // ignore.
            }
        }
    }

    // Stops this image getter.
    public void stop() {
        synchronized (this) {
            cancelCurrentAndWait();
            mDone = true;
            notify();
        }
        try {
            mGetterThread.join();
        } catch (InterruptedException ex) {
            // Ignore the exception
        }
        mGetterThread = null;
    }

    public synchronized void setPosition(int position, ImageGetterCallback cb,
            ArrayList<MediaLocTime> imageList, GetterHandler handler) {
        // Cancel the previous request.
        cancelCurrentAndWait();

        // Set new data.
        mCurrentPosition = position;
        mCB = cb;
        mImageList = imageList;
        mHandler = handler;
        mCurrentSerial += 1;

        // Kick-start the current request.
        mCancel = false;
        BitmapManager.instance().allowThreadDecoding(mGetterThread);
        notify();
    }
}

class GetterHandler extends Handler {
    private static final int IMAGE_GETTER_CALLBACK = 1;

    @Override
    public void handleMessage(Message message) {
        switch(message.what) {
            case IMAGE_GETTER_CALLBACK:
                ((Runnable) message.obj).run();
                break;
        }
    }

    public void postGetterCallback(Runnable callback) {
       postDelayedGetterCallback(callback, 0);
    }

    public void postDelayedGetterCallback(Runnable callback, long delay) {
        if (callback == null) {
            throw new NullPointerException();
        }
        Message message = Message.obtain();
        message.what = IMAGE_GETTER_CALLBACK;
        message.obj = callback;
        sendMessageDelayed(message, delay);
    }

    public void removeAllGetterCallbacks() {
        removeMessages(IMAGE_GETTER_CALLBACK);
    }
}
