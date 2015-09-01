package com.rareventure.gps2;

import com.rareventure.gps2.IGpsTrailerServiceCallback;


interface IGpsTrailerService {
    void registerCallback(IGpsTrailerServiceCallback cb);
    
    void unregisterCallback(IGpsTrailerServiceCallback cb);
}
