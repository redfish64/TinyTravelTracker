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

import android.util.Log;

import com.rareventure.android.Util;
import com.rareventure.android.database.Cache;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.CacheException;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.gps2.database.cachecreator.ViewNode;

public class TimeTree extends EncryptedRow
{
	public static int NUM_NODES = 4;

	public static final Column SUB_NODES = new Column("SUB_NODES", (Integer.SIZE>>3) * NUM_NODES);
	
	//used for tree balancing. total number of sub nodes (including descendents) including self
	public static final Column TOTAL_SUB_NODE_COUNT = new Column("TOTAL_SUB_NODE_COUNT", Integer.class);
	
	//maxtime of the points within the node. Note that this actually is the earliest gps
	//point after the user left the node.
	public static final Column MAX_TIME_SECS = new Column("MAX_TIME_SECS", Integer.class);

	//min time of the points within the node. Note that this actually is the latest gps
	//point before the user entered the  node + 1 second. We use this range because it helps us
	//calculate the speed through the node (time over distance) easier for bottom
	//level aps. I believe it also has something to do with drawing lines, but this
	//may no longer be true due to prev_ap_fk and next_ap_fk
	public static final Column MIN_TIME_SECS = new Column("MIN_TIME_SECS", Integer.class);

	//max gap in times for the sub nodes
	public static final Column TIME_JUMP_SECS = new Column("TIME_JUMP_SECS", Integer.class);
	
	/**
	 * These are the previous and next ap at the same depth as the current ap
	 */
	public static final Column PREV_AP_FK = new Column("PREV_AP_FK", Integer.class);
	public static final Column NEXT_AP_FK = new Column("NEXT_AP_FK", Integer.class);

	//this is a double for precision, since we have to add up all the distances of the sub
	//time trees
	//PERF: we only need this for the bottom level tt's (as well as max elev and min elev)
	//so we could use some sort of union structure... this is
	// oppositely true for sub_nodes. total_sub_node_count could be used to then identify
	// a bottom level time tree
	public static final Column PREV_AND_CURR_DIST_M = new Column("PREV_AND_CURR_DIST_M", Double.class);
//	public static final Column MAX_ELEV = new Column("MAX_ELEV", Integer.class);
//	public static final Column MIN_ELEV = new Column("MIN_ELEV", Integer.class);

	// number of gps points contained by the time tree
	public static final Column NUM_POINTS = new Column("NUM_POINTS", Integer.class);
	
	public static final Column[] COLUMNS = new Column[] { TOTAL_SUB_NODE_COUNT, SUB_NODES, MIN_TIME_SECS, 
			MAX_TIME_SECS, 
			TIME_JUMP_SECS, PREV_AP_FK, NEXT_AP_FK, PREV_AND_CURR_DIST_M, NUM_POINTS
//			, MAX_ELEV, MIN_ELEV
			};
	
	public static final int DATA_LENGTH = 
		EncryptedRow.figurePosAndSizeForColumns(COLUMNS)
		;

	private TimeTree tt;
	
	
	public TimeTree()
	{
	}

	/**
	 * Initialize a timetree for insertion. Note we don't do this in the constructor because when
	 * we decrypt, the data2 length will be longer than DATA_LENGTH (due to the way decryption works)
	 * so we don't want to create data2 twice when we read from the db
	 */
	public void initNewTimeTree()
	{
		data2 = new byte [DATA_LENGTH];
		
		
		for(int i = 0; i < NUM_NODES; i++)
		{
			setSubNodeFk(i, Integer.MIN_VALUE);
		}
	}
	
	public int getDataLength()
	{
		return DATA_LENGTH;
	}
	
	public ArrayList<TimeTree> getAllBottomLevelTimeTrees()
	{
		ArrayList<TimeTree> al = new ArrayList<TimeTree>();
		
		getAllBottomLevelTimeTrees(al);
		
		return al;
	}
	
