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

import com.rareventure.gps2.reviewer.map.sas.TimeRange;

public class AreaTimeRange extends TimeRange implements Comparable<AreaTimeRange> {

	public AreaTimeRange(int fullRangeStartSec, int fullRangeEndSec, int cutStartTime, int cutEndTime ) {
		super();
		this.fullRangeStartSec = fullRangeStartSec;
		this.fullRangeEndSec = fullRangeEndSec;
		
		this.startTimeSec = cutStartTime;
		this.endTimeSec = cutEndTime;
	}

	public AreaTimeRange() {
	}

	
	@Override
	public String toString() {
		return "AreaTimeRange [fullRangeStartSec=" + fullRangeStartSec
				+ ", fullRangeEndSec=" + fullRangeEndSec + ", dist=" + dist
				+ ", startTimeSec=" + startTimeSec + ", endTimeSec="
				+ endTimeSec + "]";
	}

	@Override
	public int compareTo(AreaTimeRange another) {
		return this.startTimeSec - another.startTimeSec;
	}
	
	// note we might add distance and speed here as well

}
