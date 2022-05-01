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
package com.rareventure.gps2.reviewer.map;

/**
 * converts from lon lat to mercator system of earth coordinates. This is used
 * by openstreetmaps and maybe other systems
 */
public class Mercator {
	final private static double R_MAJOR = 6378137.0;
	
	/**
	 * Maximum value in merc in the X direction (180 degrees lon)
	 */
    final public static double MAX_X = 20037508.34;

    /**
	 * Maximum value in merc in the Y direction (around 85 degrees lat)
	 */
    final public static double MAX_Y = 20037508.34;

	public static double lon2x(double lon) {
		return R_MAJOR * Math.toRadians(lon);
	}

	public static double x2lon(double x) {
		return Math.toDegrees(x) / R_MAJOR;
	}

	//180/PI() * (2 * ATAN(EXP(D2/20037508.34*180*PI()/180)) - PI()/2)
	public static double y2lat(double aY) {
		return Math.toDegrees(2 * Math.atan(Math.exp(Math.toRadians(aY/ MAX_Y)*180))
				- Math.PI / 2);
	}

	//180/PI()*LN(TAN((90+C4)*PI()/360)) * 20037508.34 / 180
	public static double lat2y(double aLat) {
		return Math.toDegrees(Math.log(Math.tan(Math.PI / 4
				+ Math.toRadians(aLat)/2))) * MAX_Y / 180; 
		}

}
