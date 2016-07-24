package com.rareventure.gps2.reviewer.map;

import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

import com.mapzen.tangram.HttpHandler;
import com.rareventure.android.SuperThread;
import com.rareventure.gps2.GTG;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.DiskLruCache;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.io.FileSystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import okio.BufferedSource;

/**
 * This http handler alters caching somewhat, it assumes a slow
 * connection. We cache every tile, and
 * whenever a request comes in, we try to satisfy it with our cache.
 * If not found, we go to the network, and return it, while saving the
 * result in our cache also.
 * <p>
 * If found, then we check the age of the tile. If it's really out of
 * date, (on the order of months), we will check the network for a replacement.
 * If the network fails, we return the cache.
 * (The goal here is to minimize making the user wait for the network, but also not
 * return ancient tiles unless absolutely necessary).
 * <p>
 *     If the age of the tile is not very fresh, but not ancient enough to
 *     check the network first, we return the cached tile as a result, and then go
 *     out to the network to replace our cached tile afterwards.
 *     This keeps the app responsive, but also loads new tiles in a reasonable
 *     timeframe.
 * </p>
 * <p>
 *     Since it's impossible to get tangram to reload a tile currently, we
 *     can't update it with another tile after we've returned one. This is
 *     why we need to choose whether a cached tile will work, even though it
 *     may not be the freshest copy.
 * </p>
 */
public class GpsTrailerMapzenHttpHandler extends HttpHandler {
    private static final long MAX_TILE_LENGTH = 1024*1024*10;
    private static Preferences prefs = new Preferences();
    private final File cacheDir;
    private SuperThread superThread;

    public GpsTrailerMapzenHttpHandler(File cacheDir, SuperThread superThread) {
        super();
        this.cacheDir = cacheDir;
        this.superThread = superThread;
    }

    @Override
    public boolean onRequest(final String url, final Callback cb) {
        superThread.addTask(new RequestTask(url, cb));
        return true;
    }

    private static enum NetworkState
    { NOT_TRIED, SUCCESS, FAIL };
    private static enum CacheState
    {
        NOT_TRIED,
        ANCIENT, //cache file is there, but very old
        OK, //cache file is there, but is somewhat old
        FRESH, //cache file is present and piping hot
        CORRUPT_OR_MISSING //cache file is either unreadble or missing
    };

    private class RequestTask extends SuperThread.Task
    {
        private static final long MIN_TILE_LENGTH = 1;
        private final String url;
        private final File file;
        private final Callback myCb;
        private final Callback cb;
        private byte [] responseData;
        private boolean respondedToCallback = false;

        private NetworkState ns = NetworkState.NOT_TRIED;
        private CacheState cs = CacheState.NOT_TRIED;
        private boolean savedNetworkResponseToCache = false;
        private IOException networkFailException;
        private Request networkFailRequest;

        @Override
        public String toString() {
            return "RequestTask{" +
                    "file=" + file +
                    ", ns=" + ns +
                    ", cs=" + cs +
                    ", respondedToCallback=" + respondedToCallback +
                    ", savedNetworkResponseToCache=" + savedNetworkResponseToCache +
                    ", url='" + url + '\'' +
                    '}';
        }

        RequestTask(String url, Callback cb)
        {
            super(0);
            this.url = url;
            this.cb = cb;
            this.myCb = new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    RequestTask.this.ns = NetworkState.FAIL;
                    RequestTask.super.stNotify(RequestTask.this);
                    networkFailRequest = request;
                    networkFailException = e;
                }

