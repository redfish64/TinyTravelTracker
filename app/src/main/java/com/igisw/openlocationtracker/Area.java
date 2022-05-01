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

import android.util.Log;

import com.igisw.openlocationtracker.CacheException;

import java.util.ArrayList;
import java.util.TreeSet;

public class Area {
	private static final int MIN_AP_DIVISIONS_FOR_AREA = 32;

	@Override
	public String toString() {
		return "Area [x1=" + x1 + ", y1=" + y1 + ", x2=" + x2 + ", y2=" + y2
				+ ", minDepth=" + minDepth + ", apiList=" + apiList + "]";
	}

	public void addResetApisToTree(TreeSet<AreaPanelInfo> sortedApiTree,
			int startTime, int endTime) {
		for (AreaPanelInfo api : apiList) {
			api.resetToStart(startTime, endTime);
			if (!api.isExhausted())
				sortedApiTree.add(api);
		}
	}

	public int x1, y1, x2, y2;

	public int minDepth;

	public ArrayList<AreaPanelInfo> apiList = new ArrayList<AreaPanelInfo>();

	private int index;

	/**
	 * @param minDepth the depth to align x1, x2, y1, and y2
	 */
	public Area(int x1, int y1, int x2, int y2, int minDepth) {
		super();
		// adjust the start and end to be on a good areapanel boundary
		this.minDepth = minDepth;

		this.x1 = AreaPanel.alignToDepth(x1, minDepth);
		this.y1 = AreaPanel.alignToDepth(y1, minDepth);

		// make x2 and y2 at least one ap unit away from x1
		this.x2 = Math.max(AreaPanel.alignToDepth(x2, minDepth), x1
				+ AreaPanel.DEPTH_TO_WIDTH[minDepth]);
		this.y2 = Math.max(AreaPanel.alignToDepth(y2, minDepth), y1
				+ AreaPanel.DEPTH_TO_WIDTH[minDepth]);
	}
	
	public void setIndex(int index)
	{
		this.index = index;
	}

	public boolean encompasses(AreaPanel ap) {
		return x1 <= ap.getX() && y1 <= ap.getY() && x2 >= ap.getMaxX()
				&& y2 >= ap.getMaxY();
	}

	public void calcAreaPanelInfos() {
		if(!apiList.isEmpty())
			return;
		
		// current path from root to area panel
		// note, we use integers so that if area panels get cached out and
		// replaced
		// it won't cause us a problem. In general, since we're read only, it
		// shouldn't
		// matter, but if the cache creator updates an area panel and the old
		// version is
		// floating around somewhere, it could cause a very hard to fix bug
		ArrayList<Integer> parentPath = new ArrayList<Integer>();

		int lastSiblingApId = -1;

		if (GTG.apCache.isEmpty())
			return;

		parentPath.add(AreaPanelCache.TOP_ROW_ID);

		// note that we get all of the area panels regardless of the start time
		// and end time
		// because there won't be many in any case (since we use bigger area
		// panels when
		// fully encompassed), and we won't need to a way to merge area panels
		// together
		// which were present for the previous and the current time when the
		// time range changes.
		while (!parentPath.isEmpty()) {
			AreaPanel ap = GTG.apCache.getRow(parentPath.get(parentPath
					.size() - 1));

			/* ttt_installer:remove_line */Log.d(GTG.TAG, "Checking ap "+ap);

			if (encompasses(ap)) {
				apiList.add(new AreaPanelInfo(index, ap.id));
				lastSiblingApId = parentPath.remove(parentPath.size() - 1);
			} else if (ap.getDepth() < minDepth) {
				throw new CacheException(
						"since we are rounding the area, all min depth ap's should be encompassed, got "+ap);
			} else { // go to children
				int nextChildIndex = lastSiblingApId == -1 ? 0 : ap
						.getIndexOfSubAreaPanelFk(lastSiblingApId) + 1;

				while (nextChildIndex < AreaPanel.NUM_SUB_PANELS
						&& (ap.getSubAreaPanelFk(nextChildIndex) == Integer.MIN_VALUE || !ap
								.isRectOverlapsSubArea(x1, y1, x2, y2,
										nextChildIndex)))
					nextChildIndex++;

				// if there is another child to check
				if (nextChildIndex < AreaPanel.NUM_SUB_PANELS) {
					/* ttt_installer:remove_line */Log.d(GTG.TAG, "Found child");
					parentPath.add(ap.getSubAreaPanelFk(nextChildIndex));
//					if(!ap.getSubAreaPanel(nextChildIndex).
//							overlapsArea(this.x1,y1,x2,y2))
//					{
//						ap
//						.isRectOverlapsSubArea(x1, y1, x2, y2,
//								nextChildIndex);
//						throw new IllegalArgumentException("remove me");
//					}
					lastSiblingApId = -1;
				} else {
					/* ttt_installer:remove_line */Log.d(GTG.TAG, "No children left to check");
					lastSiblingApId = parentPath
							.remove(parentPath.size() - 1);
				}
			}

		}
	}

