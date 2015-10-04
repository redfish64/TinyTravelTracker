/** 
    Copyright 2015 Tim Engler, Rareventure LLC

    This file is part of Tiny Travel Tracker.

    Tiny Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Tiny Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Tiny Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.rareventure.gps2.tttprotocol;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import android.content.Context;
import android.util.Log;

import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.android.SuperThread;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTG.GTGEvent;
import com.rareventure.util.FreqTrigger;

public class TTTClient {
	private static final String USER_AGENT = /* ttt_installer:obfuscate_str */"GpsTrailer/1";

	private static final int NUM_RETRIES = 3;

	public static final int OK = 'O';

	public static final int OLD_TOKEN = 'T';

	public static final int BAD_TOKEN = 'B';

	public static final int NO_ID = 'I';

	public static final int TRIAL = 'N';

	public static final int EXPIRED_TRIAL = 'E';
	
	public static final int CHECK_LICENSE = 'C';
	public static final int NOT_LICENSED = 'D';


	private static final int MAX_CHALLENGE_LENGTH = Long.SIZE>>3;

	public static Preferences prefs = new Preferences();
	
	private static final String PROTOCOL_AND_SERVER = /* ttt_installer:obfuscate_str */"http://otile1.mqcdn.com";
//	private static final String PROTOCOL_AND_SERVER = /* ttt_installer:obfuscate_str */"http://192.168.1.70:3127";
	
	private static final String GET_TILE_FORMAT = PROTOCOL_AND_SERVER+
		/* ttt_installer:obfuscate_str */"/tiles/1.0.0/osm/%d/%d/%d.jpg";

	private static final int MAX_SOCKET_TIMEOUT = 15000;

	private static final long MIN_TIME_FOR_CHECKS_MS = 1000l * 3600 * 6; 
	
	/**
	 * This is used to shut off responding to the ttt server when it asks for a license
	 * if it doesn't accept our reply. If we get too many non accepted market replies in a row, we
	 * stop asking the market server, ie. ignore further CHECK_LICENSE requests
	 */
	private FreqTrigger contactLicenseServerFreqTrigger = new FreqTrigger(0, .1f, .5f);

	private long lastCheckMs;
	
	
	/**
	 * Gets a tile. This will
	 * handle identification and verification routines to determine whether the
	 * user purchased the product, etc.
	 * @param remoteLoaderTask 
	 * 
	 * @param context
	 * @param path
	 * @return
	 */
	public InputStream getTile( SuperThread.Task remoteLoaderTask, Context context, int zoomLevel, int tileX,
			int tileY) {
		for (int i = 0; i < NUM_RETRIES; i++) {
			try {
				URL urL = new URL(String.format(GET_TILE_FORMAT,
						zoomLevel,tileX,tileY));
	
				//we promise to die soon, because sometimes we just hang here. There is no way to shut
				//this down, and if the activity is waiting for this thread to shutdown itself, it'll
				//take 30 seconds before the activity shuts down
				
				//note, since we sometimes contact the license server from here, we can't turn this on
				//the whole time (the license server needs a context, so if we are shutting down
				// we have to wait for that call to complete)
				remoteLoaderTask.promiseToDieWithoutBadEffect(true);
				
				URLConnection connection = urL.openConnection();
				connection.setReadTimeout(MAX_SOCKET_TIMEOUT);
				connection.setConnectTimeout(MAX_SOCKET_TIMEOUT);
				connection.setRequestProperty("User-Agent", USER_AGENT);
				BufferedInputStream inStream = new BufferedInputStream(
						connection.getInputStream());
	
				// the server issues a response code as the first byte of the
				// resulting stream
				
				if(remoteLoaderTask.isDead())
				{
					inStream.close();
					return null;
				}
				
				
				return inStream;
			} catch (IOException e) {
				Log.w(GTG.TAG, /* ttt_installer:obfuscate_str */"Error connecting to TTT server",e);
			} finally {
				remoteLoaderTask.promiseToDieWithoutBadEffect(false);
			}
		}

		GTG.alert( GTGEvent.TTT_SERVER_DOWN);
		return null;
	}

	public static class Preferences implements AndroidPreferences {


		/**
		 * The token of the current challenge the server had us sign
		 */
		public String currToken;

		/**
		 * Our server issued id. Note, zero means no id
		 */
		public long id;
	}

	private static final String PURCHASE_STATE_CHANGED_FORMAT = PROTOCOL_AND_SERVER+
	/* ttt_installer:obfuscate_str */"/purchaseStateChanged?id=%d&signedData=%s&signature=%s";
	
	private static final String NOTIFY_LICENSE_RESPONSE_FORMAT = PROTOCOL_AND_SERVER+
	/* ttt_installer:obfuscate_str */"/notifyLicenseResponse?token=%s&id=%d&signedData=%s&signature=%s"; 


}