	public void getAllBottomLevelTimeTrees(ArrayList<TimeTree> al)
	{
		int i;
		for(i = 0; i < NUM_NODES; i++)
		{
			TimeTree child = getSubNode(i);
			if(child == null)
				break;
			
			child.getAllBottomLevelTimeTrees(al);
		}
		
		if(i == 0)
			al.add(this);
	}
	
	
	public int getNearestTimePoint(int timeSecs, boolean latestPreviousOrEarliestNext)
	{
		if(latestPreviousOrEarliestNext)
		{
			if(timeSecs >= getMaxTimeSecs())
				return getMaxTimeSecs() - 1;
			else if (timeSecs < getMinTimeSecs())
				throw new CacheException("asked for latest previous but we are completely after time, "
					+timeSecs+", this is "+this);
		}
		else
		{
			if(timeSecs < getMinTimeSecs())
				return getMinTimeSecs();
			else if (timeSecs >= getMaxTimeSecs())
				throw new CacheException("asked for earliest next but we are completely before time, "
					+timeSecs+", this is "+this);
		}
		
		//if there are no sub nodes at all
		if(getSubNodeFk(0) == Integer.MIN_VALUE)
		{
			//then we contain the exact value
			return timeSecs;
		}
		
		int lastEndTime = 0;
		
		for(int i = 0; i < NUM_NODES; i++)
		{
			TimeTree subTT = getSubNode(i);
			
			//if its before the subtt
			if(timeSecs < subTT.getMinTimeSecs())
			{
				if(latestPreviousOrEarliestNext)
				{
					if(i == 0)
						throw new CacheException("first child isn't aligned with parent," +
								" this is "+this+", subTT is "+subTT);
					return lastEndTime;
				}
				return subTT.getMinTimeSecs();
			}
			
			//if the point is wihtin the sub tt
			if(timeSecs < subTT.getMaxTimeSecs())
				return subTT.getNearestTimePoint(timeSecs, latestPreviousOrEarliestNext);
			
			lastEndTime = subTT.getMaxTimeSecs();
		}

		throw new CacheException("last child isn't aligned with parent," +
				" this is "+this+", subTT is "+getSubNode(NUM_NODES-1));
		
	}
	

	public int getMinTimeSecs() {
		return getInt(MIN_TIME_SECS);
	}

	public int getMaxTimeSecs() {
		return getInt(MAX_TIME_SECS);
	}



	public Cache getCache() {
		return (Cache) GTG.ttCache;
	}
	
	/**
	 * Adds a segment to the time tree and updates NUM_POINTS for the addition of one gps point
	 */ 
	public TimeTree addSegmentForPoint(int startTimeSec, int endTimeSec, int prevApId, double dist)
	{
//		if(startTimeSec < getMaxTimeSecs())
//			TAssert.fail("received a segment that is earlier than the latest one! Not acceptable: "+this+" "+startTimeSec);
//		
		//first check if we can just extend the time tree for the segment
		if(getMaxTimeSecs() == startTimeSec)
		{
			extendTimeTree(endTimeSec, dist, true);
			return this;
		}
		//otherwise we will have to create a new timetree
		else
		{
			return addTimeTree(createTimeTreeForGpsPoint(startTimeSec, endTimeSec, prevApId, dist));
		}
	}
	
	
	public static TimeTree createTimeTreeForGpsPoint(int startTimeSec, int endTimeSec,
			int prevApId, double dist) {
		TimeTree tt = GTG.ttCache.newRow();
		
		tt.initNewTimeTree();
		tt.setMinTimeSecs(startTimeSec);
		tt.setMaxTimeSecs(endTimeSec);
		
		tt.setTimeJumpSecs(0);
		tt.setTotalSubNodeCount(1);
		tt.setInt(PREV_AP_FK.pos, prevApId);
		tt.setInt(NEXT_AP_FK.pos, Integer.MIN_VALUE);
		tt.setNumPoints(1);
		tt.setPrevAndCurrDistM(dist);
		
//		if(startTimeSec >= endTimeSec)
//			throw new IllegalStateException("time tree of zero or less length has been added, tt is "+tt);
		
		return tt;
	}


	private void setMinTimeSecs(int startTimeSec) {
		setInt(MIN_TIME_SECS.pos, startTimeSec);
//		if(getMaxTimeSecs() > 0 && getMinTimeSecs() >= getMaxTimeSecs())
//			throw new IllegalStateException("what? "+this); 
	}


	private void setMaxTimeSecs(int endTimeSec) {
//		if(getMinTimeSecs() >= endTimeSec)
//			throw new IllegalStateException("what? "+this); 
		setInt(MAX_TIME_SECS.pos, endTimeSec);
	}

	private void setTimeJumpSecs(int timeJumpSec) {
		setInt(TIME_JUMP_SECS.pos, timeJumpSec);
	}


	public int getTimeJumpSecs() {
		return getInt(TIME_JUMP_SECS);
	}


//	private void setMaxElev(int val) {
//		setInt(MAX_ELEV.pos, val);
//	}
//
//
//	private int getMaxElev() {
//		return getInt(MAX_ELEV);
//	}
//
//	private void setMinElev(int val) {
//		setInt(MIN_ELEV.pos, val);
//	}
//
//
//	private int getMinElev() {
//		return getInt(MIN_ELEV);
//	}


