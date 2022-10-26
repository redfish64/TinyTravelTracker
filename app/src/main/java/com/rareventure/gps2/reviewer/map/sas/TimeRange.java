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
package com.rareventure.gps2.reviewer.map.sas;

public class TimeRange {
	public int startTimeSec, endTimeSec;
	public double dist;
	
	/**
	 * This is the starting and ending seconds from the ap before the spacetime box and the ap
	 * after the spacetime box of interest
	 *
	 * This differs from startSec and endSec,
	 * since they will not include times up to but not including the prev
	 * ap and the next ap (which are outside of the box)
	 */
	public int fullRangeStartSec, fullRangeEndSec;

}
