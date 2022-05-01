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

import com.rareventure.gps2.database.LeastSquaresData;

import java.util.ArrayList;


public class LonmLeastSquaresData extends LeastSquaresData
{
	private ArrayList<LlsdData> data = new ArrayList<LonmLeastSquaresData.LlsdData>();
	private boolean isInfinite = false;

	/**
	 * @param time0
	 *            a time around the other points we are adding. Needed so we don't overflow (because we are taking time squared and time * dist
	 */
	public LonmLeastSquaresData(long time0) {
		super(time0, 0);
	}

	public void addInfiniteRange() {
		isInfinite = true;
	}
	
	public void addPoint(long time, int dist, int weight) {
		data.add(new LlsdData(time,dist,weight));
	}

	public void addRange(long time, int dist1, int dist2, int weight) {
		data.add(new LlsdData(time,dist1,dist2,weight));
	}

	@Override
	public float getSlope() {
		if(isInfinite)
			return 0;
		
		LonmDataSet lonmDataSet = new LonmDataSet();
		
		for(int i = 0; i < data.size(); i++)
		{
			lonmDataSet.addRange(data.get(i).dist1, data.get(i).dist2);
		}
		
		super.setDist0(lonmDataSet.getStartLonm());
		
		//so we now know where the data starts. Now we find the slope given the continous lonm
		for(int i = 0; i < data.size(); i++)
		{
			LlsdData llsd = data.get(i);
			
			super.addPoint(llsd.time, Util.makeContinuousFromStartLonm(lonmDataSet.getStartLonm(), llsd.dist1), llsd.weight);
			if(llsd.dist2 != llsd.dist1)
				super.addPoint(llsd.time, Util.makeContinuousFromStartLonm(lonmDataSet.getStartLonm(), llsd.dist2), llsd.weight);
		}
		
		return super.getSlope();
	}

	private static class LlsdData implements Comparable<LlsdData>
	{
		private long time;
		private int dist1,dist2;
		private int weight;

		public LlsdData(long time, int dist, int weight)
		{
			this.time = time; 
			this.dist1 = this.dist2 = dist;
			this.weight = weight;
		}

		public LlsdData(long time, int dist1, int dist2, int weight)
		{
			this.time = time; 
			this.dist1 = dist1;
			this.dist2 = dist2;
			this.weight = weight;
		}

		@Override
		public int compareTo(LlsdData another) {
			return dist1 - another.dist1;
		}
	}

}