	public void setPrevAndCurrDistM(double val) {
		setDouble(PREV_AND_CURR_DIST_M.pos, val);
	}


	public double getPrevAndCurrDistM() {
		return getDouble(PREV_AND_CURR_DIST_M	);
	}



	public void extendTimeTree(int endTimeSec, double dist, boolean containsNewPoint) {
		setMaxTimeSecs(endTimeSec);
		setPrevAndCurrDistM(getPrevAndCurrDistM()+dist);
		
		if(containsNewPoint)
			setNumPoints(getNumPoints()+1);
		
		TimeTree lastChild = getLastChild();
		if(lastChild != null)
			lastChild.extendTimeTree(endTimeSec, dist, containsNewPoint);
	}


	private TimeTree getLastChild() {
		for(int i = NUM_NODES-1; i >= 0; i--)
		{
			TimeTree subNode = getSubNode(i);
			if(subNode != null)
				return subNode;
		}
		
		return null;
	}

	/**
	 * Adds a time tree to the end of the tree. It is assumed that tt
	 * starts after the current time tree ends
	 * @param tt
	 * @return parent of this and tt (may be this)
	 */
	private TimeTree addTimeTree(TimeTree tt)
	{
		if(tt.getMinTimeSecs() < this.getMaxTimeSecs())
			throw new CacheException("trying to add a tt that is before the end of this one, this: "+this+", tt is "+tt);
		
		//since we're always adding to the end of the tree,
		//we always follow the same pattern, so that all kids have equal depth in the long run
		int index = chooseSubNodeOrCurrNodeToAddTo();
		
		//if all the kids have the same depth
		if(index == NUM_NODES)
		{
			//create a parent covering us and tt
			return createTimeTreeForKids(this, tt);
		}
		else
		{
			//the children are different lengths, so add it to the shortest child (will always be the end guy)
			
			//if there is no child there yet at all
			if(getSubNode(index) == null)
			{
				setSubNodeFk(index, tt.id);
			}
			else {
				//there is, so add the time tree to it
				setSubNodeFk(index, getSubNode(index).addTimeTree(tt).id);
			}
			
			//in any case, it becomes a descendent of us, so our sub node count increases by its subnode count
			setTotalSubNodeCount(getTotalSubNodeCount() + tt.getTotalSubNodeCount());
			
			setNumPoints(tt.getNumPoints() + getNumPoints());
		
			setTimeJumpSecs(Util.maxAll(getTimeJumpSecs(),tt.getTimeJumpSecs(),tt.getMinTimeSecs() - getMaxTimeSecs()));
			setMaxTimeSecs(tt.getMaxTimeSecs());
			
			//our distance traveled also gets updated by its distance
			setPrevAndCurrDistM(getPrevAndCurrDistM() + tt.getPrevAndCurrDistM());
			
			//since we're extending our time tree to the child one,
			//the next ap of the child should be the next ap of us.
			//keep in mind that the ap for both are the same, we are 
			//getting more specific in time as we go down the time tree
			//but the ap remains the same.
			setNextApId(tt.getNextApId());
			
			return this;
		}
	}


	private int getNumPoints() {
		return getInt(NUM_POINTS.pos);
	}

	private void setNumPoints(int numPoints) {
		setInt(NUM_POINTS.pos, numPoints);
	}

	private static TimeTree createTimeTreeForKids(TimeTree t1, TimeTree t2) {
		TimeTree tt = GTG.ttCache.newRow();
		
		tt.initNewTimeTree();
		tt.setSubNodeFk(0,t1.id);
		tt.setSubNodeFk(1,t2.id);
		
		tt.setMinTimeSecs(t1.getMinTimeSecs());
		tt.setMaxTimeSecs(t2.getMaxTimeSecs());
		
		tt.setTimeJumpSecs(Util.maxAll(t1.getTimeJumpSecs(),t2.getTimeJumpSecs(),t2.getMinTimeSecs() - t1.getMaxTimeSecs()));

		tt.setTotalSubNodeCount(1+t1.getTotalSubNodeCount() + t2.getTotalSubNodeCount());
		
		tt.setNumPoints(t1.getNumPoints()+t2.getNumPoints());
		
		tt.setPrevAndCurrDistM(t1.getPrevAndCurrDistM() + t2.getPrevAndCurrDistM());
		
		tt.setInt(PREV_AP_FK.pos, t1.getPrevApId());
		tt.setInt(NEXT_AP_FK.pos, t2.getNextApId());
		
		return tt;
	}


