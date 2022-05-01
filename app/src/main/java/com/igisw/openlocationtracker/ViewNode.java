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

import com.rareventure.gps2.database.TAssert;
import com.igisw.openlocationtracker.GpsTrailerOverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * represents nodes that are visible according to an stbox which may or may not
 * be the current one.
 * 
 * View Nodes can either be SET or EMPTY, and this is according to the stbox
 * in their posession.. all view nodes will have an AreaPanel. If an AreaPanel
 * does not exist for a particular location visible from an stbox, a 
 * null child will be created for it (ie there will be no view nodes with null
 * area panels)
 * 
 * 
 */
public class ViewNode {
	
	/**
	 * The minimum time jump for a gap to be worthy to chase down a line for as related to
	 * ap size
	 */
	//10 hours for a depth a quarter the size of the world (~5000 to 10000 miles depending on
	// how close you are to the poles)
	//WARNING depends on size 2 for SUB_AREA_PANELS_PER_SIDE
	private static final float MIN_TIME_JUMP_SECS_FOR_LINES_TO_AP_SIZE = 72000f / (1<< 24);

	/**
	 * The max number of time trees to sample when figuring out lines
	 */
	private static final int MAX_TIME_TREE_SAMPLES = 5;

	private static final Comparator<TimeTree> TIME_TREE_TIME_SEC_COMPARATOR = new Comparator<TimeTree>() {

		@Override
		public int compare(TimeTree lhs, TimeTree rhs) {
			return lhs.getTimeSec() - rhs.getTimeSec();
		}
	};

	private static final int MAX_CALCULATED_LINES_PER_ROUND = 128;

	private static final int MIN_CALCULATED_LINES_PER_ROUND = 4;
	
	public String toString() {
		return "ViewNode(status=" + status + ",stBox=" + stBox + ",ap=" + ap()
				+ ",childrenNull?=" + (children == null ? "Y" : "N")
				+ ",dirtyDescendents="+dirtyDescendents + ")";
	}

	// status of View node
	public static enum VNStatus {
		// is not in the stb and does not contain any sub area panels within the
		// stb
		EMPTY,
		// intersects the stb. If a view node is in this state, children[] will be set
		SET
	};

	public VNStatus status;

	// the space time box this view node has been updated to. always present
	// unless vnstatus is null
	public AreaPanelSpaceTimeBox stBox;

	public int apId;
	
	public AreaPanel ap()
	{
		return GTG.apCache.getRow(apId);
	}
	
	/**
	//children of viewnode. If set, always an array of length 
	//AreaPanel.NUM_SUB_PANELS. if a particular location doesn't 
	//have an area panel at all (regardless of time), the view panel will be null. 
	//children will also be null if VNStatus is EMPTY or null, or we've reached
	//min depth	
	 */
	public ViewNode[] children;

	/**
	// the count of all descendents of current node that are dirty, including
	// itself with respect to the latest stBox (not the viewnodes stBox)
	// this includes view nodes with null status
	 */
	public int dirtyDescendents;

	/**
	// the range of time that the stbox overlaps the areapanel's time(s)
	// just a length of 2 array.. ie. min and max
	// note, this is needed so that we can display the total range of time
	// covered within the timeview (as the rainbow bar). Specifically, this is
	// why the lower bound is needed.
	// also note this is calculated with some fuzziness present, ie. the range
	// will not be exact, since it only deals with colors. Keep this in mind
	// if using for other purposes. Take a look at TimeTree.findOverlappingRange
	// for more information on this
	 */
	public int[] overlappingRange;

	/**
	 * When lines are calculated for view nodes, we start by doing the start and
	 * end times, then we look inside and do the ends of the largest time tree.
	 * This is to hopefully pick up long lines to far away places first, since
	 * those are off screen and we'll only have one chance to pick them up.
	 * 
	 * Note that we do not use timejump for this purpose, because timejump
	 * will include cases where we go from A -> B -> C -> B -> A where C is a far
	 * away place and A and B is not. The point is that A has a larger
	 * timejump than A, although the line from A to B is short. And you can
	 * imagine A's and B's ad infinitum.
	 */
	public int largestTimeTreeLengthForUncreatedLines = Integer.MAX_VALUE;
	
