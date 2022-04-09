package com.igisw.openlocationtracker;

import com.igisw.openlocationtracker.IGpsTrailerServiceCallback;


interface IGpsTrailerService {
    void registerCallback(IGpsTrailerServiceCallback cb);
    
    void unregisterCallback(IGpsTrailerServiceCallback cb);
}