                @Override
                public void onResponse(Response response) {
                    if(!response.isSuccessful()) {
                        RequestTask.this.ns = NetworkState.FAIL;
                    }
                    else
                    {
                        try {
                            RequestTask.this.responseData = getDataFromResponse(response);
                            RequestTask.this.ns = NetworkState.SUCCESS;
                        }
                        catch (IOException e)
                        {
                            Log.i(GTG.TAG,"Error reading response from server",e);
                            RequestTask.this.ns = NetworkState.FAIL;
                        }
                    }

                    RequestTask.super.stNotify(RequestTask.this);
                }
            };
            this.file = getCacheFile(url);
        }

        private byte[] getDataFromResponse(Response response) throws IOException {
            BufferedSource source = response.body().source();
            return source.readByteArray();
        }

        private File getCacheFile(String url) {
            String fileName = cacheDir+"/"+Util.md5Hex(url);
            return new File(fileName);
        }

        /**
         * The main loop of the task.
         * <p>We take a look at the state, and decide what needs to be done accordingly,
         * whether checking the cache or going out to the network
         * </p>
         */
        @Override
        protected void doWork() {
//            Log.d(GTG.TAG,"GpsTrailerMapzenHttpHandler.doWork() "+this);
            //note that doWork() will automatically be called again after returning
            //until stExit() is called

            if (cs == CacheState.NOT_TRIED) {
                updateCacheState();
            }

            if(!respondedToCallback)
            {
                if (cs == CacheState.OK || cs == CacheState.FRESH)
                {
                    respondToCallbackWithCache();
                }
                else if (ns == NetworkState.NOT_TRIED)
                {
                    requestFromNetwork();
                    stWait(0,RequestTask.this);
                    return; //stwait does not pause the thread, but simply prevents
                    //doWork() from being called again until stNotify() is called.
                }
                else if (ns == NetworkState.FAIL)
                {
                    //if we can't get a result from the network
                    //we will return the cache state, even if ancient
                    if(cs != CacheState.CORRUPT_OR_MISSING)
                    {
                        respondToCallbackWithCache();
                    }
                    else
                    {
                        //the cache is empty and the network failed so we give up
                        cb.onFailure(networkFailRequest, networkFailException);
//                        Log.d(GTG.TAG,"Task finished unsuccesfully for "+RequestTask.this);
                        stExit();
                        return;
                    }
                }
                else // (ns == NetworkState.SUCCESS)
                {
                    respondToCallbackWithResponse();
                }
            }
            else if(ns == NetworkState.SUCCESS && !savedNetworkResponseToCache)
            {
                saveNetworkResponseToCache();
            }
            else { //nothing more to do
//                Log.d(GTG.TAG,"Task finished for "+RequestTask.this);
                stExit();
                return;
            }

            //if we get here, doWork() will be called again
        }

        private void saveNetworkResponseToCache() {
            try {
                writeToFile(file, responseData);
            }
            catch (IOException e)
            {
                Log.e(GTG.TAG,"Can't save response to file "+file,e);
            }

            //even if we fail, we still mark that we saved the network response,
            //because there is nothing that would be done differently had we
            //succeeded.
            savedNetworkResponseToCache = true;
        }

        private void writeToFile(File file, byte[] bytes) throws IOException {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();;
        }

        private void respondToCallbackWithResponse() {
            try {
                cb.onResponse(createResponseFromBytes(responseData));
                respondedToCallback = true;
            } catch (IOException e) {
                Log.w(GTG.TAG,"Couldn't read network response for "+url+", assuming failed",e);
                ns = NetworkState.FAIL;
            }
        }

        private void requestFromNetwork() {
            GpsTrailerMapzenHttpHandler.super.onRequest(url,myCb);
        }

        private Response createResponseFromBytes(byte [] data)
        {
            Response.Builder b = new Response.Builder();
            b.body(ResponseBody.create(null,data));
            b.code(200);
            b.protocol(Protocol.HTTP_1_1);
            b.request(okRequestBuilder.tag(url).url(url).build());
            Response r = b.build();

            return r;
        }

        private void respondToCallbackWithCache() {
            //the tanzam code only looks at the body, so we just use dummy values for everything else
            //Also, it reads the data in one go, and stores it all in memory, so there is no
            //reason to do any fancy buffering with streams
            //See MapController.startUrlRequest()

            try {
                cb.onResponse(createResponseFromBytes(getFileBytes(file)));
                respondedToCallback = true;
            } catch (IOException e) {
                Log.w(GTG.TAG,"Couldn't read cache file "+file+", assuming corrupt",e);
                cs = CacheState.CORRUPT_OR_MISSING;
            }
        }

        private byte[] getFileBytes(File file) throws IOException {
            long len = file.length();
            if(len > MAX_TILE_LENGTH)
                throw new IOException("tile too big, "+file+": "+len);
            if(len < MIN_TILE_LENGTH)
                throw new IOException("tile too small, "+file+": "+len);
            byte [] out = new byte[(int)len];
            FileInputStream fis = new FileInputStream(file);
            com.rareventure.android.Util.readFully(fis, out);
            fis.close();
            return out;
        }


        private void updateCacheState() {
            if(!file.exists()) {
                cs = CacheState.CORRUPT_OR_MISSING;
                return;
            }

            long ageMs = System.currentTimeMillis() - file.lastModified();
            if(ageMs >  GpsTrailerMapzenHttpHandler.prefs.ancientCacheMs)
                cs = CacheState.ANCIENT;
            else if(ageMs >  GpsTrailerMapzenHttpHandler.prefs.okCacheMs)
                cs = CacheState.OK;
            else
                cs = CacheState.FRESH;
        }


    }
    public static class Preferences
    {
        /**
         * Any tile created before this period is considered ancient.
         * This means that we will go to the network *first* and only
         * if that fails, display this very old tile.
         */
        public long ancientCacheMs = 1000l * 60 * 60 * 24 * 30; //one month

        /**
         * Any tile created before this period will be returned immediately
         * for any request (as long as its not before ancientCacheMs).
         * However, the network will still be probed afterwards to
         * refresh this tile for the next time.
         */
        public long okCacheMs = 1000l * 60 * 60 * 24 * 2; // two days
    }


}