	public static ViewNode createHeadViewNode() {
		ViewNode head = new ViewNode();

		if(GTG.apCache.isDbFilled())
		{
			head.status = null;
			head.dirtyDescendents = 1;
			head.apId = GTG.apCache.TOP_ROW_ID;
			
			return head;
		}
		else //there are no points at all
		{
			head.status = VNStatus.EMPTY;
			head.dirtyDescendents = 0;
			head.apId = GTG.apCache.TOP_ROW_ID;
			
			return head;
		}
	}

	public ViewNode() {
	}

	/**
	 * sets the status to on for the view node ofr the current stbox. Note that the view node
	 * may have already been set, but for a prior stbox (which would make its status unknown
	 * for the current stbox)
	 * 
	 * @param parentsAndCurrent
	 * @param newLocalStBox
	 * @param childrenDirty
	 * @param minDepth
	 */
	public void setSetStatus(
			ArrayList<ViewNode> parentsAndCurrent,
			AreaPanelSpaceTimeBox newLocalStBox, boolean childrenDirty,
			int minDepth) {
		
		AreaPanel ap = ap();
		
		this.status = VNStatus.SET;

		//update dirty descendents
		int oldDirtyDescendents = this.dirtyDescendents;

		if (ap.getDepth() == minDepth) {
			this.dirtyDescendents = 0;
			if(children != null)
				TAssert.fail("why are children not null");
		}
		else {
			
			//if the item was not set before, it would not have children
			if(children == null)
			{
				createUnknownChildren();
			}
			else if (!childrenDirty) // if the change to the stbox wouldn't set
										// the children to dirty
			{
				// set them all to clean if they were clean for the last stbox.
				// (this will update this.dirtyDescendents)
				cleanAllDescendentsIfCleanBefore(newLocalStBox, stBox, minDepth);
			}
			else
			{
				//we can only update this one
				this.dirtyDescendents--;
			}
		}

		// update the setNodesInSelfAndDescendentsCount for all the ancestors
		for (ViewNode vn : parentsAndCurrent)
		{
			if(vn != this)
				vn.dirtyDescendents += this.dirtyDescendents - oldDirtyDescendents;
		}

		stBox = newLocalStBox;
	}

	private boolean hasUnknownChild(AreaPanelSpaceTimeBox newLocalStBox) {
		if(children != null)
		{
			for(ViewNode child : children)
			{
				if(child != null && child.needsProcessing(newLocalStBox))
					return true;
			}
		}
		
		return false;
	}

	/**
	 * Updates dirtyDescendents so that if they were clean according to parentStBox, they're clean now.
	 */
	private void cleanAllDescendentsIfCleanBefore(AreaPanelSpaceTimeBox newStBox,
			AreaPanelSpaceTimeBox parentStBox, int minDepth) {
		if (status == null || this.stBox != parentStBox)
			return; // we were dirty before

		//else we were clean before, so we're clean now
		this.stBox = newStBox;
		
		//update our own dirtyDescendents because we've just turned clean
		this.dirtyDescendents--;
		
		// if we were empty, then there definitely aren't any children and we
		// were clean before
		if (status == VNStatus.EMPTY) {
			return;
		}

		//status is SET
		
		if(ap().getDepth() != minDepth) //if we're at minDepth, there are no children,
			//but otherwise we need to clean them
			for (ViewNode child : children) {
				if(child == null)
					continue;
				int oldChildDirtyDescendents = child.dirtyDescendents;
			
				child.cleanAllDescendentsIfCleanBefore(newStBox, parentStBox, minDepth);
			
				dirtyDescendents += child.dirtyDescendents - oldChildDirtyDescendents;
			}
	}

	public void setEmptyStatus(ArrayList<ViewNode> parentsAndCurrent,
			AreaPanelSpaceTimeBox newLocalStBox) {
		int oldDirtyDescendents = dirtyDescendents;

		for (ViewNode vn : parentsAndCurrent) {
			vn.dirtyDescendents -= oldDirtyDescendents;

			if (vn.dirtyDescendents < 0)
				TAssert.fail("Why is dirtyDescendents negative? " + vn);
		}

		stBox = newLocalStBox;

		// PERF we could possibly also set all the children to EMPTY as well, so
		// we can cache the children if the user goes back
		children = null;

		status = ViewNode.VNStatus.EMPTY;
	}