	/**
	 * This represents a particular area panel within an area and a particular
	 * time tree of that area panel. It is used to find the exact times that a
	 * path enters and leaves a particular area.
	 */
	public static class AreaPanelInfo implements Comparable<AreaPanelInfo> {
		public int apId, currTtId = -1, currTtStartTime = Integer.MAX_VALUE;

		/**
		 * The index of the area that contains this API
		 */
		public int areaIndex;

		public AreaPanelInfo(int areaIndex, int apId) {
			this.areaIndex = areaIndex;
			this.apId = apId;
		}

		public TimeTree tt() {
			if(currTtId == -1)
				throw new CacheException("-1 tt: "+this);
			return GTG.ttCache.getRow(currTtId);
		}

		private AreaPanelInfo(int apId, int currTtId, int currTtStartTime,
				int areaIndex) {
			super();
			this.apId = apId;
			this.currTtId = currTtId;
			this.currTtStartTime = currTtStartTime;
			this.areaIndex = areaIndex;
		}

		public AreaPanelInfo copy() {
			return new AreaPanelInfo(apId, currTtId, currTtStartTime, areaIndex);
		}

		public void resetToStart(int startTime, int endTime) {
			TimeTree tt = ap().getTimeTree();
			setTt(tt.getEncompassigTimeTreeOrMinTimeTreeAfterTime(startTime, true),
					endTime);
		}

		public boolean isExhausted() {
			return currTtId == -1;
		}

		@Override
		public int compareTo(AreaPanelInfo another) {
			if (another.currTtStartTime == Integer.MAX_VALUE)
				throw new CacheException(
						"other area panel info is dead and shouldn't be compared "
								+ another);
			if (currTtStartTime == Integer.MAX_VALUE)
				throw new CacheException(
						"area panel info is dead and shouldn't be compared "
								+ this);

			return currTtStartTime - another.currTtStartTime;
		}

		public AreaPanel ap() {
			return GTG.apCache.getRow(apId);
		}

		/**
		 * if the tt starts beyond endTime, sets to null also sets to null if tt
		 * is null
		 * 
		 * @param tt
		 * @param endTime
		 * @return
		 */
		public boolean setTt(TimeTree tt, int endTime) {
			if (tt == null || tt.getMinTimeSecs() >= endTime) {
				currTtId = -1;
				currTtStartTime = Integer.MAX_VALUE;
				return false;
			}

			currTtId = tt.id;
			currTtStartTime = tt.getMinTimeSecs();

			return true;
		}

		@Override
		public String toString() {
			return "AreaPanelInfo [apId=" + apId + ", currTtId=" + currTtId
					+ ", currTtStartTime=" + currTtStartTime + ", areaIndex="
					+ areaIndex + "]";
		}

		
	}

	public int getCenterX() {
		return (x1 + x2) >> 1;
	}

	public int getCenterY() {
		return (y1 + y2) >> 1;
	}

} // end of Area class

