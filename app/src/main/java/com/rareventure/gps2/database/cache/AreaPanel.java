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
import java.util.Arrays;

import android.util.Log;

import com.rareventure.android.Util;
import com.rareventure.android.database.Cache;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.gps2.database.cache.TimeTree.IntersectsTimeStatus;
import com.rareventure.gps2.reviewer.map.Mercator;

/**
 * An areapanel is a square of X Y coordinates shared in the same coordinates as
 * mercator and open street maps. It differs in the X and Y coordinates which are
 * always the same regardless of the depth (osm resets the coordinates per each depth
 * level).
 * <p>Each area panel contains a set of sub area panels which each contain a set of sub-sub
 * area panels and so on to the deepest level defined by MAX_DEPTH.
 * <p>
 * X goes left to right and Y goes top to bottom
 * <p>
 *     When displaying points to the screen, we find a depth of area panels appropriate
 *     for the zoom level. In this way, if the user is zoomed out so that a city is just
 *     a single point, we can use a large area panel that encompasses the points
 *     in the city. OTOH, if we are zoomed in very closely, we would use a smaller
 *     area panels to represent the points.
 * </p>
 */
public class AreaPanel extends EncryptedRow {
	//x and y are absolute coordinates of the earth
	public static final Column X = new Column("X", Integer.class);
	public static final Column Y = new Column("Y", Integer.class);

	public static final Column DEPTH = new Column("DEPTH", Integer.class);

	//number of child subpanels contained in one panel.
	//TODO 3: WARNING: this value must be two for support of line views and
	//                 the current implementation of autozoom
	public static final int NUM_SUB_PANELS_PER_SIDE = 2;
	public static final int NUM_SUB_PANELS = NUM_SUB_PANELS_PER_SIDE*NUM_SUB_PANELS_PER_SIDE;

	/**
	 * This is the size of the area panel in MAX_AP_UNITS at each depth level (lower value
	 * means smaller panels), up to the root level with one tile.
	 */
	public static final int [] DEPTH_TO_WIDTH;

	public static final double LATLON_TO_LATLONM = 1000000.;

	/**
	 * The maximum layers of area panels
	 */
	public static int MAX_DEPTH = 24;

	static {
		//we use 26 because its the max depth of opernstreetmaps
		//trying depth of 24 
		
		//here is 26 for 2 1/2 years
//		-rw-rw-r-- root     sdcard_rw 14013804 2012-11-20 15:07 AreaPanel.tt
//		-rw-rw-r-- root     sdcard_rw    54316 2012-11-20 15:07 MediaLocTime.tt
//		-rw-rw-r-- root     sdcard_rw 86251876 2012-11-20 15:07 TimeTree.tt
//		-rw-rw-r-- root     sdcard_rw     6208 2012-11-20 17:01 cache.td
//		-rw-rw-r-- root     sdcard_rw 13123584 2012-11-20 16:55 gps.db3
//		-rw-rw-r-- root     sdcard_rw   524288 2012-11-20 16:55 gps.db3-journal
//		drwxrwxr-x root     sdcard_rw          2012-11-08 15:36 tile_cache

		
		int maxDepth = (int)Math.floor((MAX_DEPTH) * Math.log(2) / Math.log(NUM_SUB_PANELS_PER_SIDE));
		

		DEPTH_TO_WIDTH = new int[maxDepth+1];
		
		DEPTH_TO_WIDTH[0] = 1;
		
		for(int i = 1; i < DEPTH_TO_WIDTH.length; i++)
			DEPTH_TO_WIDTH[i] = DEPTH_TO_WIDTH[i-1] * NUM_SUB_PANELS_PER_SIDE;
	}

	/**
	 * Max ap units is the number of panels at the deepest level we
	 */
	public static final int MAX_AP_UNITS = DEPTH_TO_WIDTH[DEPTH_TO_WIDTH.length-1];
	
	
	public static final Column TIME_TREE_FK = new Column("TIME_TREE_FK",Integer.class);