	/**
	 * @param newLocalStBox
	 * @return overlapping range if interval overlaps, or null if it doesn't
	 */
	public int [] checkTimeInterval(AreaPanelSpaceTimeBox newLocalStBox) {
		AreaPanel ap = ap();
		
		// if we're out of range
		// note that time tree can be null if we only created the head AreaPanel. In its initial
		// state it does not have a time tree
		if (ap.getTimeTree() == null ||  newLocalStBox.maxZ < ap.getTimeTree().getMinTimeSecs()
				|| newLocalStBox.minZ >= ap.getTimeTree()
						.getMaxTimeSecs())
			return null;

		if(newLocalStBox.pathList != null)
		{
			//PERF: we can remember the parents overlapping range to shorten the area that we need to check for
			//the child
			
			//we need to find the first and last occurrence of the area panel within the path list,
			//for the overlapping range

			int [] overlappingRange = new int[2];
			
			int [] tempOverlappingRange = new int[2];
			
			TimeTree rootTt = ap.getTimeTree();
			
			int fromEndIndex;
			for(fromEndIndex = newLocalStBox.pathList.size()-1; fromEndIndex >= 0; fromEndIndex--)
			{
				Path p = newLocalStBox.pathList.get(fromEndIndex);
				
				tempOverlappingRange[0] = p.startTimeSec;
				tempOverlappingRange[1] = p.endTimeSec;
				
				TimeTree.findOverlappingRange(tempOverlappingRange, rootTt, 
						GpsTrailerOverlay.prefs.timeTreeFuzzinessPerc);
				
				if(tempOverlappingRange[0] < tempOverlappingRange[1])
				{
					overlappingRange[0] = tempOverlappingRange[0];
					overlappingRange[1] = tempOverlappingRange[1];
					break;
				}
			}
			
			//if no path has the area panel
			if(overlappingRange[0] == 0)
				return null;
			
			
			for(int fromStartIndex = 0; fromStartIndex < fromEndIndex; fromStartIndex++)
			{
				Path p = newLocalStBox.pathList.get(fromStartIndex);
				
				tempOverlappingRange[0] = p.startTimeSec;
				tempOverlappingRange[1] = p.endTimeSec;
				
				TimeTree.findOverlappingRange(tempOverlappingRange, rootTt, 
						GpsTrailerOverlay.prefs.timeTreeFuzzinessPerc);
				
				if(tempOverlappingRange[0] < tempOverlappingRange[1])
				{
					overlappingRange[0] = tempOverlappingRange[0];
					break;
				}
			}

			return overlappingRange;

		}
		else
		{
			int [] overlappingRange = new int[] { newLocalStBox.minZ,
					newLocalStBox.maxZ };
	
			TimeTree.findOverlappingRange(overlappingRange, ap.getTimeTree(),
					GpsTrailerOverlay.prefs.timeTreeFuzzinessPerc);
	
			// Log.d("GPS","Checking time interval for "+newLocalStBox+
			// ", minTimeSecs is "+ap.getTimeTree().getMinTimeSecs()+
			// ", maxTimeSecs is "+ap.getTimeTree().getMaxTimeSecs()+
			// ", res is "+res);
			return overlappingRange[0] < overlappingRange[1] ? overlappingRange : null;
		}
	}


	// meaningfully means that it decreased or moved in a way that would affect
	// whether the area panel is on, or the min or max times of the overlapping
	// range in an important way.

	public boolean timeChangedMeaningfullyForSetAreaPanel(
			AreaPanelSpaceTimeBox newLocalStBox) {
		if (newLocalStBox.minZ <= stBox.minZ
				&& newLocalStBox.maxZ >= stBox.maxZ)
			return false;

		// PERF: we could keep track of time tree min and max internally from
		// last match
		// in the status
		return true;
	}

