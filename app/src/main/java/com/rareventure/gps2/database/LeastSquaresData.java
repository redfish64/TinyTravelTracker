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

public class LeastSquaresData {
	private long time0;
	private int dist0;
	private double sumX;
	private double sumXSqr;
	private int sumY;
	private double sumXtimesY;
	private int totalWeight;

	/**
	 * @param time0
	 *            a time around the other points we are adding. Needed so we don't overflow (because we are taking time squared and time * dist
	 */
	public LeastSquaresData(long time0, int dist0) {
		this.time0 = time0;
		this.dist0 = dist0;
	}
	
	protected void setDist0(int dist0)
	{
		this.dist0 = dist0;
	}

	public void addPoint(long time, int dist, int weight) {
		time -= time0;
		dist -= dist0;

		sumX += (double) time * weight;
		sumXSqr += ((double) time) * time * weight;
		sumY += dist * weight;
		sumXtimesY += ((double) dist) * time * weight;

		totalWeight += weight;
	}

	public float getSlope() {
		double dm = totalWeight * sumXSqr - sumX * sumX;
		
		//this can happen if there are two points that are in the exact same spot
		if(dm == 0)
			return 0; //just use a zero slope (because any would work)
		
		return (float) ((totalWeight * sumXtimesY - sumY * sumX) / dm);
	}
}