	public static final Column SUB_AREA_PANELS = new Column("SUB_AREA_PANELS",(Integer.SIZE >> 3) * NUM_SUB_PANELS); 

	
	//PERF: do we really need weekly? it may be overkill
	
	public static final int [] TIME_JUMP_SECS =
		new int[] {
		0,
		(int) (1000 * 3600 * 25.5), //a little over a day for every day trips
		1000 * 3600 * 8 // a little over a week for weekly trips
	};

	public static final Column[] COLUMNS = new Column[] { Y, X, DEPTH, TIME_TREE_FK, SUB_AREA_PANELS};
	
	public static final Preferences prefs = new Preferences();
	
	
	
	/**
	 * size of data
	 */
	public static final int DATA_LENGTH = 
		EncryptedRow.figurePosAndSizeForColumns(COLUMNS);
	
	
	public AreaPanel() {
		super();
	}
	
	public int getDataLength()
	{
		return DATA_LENGTH;
	}
	
	public static int convertXToLonm(int x) {
		return (int) Math.round((Util.LONM_PER_WORLD*(double)x / MAX_AP_UNITS) + Util.MIN_LONM);
	}

	public static double convertXToLon(int x) {
		return (Util.LON_PER_WORLD*(double)x / MAX_AP_UNITS) + Util.MIN_LON;
	}



	public static  int convertYToLatm(int y) {
		int result = (int) Math.round(Mercator.y2lat(Mercator.MAX_Y - y * Mercator.MAX_Y*2 / MAX_AP_UNITS)*LATLON_TO_LATLONM);

//		Log.d("GTG","convert y to latm, y: "+y+" result: "+result);
		return result;
	}

	public static  double convertYToLat(int y) {
		return Mercator.y2lat(Mercator.MAX_Y - y * Mercator.MAX_Y*2 / MAX_AP_UNITS );
	}

	public static  int convertLonmToX(int lonm) {
		return (int) Math.round((MAX_AP_UNITS*(double)(lonm - Util.MIN_LONM) / Util.LONM_PER_WORLD));
	}

	public static int convertLonToX(double lon) {
		return (int) Math.round((MAX_AP_UNITS*(double)(lon * LATLON_TO_LATLONM - Util.MIN_LONM) / Util.LONM_PER_WORLD));
	}


	public static double convertLonToXDouble(double lon) {
		return MAX_AP_UNITS*(double)(lon - Util.MIN_LON) / Util.LON_PER_WORLD;
	}

	/**
	 * Returns the y position based on latitude.
	 * Note that this is based of Mercator which will have infinite number of points approaching
	 * +/-90 degrees. So if we are out of range, (beyond the configured min/max latitude),
	 * we return -1 and MAX_AP_UNITS based on which direction we are out
	 *
	 * @param latm
	 * @return
	 */
	public static  int convertLatmToY(int latm) {
		long v = Math.round(
				( Mercator.MAX_Y - Mercator.lat2y(latm/LATLON_TO_LATLONM) )
				* MAX_AP_UNITS / (Mercator.MAX_Y * 2));
//		Log.d("GTG","convert latm to y, latm: "+latm+" result: "+v);
		if(v > MAX_AP_UNITS)
			return MAX_AP_UNITS;
		if(v < 0)
			return -1;

		return (int) v;
	}

	public static  int convertLatToY(double lat) {
		long v = Math.round((Mercator.MAX_Y - Mercator.lat2y(lat)) * MAX_AP_UNITS / (Mercator.MAX_Y * 2));
//		Log.d("GTG","convert latm to y, latm: "+latm+" result: "+v);
		if(v > MAX_AP_UNITS)
			return MAX_AP_UNITS;
		if(v < 0)
			return -1;

		return (int) v;
	}

	public static double convertLatToYDouble(double lat) {
		double v = (Mercator.MAX_Y - Mercator.lat2y(lat)) * MAX_AP_UNITS / (Mercator.MAX_Y * 2);
//		Log.d("GTG","convert latm to y, latm: "+latm+" result: "+v);
		if(v > MAX_AP_UNITS)
			return MAX_AP_UNITS;
		if(v < 0)
			return -1;

		return v;
	}

	public int getX()
	{
		return getInt(X);
	}
	