	public boolean xyChangedMeaningfullyWithRegardsToChildren(
			AreaPanelSpaceTimeBox newLocalStBox) {
		int[] truncatedOldBox = new int[4];
		int[] truncatedNewBox = new int[4];

		truncatedOldBox[0] = stBox.minX;
		truncatedOldBox[1] = stBox.minY;
		truncatedOldBox[2] = stBox.maxX;
		truncatedOldBox[3] = stBox.maxY;

		truncatedNewBox[0] = newLocalStBox.minX;
		truncatedNewBox[1] = newLocalStBox.minY;
		truncatedNewBox[2] = newLocalStBox.maxX;
		truncatedNewBox[3] = newLocalStBox.maxY;

		AreaPanel ap = ap();
		
		truncateToBox(truncatedOldBox, ap.getX(), ap.getY(), ap.getMaxX(),
				ap.getMaxY());
		truncateToBox(truncatedNewBox, ap.getX(), ap.getY(), ap.getMaxX(),
				ap.getMaxY());

		if (truncatedOldBox[0] != truncatedNewBox[0]
				|| truncatedOldBox[1] != truncatedNewBox[1]
				|| truncatedOldBox[2] != truncatedNewBox[2]
				|| truncatedOldBox[3] != truncatedNewBox[3])
			return true;
		return false;
	}

	/**
	 * Truncates the box specified by t to be in the border of x1, y1, x2, y2.
	 * If box is completely outside of x1,y1,... then it is set to 0,0,0,0. t is
	 * directly changed.
	 * 
	 * @param t
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 */
	private void truncateToBox(int[] t, int x1, int y1, int x2, int y2) {
		// if we're wrapping lon 0
		if (t[0] > t[2]) {
			// ap is in the middle and we're wrapped around lon 0 not touching
			// it
			if (t[0] > x2 && t[2] < x1)
				t[0] = t[2] = -1;
			else {
				if (t[0] > x2 || t[0] < x1)
					t[0] = x1;
				if (t[2] > x2 || t[2] < x1)
					t[2] = x1;
			}
		} else {
			if (t[0] < x1)
				t[0] = x1;
			if (t[0] > x2)
				t[0] = x2;
			if (t[2] < x1)
				t[2] = x1;
			if (t[2] > x2)
				t[2] = x2;
		}
		if (t[1] < y1)
			t[1] = y1;
		if (t[1] > y2)
			t[1] = y2;
		if (t[3] < y1)
			t[3] = y1;
		if (t[3] > y2)
			t[3] = y2;

		// if we're completely outside of the box
		if (t[2] - t[0] == 0 || t[3] - t[1] == 0) {
			// set everything to -1
			t[0] = -1;
			t[1] = -1;
			t[2] = -1;
			t[3] = -1;
		}
	}

	public boolean hasUnknownChildren(AreaPanelSpaceTimeBox stBox2) {
		if (children == null)
			return true;

		for (int i = 0; i < children.length; i++) {
			if (children[i] != null && children[i].needsProcessing(stBox2))
				return true;
		}

		return false;
	}

	public boolean needsProcessing(AreaPanelSpaceTimeBox newLocalStBox) {
		return status == null || newLocalStBox != stBox;
	}

	/**
	 * Fills in immediate children for an unknown node. If areapanel is
	 * present, child becomes unk, otherwise empty.
	 * <p>
	 * WARNING: dirtyDescendents for ancestors of node are *not* updated.
	 * It is up to the caller to do this.
	 */
	public void createUnknownChildren() {
		this.dirtyDescendents = 0;
		
		//create "unknown" children
		children = new ViewNode[AreaPanel.NUM_SUB_PANELS];
		
		AreaPanel ap = ap();
		for (int i = 0; i < children.length; i++) {
			AreaPanel childAp = ap.getSubAreaPanel(i);
			
			if(childAp != null) {
				children[i] = new ViewNode();
				children[i].status = null;
				children[i].dirtyDescendents = 1;
				children[i].apId = childAp.id;
				this.dirtyDescendents ++;
			}
		}
	}

	/**
	 * Marks node and all descendents as dirty.
	 * If the depth of node is minDepth (the tiniest it can be to
	 * display in current view), will set its children to null
	 * @param timesToLines 
	 */
	public int turnOnAllDirtyFlags(int minDepth) {
		//for all non min depth nodes, we clear the area lines,
		// so we must reset the clearLineCalcs
		int myDepth = ap().getDepth();
		if(myDepth != minDepth)
			this.clearLineCalcs();
		
		if (children != null) {
			this.dirtyDescendents = 1;
			
			// chop the tree at the min depth
			if (minDepth == myDepth)
			{
				children = null;
			}
			else {
				for (ViewNode child : children) {
					if(child != null)
						dirtyDescendents += child.turnOnAllDirtyFlags(minDepth);
				}
			}
		}
		else
		{
			//if the min depth changed, we need to see if we need to add children
			//in general all set nodes that are not at min depth need children
			if(status == VNStatus.SET && minDepth != ap().getDepth() && children == null)
			{
				//note that we add one to dirty descendents after
				//createUnknownChildren() because it resets dirty descendents
				//to the number of children
				createUnknownChildren();
				dirtyDescendents++;
			}
			else
				this.dirtyDescendents = 1;
		}
		
		return dirtyDescendents;
	}

