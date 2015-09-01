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
package com.rareventure.gps2.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import junit.framework.Assert;
import android.database.Cursor;

import com.rareventure.android.DbUtil;
import com.rareventure.android.Util;
import com.rareventure.android.database.Cache;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerCrypt;

//TODO 2.5: PERF: Make this class not load all user locations into memory
public class UserLocationCache extends Cache<UserLocationRow>{
	private static final int USER_LOC_CACHE_MAX_CACHE_SIZE = 256;

	public UserLocationCache()
	{
		super(new DbDatastoreAccessor<UserLocationRow>(UserLocationRow.TABLE_INFO), USER_LOC_CACHE_MAX_CACHE_SIZE);
	}
	
//	private final Comparator<UserLocationRow> USER_LOCATIONS_LONM_COMPARATOR = new Comparator<UserLocationRow>() {
//
//				@Override
//				public int compare(UserLocationRow o1, UserLocationRow o2) {
//					if(o2 == null)
//						return o1.getLonm() - searchFor;
//					
//					//we won't overflow on this, ie only -180M to 180M
//					return o1.getLonm() - o2.getLonm();
//				}
//			};
//			
//	private ArrayList<UserLocationRow> userLocations = new ArrayList<UserLocationRow>();
//
//	private int searchFor; 
//	
//	public UserLocationIterator getUserLocations(int startLonm, int widthLonm, int startLatm, int heightLatm)
//	{
//		searchFor = startLonm;
//		int startIndex = Collections.binarySearch(userLocations, null, USER_LOCATIONS_LONM_COMPARATOR);
//		startIndex = startIndex >= 0 ? startIndex : -startIndex-1;
//		
//		return new UserLocationIterator(startIndex, startLonm, widthLonm, startLatm, heightLatm);
//	}
//	
//	public class UserLocationIterator
//	{
//		// the number of times we wrapped around the earth
//		public int timesWrapped;
//		
//		int i;
//
//		private int startLatm;
//
//		private int heightLatm;
//
//		private int widthLonmLeft;
//
//		private int lastLonm;
//		
//		public UserLocationIterator(int startIndex, int startLonm, int widthLonm, int startLatm, int heightLatm)
//		{
//			i = startIndex-1;
//			this.widthLonmLeft = widthLonm;
//			this.startLatm = startLatm;
//			this.heightLatm = heightLatm;
//			lastLonm = startLonm;
//		}
//		
//		public UserLocationRow getNext() {
//			while(true)
//			{
//				i++;
//				
//				if(i >= userLocations.size())
//				{
//					i -= userLocations.size();
//					timesWrapped++;
//				}
//				
//				//we measure how many points to return based on the width of lonm. This
//				//way if we wrap the world several times, we can return the points over and over again
//				widthLonmLeft -= Util.subtractLonm(userLocations.get(i).getLonm(),lastLonm);
//				
//				lastLonm = userLocations.get(i).getLonm();
//				
//				if(widthLonmLeft < 0)
//					return null;
//				
//				if(userLocations.get(i).getLatm() >= startLatm &&
//						userLocations.get(i).getLatm() < startLatm + heightLatm)
//					break;
//			}			
//			return userLocations.get(i);
//		}
//	}

	@Override
	protected UserLocationRow allocateRow() {
		return GpsTrailerCrypt.allocateUserLocationRow();
	}

}
