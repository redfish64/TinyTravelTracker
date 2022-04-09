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

import com.igisw.openlocationtracker.Area.AreaPanelInfo;
import com.rareventure.gps2.reviewer.map.sas.TimeRange;

public class Path extends TimeRange {

	private AreaPanelInfo[] apiPath;

	public Path(AreaPanelInfo[] apiPath, TimeTree startTree, TimeTree endTree) {
		this.apiPath = new AreaPanelInfo[apiPath.length];

		for (int i = 0; i < apiPath.length; i++) {
			this.apiPath[i] = apiPath[i].copy();
		}

		this.fullRangeStartSec = startTree.getMinTimeSecs();
		this.fullRangeEndSec = endTree.getMaxTimeSecs();
		this.startTimeSec = startTree.calcTimeRangeCutStart();
		this.endTimeSec = endTree.calcTimeRangeCutEnd();
	}

}