	/**
	 * add a newly created gps point to the top of ViewNodes. This
	 * will update them so they are consistent with the AreaPanels
	 * as long as the AreaPanels are updated for this point as well.
	 * <p>
	 * WARNING: All the view nodes must be clean before this is called. It
	 * does not update dirtyDescendents!
	 */
	public void addPointToHead(int x, int y, int lastTimeSec,
			int timeSec, int minDepth) 
	{
		if(dirtyDescendents != 0)
			TAssert.fail("Don't call this method unless everything is clean");

		if(stBox == null)
			return;
		
		addPointToViewNode(x, y, lastTimeSec, timeSec, minDepth);
		
	}
	
	/**
	 * Adds a point to the view node and updates itself and all children.
	 * True if a
	 * @param currGpsLocRow
	 * @param lastTimeSec
	 * @param timeSec
	 * @param minDepth 
	 */
	private void addPointToViewNode(int x, int y, int lastTimeSec, int timeSec, int minDepth) {
		//if its unknown, it may not have an stbox so we let it stay unknown
		if(status == null)
		{
			TAssert.fail("Why am I unknown if everything is clean? "+this);
		}
		
		AreaPanel ap = ap();
		
		//if the area panel doesn't contain the point at all, we're done
		if(!ap.containsPoint(x, y))
			return;
		
		if(status == VNStatus.EMPTY)
		{
			//if were empty and the point falls outside of the stb,
			//then there is nothing to do
			if(stBox.maxZ < lastTimeSec)
				return;

			status = VNStatus.SET;
			overlappingRange = 
				new int[] { Math.max(stBox.minZ, lastTimeSec), 
					Math.min(stBox.maxZ, timeSec) };
		}
		else 
			//assuming that the point time is later than any time previously known
			overlappingRange[1] = Math.min(stBox.maxZ, timeSec);

		//now update the children
		if(ap.getDepth() != minDepth) {
			
			if(children == null)
				//create children for new set state
				children = new ViewNode[AreaPanel.NUM_SUB_PANELS];
			
			//we add the children that need to be there based on the area panels
			for (int i = 0; i < children.length; i++) {
				AreaPanel childAp = ap.getSubAreaPanel(i);
				
				if(childAp == null)
					continue;
				
				if(childAp != null)
				{
					if(children[i] == null) {
						//we set the child to empty at first, regardless if it really is or not
						children[i] = new ViewNode();
						children[i].status = VNStatus.EMPTY;
						children[i].stBox = stBox;
						children[i].apId = childAp.id;
					}
						
					//TODO 3 I'm not sure if this is entirely correct, what about
					// time gaps?
					//skip children that are empty (but still convert them from null to empty
					// if the ap is there) A null child indicates there is no associated child ap at all.
					if(childAp.outsideOfXY(stBox) || 
							childAp.getEndTimeSec() <= stBox.minZ
							|| childAp.getStartTimeSec() >= stBox.maxZ)
						continue;
						
					//now we add it as we did to this view node
					children[i].addPointToViewNode(x, y, lastTimeSec, timeSec, minDepth);
					
					//note, we keep looping because all the children that don't have the point
					//still need to be created and marked as empty. Otherwise they will be considered
					//to not even have an ap at all, and when they do get it range, they would still
					// be considered empty
				}
			}
			
			//note, in some cases the parent can be set and the child not. This occurs where the parent
			//overlaps the stbox but the child containing the point does not
			return;
		}
		
		
		
	}

	/**
	 * 
	 * @return the number of children that are set and do not need processing ofr
	 *   the current stbox
	 */
	public int getDensity(AreaPanelSpaceTimeBox newStBox) {
		int density = 0;
		if(children != null)
		{
			for(ViewNode child : children)
			{
				if(child != null && child.status == VNStatus.SET 
						&& !child.needsProcessing(newStBox))
					density++;
			}
		}
		return density;
	}