	public int getPrevApId() {
		return getInt(PREV_AP_FK);
	}

	public int getNextApId() {
		return getInt(NEXT_AP_FK);
	}


	private void setTotalSubNodeCount(int i) {
		setInt(TOTAL_SUB_NODE_COUNT.pos, i);
	}


	private void setSubNodeFk(int index, int fk) {
		setInt(TimeTree.SUB_NODES.pos + (Integer.SIZE >> 3) * index, fk);
	}


	/**
	 * Chooses either to add a point to the latest sub node,
	 * a new empty sub node, or to convert the parent and the new node as a sub node
	 * and create a parent for it in place of the current node
	 * 
	 * @return index of subnode to add to. If index points to an empty subnode slot,
	 *  a sub node should be created. if NUM_NODES is returned, the parent should
	 *  be set as a sibling of the new node and a new parent should be created 
	 */
	private int chooseSubNodeOrCurrNodeToAddTo() {
		
		
		for(int i = NUM_NODES-1; i >= 1; i--)
		{
			if(getSubNodeFk(i) != Integer.MIN_VALUE)
			{
				if(getSubNode(i).getTotalSubNodeCount() < getSubNode(i-1).getTotalSubNodeCount())
					return i; //if the last node has last sub nodes, we add to it until its equal with the current one
				else //if they are equal
				{
					if(getSubNode(i).getTotalSubNodeCount() < getSubNode(i-1).getTotalSubNodeCount())
						TAssert.fail("last node has more subnodes that previous node, last node: "+getSubNode(i)+
								", previous node: "+getSubNode(i-1));
					//in this case we add another child if there is room, or we make the parent a sibling of a node new with
					//a new parent
					//(if we return NUM_NODES the caller should make the parent a sibling)
					return i+1;
				}
			}
		}
		
		return NUM_NODES;
	}


	private int getTotalSubNodeCount() {
		return getInt(TOTAL_SUB_NODE_COUNT);
	}


	public TimeTree getSubNode(int i) {
		int fk = getSubNodeFk(i);
		
		if(fk != Integer.MIN_VALUE)
			return GTG.ttCache.getRow(fk);
		return null;
	}


	public int getSubNodeFk(int index) {
		return getInt(SUB_NODES.pos + (Integer.SIZE>>3) * index);
	}
	
	/**
	 * 
	 * @param startTimeSecs
	 * @param endTimeSecs
	 * @return true if intersects. False if for sure, like, doesn't. null if the children might
	 */
	private IntersectsTimeStatus intersectsTimeNoRecursive(int startTimeSecs, int endTimeSecs) 
	{
		if(endTimeSecs <= getMinTimeSecs())
			return IntersectsTimeStatus.RANGE_IS_BEFORE;
		if(startTimeSecs >= getMaxTimeSecs())
			return IntersectsTimeStatus.RANGE_IS_AFTER;
		
		//if it crosses the start or the end of the time tree, then it automatically intersects
		if(startTimeSecs <= getMinTimeSecs())
		{
			if(endTimeSecs >= getMaxTimeSecs())
				return IntersectsTimeStatus.ENVELOPS_TIME_TREE;

			return IntersectsTimeStatus.RANGE_OVERLAPS_MIN_TIME;
		}
		
		if(endTimeSecs >= getMaxTimeSecs())
			return IntersectsTimeStatus.RANGE_OVERLAPS_MAX_TIME;
		
		//else it's in the center of this sub tt
		
		//if we aren't higher than the time jump, then we overlap 
		if(endTimeSecs - startTimeSecs >= getTimeJumpSecs())
		{
			return IntersectsTimeStatus.INTERSECTS_OVER_TIME_JUMP;
		}
		
		return null;
	}

	public static int hackTimesLookingUpTimeTrees;

