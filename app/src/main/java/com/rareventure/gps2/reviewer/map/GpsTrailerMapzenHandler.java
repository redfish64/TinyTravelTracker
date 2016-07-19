package com.rareventure.gps2.reviewer.map;

import android.util.Log;

import com.mapzen.tangram.HttpHandler;
import com.rareventure.gps2.GTG;
import com.squareup.okhttp.Callback;

/**
 * Created by tim on 7/18/16.
 */
public class GpsTrailerMapzenHandler extends HttpHandler {
    public GpsTrailerMapzenHandler() {
        super();
    }

    @Override
    public boolean onRequest(String url, Callback cb) {
        Log.i(GTG.TAG,"url is "+url);

        //ex of our gps format: tim://foo/1/1/1

        if(url.startsWith("tim"))
            return false;
        return super.onRequest(url, cb);
    }
}
