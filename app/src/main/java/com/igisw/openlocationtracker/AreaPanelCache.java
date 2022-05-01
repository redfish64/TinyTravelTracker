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

import java.util.ArrayList;
import java.util.Iterator;

public class AreaPanelCache extends Cache<AreaPanel> {

	private static final int MAX_AUTOZOOM_TO_TEST_AP_PANEL_SIZE = 8;



	public AreaPanelCache(DatastoreAccessor<AreaPanel> timmyDatastoreAccessor) {
//		super(new DbDatastoreAccessor<AreaPanel>(AreaPanel.TABLE_INFO), prefs.maxCache);
		super(timmyDatastoreAccessor, prefs.maxCache);
		for(int i = 0; i < 4; i++)
			apArrays[i] = new ArrayList<AreaPanel>();
	}

	@Override
	public AreaPanel allocateRow() {
		return GpsTrailerCrypt.allocateAreaPanel();
	}
	
	
	
	public static Preferences prefs = new Preferences();

	public static int TOP_ROW_ID = 0;
	
	

	public AreaPanel getTopRow() {
		if(isDbFilled())
			return super.getRow(TOP_ROW_ID);
		return null;
	}

	/**
	 * True if there is an area panel. Note that this does not mean
	 * there are any gps points for the area panel
	 * @param db 
	 * @return
	 */
	public boolean isDbFilled() {
		if(!isEmpty())
			return true;
		
		return super.getNextRowId() != 0;
	}
	
	/**
	 * True if there is an ap, and it contains actual gps points
	 */
	public boolean hasGpsPoints()
	{
		AreaPanel ap = getTopRow();
		return ap != null && ap.getTimeTree() != null;
	}


	@Override
	public AreaPanel getRow(int id) {
		if(GTG.DEBUG_SHOW_AREA_PANELS)
			Log.d("GTG", "Cache: "+super.getRow(id));
		
		return super.getRow(id);
	}

	@Override
	public AreaPanel getRowNoFail(int id) {
		if(GTG.DEBUG_SHOW_AREA_PANELS)
			Log.d("GTG", "Cache: "+super.getRowNoFail(id));

		AreaPanel ap = super.getRowNoFail(id);
		
		return ap;
	}

	public Iterator<AreaPanel> getCacheRowsForArea(AreaPanelSpaceTimeBox stBox,
												   float lonmToPixels, int maxPointsToDisplay) {
		final int maxDepth = AreaPanel.getDepthUnder(AreaPanel.
				convertLonmToX((int)(lonmToPixels  * prefs.maxPointPixelWidth)+Util.MIN_LONM));
		
		ArrayList<AreaPanel> items = new ArrayList<AreaPanel>();
		
		//if we are wrapping off the edge of the world
		if(stBox.maxX < stBox.minX)
		{
			getCacheRowsForArea(items, maxDepth, stBox.minX, stBox.minY, AreaPanel.MAX_AP_UNITS, stBox.maxY,
					stBox.minZ, stBox.maxZ);
			getCacheRowsForArea(items, maxDepth, 0, stBox.minY, stBox.maxX, stBox.maxY, stBox.minZ, stBox.maxZ);
		}
		else
		{
			getCacheRowsForArea(items, maxDepth, stBox.minX, stBox.minY, stBox.maxX, stBox.maxY, 
					stBox.minZ, stBox.maxZ);
		}
		
//		Log.i("GPS", "num area panels: "+items.size());
//		
//		int [] dcount = new int [AreaPanel.DEPTH_TO_WIDTH.length];
//		
//		for(AreaPanel ap : items)
//		{
//			dcount[ap.getDepth()]++;
//		}
//		
//		for(int i = 0; i < dcount.length; i++)
//		{
//			Log.i("GPS", "area panels for depth "+i+": "+dcount[i]);
//		}
//		
//		
		
		return items.iterator();
	}
	
	/**
	 * 
	 * @param path
	 * @param maxDepth
	 * @param minX
	 * @param minY
	 * @param maxX
	 * @param maxY
	 * @param endTimeSecs 
	 * @param startTimeSecs 
	 * @return true if the search range overlaps any points at all
	 */
	private boolean goDownToDepth(ArrayList<AreaPanel> path, int maxDepth, int minX, int minY, int maxX, int maxY, 
			int startTimeSecs, int endTimeSecs)
	{
		AreaPanel currPanel = path.get(path.size()-1);
		
		while(true)
		{
			if(currPanel.getDepth() <= maxDepth)
				return true;
			
			currPanel = currPanel.getFirstOverlappingSubPanel(0,minX,minY,maxX,maxY, startTimeSecs, endTimeSecs);
			
			if(currPanel == null)
				return false;
			
			path.add(currPanel);
		}
	}
	