	public static IntersectsTimeStatus intersectsTime(TimeTree tt, int startTimeSecs, int endTimeSecs) {
		//Log.d("GPS","intersectsTime, startTime="+startTimeSecs+", endTime="+endTimeSecs+" tt "+tt);

		{
			IntersectsTimeStatus ttIntersects = tt.intersectsTimeNoRecursive(startTimeSecs, endTimeSecs);
			if(ttIntersects != null)
			{
				//Log.d("GPS", "returning off the bat "+ttIntersects);
				return ttIntersects;
			}
		}
		
		//note, now we know that the range is internal
		
		while(true)
		{
			hackTimesLookingUpTimeTrees++;
			TimeTree subTT = null;
			
			//PERF: we could probably start from the center instead
			for(int i=0; i < NUM_NODES; i++)
			{
				int fk = tt.getSubNodeFk(i);
				
				//for time trees, fk's are filled from the start to a point. Then there are nulls
				if(fk == Integer.MIN_VALUE)
				{
					ArrayList<TimeTree> res = new ArrayList();
					for(int j = 0; j < NUM_NODES; j++)
						res.add(tt.getSubNode(j));
					
					TAssert.fail("tt claims to envelope time range: "+startTimeSecs+", "+endTimeSecs+
							", but none of its children overlap it (short children) tt: "+tt
							+" children: "+res);

					//following was commented out and replaced with assert above:
					//return IntersectsTimeStatus.EMPTY_WITHIN;
				}
				
				subTT = GTG.ttCache.getRow(fk);
				
				//Log.d("GPS","intersectsTime, subTT "+subTT);

				IntersectsTimeStatus intersects = subTT.intersectsTimeNoRecursive(startTimeSecs, endTimeSecs);
				
				//if we have an idea
				if(intersects != null)
				{
					if(intersects.overlaps)
						//note, we could be more specific here and indicate whether start time or end time
						// intersects the child
					{
						//Log.d("GPS", "returning interesects within");
						return IntersectsTimeStatus.INTERSECTS_WITHIN;
					}
					else if (intersects == IntersectsTimeStatus.RANGE_IS_BEFORE)
					{
						//Log.d("GPS", "returning empty within");
						return IntersectsTimeStatus.EMPTY_WITHIN;
					}
					else
						//its past this nodes end point so we go on to the next one
						continue;
				}
				
				//it may be contained in the children of this child
				tt = subTT;
				
				//rerun the above with subTT...
				//note that we are only checking one child. This is because we only need to check it
				//if the time interval is completely enveloped by the child (in which case, it couldn't
				// be also contained by any other time tree.
				break;
			}
			
			//if we ran past the edge of the node
			if(subTT != tt)
			{
				ArrayList<TimeTree> res = new ArrayList();
				for(int i = 0; i < NUM_NODES; i++)
					res.add(tt.getSubNode(i));
				
				TAssert.fail("tt claims to envelope time range, but none of its children overlap it tt: "+tt
						+" children: "+res);
			}
		}// while checking the tree
	}
	
	/**
	 * 
	 * @param range Range in seconds, will be modified to reflex the range actually covered.
	 *  Note that this will be the cut range.
	 * @param timeFuzzinessPerc the amount of leeway we have when returning a start and end time.
	 *   This allows us to save time and not go all the way down the tree.
	 *   Note, we do this instead of a range because there is no real way to determine beforehand
	 *   what we'll find in the current window. For example, if the time range is 2 years, but we
	 *   were only in the particular ap for a few minutes, then the time has to be very specific,
	 *   but we wouldn't know this before we looked
	 * Note that having a timeFuzzinessPerc does not indicate that we are displaying ap's when
	 * they don't have time tree in the range. The max time jump must be under the range time
	 * for the time tree to be used. (ex if the range was for 2 months, and the time jump for
	 * 1 month, there is no way the range would fit inside the time jump area, so we know for
	 * sure the area panel was visited)
	 * 
	 * 
	 * 
	 **/
	public static void findOverlappingRange(int [] range, TimeTree tt,float timeFuzzinessPerc) {
		TimeTree lt = tt, rt = tt;
		
		//set the initial fuzziness based on tt.
		int leftFuzziness = handleLeftOverlappingRange(range, lt), 
			rightFuzziness = handleRightOverlappingRange(range, rt);

		//while we are still too fuzzy
		while(range[1] > range[0] && (leftFuzziness +rightFuzziness) > (range[1] - range[0]) * timeFuzzinessPerc)
		{
			if(leftFuzziness >= rightFuzziness)
			{
				lt = getSubTree(lt, range[0], false);
				
				//this updates range, and returns fuzziness
				leftFuzziness = handleLeftOverlappingRange(range, lt);
			}
			else 
			{
				rt = getSubTree(rt, range[1], true);

				//this updates range, and returns fuzziness
				rightFuzziness = handleRightOverlappingRange(range, rt);
			}
		
		}
		
		range[0] = Math.max(lt.calcTimeRangeCutStart(), range[0]);
		range[1] = Math.min(range[1],rt.calcTimeRangeCutEnd());
	}
	