	public boolean timeChangedMeaningfullyWithRegardsToChildren(
			AreaPanelSpaceTimeBox newStBox) {
		int oldMinT, oldMaxT;
		int newMinT, newMaxT;
		
		AreaPanel ap = ap();
		
		oldMinT = newMinT = ap.getStartTimeSec();
		oldMaxT = newMaxT = ap.getEndTimeSec();
		
		//first truncate stbox min and max to area panel min and max for
		//both old and new stbox
		if(newMaxT <= newStBox.minZ ||	newMinT >= newStBox.maxZ)
			newMinT = newMaxT = 0;
		else {
			newMinT = Math.max(newMinT, newStBox.minZ);
			newMaxT = Math.min(newMaxT, newStBox.maxZ);
		}
			
		if(oldMaxT <= stBox.minZ ||	oldMinT >= stBox.maxZ)
			oldMinT = oldMaxT = 0;
		else {
			oldMinT = Math.max(oldMinT, stBox.minZ);
			oldMaxT = Math.min(oldMaxT, stBox.maxZ);
		}
			
		//if they have moved, then the children are dirty
		return newMinT != oldMinT || newMaxT != oldMaxT;
	}

	public void calcLinesForStBox(ArrayList<TimeTree> scratchPath,
			AreaPanelSpaceTimeBox apStBox, HashMap<Integer, ViewLine> startTimeToViewLine, 
			HashMap<Integer, ViewLine> endTimeToViewLine,
			int lastNumberOfViewNodesLinesCalculatedFor) { 		
		if(largestTimeTreeLengthForUncreatedLines == Integer.MIN_VALUE) return;
		AreaPanel myAp = ap();
		
		//if we haven't calculated for the ends yet
		if(largestTimeTreeLengthForUncreatedLines == Integer.MAX_VALUE)
		{
			TimeTree tt = myAp.getTimeTree();
			
			addLineForGapStart(tt, startTimeToViewLine, endTimeToViewLine);
			addLineForGapEnd(tt, startTimeToViewLine, endTimeToViewLine);
			
			largestTimeTreeLengthForUncreatedLines = tt.getTimeSec()-1;
			
			return;
		}

		//we start over searching for the next time tree every time. This is because
		//if we kept our place and started from where we last found a time treefor a line
		//we would be ignoring all the timetrees that we skipped over last time, because
		// their time length was too small. But now that we are using a smaller time length,
		// they may be applicaable for this round.
		
		//next time trees that have not been explored yet
		scratchPath.clear();
		scratchPath.add(myAp.getTimeTree());

		//we start calculating a lot of lines and end with a little
		int maxLines = Math.max(MAX_CALCULATED_LINES_PER_ROUND - lastNumberOfViewNodesLinesCalculatedFor, MIN_CALCULATED_LINES_PER_ROUND);
		
		int calculatedLines = 0;
		
		while(!scratchPath.isEmpty())
		{
			TimeTree tt = scratchPath.remove(scratchPath.size()-1);
			
			int timeLengthSec = tt.getTimeSec();
			
			//if we are at the bottom level and 
			//if we haven't (probably) already created the line
			//(incase there are two gaps exactly the same size, we will reprocess time gaps that are exactly the same
			// size as last time)
			// we only create lines for the bottom level because we would end up recreating them again and again
			// for each timetree child. 
			if(tt.isBottomLevel() && timeLengthSec <= largestTimeTreeLengthForUncreatedLines)
			{
//				Log.d(GTG.TAG,"creating lines for child size "+childTimeLengthSec+
//						" tt "+childTt);
				
				calculatedLines += addLineForGapStart(tt, startTimeToViewLine, endTimeToViewLine) ? 1 : 0;
				
				calculatedLines += addLineForGapEnd(tt, startTimeToViewLine, endTimeToViewLine) ? 1 : 0;

				
				//if we calculated enough lines for this round
				if(calculatedLines >= maxLines)
				{
					largestTimeTreeLengthForUncreatedLines = timeLengthSec-1; 
					return; //exit to work on some other nodes for awhile
				}
			}

			for(int i = 0; i < TimeTree.NUM_NODES; i++)
			{
				TimeTree childTt = tt.getSubNode(i);
				
				//no more tt's
				if(childTt == null)
					break;
				
				//skip children out of range
				if(childTt.getMaxTimeSecs() <= apStBox.minZ)
					continue;
				
				//if out of range on the higher time side then the rest will never be included
				// in the stb
				if(childTt.getMinTimeSecs() >= apStBox.maxZ)
					break;
				
				//insert in order of greatest time sec last (and remove from last to first)
				int childLocation = Collections.binarySearch(scratchPath, childTt, TIME_TREE_TIME_SEC_COMPARATOR);
				if(childLocation < 0)
				{
					// x = -(insertion point) - 1
				    // (insertion point) = -x -1
					scratchPath.add(-childLocation - 1, childTt);
				}
				else
					scratchPath.add(childLocation, childTt);
				
			}
		}
		
		//we have crawled through all of them
		largestTimeTreeLengthForUncreatedLines = Integer.MIN_VALUE;
	}

