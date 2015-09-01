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

import com.rareventure.android.DbUtil;
import com.rareventure.android.database.Cache;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerCrypt;

public class GpsLocationCache extends Cache<GpsLocationRow> {

	public GpsLocationCache(int maxCache) {
		this(
				new DbDatastoreAccessor<GpsLocationRow>(
						GpsLocationRow.TABLE_INFO), maxCache);
	}

	public GpsLocationCache(DbDatastoreAccessor<GpsLocationRow> da, int maxCache) {
		super(da, maxCache);
	}

	@Override
	public GpsLocationRow allocateRow() {
		return GpsTrailerCrypt.allocateGpsLocationRow();
	}

	public boolean hasGpsPoints() {
		return getRowNoFail(1) != null;
	}

	public GpsLocationRow getLatestRow() {
		return getRowNoFail((int)DbUtil.runQuery(GTG.db, "select max(_id) from "
				+ GpsLocationRow.TABLE_NAME));
	}

}
