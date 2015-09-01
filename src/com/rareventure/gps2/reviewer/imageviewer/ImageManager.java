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

package com.rareventure.gps2.reviewer.imageviewer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * ImageManager is used to retrieve and store images
 * in the media content provider.
 */
public class ImageManager {
    private static final String TAG = "ImageManager";

    private static final Uri STORAGE_URI = Images.Media.EXTERNAL_CONTENT_URI;
    private static final Uri THUMB_URI
            = Images.Thumbnails.EXTERNAL_CONTENT_URI;

    private static final Uri VIDEO_STORAGE_URI =
            Uri.parse("content://media/external/video/media");

    // ImageListParam specifies all the parameters we need to create an image
    // list (we also need a ContentResolver).
    public static class ImageListParam implements Parcelable {
        public DataLocation mLocation;
        public int mInclusion;
        public int mSort;
        public String mBucketId;

        // This is only used if we are creating a single image list.
        public Uri mSingleImageUri;

        // This is only used if we are creating an empty image list.
        public boolean mIsEmptyImageList;

        public ImageListParam() {}

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mLocation.ordinal());
            out.writeInt(mInclusion);
            out.writeInt(mSort);
            out.writeString(mBucketId);
            out.writeParcelable(mSingleImageUri, flags);
            out.writeInt(mIsEmptyImageList ? 1 : 0);
        }

        private ImageListParam(Parcel in) {
            mLocation = DataLocation.values()[in.readInt()];
            mInclusion = in.readInt();
            mSort = in.readInt();
            mBucketId = in.readString();
            mSingleImageUri = in.readParcelable(null);
            mIsEmptyImageList = (in.readInt() != 0);
        }

        public String toString() {
            return String.format("ImageListParam{loc=%s,inc=%d,sort=%d," +
                "bucket=%s,empty=%b,single=%s}", mLocation, mInclusion,
                mSort, mBucketId, mIsEmptyImageList, mSingleImageUri);
        }

        public static final Parcelable.Creator CREATOR
                = new Parcelable.Creator() {
            public ImageListParam createFromParcel(Parcel in) {
                return new ImageListParam(in);
            }

            public ImageListParam[] newArray(int size) {
                return new ImageListParam[size];
            }
        };

        public int describeContents() {
            return 0;
        }
    }

    // Location
    public static enum DataLocation { NONE, INTERNAL, EXTERNAL, ALL }

    // Inclusion
    public static final int INCLUDE_IMAGES = (1 << 0);
    public static final int INCLUDE_VIDEOS = (1 << 2);

    // Sort
    public static final int SORT_ASCENDING = 1;
    public static final int SORT_DESCENDING = 2;

    public static final String CAMERA_IMAGE_BUCKET_NAME =
            Environment.getExternalStorageDirectory().toString()
            + "/DCIM/Camera";
    public static final String CAMERA_IMAGE_BUCKET_ID =
            getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    /**
     * Matches code in MediaProvider.computeBucketValues. Should be a common
     * function.
     */
    public static String getBucketId(String path) {
        return String.valueOf(path.toLowerCase().hashCode());
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatibleFolder() {
        File nnnAAAAA = new File(
            Environment.getExternalStorageDirectory().toString()
            + "/DCIM/100ANDRO");
        if ((!nnnAAAAA.exists()) && (!nnnAAAAA.mkdir())) {
            Log.e(TAG, "create NNNAAAAA file: " + nnnAAAAA.getPath()
                    + " failed");
        }
    }

    public static int roundOrientation(int orientationInput) {
        int orientation = orientationInput;
        if (orientation == -1) {
            orientation = 0;
        }

        orientation = orientation % 360;
        int retVal;
        if (orientation < (0 * 90) + 45) {
            retVal = 0;
        } else if (orientation < (1 * 90) + 45) {
            retVal = 90;
        } else if (orientation < (2 * 90) + 45) {
            retVal = 180;
        } else if (orientation < (3 * 90) + 45) {
            retVal = 270;
        } else {
            retVal = 0;
        }

        return retVal;
    }

    /**
     * @return true if the mimetype is an image mimetype.
     */
    public static boolean isImageMimeType(String mimeType) {
        return mimeType.startsWith("image/");
    }

    /**
     * @return true if the mimetype is a video mimetype.
     */
    /* This is commented out because isVideo is not calling this now.
    public static boolean isVideoMimeType(String mimeType) {
        return mimeType.startsWith("video/");
    }
    */

    public static void setImageSize(ContentResolver cr, Uri uri, long size) {
        ContentValues values = new ContentValues();
        values.put(Images.Media.SIZE, size);
        cr.update(uri, values, null, null);
    }

    //
    // Stores a bitmap or a jpeg byte array to a file (using the specified
    // directory and filename). Also add an entry to the media store for
    // this picture. The title, dateTaken, location are attributes for the
    // picture. The degree is a one element array which returns the orientation
    // of the picture.
    //
    public static Uri addImage(ContentResolver cr, String title, long dateTaken,
            Location location, String directory, String filename,
            Bitmap source, byte[] jpegData, int[] degree) {
        // We should store image data earlier than insert it to ContentProvider, otherwise
        // we may not be able to generate thumbnail in time.
        OutputStream outputStream = null;
        String filePath = directory + "/" + filename;
        try {
            File dir = new File(directory);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(directory, filename);
            outputStream = new FileOutputStream(file);
            if (source != null) {
                source.compress(CompressFormat.JPEG, 75, outputStream);
                degree[0] = 0;
            } else {
                outputStream.write(jpegData);
                degree[0] = getExifOrientation(filePath);
            }
        } catch (FileNotFoundException ex) {
            Log.w(TAG, ex);
            return null;
        } catch (IOException ex) {
            Log.w(TAG, ex);
            return null;
        } finally {
            Util.closeSilently(outputStream);
        }

        ContentValues values = new ContentValues(7);
        values.put(Images.Media.TITLE, title);

        // That filename is what will be handed to Gmail when a user shares a
        // photo. Gmail gets the name of the picture attachment from the
        // "DISPLAY_NAME" field.
        values.put(Images.Media.DISPLAY_NAME, filename);
        values.put(Images.Media.DATE_TAKEN, dateTaken);
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.ORIENTATION, degree[0]);
        values.put(Images.Media.DATA, filePath);

        if (location != null) {
            values.put(Images.Media.LATITUDE, location.getLatitude());
            values.put(Images.Media.LONGITUDE, location.getLongitude());
        }

        return cr.insert(STORAGE_URI, values);
    }

    public static int getExifOrientation(String filepath) {
        int degree = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filepath);
        } catch (IOException ex) {
            Log.e(TAG, "cannot read exif", ex);
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognize a subset of orientation tag values.
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                }

            }
        }
        return degree;
    }

    static boolean isSingleImageMode(String uriString) {
        return !uriString.startsWith(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
                && !uriString.startsWith(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString());
    }

    public static ImageListParam getImageListParam(DataLocation location,
         int inclusion, int sort, String bucketId) {
         ImageListParam param = new ImageListParam();
         param.mLocation = location;
         param.mInclusion = inclusion;
         param.mSort = sort;
         param.mBucketId = bucketId;
         return param;
    }

    public static ImageListParam getSingleImageListParam(Uri uri) {
        ImageListParam param = new ImageListParam();
        param.mSingleImageUri = uri;
        return param;
    }

    public static ImageListParam getEmptyImageListParam() {
        ImageListParam param = new ImageListParam();
        param.mIsEmptyImageList = true;
        return param;
    }

    private static boolean checkFsWritable() {
        // Create a temporary file to see whether a volume is really writeable.
        // It's important not to put it in the root directory which may have a
        // limit on the number of files.
        String directoryName =
                Environment.getExternalStorageDirectory().toString() + "/DCIM";
        File directory = new File(directoryName);
        if (!directory.isDirectory()) {
            if (!directory.mkdirs()) {
                return false;
            }
        }
        File f = new File(directoryName, ".probe");
        try {
            // Remove stale file if any
            if (f.exists()) {
                f.delete();
            }
            if (!f.createNewFile()) {
                return false;
            }
            f.delete();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static boolean hasStorage() {
        return hasStorage(true);
    }

    public static boolean hasStorage(boolean requireWriteAccess) {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (requireWriteAccess) {
                boolean writable = checkFsWritable();
                return writable;
            } else {
                return true;
            }
        } else if (!requireWriteAccess
                && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    private static Cursor query(ContentResolver resolver, Uri uri,
            String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        try {
            if (resolver == null) {
                return null;
            }
            return resolver.query(
                    uri, projection, selection, selectionArgs, sortOrder);
         } catch (UnsupportedOperationException ex) {
            return null;
        }

    }

    public static boolean isMediaScannerScanning(ContentResolver cr) {
        boolean result = false;
        Cursor cursor = query(cr, MediaStore.getMediaScannerUri(),
                new String [] {MediaStore.MEDIA_SCANNER_VOLUME},
                null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }

        return result;
    }

    public static String getLastImageThumbPath() {
        return Environment.getExternalStorageDirectory().toString() +
               "/DCIM/.thumbnails/image_last_thumb";
    }

    public static String getLastVideoThumbPath() {
        return Environment.getExternalStorageDirectory().toString() +
               "/DCIM/.thumbnails/video_last_thumb";
    }

    public static String getTempJpegPath() {
        return Environment.getExternalStorageDirectory().toString() +
               "/DCIM/.tempjpeg";
    }
}
