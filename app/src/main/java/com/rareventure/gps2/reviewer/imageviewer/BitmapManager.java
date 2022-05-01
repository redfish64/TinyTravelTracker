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

package com.rareventure.gps2.reviewer.imageviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileDescriptor;
import java.util.WeakHashMap;

/**
 * This class provides several utilities to cancel bitmap decoding.
 *
 * The function decodeFileDescriptor() is used to decode a bitmap. During
 * decoding if another thread wants to cancel it, it calls the function
 * cancelThreadDecoding() specifying the Thread which is in decoding.
 *
 * cancelThreadDecoding() is sticky until allowThreadDecoding() is called.
 */
public class BitmapManager {
    private static final String TAG = "BitmapManager";
    private static enum State {CANCEL, ALLOW}
    private static class ThreadStatus {
        public State mState = State.ALLOW;
        public BitmapFactory.Options mOptions;

        @Override
        public String toString() {
            String s;
            if (mState == State.CANCEL) {
                s = "Cancel";
            } else if (mState == State.ALLOW) {
                s = "Allow";
            } else {
                s = "?";
            }
            s = "thread state = " + s + ", options = " + mOptions;
            return s;
        }
    }

    private final WeakHashMap<Thread, ThreadStatus> mThreadStatus =
            new WeakHashMap<Thread, ThreadStatus>();

    private static BitmapManager sManager = null;

    private BitmapManager() {
    }

    /**
     * Get thread status and create one if specified.
     */
    private synchronized ThreadStatus getOrCreateThreadStatus(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        if (status == null) {
            status = new ThreadStatus();
            mThreadStatus.put(t, status);
        }
        return status;
    }

    /**
     * The following three methods are used to keep track of
     * BitmapFaction.Options used for decoding and cancelling.
     */
    private synchronized void setDecodingOptions(Thread t,
            BitmapFactory.Options options) {
        getOrCreateThreadStatus(t).mOptions = options;
    }

    synchronized void removeDecodingOptions(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        status.mOptions = null;
    }

    /**
     * The following three methods are used to keep track of which thread
     * is being disabled for bitmap decoding.
     */
    public synchronized boolean canThreadDecoding(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        if (status == null) {
            // allow decoding by default
            return true;
        }

        boolean result = (status.mState != State.CANCEL);
        return result;
    }

    public synchronized void allowThreadDecoding(Thread t) {
        getOrCreateThreadStatus(t).mState = State.ALLOW;
    }

    public synchronized void cancelThreadDecoding(Thread t) {
        ThreadStatus status = getOrCreateThreadStatus(t);
        status.mState = State.CANCEL;
        if (status.mOptions != null) {
            status.mOptions.requestCancelDecode();
        }

        // Wake up threads in waiting list
        notifyAll();
    }

    public static synchronized BitmapManager instance() {
        if (sManager == null) {
            sManager = new BitmapManager();
        }
        return sManager;
    }

    /**
     * The real place to delegate bitmap decoding to BitmapFactory.
     */
    public Bitmap decodeFileDescriptor(FileDescriptor fd,
                                       BitmapFactory.Options options) {
        if (options.mCancel) {
            return null;
        }

        Thread thread = Thread.currentThread();
        if (!canThreadDecoding(thread)) {
            Log.d(TAG, "Thread " + thread + " is not allowed to decode.");
            return null;
        }

        setDecodingOptions(thread, options);
        Bitmap b = BitmapFactory.decodeFileDescriptor(fd, null, options);

        removeDecodingOptions(thread);
        return b;
    }
}