	public int getY()
	{
		return getInt(Y);
	}
	
	public int getDepth()
	{
		return getInt(DEPTH);
	}
	
	public int getTimeTreeFk()
	{
		return getInt(TIME_TREE_FK);
	}
	
	public int getSubAreaPanelFk(int i) {
		return Util.byteArrayToInt(data2, SUB_AREA_PANELS.pos + i * (Integer.SIZE >> 3));
	}
	
	public ArrayList<AreaPanel> getChildrenPanels() 
	{
		ArrayList<AreaPanel> children = new ArrayList<AreaPanel>(NUM_SUB_PANELS);
		for(int i = 0; i < NUM_SUB_PANELS; i++)
		{
			if (getSubAreaPanelFk(i) != Integer.MIN_VALUE)
				children.add(getSubAreaPanel(i));
		}
		
		return children;
	}

	public void setChildAreaPanel(AreaPanel child, int i) {
		setChildAreaPanelFk(child.id, i);
	}
	
	public void setChildAreaPanelFk(int childFk, int index) {
		setInt(SUB_AREA_PANELS.pos+index*(Integer.SIZE >> 3), childFk);
	}
	
	public void setData(int x, int y, int depth) {
//		public static final Column[] COLUMNS = new Column[] { MIN_LATM, MIN_LONM, TIME_INTERVAL_TREE_HEAD_FK, SUB_AREA_PANELS, POINT_COUNT, LINE_COUNT,
//			POINTS, LINES};
		
		if(data2 == null)
		{
			data2 = new byte [DATA_LENGTH];
		}
		
		setInt(X.pos,x);
		setInt(Y.pos,y);
		
		setInt(TIME_TREE_FK.pos,Integer.MIN_VALUE);
		for(int i = 0; i < NUM_SUB_PANELS; i++)
			setChildAreaPanelFk(Integer.MIN_VALUE, i);
		
		setInt(DEPTH.pos, depth);
	}
	

	public boolean containsPoint(int x, int y) {
		//if the point is out of range or the status isn't set
		if(x < getX() || x >= getMaxX() || y < getY() || y >= getMaxY())
			return false;
		
		return true;
	}

	public String toStringFieldsOnly()
	{
		return 
		String.format("AreaPanel(id=%d,x=%d,y=%d,mx=%d,my=%d,depth=%d," +
				"timeIntervalTreeHeadFk=%d,stSec=%d,etSec=%d,sp0=%d,sp1=%d,sp2=%d,sp3=%d)",
				this.id, getX(), getY(), getMaxX(), getMaxY(), getDepth(),
				getTimeTreeFk(),
				getTimeTree() == null ? -1 : getStartTimeSec(),
						getTimeTree() == null ? -1 : getEndTimeSec(),
					getSubAreaPanelFk(0),
					getSubAreaPanelFk(1),
					getSubAreaPanelFk(2),
					getSubAreaPanelFk(3)
					);
	}
	
	public String toString()
	{
		return "#"+toStringFieldsOnly();
//		+"\n"+Util.gnuPlot2DIt(
//						minLonm, minLatm, 
//						maxLonm, minLatm,
//						maxLonm, maxLatm,
//						minLonm, minLatm, 
//						minLonm, maxLatm);
	}

	public static class Preferences
	{
	}



