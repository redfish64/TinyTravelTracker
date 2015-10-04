/*
 * Code based off the PhotoSortrView from Luke Hutchinson's MTPhotoSortr
 * example (http://code.google.com/p/android-multitouch-controller/)
 *
 * License:
 *   Dual-licensed under the Apache License v2 and the GPL v2.
 */
package org.metalev.multitouch.controller;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap;
import android.graphics.Color;

import android.content.res.Resources;
import android.content.Context;
import android.content.res.Configuration;

import android.util.DisplayMetrics;
import android.util.Log;

import java.io.Serializable;

import org.metalev.multitouch.controller.MultiTouchController.PositionAndScale;

public abstract class MultiTouchEntity implements Serializable {

    protected boolean mFirstLoad = true;

    protected transient Paint mPaint = new Paint();

    protected int mWidth;
    protected int mHeight;

    // width/height of screen
    protected int mDisplayWidth;
    protected int mDisplayHeight;

    protected float mCenterX;
    protected float mCenterY;
    protected float mScaleX;
    protected float mScaleY;
    protected float mAngle;

    protected float mMinX;
    protected float mMaxX;
    protected float mMinY;
    protected float mMaxY;

    // area of the entity that can be scaled/rotated
    // using single touch (grows from bottom right)
    protected final static int GRAB_AREA_SIZE = 40;
    protected boolean mIsGrabAreaSelected = false;
    protected boolean mIsLatestSelected = false;

    protected float mGrabAreaX1;
    protected float mGrabAreaY1;
    protected float mGrabAreaX2;
    protected float mGrabAreaY2;

    protected float mStartMidX;
    protected float mStartMidY;

	private static final int UI_MODE_ROTATE = 1;
    private static final int UI_MODE_ANISOTROPIC_SCALE = 2;
    protected int mUIMode = UI_MODE_ROTATE;

    public MultiTouchEntity() {
    }

    public MultiTouchEntity(Resources res) {
        getMetrics(res);
    }

    protected void getMetrics(Resources res) {
        DisplayMetrics metrics = res.getDisplayMetrics();
        mDisplayWidth =
            (res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                ? Math.max(metrics.widthPixels, metrics.heightPixels)
                : Math.min(metrics.widthPixels, metrics.heightPixels);
        mDisplayHeight =
            (res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                ? Math.min(metrics.widthPixels, metrics.heightPixels)
                : Math.max(metrics.widthPixels, metrics.heightPixels);
    }

    /**
     * Set the position and scale of an image in screen coordinates
     */
    public boolean setPos(PositionAndScale newImgPosAndScale) {
        float newScaleX;
        float newScaleY;

        if ((mUIMode & UI_MODE_ANISOTROPIC_SCALE) != 0) {
            newScaleX = newImgPosAndScale.getScaleX();
        } else {
            newScaleX = newImgPosAndScale.getScale();
        }

        if ((mUIMode & UI_MODE_ANISOTROPIC_SCALE) != 0) {
            newScaleY = newImgPosAndScale.getScaleY();
        } else {
            newScaleY = newImgPosAndScale.getScale();
        }

        return setPos(newImgPosAndScale.getXOff(),
                      newImgPosAndScale.getYOff(),
                      newScaleX,
                      newScaleY,
                      newImgPosAndScale.getAngle());
    }

    /**
     * Set the position and scale of an image in screen coordinates
     */
    protected boolean setPos(float centerX, float centerY,
                             float scaleX, float scaleY, float angle) {
        float ws = (mWidth / 2) * scaleX;
        float hs = (mHeight / 2) * scaleY;

        mMinX = centerX - ws;
        mMinY = centerY - hs;
        mMaxX = centerX + ws;
        mMaxY = centerY + hs;

        mGrabAreaX1 = mMaxX - GRAB_AREA_SIZE;
        mGrabAreaY1 = mMaxY - GRAB_AREA_SIZE;
        mGrabAreaX2 = mMaxX;
        mGrabAreaY2 = mMaxY;

        mCenterX = centerX;
        mCenterY = centerY;
        mScaleX = scaleX;
        mScaleY = scaleY;
        mAngle = angle;

        return true;
    }

    /**
     * Return whether or not the given screen coords are inside this image
     */
    public boolean containsPoint(float touchX, float touchY) {
        return (touchX >= mMinX && touchX <= mMaxX && touchY >= mMinY && touchY <= mMaxY);
    }

    public boolean grabAreaContainsPoint(float touchX, float touchY) {
        return (touchX >= mGrabAreaX1 && touchX <= mGrabAreaX2 &&
                touchY >= mGrabAreaY1 && touchY <= mGrabAreaY2);
    }

    public void reload(Context context) {
        mFirstLoad = false; // Let the load know properties have changed so reload those,
                            // don't go back and start with defaults
        load(context, mCenterX, mCenterY);
    }

    public abstract void draw(Canvas canvas);
    public abstract void load(Context context, float startMidX, float startMidY);
    public abstract void unload();

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public float getCenterX() {
        return mCenterX;
    }

    public float getCenterY() {
        return mCenterY;
    }

    public float getScaleX() {
        return mScaleX;
    }

    public float getScaleY() {
        return mScaleY;
    }

    public float getAngle() {
        return mAngle;
    }

    public float getMinX() {
        return mMinX;
    }

    public float getMaxX() {
        return mMaxX;
    }

    public float getMinY() {
        return mMinY;
    }

    public float getMaxY() {
        return mMaxY;
    }

    public void setIsGrabAreaSelected(boolean selected) {
        mIsGrabAreaSelected = selected;
    }

    public boolean isGrabAreaSelected() {
        return mIsGrabAreaSelected;
    }
}
