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
package com.igisw.openlocationtracker;

public class TimeTreeCache extends Cache<TimeTree> {

	public TimeTreeCache(DatastoreAccessor<TimeTree> timmyDatastoreAccessor) {
		super(timmyDatastoreAccessor, prefs.maxCache);
	}

	@Override
	public TimeTree allocateRow() {
		return GpsTrailerCrypt.allocateTimeTree();
	}
	
	public static Preferences prefs = new Preferences();

	public static class Preferences
	{
		/**
		 * Max number of time tree nodes to cache
		 */
		public int maxCache = 4096;
	}




}