	/**
	 * Adds a point to the panel and saves it
	 */
	public void addPoint(int id, AreaPanel prevAp, int prevX, int prevY, int x,
			int y, int lastTimeSec, int timeSec, double dist) {
		
		//handle child
		
		if(getDepth() == 0)
			//TODO 3: do we want to link to gps points?
			//setPointFk(id);
		 {
			
		 }
		else
		{
			int depth = getDepth();
			
			int xIndex = (x-getX())/(DEPTH_TO_WIDTH[depth-1]);
			int yIndex = (y-getY())/(DEPTH_TO_WIDTH[depth-1]);
			
			int index = xIndex + yIndex * NUM_SUB_PANELS_PER_SIDE ;
			
//			Log.d(GTG.TAG,"addPoint: subApFk = "+getSubAreaPanelFk(index)+" prevAp="+prevAp);
			
			if(getSubAreaPanelFk(index) == Integer.MIN_VALUE)
			{
				AreaPanel childAreaPanel = GTG.apCache.newRow();
				childAreaPanel.setData(getX() + xIndex * DEPTH_TO_WIDTH[depth-1], 
						getY() + yIndex * DEPTH_TO_WIDTH[depth-1], depth-1);
				
				AreaPanel subPrevAp = null;
				if(prevAp != null)
				{
					subPrevAp = prevAp.getSubAreaPanel(prevX, prevY);
					if(subPrevAp == null)
						throw new IllegalStateException("What? why is there a prevAp but not a subPrevAp? "+prevAp+" prevX "+prevX+" prevY "+prevY);
				}
				
				childAreaPanel.addPoint(id, prevAp == null ? null : subPrevAp,
						prevX, prevY, 
						x,y, lastTimeSec, timeSec, dist);
				setSubAreaPanelFk(index, childAreaPanel.id);
			}
			else 
			{
				getSubAreaPanel(index).addPoint(id,prevAp.getSubAreaPanel(prevX, prevY),
						prevX, prevY, x, y, lastTimeSec, timeSec, dist);
			}
		}
		
		int prevApIdToUse = (prevAp == null ? Integer.MIN_VALUE : prevAp.id);

		if(getTimeTreeFk() == Integer.MIN_VALUE)
		{
			//the areapanel will extend in time all the way from the last time to the current time
			//we add 1 to last time sec, so that we won't be on at the last gps point
			//we add 1 to timeSec because aps must be at least one second long and this
			//guarantees this
			//note that line calculating depends 
			//on the time between the time trees to be at least one second
			setTimeTreeFk(TimeTree.createTimeTreeForGpsPoint(lastTimeSec+1, timeSec+1, 
					prevApIdToUse, dist).id);
		}
		else
		{
			TimeTree tt = getTimeTree();
			
			//if the previous point was in the area panel, we denote that the areapanel
			//contains the whole timeperiod from that point to this point
			//Example if the last point was at 10:01 and the current was at 10:05,
			//the time range contained by the area panel would include 10:01 through
			// 10:05
			if(tt.getMaxTimeSecs() >= lastTimeSec)
				setTimeTreeFk(tt.addSegmentForPoint(tt.getMaxTimeSecs(), timeSec+1, 
						prevApIdToUse, dist).id);
			else
				setTimeTreeFk(tt.addSegmentForPoint(lastTimeSec+1, timeSec+1, 
						prevApIdToUse, dist).id);
		}
		
		//hook up the prev ap's time tree to this ap, and extend its time to
		//up to but not including the current ap
		if(prevAp != null && prevAp != this)
		{
			TimeTree tt = prevAp.getTimeTree();

			//extend the ap to the current ap's time
			//note, we set distance to zero since it doesn't actually touch
			//the next point. Also note that since we are extending from tt.getMaxTimeSecs()
			// this will never result in the creation of a new tt, so we can
			// use extendTimeTree rather than addSegmentForPoint
			tt.extendTimeTree(timeSec-1, 0, false);
			
			//setup the next ap id
			tt.setNextApIdForThisAndAllChildren(this.id);
		}

//		if(getDepth() == 0)
//			Log.d(GTG.TAG,"Added ap "+this);

	}

	private AreaPanel getSubAreaPanel(int x, int y) {
		int depth = getDepth();
		int xIndex = (x-getX())/(DEPTH_TO_WIDTH[depth-1]);
		int yIndex = (y-getY())/(DEPTH_TO_WIDTH[depth-1]);
		
		int index = xIndex + yIndex * NUM_SUB_PANELS_PER_SIDE ;
		
		return getSubAreaPanel(index);
	}

	public TimeTree getTimeTree() {
		int fk = getTimeTreeFk();
		
		if(fk == Integer.MIN_VALUE)
			return null;
		return GTG.ttCache.getRow(fk);
	}

	private void setTimeTreeFk(int fk) {
		setInt(TIME_TREE_FK.pos, fk);
	}

