/** 
    Copyright 2022 Igor Calì <igor.cali0@gmail.com>

    This file is part of Open Travel Tracker.

    Open Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Open Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Open Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.igisw.openlocationtracker;

import com.rareventure.android.DbUtil;

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