	private boolean addLineForGapStart(TimeTree tt,
			HashMap<Integer, ViewLine> startTimeToViewLine, HashMap<Integer, ViewLine> endTimeToViewLine) {
		
		if(tt.getMaxTimeSecs() > stBox.minZ)
		{
			int startTimeSec = tt.getMinTimeSecs();

			//the start of the tt equals the end of the viewline
			ViewLine existingViewLine = endTimeToViewLine.get(startTimeSec);
			
			//co: even lines between neighboring points are visibile depending on the size
			//of the ap dot.
//			AreaPanel ap1 = tt.getPrevAp();
//			AreaPanel ap2 = ap();
//			
//			//if were at the very end
//			if(ap1 == null)
//				return; //there is no line to draw
//			
//			if(isLineTooShortToDraw(ap1, ap2))
//			{
//				if(existingViewLine != null)
//				{
//					startTimeToViewLine.remove(existingViewLine.startTimeSec);
//					endTimeToViewLine.remove(existingViewLine.endTimeSec);
//				}
//				return;
//			}
			
			if(existingViewLine == null)
			{
				int prevApEndTime = tt.getPrevApPrevTtEndTime();
				
				//if we're at the absolute beginning there will be no prev ap
				if(prevApEndTime == Integer.MIN_VALUE)
					return false;
				
				//we don't include lines that are completely off the st box
				if(prevApEndTime < stBox.minZ)
					return false;
				
				existingViewLine = new ViewLine(startTimeSec, prevApEndTime);
				startTimeToViewLine.put(existingViewLine.startTimeSec, existingViewLine);
				endTimeToViewLine.put(existingViewLine.endTimeSec, existingViewLine);
			}

			existingViewLine.startApId = tt.getPrevApId();
			existingViewLine.endApId = apId;
			
			return true;
		}
		
		return false;
	}

	private boolean addLineForGapEnd(TimeTree tt,
			HashMap<Integer, ViewLine> startTimeToViewLine, HashMap<Integer, ViewLine> endTimeToViewLine) {
		
		//note that we must show lines that are half cut off by the stbox. Consider a line from point A at 10:00 and point B at 11:00.
		// At 10:30 - 10:35 there are no points. So if we display no line then it is blank. Now suppose we zoom out so that A and B
		// merge into one areapanel C. C will not have a separation in its timetrees for the gap between A and B. So we have to show
		// C. This makes it inconsistent when we view C compared to when we view nothing when zoomed in where A and B become visible.
		
		if(tt.getMinTimeSecs() < stBox.maxZ)
		{
			int endTimeSec = tt.getMaxTimeSecs();

			//the end of the tt equasl the start of the viewline
			ViewLine existingViewLine = startTimeToViewLine.get(endTimeSec);

			//co: even lines between neighboring points are visibile depending on the size
			//of the ap dot.
//			AreaPanel ap1 = ap();
//			AreaPanel ap2 = tt.getNextAp();
//			
//			//if were at the very end
//			if(ap2 == null)
//				return; //there is no line to draw
//			
//			if(isLineTooShortToDraw(ap1, ap2))
//			{
//				if(existingViewLine != null)
//				{
//					startTimeToViewLine.remove(existingViewLine.startTimeSec);
//					endTimeToViewLine.remove(existingViewLine.endTimeSec);
//				}
//				return;
//			}
			
			if(existingViewLine == null)
			{
				int nextApStartTime = tt.getNextApNextTtStartTime();
				
				//if we're at the absolute end there will be no next ap
				if(nextApStartTime == Integer.MIN_VALUE)
					return false;
				
				//we don't include lines half way off the stbox
				if(nextApStartTime > stBox.maxZ)
					return false;
				
				existingViewLine = new ViewLine(nextApStartTime, endTimeSec);
				startTimeToViewLine.put(existingViewLine.startTimeSec, existingViewLine);
				endTimeToViewLine.put(existingViewLine.endTimeSec, existingViewLine);
			}

			existingViewLine.startApId = apId; 
			existingViewLine.endApId = tt.getNextApId();
			
			if(existingViewLine.startApId == Integer.MIN_VALUE || existingViewLine.endApId == Integer.MIN_VALUE)
				throw new IllegalStateException("you know what you doing "+existingViewLine);
			
			return true;
		}
		
		return false;
	}

//	/**
//	 * Returns true if the line is within 1 unit for the current ap depth either 
//	 * vertically, horizontally or both.
//	 */
//	private boolean isLineTooShortToDraw(AreaPanel ap1, AreaPanel ap2) {
//		if(1==1) return false; 
//		int x1 = ap1.getX();
//		int x2 = ap2.getX();
//		int y1 = ap1.getY();
//		int y2 = ap2.getY();
//		
//		if(y2 - y1 <= ap1.getWidth() && x2 - x1 <= ap1.getWidth())
//			return true;
//		
//		return false;
//	}