	/**
	 * Returns the subtree which intersects the timeSec, or if the timeSec
	 * falls within a gap, the subtree to the left or right. It is assumed
	 * that timeSec is within tt (and there are at least two kids, which should
	 * always be true)
	 * @param timeSec
	 * @param left
	 * @return 
	 */
	private static TimeTree getSubTree(TimeTree tt, int timeSec, boolean left) {
		if(tt.getSubNode(0) == null)
		{
			/* ttt_installer:remove_line */Log.d("wha?",tt.toString());
		}
		if(timeSec < tt.getSubNode(0).getMaxTimeSecs())
			return tt.getSubNode(0);
		
		for(int i = 1; i < NUM_NODES; i++)
		{
			//if we want to return the left one in case of a gap, and we are before min,
			//(we already know were after max of the left one)
			//return the one beforehand
			if(left && timeSec < tt.getSubNode(i).getMinTimeSecs())
				return tt.getSubNode(i-1);
			//otherwise if we are before max, we are either right, or within the tt itself
			if(timeSec < tt.getSubNode(i).getMaxTimeSecs())
				return tt.getSubNode(i);
		}
		
		TAssert.fail("sub trees dont extend to timeSec, tt: "+tt+" timeSec: "+timeSec);
		return null;
	}


	/**
	 * 
	 * @param range
	 * @param lt
	 * @return the amount of leeway that the range could have on the left side
	 */
	private static int handleLeftOverlappingRange(int[] range, TimeTree lt) {
		//if we are outside
		if(range[0] <= lt.getMinTimeSecs())
		{
			range[0] = lt.getMinTimeSecs();
			return 0;//no more fuzziness
		}
		
		//the most amount of fuzziness we could have would be all the way
		//from the location to the end of the time tree, or the max space
		//between children
		return Math.min(lt.getTimeJumpSecs(), lt.getMaxTimeSecs() - range[0]);
	}


	/**
	 * 
	 * @param range
	 * @param rt
	 * @return the amount of leeway that the range could have on the right side
	 */
	private static int handleRightOverlappingRange(int[] range, TimeTree rt) {
		if(range[1] >= rt.getMaxTimeSecs())
		{
			range[1] = rt.getMaxTimeSecs();
			return 0;
		}
		
		return Math.min(range[1] - rt.getMinTimeSecs(), rt.getTimeJumpSecs());
	}


	public String toString()
	{
		return "TimeTree(id="+id+",minTimeSecs="+getMinTimeSecs()+",maxTimeSecs="+getMaxTimeSecs()
			+",timeJumpSecs="+getTimeJumpSecs()+",totalSubNodeCount="+getTotalSubNodeCount()+"dist="+getPrevAndCurrDistM()+",isBottom?="
			+(getSubNodeFk(0)==Integer.MIN_VALUE)+")";
	}
	
	public static enum IntersectsTimeStatus
	{
		//the stb is within the area panels time interval
		//but doesn't overlap any time interval of a segment
		//contained within it's descendents
		EMPTY_WITHIN(false), 
		
		//same as EMPTY_WITHIN, except the STB does intersect
		//with a segment contained within the descendents
		INTERSECTS_WITHIN(true),
		
		//the stb is completely before the area panel
		RANGE_IS_BEFORE(false),
		//the stb is completely after the area panel
		RANGE_IS_AFTER(false),
		
		RANGE_OVERLAPS_MIN_TIME(true),
		RANGE_OVERLAPS_MAX_TIME(true), ENVELOPS_TIME_TREE(true)
		, 
		INTERSECTS_OVER_TIME_JUMP(true) //this means that the time range
		//asked for is larger than the largest gap within the ap, so the
		//ap must be on
		;
		
		public final boolean overlaps;
		
		private IntersectsTimeStatus(boolean overlaps)
		{
			this.overlaps = overlaps;
		}
	}



	public AreaPanel getPrevAp() {
		int fk = getPrevApId();
		
		if(fk == Integer.MIN_VALUE)
			return null;
		return GTG.apCache.getRow(fk);
	}

	public AreaPanel getNextAp() {
		int fk = getNextApId();
		
		if(fk == Integer.MIN_VALUE)
			return null;
		return GTG.apCache.getRow(fk);
	}


	public void setNextApId(int id) {
		setInt(NEXT_AP_FK.pos, id);
	}