	private void getCacheRowsForArea(ArrayList<AreaPanel> out, int maxDepth,
			int minX, int minY, int maxX, int maxY, int startTimeSecs, int endTimeSecs) 
	{
		ArrayList<AreaPanel> path = new ArrayList<AreaPanel>(AreaPanel.DEPTH_TO_WIDTH.length - maxDepth);
		
		path.add(getTopRow());
		
		while(true)
		{
			goDownToDepth(path, maxDepth, minX, minY, maxX, maxY, startTimeSecs, endTimeSecs);
			out.add(path.get(path.size()-1));
			goUpToNextOverlappingSubPath(path, minX,minY,maxX,maxY, startTimeSecs, endTimeSecs);
			if(path.size() == 0)
				return;
		}
		
	}

	private void goUpToNextOverlappingSubPath(ArrayList<AreaPanel> path, int minX, int minY, int maxX,
			int maxY, int startTimeSecs, int endTimeSecs) {
		
		while(true)
		{
			AreaPanel lastSubAP = path.remove(path.size()-1);
			
			if(path.size() == 0)
				return;
			
			AreaPanel ap = path.get(path.size()-1);
			AreaPanel nextSubAP = ap.getFirstOverlappingSubPanel(ap.getIndexOfSubAreaPanel(lastSubAP)+1, minX, minY, maxX, maxY,
						startTimeSecs, endTimeSecs);
			
			if(nextSubAP != null)
			{
				path.add(nextSubAP);
				return;
			}
		}
			
	}
	
	//apArrays are in the order left top right bottom
	private ArrayList<AreaPanel> [] apArrays = new ArrayList[4];
	

