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

import rtree.AABB;

public class ViewMLT {
	MediaLocTime firstMlt;

	int totalNodes;
	long totalX, totalY;
	protected int width;

	int maxZ = Integer.MIN_VALUE;

	int minZ = Integer.MAX_VALUE;

	public ViewMLT(int currViewMltWidth, MediaLocTime mlt) {
		width = currViewMltWidth;
		this.firstMlt = mlt;
		minZ = maxZ = mlt.getTimeSecs();
		addMlt(mlt);
	}

	public int getCenterX() {
		return (int) (totalX / totalNodes);
	}

	public int getCenterY() {
		return (int) (totalY / totalNodes);
	}

	/**
	 * 
	 * @param mlt
	 * @param minZ
	 *            the current minZ of the apBox.
	 * @param maxZ
	 * @return true if we were able to successfully remove the mlt, false
	 *         otherwise
	 */
	public boolean removeMlt(MediaLocTime mlt) {
		if (mlt == firstMlt || mlt.getTimeSecs() == minZ ||
				mlt.getTimeSecs() == maxZ) {
			// we can't remove it because we are displaying its picture and we
			// don't have any backup mlt to choose another picture
			//or its at the end points of time, so we won't have an accurate
			//minZ/maxZ
			return false;
		}

		totalX -= mlt.getX();
		totalY -= mlt.getY();
		totalNodes--;

		mlt.viewMlt = null;

		return true;
	}

	public void addMlt(MediaLocTime mlt) {
		totalX += mlt.getX();
		totalY += mlt.getY();
		totalNodes++;

		mlt.viewMlt = this;
		
		if(mlt.getTimeSecs() > this.maxZ)
			this.maxZ = mlt.getTimeSecs();
		if(mlt.getTimeSecs() < this.minZ)
			this.minZ = mlt.getTimeSecs();
		
	}

	/**
	 * this is a generous box. We don't know the actual extent of the view mlt
	 * because as we add mlts to the viewmlt the viewmlt can shift its center and 
	 * it could possible wander away so far from where it once was that some of the 
	 * mlt's associated to it no longer are within its range.
	 */
	public AABB getGenerouslyApproximatedArea() {
		AABB aaBB = new AABB();
		aaBB.minX = getCenterX() - width * 2;
		aaBB.maxX = getCenterX() + width * 2;
		aaBB.minY = getCenterY() - width * 2;
		aaBB.maxY = getCenterY() + width * 2;
		aaBB.minZ = minZ;
		aaBB.maxZ = maxZ;
		
		return aaBB;
	}
}
