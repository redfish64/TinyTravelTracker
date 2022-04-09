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

import com.mapzen.tangram.MapController;

public interface GpsOverlay {

	void startTask(MapController mapController);

	void onPause();
	void onResume();

	void notifyScreenChanged(AreaPanelSpaceTimeBox newStBox);

	boolean onTap(float x, float y);

	/**
	 * Long press is active and moving
	 */
	boolean onLongPressMove(float startX, float startY, float endX,
							float endY);

	/**
	 * long press has ended
	 */
	boolean onLongPressEnd(float startX, float startY, float endX,
						   float endY);
}