	private void setSubAreaPanelFk(int index, int fk) {
		if(fk == -1)
			TAssert.fail();
		
		setInt(SUB_AREA_PANELS.pos + index * (Integer.SIZE >> 3), fk);
	}

	public int getCenterLonm() {
		return convertXToLonm(getCenterX());
	}

	public int getCenterLatm() {
		return convertYToLatm(getCenterY());
	}

	public int getCenterX() {
		return (getX() + getMaxX()) >> 1;
	}

	public int getCenterY() {
		return (getY() + getMaxY()) >> 1;
	}

	public int getEndTimeSec() {
		return getTimeTree().getMaxTimeSecs();
	}

	public int getStartTimeSec() {
		return getTimeTree().getMinTimeSecs();
	}

	public static int getDepthUnder(int units) {
		int index = Arrays.binarySearch(DEPTH_TO_WIDTH, units);
		
		if(index >= 0)
			return index;
		
		//return the point that's just below the number of units
		return (-index)-1;
	}

	public int getOverlappingMap(int minX, int minY, int maxX,
			int maxY) {
		int result = 0;
		
		int depth = getDepth();

		if(maxX >= getX())
		{
			if(minX < getX()+DEPTH_TO_WIDTH[depth-1])
				result |= 1;
			if(minX < getX()+DEPTH_TO_WIDTH[depth])
				result |= 2;
		}
		if(maxY >= getY())
		{
			if(minY < getY()+DEPTH_TO_WIDTH[depth-1])
				result |= 4;
			if(minY < getY()+DEPTH_TO_WIDTH[depth])
				result |= 8;
		}
		
		return result;
	}

	public AreaPanel getFirstOverlappingSubPanel(int startingIndex, int minX, int minY, int maxX,
			int maxY, int startTimeSecs, int endTimeSecs) {

		
		for(int i = startingIndex; i < NUM_SUB_PANELS; i++)
		{
			if(getSubAreaPanelFk(i) != Integer.MIN_VALUE && 
					isRectOverlapsSubArea(minX, minY, maxX, maxY, i))
			{
				AreaPanel aa = getSubAreaPanel(i);
				
				IntersectsTimeStatus status = TimeTree.intersectsTime(aa.getTimeTree(),startTimeSecs, endTimeSecs);
				
				if(status.overlaps)
				{
					
					if(status == IntersectsTimeStatus.RANGE_OVERLAPS_MIN_TIME)
					{
						TimeTree tt = aa.getTimeTree().getBottomLevelEncompassigTimeTree(startTimeSecs);
						
						if(tt.calcTimeRangeCutEnd() < startTimeSecs)
							continue;
					}
					
					if(status == IntersectsTimeStatus.RANGE_OVERLAPS_MAX_TIME)
					{
						TimeTree tt = aa.getTimeTree().getBottomLevelEncompassigTimeTree(endTimeSecs);
						
						if(tt.calcTimeRangeCutStart() > endTimeSecs)
							continue;
					}

					return aa;
				}
			}
		}
		
		//doesn't overlap at all
		return null;
				
	}
		
	public AreaPanel getSubAreaPanel(int i) {
		int fk = getSubAreaPanelFk(i);
		
		if(fk != Integer.MIN_VALUE)
		{
			AreaPanel ap = GTG.apCache.getRow(fk);
			
			return ap;
		}
		else return null;
	}

	public boolean isRectOverlapsSubArea(int minX, int minY, int maxX,
			int maxY, int i) {
		int depth = getDepth();

		return 
			(maxX > getX() + DEPTH_TO_WIDTH[depth-1] * (i%NUM_SUB_PANELS_PER_SIDE))
			&& maxY > getY() + DEPTH_TO_WIDTH[depth-1] * (i/NUM_SUB_PANELS_PER_SIDE)
			&& minX < getX() + DEPTH_TO_WIDTH[depth-1] * (i%NUM_SUB_PANELS_PER_SIDE +1)
			&& minY < getY() + DEPTH_TO_WIDTH[depth-1] * (i/NUM_SUB_PANELS_PER_SIDE +1);
	}