	//WARNING: this only works when SUB_AP_COUNT is 4
	public AreaPanelSpaceTimeBox getAutoZoomArea(int startTimeSec, int endTimeSec, ArrayList<Path> paths) {
		
		AreaPanel topAp = GTG.apCache.getTopRow();
		
		if(topAp == null)
			return null;
		
		//we deal with the four sides of the current box and shrink it until
		//we are too close for it to matter
		for(int i = 0; i < 4; i++)
		{
			apArrays[i].add(topAp);
		}
		
		
		AreaPanelSpaceTimeBox res = new AreaPanelSpaceTimeBox(0, 0, topAp.getMaxX(),
				topAp.getMaxY(),startTimeSec, endTimeSec);
		res.pathList = paths;
		
		for(;;)
		{
			//if no ap's are left, the cache area is undefined
			if(apArrays[0].size() == 0)
				return null;
				
			AreaPanel testApPanel = apArrays[0].get(0);
			
			int currDepth = testApPanel.getDepth(); 
			if(currDepth == 0 || (res.getWidth()+ res.getHeight()) / (testApPanel.getMaxX() - testApPanel.getX())
			    > MAX_AUTOZOOM_TO_TEST_AP_PANEL_SIZE)
			{
				break;
			}
			
			for(int i = 0; i < 4; i++)
			{
				boolean foundAnyOuterSubAps = false;
				
				//check outer area for all active area panels. Note that we throw
				//sub ap rows on the end of the array but that doesn't matter because
				//we are going backwards
				for(int j = apArrays[i].size()-1; j >= 0; j--)
				{
					AreaPanel ap = apArrays[i].get(j);
					
					//first set is used to determine whether to add a new item to the 
					//array or write over the current entry (which we do the first time to get rid of the area panel at the higher level)
					boolean firstSet = false;
					
					//if left or top
					if(i == 0 || i == 1)
					{
						//top left
						firstSet = setAutoZoomApIfExistsAndUpdateRes(ap.getSubAreaPanel(0), j, apArrays[i], firstSet, 
								startTimeSec, endTimeSec, paths);
						
						//bottom left (for left) / top right (for top)
						firstSet = setAutoZoomApIfExistsAndUpdateRes(i == 0 ? ap.getSubAreaPanel(2) :
							ap.getSubAreaPanel(1), j, apArrays[i], firstSet, startTimeSec, endTimeSec, paths);
					}
					else //right or bottom
					{
						//bottom right
						firstSet = setAutoZoomApIfExistsAndUpdateRes(ap.getSubAreaPanel(3), j, apArrays[i], 
								firstSet, startTimeSec, endTimeSec, paths);
						
						//top right (for right) / bottom left (for bottom)
						firstSet = setAutoZoomApIfExistsAndUpdateRes(i == 2 ? ap.getSubAreaPanel(1) :
							ap.getSubAreaPanel(2), j, apArrays[i], firstSet, startTimeSec, endTimeSec, paths);
						
						
					}
					
					foundAnyOuterSubAps = foundAnyOuterSubAps || firstSet;
				}
				
				//if there are hits on the outers side, delete all the rows that didn't have hits there
				if(foundAnyOuterSubAps)
				{
					for(int j = apArrays[i].size()-1; j >= 0; j--)
					{
						AreaPanel ap = apArrays[i].get(j);
	
						if(ap.getDepth() == currDepth)
							apArrays[i].remove(j);
					}
				}
				else //there are no hits in the outer side, go to the inner side
				{
					//adjust the space time box inwards
					if(i == 0)
						res.minX = apArrays[i].get(0).getCenterX();
					else if(i == 2)
						res.maxX = apArrays[i].get(0).getCenterX();
					else if(i == 1)
						res.minY = apArrays[i].get(0).getCenterY();
					else //(i == 3)
						res.maxY = apArrays[i].get(0).getCenterY();
					
					//find the inner arrays
					for(int j = apArrays[i].size()-1; j >= 0; j--)
					{
						AreaPanel ap = apArrays[i].get(j);
						
						//first set is used to determine whether to add a new item to the 
						//array or write over the current entry
						boolean firstSet = false;
						
						//if left or top
						if(i == 0 || i == 1)
						{
							//bottom right
							firstSet = setAutoZoomApIfExistsAndUpdateRes(ap.getSubAreaPanel(3), j, apArrays[i], 
									firstSet, startTimeSec, endTimeSec, paths);
							
							//top right (for left) / bottom left (for top)
							firstSet = setAutoZoomApIfExistsAndUpdateRes(i == 0 ? ap.getSubAreaPanel(1) :
								ap.getSubAreaPanel(2), j, apArrays[i], firstSet, startTimeSec, endTimeSec, paths);
						}
						else //right or bottom
						{
							//top left
							firstSet = setAutoZoomApIfExistsAndUpdateRes(ap.getSubAreaPanel(0), j, apArrays[i], 
									firstSet, startTimeSec, endTimeSec, paths);
							
							//bottom left (for right) / top right (for bottom)
							firstSet = setAutoZoomApIfExistsAndUpdateRes(i == 2 ? ap.getSubAreaPanel(2) :
								ap.getSubAreaPanel(1), j, apArrays[i], 
									firstSet, startTimeSec, endTimeSec, paths);
						}
						
						if(!firstSet)
						{
							apArrays[i].remove(j);
						}
					}
				
				}//if we didn't find outer panels

			}
		}
		
		//clean up after ourselves
		for(int i = 0; i < 4; i++)
		{
			apArrays[i].clear();
		}
		
		return res;
	}
	
	//if the given sub ap contains the time period, then set it up for the next autozoom round.
	private boolean setAutoZoomApIfExistsAndUpdateRes(AreaPanel subAp, int index,
			ArrayList<AreaPanel> arrayList, boolean firstSet, int startTimeSec, int endTimeSec, 
			ArrayList<Path> paths) {
		if(subAp != null)
		{
			TimeTree topTt = subAp.getTimeTree();
			
			if(subAp != null && TimeTree.intersectsTime(subAp.getTimeTree(), startTimeSec, endTimeSec).overlaps) 
			{
				if(!pathsOverlap(subAp.getTimeTree(), paths))
					return firstSet;

//				Log.d(GTG.TAG,"Found subAp for index "+index+", subAp "+subAp);
				
				//if we write over the previous levels ap in the array 
				// (which we do the first time)
				if(!firstSet)
					arrayList.set(index, subAp);
				else 
					arrayList.add(subAp);
				
				return true;
			}
		}
		
		return firstSet;
	}

	private boolean pathsOverlap(TimeTree timeTree, ArrayList<Path> paths) {
		if(paths != null)
		{
			for(Path p : paths)
			{
				if(TimeTree.intersectsTime(timeTree, p.startTimeSec, p.endTimeSec).overlaps)
				{
					return true;
				}
			}
			
			return false;
		}
		else
			return true;
	}

	public static class Preferences
	{
		/**
		 * Max number of area panels to cache
		 */
		public int maxCache = 2048;
		
		/**
		 * Maximum number of points where if this is met or exceeded, we consider autozoom to be finished
		 */
		public int maxAutoZoomPointCount = 10;

		/**
		 * The maximum width in pixels of the points on the screen 
		 */
		public float maxPointPixelWidth = 1f;
	}


}
