package com.rareventure.gps2.reviewer.map;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;

import com.mapzen.tangram.MapController;

import com.mapzen.tangram.MapView;
import com.mapzen.tangram.TouchInput;
import com.mapzen.tangram.networking.HttpHandler;
import com.mapzen.tangram.viewholder.GLViewHolderFactory;

/**
 * A hack to modify MapController so that we can handle long press + pan
 */
public class MyMapController extends MapController {

    private final Context context;
    private MyTouchInput touchInput;

    protected MyMapController(Context context) {
        super(context);
        this.context = context;
    }

    protected void setupTouchListener()
    {
        //we create a new touch input that works as a proxy, but also supports new long press pan
        //controls.
        //We need to do this, rather than extend TouchInput, because MapController creates
        //a touch input in its constructor, and sets responders that call private methods within
        // it, such as nativeHandleFlingGesture(mapPointer, posX, posY, velocityX, velocityY);
        touchInput = new MyTouchInput(context, getTouchInput());
    }

    public boolean handleGesture(MapView mapView, MotionEvent ev) {
        return touchInput.onTouch(mapView, ev);
    }


    /**
     * Set a responder for long press gestures, including long press pan
     *
     * @param responder LongPressResponder to call
     */
    public void setLongPressResponderExt(final MyTouchInput.LongPressResponder responder) {
        touchInput.setLongPressResponder(new MyTouchInput.LongPressResponder() {
            @Override
            public void onLongPress(float x, float y) {
                if (responder != null) {
                    responder.onLongPress(x, y);
                }
            }

            @Override
            public boolean onLongPressPan(float x1, float x2, float x3, float x4) {
                if (responder != null) {
                    return responder.onLongPressPan(x1, x2, x3, x4);
                }
                return false;
            }

            @Override
            public void onLongPressUp(float x, float y) {
                if (responder != null) {
                    responder.onLongPressUp(x, y);
                }
            }
        });
    }


}