	public int getIndexOfSubAreaPanel(AreaPanel lastSubAP) {
		for(int i = 0; i < NUM_SUB_PANELS; i++)
		{
			if(getSubAreaPanelFk(i) == lastSubAP.id)
				return i;
		}

		throw new IllegalArgumentException(lastSubAP+" not in panel");
	}
	
	public int getIndexOfSubAreaPanelFk(int lastSubApFk) {
		for(int i = 0; i < NUM_SUB_PANELS; i++)
		{
			if(getSubAreaPanelFk(i) == lastSubApFk)
				return i;
		}

		throw new IllegalArgumentException(lastSubApFk+" not in panel");
	}
	
	public static class PointCoverageData
	{
		public int minX, maxX, minY, maxY;
		public int modAmount;
		
		public PointCoverageData(int modAmount)
		{
			minY = minX = MAX_AP_UNITS;
			maxX = maxY = 0;
			
			this.modAmount = modAmount;
			
		}

		public void adjustFor(int x, int y) {
			//modamount is a way to get around the fact if 
			//the active points overlap the 0 lonm point. 
			//It's not perfect, since if the points overlap wherever modPoint is,
			//we would split the screen at that point as well. But it will handle
			//any case where the length of the points don't extend beyond half the
			//world
			x = (int) (((long)x + modAmount) % MAX_AP_UNITS);
			y = (int) (((long)y + modAmount) % MAX_AP_UNITS);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
		
		public int shouldAdjustFor(AreaPanel ap)
		{
			int res = 0;
			res = res | (ap.getX() < minX ? 1 : 0);
			res = res | (ap.getX()+DEPTH_TO_WIDTH[ap.getDepth()] > maxX ? 2 : 0);
			res = res | (ap.getY() < minY ? 4 : 0);
			res = res | (ap.getY()+DEPTH_TO_WIDTH[ap.getDepth()] > maxY ? 8 : 0);
			
			return res;
		}
	}
	
	public void getPointCoverage(int startTimeSec, int endTimeSec,
			PointCoverageData pcd) {
		int res = pcd.shouldAdjustFor(this);
		if(res != 0)
		{
			if(getDepth() == 0)
			{
				pcd.adjustFor(getX(), getY());
			}
			else {
				//we start with the sub panels at the subpanels which are on the edges of 
				//the current panel, and the
				//current panel rests beyond the edge of the pcd in the same direction
				//then we do the others
				for(int tryOpposite = 0; tryOpposite < 2; tryOpposite++)
				{
					for(int i = 0; i < AreaPanel.NUM_SUB_PANELS; i++)
					{
						AreaPanel subPanel = getSubAreaPanel(i);
						
						if(subPanel != null && (tryOpposite == 0) == (
								((res & 1) == 1 && i % AreaPanel.NUM_SUB_PANELS_PER_SIDE == 0 )
								|| 
								((res & 2) == 2 &&
									(i % AreaPanel.NUM_SUB_PANELS_PER_SIDE == AreaPanel.NUM_SUB_PANELS_PER_SIDE-1)) 
								||
								((res & 4) == 4 && i < AreaPanel.NUM_SUB_PANELS_PER_SIDE)
								||
								((res & 8) == 8 && i >= AreaPanel.NUM_SUB_PANELS_PER_SIDE * 
											(AreaPanel.NUM_SUB_PANELS_PER_SIDE - 1))
											)
								&& TimeTree.intersectsTime(subPanel.getTimeTree(),startTimeSec, endTimeSec).
								overlaps)
							subPanel.getPointCoverage(startTimeSec, endTimeSec, pcd);
					}
				}
			}
		}
	}

	@Override
	public Cache getCache() {
		return (Cache)GTG.apCache;
	}

	public int getMaxX() {
		if(getDepth() >= DEPTH_TO_WIDTH.length || getDepth() < 0)
			throw new IllegalStateException("bad depth "+getDepth()+" dtow is "+DEPTH_TO_WIDTH.length);
		return getX() + DEPTH_TO_WIDTH[getDepth()];
	}
	
