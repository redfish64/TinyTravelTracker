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
package com.rareventure.android;

/**
 * Constants for writing data thread in TapMusicService. Kept here so that TapMusicHelper can use
 * these without needing to import TapMusicService (and then the android libraries)
 */
public enum WriteConstants {
	MODE_WRITE_ACCEL_DATA,
	MODE_WRITE_AUDIO_DATA,
	MODE_WRITE_AUDIO_DATA_OVERFLOW,
	MODE_WRITE_ACCEL_DATA_OVERFLOW,
	MODE_EXIT,
	MODE_WRITE_GPS_DATA,
	MODE_WRITE_COMPASS_DATA,
	MODE_WRITE_GPS_DATA2,
	MODE_WRITE_SENSOR_DATA,
	MODE_WRITE_STRATEGY_STATUS, 
	EXCEPTION,

}