	/**
	 * Returns the time tree with the minimum min
	 * time that is greater than timeSec, unless
	 * a time tree encompasses timeSec (with no gaps), in which
	 * case the zero time jump lowest leaf time
	 * tree that does encompass it will be returned
	 */
	public TimeTree getEncompassigTimeTreeOrMinTimeTreeAfterTime(int timeSec, boolean alwaysBottomLevel) {
		
		//if the time tree is before the time completely,
		if(getMaxTimeSecs() <= timeSec)
			return null;
		
		TimeTree tt = this;
		
		for(;;)
		{
			if(alwaysBottomLevel && tt.getMinTimeSecs() >= timeSec)
			{
				return tt;
			}
			
			//if we've reached the bottom time tree 
			//and we are encompassing the time
			if(tt.getSubNodeFk(0) == Integer.MIN_VALUE)
				return tt;

			int i;
			
			for(i = 0; i < NUM_NODES; i++)
			{
				//we don't check for null here because we should never get past the max child
				TimeTree child = tt.getSubNode(i);
				
				if(child.getMaxTimeSecs() > timeSec) {
					tt = child;
					break;
				}
			}
					
			if(i == NUM_NODES)
				throw new CacheException("Child tt's don't extend to parent t "+tt+" " + tt.getSubNode(i-1));
		}
	}

	/**
	 * Returns bottom level time tree encompassing the time
	 * @param timeSec
	 * @return
	 */
	public TimeTree getBottomLevelEncompassigTimeTree(int timeSec) {
		
		//if the time tree is before the time completely,
		if(getMaxTimeSecs() <= timeSec)
			return null;
		
		TimeTree tt = this;
		
		for(;;)
		{
			if(tt.getMinTimeSecs() > timeSec)
			{
				return null;
			}
			
			//if we've reached the bottom time tree 
			//and we are encompassing the time
			if(tt.getSubNodeFk(0) == Integer.MIN_VALUE)
				return tt;

			int i;
			
			for(i = 0; i < NUM_NODES; i++)
			{
				//we don't check for null here because we should never get past the max child
				TimeTree child = tt.getSubNode(i);
				
				if(child.getMaxTimeSecs() > timeSec) {
					tt = child;
					break;
				}
			}
					
			if(i == NUM_NODES)
				throw new CacheException("Child tt's don't extend to parent t "+tt+" " + tt.getSubNode(i-1));
		}
	}


	/**
	 * Returns the time tree with the maximum max
	 * time that is less than timeSec, unless
	 * a time tree encompasses timeSec, in which
	 * case the zero time jump lowest leaf time
	 * tree that does encompass it will be returned
	 */
	public TimeTree getEncompassigTimeTreeOrMaxTimeTreeBeforeTime(int timeSec, boolean alwaysBottomLevel) {
		
		//if the time tree is after the time completely,
		if(getMinTimeSecs() >= timeSec)
			return null;
		
		TimeTree tt = this;
		
		for(;;)
		{
			if(!alwaysBottomLevel && tt.getMaxTimeSecs() <= timeSec)
			{
				return tt;
			}
			
			//if we've reached the bottom time tree 
			//and we are encompassing the time
			if(tt.getSubNodeFk(0) == Integer.MIN_VALUE)
				return tt;
			
			int i;
			for(i = NUM_NODES-1; i >= 0; i--)
			{
				TimeTree child = tt.getSubNode(i);
				
				//since we're starting from the farthest end
				//and going in, we may get a null or two before
				//the actual children
				if(child == null)
					continue;
				
				if(child.getMinTimeSecs() < timeSec) {
					tt = child;
					break;
				}
			}
			
			if(i < 0)
				throw new CacheException("Child tt's don't extend to parent t " + tt+" "+ tt.getSubNode(i+1));
		}
	}


	public void setNextApIdForThisAndAllChildren(int id) {
		tt = this;

		for(;;)
		{
			tt.setNextApId(id);
			int i;
			
			for(i = NUM_NODES-1; i >= 0; i--)
			{
				TimeTree child = tt.getSubNode(i);
				
				//since we're starting from the farthest end
				//and going in, we may get a null or two before
				//the actual children
				if(child == null)
					continue;
				
				tt = child;
				break;
			}
			
			if(i == -1)
				break;
		}
	}

