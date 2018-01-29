package com.rareventure.gps2.reviewer.map;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.View;

/**
 * A GLSurfaceView which holds onto its ontouchlistener
 */

public class MyGLSurfaceView extends GLSurfaceView {
    private OnTouchListener otl;

    public MyGLSurfaceView(Context context) {
        super(context);
    }

    public MyGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setOnTouchListener(OnTouchListener l) {
        this.otl = l;
        super.setOnTouchListener(l);
    }

    public OnTouchListener getOnTouchListener(){
        return otl;
    }
}
