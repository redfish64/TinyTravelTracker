package com.rareventure.gps2.reviewer.map;

import android.os.Handler;
import android.util.Log;

import com.mapzen.tangram.HttpHandler;
import com.rareventure.gps2.GTG;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;

/**
 * This special handler will create tiles for gps points when url starts with "gpstrailer:"
 */
public class GpsTrailerMapzenHttpHandler extends HttpHandler {
    private static final byte[] HACK_STATIC_GEOJSON =
            ("{ \"type\": \"FeatureCollection\",\n"+
                    "    \"features\": [\n"+
//                    "      { \"type\": \"Feature\",\n"+
//                    "        \"geometry\": {\"type\": \"Point\", \"coordinates\": [102.0, -0.5]},\n"+
//                    "        \"properties\": {\"prop0\": \"value0\"}\n"+
//                    "        },\n"+
                    "      { \"type\": \"Feature\",\n"+
                    "        \"geometry\": {\n"+
                    "          \"type\": \"MultiPoint\",\n"+
                    "          \"coordinates\": [\n"+
                    "            [-102.0, 0.0], [-103.0, 1.0], [-104.0, 0.0], [-105.0, 1.0]\n"+
                    "            ]\n"+
                    "          },\n"+
                    "        \"properties\": {\n"+
                    "          \"prop0\": \"value0\",\n"+
                    "          \"prop1\": 0.0\n"+
                    "          }\n"+
                    "        }\n"+
//                    "      { \"type\": \"Feature\",\n"+
//                    "         \"geometry\": {\n"+
//                    "           \"type\": \"Polygon\",\n"+
//                    "           \"coordinates\": [\n"+
//                    "             [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],\n"+
//                    "               [100.0, 1.0], [100.0, 0.0] ]\n"+
//                    "             ]\n"+
//                    "         },\n"+
//                    "         \"properties\": {\n"+
//                    "           \"prop0\": \"value0\",\n"+
//                    "           \"prop1\": {\"this\": \"that\"}\n"+
//                    "           }\n"+
//                    "         }\n"+
                    "       ]\n"+
                    "     }\n").getBytes();
    public Handler hackHandler = new Handler();

    public GpsTrailerMapzenHttpHandler() {
        super();
    }

    @Override
    public boolean onRequest(final String url, final Callback cb) {
        Log.i(GTG.TAG,"url is "+url);

        //ex of our gps format: gpstrailer://foo/1/1/1

        if(url.startsWith("gpstrailer"))
        {
            hackHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //the tanzam code only looks at the body, so we just use dummy values for everything else
                    Response.Builder b = new Response.Builder();
                    b.body(ResponseBody.create(null,
                            HACK_STATIC_GEOJSON
                            //"".getBytes()
                            ));
                    b.code(200);
                    b.protocol(Protocol.HTTP_1_1);
                    b.request(okRequestBuilder.tag("http://mickymouse").url("http://mickymouse").build());
                    Response r = b.build();
                    try {
                        cb.onResponse(r);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            },100);
//            gpsTrailerOverlay.queueRequest()
            return true;
        }
        return super.onRequest(url, cb);
    }
}