	public int getWidth() {
		return DEPTH_TO_WIDTH[getDepth()];
	}

	public int getMaxY() {
		return getY() + DEPTH_TO_WIDTH[getDepth()];
	}

	public static double convertApYToAbsPixelY2(double apY, long zoom8BitPrec) {
		return apY  * zoom8BitPrec / AreaPanel.MAX_AP_UNITS;  
	}

	public static double convertApXToAbsPixelX2(double apX, long zoom8BitPrec) {
		return apX * zoom8BitPrec / AreaPanel.MAX_AP_UNITS;  
	}

	public boolean overlapsStbXY(AreaPanelSpaceTimeBox newLocalStBox) {
		return !outsideOfXY(newLocalStBox);
		// return getX() < newLocalStBox.maxX && getY() <
		// newLocalStBox.maxY
		// && getMaxX() >= newLocalStBox.minX && getMaxY() >=
		// newLocalStBox.minY;
	}

	public boolean outsideOfXY(AreaPanelSpaceTimeBox newLocalStBox) {
		// if we are wrapping 0 degrees longitude
		if (newLocalStBox.maxX < newLocalStBox.minX) {
			return (getMaxX() < newLocalStBox.minX && getX() > newLocalStBox.maxX)
					|| getMaxY() < newLocalStBox.minY
					|| getY() > newLocalStBox.maxY;
		}

		return getMaxX() < newLocalStBox.minX
				|| getMaxY() < newLocalStBox.minY
				|| getX() > newLocalStBox.maxX
				|| getY() > newLocalStBox.maxY;
	}

	public static AreaPanel findAreaPanelForTime(int timeSec, boolean latestPrevOrEarliestNext) 
	{
		ArrayList<AreaPanel> candidates = new ArrayList<AreaPanel>();
		
		candidates.add(GTG.apCache.getTopRow());
		
		int currDepth = candidates.get(0).getDepth();
		
		//if the time is outside all the points
		if(candidates.get(0).getStartTimeSec() > timeSec && latestPrevOrEarliestNext
				|| candidates.get(0).getEndTimeSec() <= timeSec && !latestPrevOrEarliestNext)
			return null;
		
		while(true)
		{
			AreaPanel bestAp = null;
			
			if(candidates.size() > 1)
			{
				//note, there are often "high traffic" spots that contain a lot of minimum depth points
				//that are repeatedly visited (even though they're max depth)
				//because so if we don't look within timetree, we will end up with a lot
				//of candidate points
				 
				//search all the candidates for areapanels that overlap the time
				//or, if none of them do, then the areapanel with the closest time
				//without going over or without going under depending on latestPrevOrEarliestNext
				int bestTime = latestPrevOrEarliestNext ? Integer.MIN_VALUE : Integer.MAX_VALUE;

				for(AreaPanel ap : candidates)
				{
					int apStartTime = ap.getStartTimeSec();
					int apEndTime = ap.getEndTimeSec();
	
					if(latestPrevOrEarliestNext)
					{
						if(timeSec >= apEndTime 
							&& bestTime < apEndTime-1)
						{
							bestTime = apEndTime-1;
							bestAp = ap;
						}
						//else we're in the middle of it
						else if(timeSec >= apStartTime)
						{
							int apTime = ap.getTimeTree().getNearestTimePoint(timeSec, true);
							
							if(apTime > bestTime)
							{
								bestTime = apTime;
								bestAp = ap;
							}
						}
						//else it's completely after the time (and were looking for 
						// latest previous)
					}
					else //we're looking for earliest next
					{
						if(timeSec < apStartTime 
								&& bestTime > apStartTime)
						{
							bestTime = apStartTime;
							bestAp = ap;
						}
						//else we're in the middle of it
						else if(timeSec < apEndTime)
						{
							int apTime = ap.getTimeTree().getNearestTimePoint(timeSec, false);
							
							if(apTime < bestTime)
							{
								bestTime = apTime;
								bestAp = ap;
							}
							//co: two areapanels can share the same time because prev and next areapanel times 
							//overlap for each gps point 
//							else if (apTime == bestTime)
//							{
//								throw new IllegalStateException("two area panels should never share the same time");
//							}
						}
					}
				} //for each candidate
				
				if(bestAp == null)
					throw new IllegalStateException("no good candidates?");
			} //if there is more than one candidate
			else
			{
				bestAp = candidates.get(0);
			}
			
			if(currDepth == 0)
				return bestAp;
			
			candidates.clear();
			
			int bestNonOverlappingApTime = latestPrevOrEarliestNext ? Integer.MIN_VALUE : Integer.MAX_VALUE;
			AreaPanel bestNonOverlappingSubAp = null;
			
			for(int i = 0; i < NUM_SUB_PANELS; i++)
			{
				AreaPanel subAp = bestAp.getSubAreaPanel(i);
				//if the sub ap contains the same time as the parent
				//(at least one sub ap should always)
				if(subAp == null)
					continue;
				
				if(subAp.getStartTimeSec() > timeSec)
				{
					if(!latestPrevOrEarliestNext)
					{
						if(subAp.getStartTimeSec() < bestNonOverlappingApTime)
						{
							bestNonOverlappingSubAp = subAp;
							bestNonOverlappingApTime = subAp.getStartTimeSec();
						}
					}
					continue;
				}
				if(subAp.getEndTimeSec() <= timeSec)
				{
					if(latestPrevOrEarliestNext)
					{
						if(subAp.getEndTimeSec()-1 > bestNonOverlappingApTime)
						{
							bestNonOverlappingSubAp = subAp;
							bestNonOverlappingApTime = subAp.getEndTimeSec() - 1;
						}
					}
					continue;
				}
					
				candidates.add(subAp);
			}
			
			//this occurs we get far enough down where the user only spent an instant within 
			//each area panel (ie a single gps reading). For background, an area panel that
			//contains two sequential gps reading is considered to contain the time between
			//both
			//note that we always add the best non overlapping ap because even though an 
			//ap overlaps the time, doesn't mean its internal gap is in a better position
			//then an external one
			if(bestNonOverlappingSubAp != null)
				candidates.add(bestNonOverlappingSubAp);
			
			currDepth--;
			
		} // while loop		
	}

