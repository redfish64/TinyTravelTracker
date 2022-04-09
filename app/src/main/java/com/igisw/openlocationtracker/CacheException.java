package com.igisw.openlocationtracker;

import android.util.Log;

/**
 * A CacheException means something went wrong with the cache. We're all for last ditch automated
 * fixes around here, so we mark the cache corrupt, so next time we load, it'll rebuild it
 * and hopefully fix the cause.
 */
public class CacheException extends IllegalStateException {
    public CacheException(Throwable cause) {
        super(cause);
        init();
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
        init();
    }

    public CacheException(String detailMessage) {
        super(detailMessage);
        init();

    }

    public CacheException() {
        init();
    }

    private void init()
    {
        Log.e(GTG.TAG,"CacheException created, marking database corrupt");
        GTG.timmyDb.setCorrupt();
    }
}