	/**
	 * This calculates a really cheap estimate of distance in meters
	 * between the center of the aps. It is only used to choose a 
	 * proper size of the view node, so we really don't care much.
	 * 
	 * @return distance in area panel units
	 */
	private int estimateDist(AreaPanel ap1, AreaPanel ap2) {
		int yDist = Math.abs(ap1.getCenterY() - ap2.getCenterY());
		int xDist = Math.abs(ap1.getCenterX() - ap2.getCenterX());
		
		if(xDist < (yDist >> 1) || yDist < (xDist >>1))
			return xDist + yDist;
		
		return (int) ((xDist + yDist) * 7l /10) ;
	}

    // meaningfully means that it decreased or moved in a way that would affect
    // whether the area panel is on
    // if the area panels min and max times were on Jun 22, 1991 and the range decreased
    // from Jun 21 - Jun 29 to Jun 21 - Jun 28, that would NOT be meaningful.
    // NOTE, As long as the resulting localStBox isn't internal, we won't be
    // doing a complex time search, and this pre checking won't result
    // in much speed up, so these only check internal conditions
    
    public boolean timeDecreasedMeaningfully(AreaPanelSpaceTimeBox newLocalStBox,
    		AreaPanel ap) {
            if((newLocalStBox.minZ <= stBox.minZ 
            		|| newLocalStBox.minZ <= ap.getStartTimeSec())
            		&& (newLocalStBox.maxZ >= stBox.maxZ
            			|| newLocalStBox.maxZ >= ap.getEndTimeSec()) )
                    return false;
            
            //PERF: we could keep track of time tree min and max internally from last match
            //in the status
            return true;
    }

    public boolean timeIncreasedMeaningfully(AreaPanelSpaceTimeBox newLocalStBox, AreaPanel ap) {
    	//note if the ap is the head ap and there are no points, it will exist withouth a timetree
        if(ap.getTimeTreeFk() != Integer.MIN_VALUE && ( newLocalStBox.minZ < stBox.minZ && stBox.minZ > ap.getStartTimeSec() ||
        		newLocalStBox.maxZ > stBox.maxZ && stBox.maxZ < ap.getEndTimeSec())
        		)
                return true;
        return false;
}

    public boolean outsideOfXY(AreaPanelSpaceTimeBox newLocalStBox, AreaPanel ap) {
        // if we are wrapping 0 degrees longitude
        if (newLocalStBox.maxX < newLocalStBox.minX) {
                return (ap.getMaxX() < newLocalStBox.minX && ap.getX() > newLocalStBox.maxX)
                                || ap.getMaxY() < newLocalStBox.minY
                                || ap.getY() > newLocalStBox.maxY;
        }

        return ap.getMaxX() < newLocalStBox.minX
                        || ap.getMaxY() < newLocalStBox.minY
                        || ap.getX() > newLocalStBox.maxX
                        || ap.getY() > newLocalStBox.maxY;
}

	public void clearLineCalcs() {
		largestTimeTreeLengthForUncreatedLines = Integer.MAX_VALUE;
	}


}
