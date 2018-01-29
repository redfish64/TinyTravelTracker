package com.rareventure.gps2.reviewer.map;

import android.opengl.GLSurfaceView;

import com.mapzen.tangram.MapController;

import com.mapzen.tangram.TouchInput;

/**
 * A hack to modify MapController so that we can handle long press + pan
 */
public class MyMapController extends MapController {

    private MyTouchInput touchInput;

    protected MyMapController(GLSurfaceView view) {
        super(view);

        //we create a new touch input that works as a proxy, but also supports new long press pan
        //controls.
        //We need to do this, rather than use a new custom TouchInput, because MapController creates
        //a touch input in its constructor, and sets rseponders that call private methods within
        // it, such as nativeHandleFlingGesture(mapPointer, posX, posY, velocityX, velocityY);
        touchInput = new MyTouchInput(view.getContext(), (TouchInput) ((MyGLSurfaceView)view).getOnTouchListener());

        //we re-set the touch listener to our special proxy that handles longpress pan
        view.setOnTouchListener(touchInput);

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
