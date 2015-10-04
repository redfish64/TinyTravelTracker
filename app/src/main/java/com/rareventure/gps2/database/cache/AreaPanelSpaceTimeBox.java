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
package com.rareventure.gps2.database.cache;

import java.util.ArrayList;

import com.rareventure.gps2.reviewer.map.sas.Path;

import rtree.AABB;
import rtree.BoundedObject;
import android.graphics.Point;


/**
 * A space time box using areapanel coordinates and time units
 */
public class AreaPanelSpaceTimeBox extends AABB implements BoundedObject {




	public ArrayList<Path> pathList;


	public AreaPanelSpaceTimeBox(int minX, int minY, int maxX, int maxY,
			int startTimeSecs, int endTimeSecs) {
		super();
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.minZ = startTimeSecs;
		this.maxZ = endTimeSecs;
	}


	public AreaPanelSpaceTimeBox() {
	}


	public AreaPanelSpaceTimeBox(AreaPanelSpaceTimeBox o) {
		this.minX = o.minX;
		this.minY = o.minY;
		this.maxX = o.maxX;
		this.maxY = o.maxY;
		this.minZ = o.minZ;
		this.maxZ = o.maxZ;
		this.pathList = o.pathList;
	}


	public int getWidth() {
		//if we are wrapping lon 0
		if(minX > maxX)
			return minX - maxX;
		return this.maxX - this.minX;
	}


	public int getHeight() {
		return maxY - minY;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AreaPanelSpaceTimeBox other = (AreaPanelSpaceTimeBox) obj;
		if (maxZ != other.maxZ)
			return false;
		if (maxX != other.maxX)
			return false;
		if (maxY != other.maxY)
			return false;
		if (minX != other.minX)
			return false;
		if (minY != other.minY)
			return false;
		if (minZ != other.minZ)
			return false;
		if (pathList != other.pathList)
			return false;
		return true;
	}


	public void apUnitsToPixels(Point p, int x, int y,
			int width, int height) {
		p.x = (int) (((long)x - minX) * width / getWidth()); 
		p.y = (int) (((long)y - minY) * height / getHeight()); 
	}
	
	public void pixelsToApUnits(Point p, int x, int y,
			int width, int height) {
		p.x = (int) (((long)x) * getWidth() / width + minX); 
		p.y = (int) (((long)y) * getHeight() / height + minY); 
	}
	


	public void addBorder(int borderWidth) {
		this.minX -= borderWidth;
		this.maxX += borderWidth;
		
		this.minY -= borderWidth;
		this.maxY += borderWidth;
	}


	public int getTotalTimeSec() {
		return maxZ - minZ;
	}


	public boolean isPathsChanged(AreaPanelSpaceTimeBox o) {
		return this.pathList != o.pathList;
	}

	public boolean contains(int px, int py) {
		return px >= minX && px <= maxX &&
				py >= minY && py <= maxY;
	}


	
}