	public int getCenterTimeSec() {
		return (getStartTimeSec()>>1) + (getEndTimeSec()>>1);
	}

	public int getTimeSpanSecs() {
		return getEndTimeSec()-getStartTimeSec();
	}

	public boolean overlapsArea(int x1, int y1, int x2, int y2) {
		if(getMaxX() <= x1 || getMaxY() <= y1 || getX() >= x2 || getY() >= y2)
			return false;
		
		return true;
	}

	/**
	 * Given a value, rounds it to the nearest areapanel boundary
	 * at a depth.
	 * 
	 * @return
	 */
	public static int alignToDepth(int val, int depth) {
		return val / DEPTH_TO_WIDTH[depth] * DEPTH_TO_WIDTH[depth]; 
	}

	/**
	 * 
	 * @param depth
	 * @param timeSec
	 * @return the child ap that is at depth and encompasses timeSec
	 */
	public AreaPanel getChildApAtDepthAndTime(int depth, int timeSec) {
		AreaPanel ap = this;
		
		if(ap.getTimeTree().getBottomLevelEncompassigTimeTree(timeSec) == null)
			return null;
		
		for(;;)
		{
			if(ap.getDepth() == depth)
				return ap;
			
			int i;
			for(i = NUM_SUB_PANELS - 1; i >= 0; i--)
			{
				AreaPanel childAp = ap.getSubAreaPanel(i);
				if(childAp == null) continue;
				
				if(childAp.getTimeTree().getBottomLevelEncompassigTimeTree(timeSec) == null)
					continue;
				
				ap = childAp;
				break;
			}
			
			if(i == -1)
				throw new IllegalStateException("Parent contains time, but child does not");
		}
	}

	public boolean overlaps(AreaPanel o) {
		if(getMaxX() <= o.getX() || getMaxY() <= o.getY() || getX() >= o.getMaxX() || getY() >= o.getMaxY())
			return false;
		
		return true;
	}

}
