package com.rareventure.gps2.reviewer.map;

import android.content.Context;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;

import com.almeros.android.multitouch.RotateGestureDetector;
import com.almeros.android.multitouch.RotateGestureDetector.OnRotateGestureListener;
import com.almeros.android.multitouch.ShoveGestureDetector;
import com.almeros.android.multitouch.ShoveGestureDetector.OnShoveGestureListener;
import com.mapzen.tangram.TouchInput;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * {@code TouchInput} collects touch data, applies gesture detectors, resolves simultaneous
 * detection, and calls the appropriate input responders.
 */
public class MyTouchInput implements OnTouchListener {

    private boolean longPressActive;

    /**
     * This is used to detect long presses. It is the same as panTapGestureDetector, but with
     * setIsLongpressEnabled() set to true. The problem is that after a longpress, GestureDetector
     * will mute onScroll messages until the long press is finished. This doesn't work well for
     * applications that use long press and onScroll events together (such as Open Travel Tracker).
     * <p>
     *     So by detecting long presses with this gesture detector, we are able to shutoff long
     *     press detection in the panTapGestureDetector, so it will still report onScroll messages
     *     even after a long press.
     * </p>
     */
    private final GestureDetector longPressGestureDetector;
    private final GestureDetector panGestureDetector;

    private TouchInput orig;

    public MyTouchInput(Context context, TouchInput onTouchListener) {
        this.orig = onTouchListener;

        this.longPressGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener()
        {
            @Override
            public void onLongPress(MotionEvent e) {
                MyTouchInput.this.onLongPress(e);
            }
        }
        );

        this.panGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener()
        {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return MyTouchInput.this.onScroll(e1, e2, distanceX, distanceY);
            }
        }
        );

        panGestureDetector.setIsLongpressEnabled(false);
    }

    public void onLongPress(MotionEvent e) {
        longPressActive = true;
         if (longPressResponder != null) {
             longPressResponder.onLongPress(e.getX(), e.getY());
         }

    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        //we handle all the touch stuff ourselves, rather than
        //allowing the proxy to do it. This is because
        //we have to alter panTapGestureDetector and
        //it is private within TouchInput

        if(event.getActionMasked() == MotionEvent.ACTION_UP)
        {
            //notify the responder that the long press is finished
            if(longPressActive)
            {
                //notify the responder that the long press is finished
                if(longPressResponder != null)
                    longPressResponder.onLongPressUp(event.getX(), event.getY());
                longPressActive = false;
            }
        }

        longPressGestureDetector.onTouchEvent(event);
        panGestureDetector.onTouchEvent(event);

        if(!longPressActive)
            orig.onTouch(view,event);

        return true;
    }

    /**
     * This is our long press responder. We add the ability to drag.
     */
    public interface LongPressResponder {
        /**
         * Called immediately after a long press is detected
         *
         * The duration threshold for a long press is determined by {@link ViewConfiguration}
         * @param x The x screen coordinate of the pressed point
         * @param y The y screen coordinate of the pressed point
         */
        void onLongPress(float x, float y);

        /**
         * Called when long press has stopped
         *
         * @param x The x screen coordinate of the pressed point
         * @param y The y screen coordinate of the pressed point
         */
        void onLongPressUp(float x, float y);

        /**
         * Called repeatedly while a touch point is dragged after a long press
         * @param startX The starting x screen coordinate for an interval of motion
         * @param startY The starting y screen coordinate for an interval of motion
         * @param endX The ending x screen coordinate for an interval of motion
         * @param endY The ending y screen coordinate for an interval of motion
         * @return True if the event is consumed, false if the event should continue to propagate
         */
        boolean onLongPressPan(float startX, float startY, float endX, float endY);
    }

    private LongPressResponder longPressResponder;

    /**
     * Set a {@link LongPressResponder}
     * @param responder The responder object, or null to leave these gesture events unchanged
     */
    public void setLongPressResponder(LongPressResponder responder) {
        this.longPressResponder = responder;
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if(longPressResponder == null || !longPressActive)
            return false;

        //copied from TouchInput
        float x = 0, y = 0;
        int n = e2.getPointerCount();
        for (int i = 0; i < n; i++) {
            x += e2.getX(i) / n;
            y += e2.getY(i) / n;
        }

        return longPressResponder.onLongPressPan(x + distanceX, y + distanceY, x, y);
    }

}