	public TimeTree getMinTimeTreeAfterTime(int timeSec) {
		//if the time tree is before the time completely,
		if(getMaxTimeSecs() <= timeSec)
			return null;
		if(getMinTimeSecs() >= timeSec)
			return this;
		
		TimeTree tt = this;
		
		TimeTree bestTt = null;
		
		for(;;)
		{
			if(tt.getSubNodeFk(0) == Integer.MIN_VALUE)
				return bestTt;

			int i;
			
			for(i = NUM_NODES-1; i>= 0;  i--)
			{
				TimeTree childTt = tt.getSubNode(i);
				if(childTt == null)
					continue;
				
				if(childTt.getMinTimeSecs() >= timeSec && (bestTt == null 
						|| childTt.getMinTimeSecs() < bestTt.getMinTimeSecs()))
				{
					bestTt = childTt;
				}
				else if(childTt.getMaxTimeSecs() <= timeSec)
				{
					return bestTt;
				}
				else //we're encompassing
				{
					tt = childTt;
					break;
				}
			}
					
			if(i == -1)
				throw new CacheException("Child tt doesn't start at parent t "+tt+" " + tt.getSubNode(0));
		}
	}

	public int getTimeSec() {
		return getMaxTimeSecs() - getMinTimeSecs();
	}

	public int calcTimeRangeCutEnd() {
		AreaPanel np = getNextAp();
		if(np != null)
		{
			TimeTree ntt = np.getTimeTree().getEncompassigTimeTreeOrMaxTimeTreeBeforeTime(getMaxTimeSecs(), true);
			return ntt.getMinTimeSecs();
		}
		else
			return getMaxTimeSecs();
		
	}
	
	
	public int calcTimeRangeCutStart() {
		AreaPanel pp = getPrevAp();
		if(pp != null)
		{
			TimeTree ptt = pp.getTimeTree().getEncompassigTimeTreeOrMinTimeTreeAfterTime(getMinTimeSecs(), true);
			return ptt.getMaxTimeSecs();
		}
		else
			return getMinTimeSecs();
		
	}

	public int getPrevApPrevTtEndTime() {
		AreaPanel ap = getPrevAp();
		if(ap == null)
			return Integer.MIN_VALUE;
		
		return ap.getTimeTree().getEncompassigTimeTreeOrMaxTimeTreeBeforeTime(getMinTimeSecs(), false).getMaxTimeSecs();
	}

	public int getNextApNextTtStartTime() {
		AreaPanel ap = getNextAp();
		if(ap == null)
			return Integer.MIN_VALUE;
		
		return ap.getTimeTree().getEncompassigTimeTreeOrMinTimeTreeAfterTime(getMaxTimeSecs(), false).getMinTimeSecs();
	}

	public boolean isBottomLevel() {
		return getSubNodeFk(0) == Integer.MIN_VALUE;
	}
 
	/**
	 * True if the cut time (the actual gps points) overlap the given time
	 */
	public boolean isCutTimeOverlap(int startTimeSec, int endTimeSec) {
		// at the bottom level there are areas that are covered and gaps between
		// we return true if the start and end are in different gaps or
		// the start or end intersect with a covered area
		// By covered area, I mean a time tree using the cut start and cut end
		// values
		
		
		
		//note that we don't set bottom to true, since the only time we won't be at the 
		//bottom is when the tt is after the start time (and the end time in the second
		// statement). In this case we act the same regardless if the time tree is at the 
		//bottom or not
		TimeTree startTt = getEncompassigTimeTreeOrMinTimeTreeAfterTime(startTimeSec, false);
		TimeTree endTt = getEncompassigTimeTreeOrMinTimeTreeAfterTime(endTimeSec, false);
		
		//if the minimum start and end tt are different, they must overlap the ap, since
		//there must be interleaving gps points between the start time and end time in that case
		if(startTt != endTt &&
				//this is to check for cases where the start time does not overlap, but the
				//end time does (or vice versa) but it doesn't overlap the cut time (which
				// we haven't determined yet). In this case the startTt and endTt could be
				// different but they represent the same time
				(startTt == null || endTt == null || startTt.getMinTimeSecs() == endTt.getMinTimeSecs()
						|| startTt.getMaxTimeSecs() == endTt.getMaxTimeSecs()))
			return true;
		
		//if null the start time and end time extends beyond the end of the time tree
		if(startTt == null)
			return false;
		
		int startCutSec = startTt.calcTimeRangeCutStart();
		
		if(startTimeSec < startCutSec)
		{
			//both start and end are before the tt
			if(endTimeSec <= startCutSec)
				return false;

			//else the end time is either intersecting or after the tt
			return true;
		}
		
		//startTimeSec >= startCutSec
		int endCutSec = startTt.calcTimeRangeCutEnd();
		
		//(note that we know already that endtime isn't beyond the end of the tt
		// because if it was a different endTt would have been returned)
		
		//the start time intersects the tt
		if(startTimeSec < endCutSec)
			return true;
	
		//otherwise both the start time and end time must be after the tt
		return false;
	}

}
