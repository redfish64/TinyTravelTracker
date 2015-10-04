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
package com.rareventure.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MultiDimTree<T extends MultiDimTree.MultiDimLeaf> {
	public long min, max;
	
	//either a tree or a value
	public MultiDimTree<T> left, right;
	
	public MultiDimTree<T> nextDimTree;
	
	public T value;
	
	
	public MultiDimTree(long min, long max, MultiDimTree<T> left,
			MultiDimTree<T> right) {
		super();
		this.min = min;
		this.max = max;
		this.left = left;
		this.right = right;
	}

	
	public MultiDimTree(T value) {
		super();
		this.value = value;
	}


	public interface MultiDimLeaf
	{
		public long getDim(int dim);
	}
	
	private static class DimensionComparator implements Comparator<MultiDimLeaf>
	{
		int dim;
		
		
		
		public DimensionComparator(int dim) {
			super();
			this.dim = dim;
		}



		@Override
		public int compare(MultiDimLeaf lhs, MultiDimLeaf rhs) {
			long res = lhs.getDim(dim) - rhs.getDim(dim);
			
			if(res < 0)
				return - 1;
			else if (res == 0)
				return 0;
			return 1;
		}
	}
	
	public static <R extends MultiDimLeaf> MultiDimTree<R> createTree(R [] items,
			int totalDimensions)
	{
		return createTree(items,0, items.length,new DimensionComparator(0),
				0, totalDimensions);
	}
	
	private static <R extends MultiDimLeaf> MultiDimTree<R> createTree(R [] items, int start,
			int end, DimensionComparator dc, int dim, int totalDimensions)
	{	
		dc.dim = dim;
		Arrays.sort(items, start, end, dc);
		
		//if there is only one item left
		if(end == start+1)
		{
			return new MultiDimTree<R>(items[start]);
		}
		
		//otherwise there is more than one
		int mid = (start+end) >> 1;

		MultiDimTree<R> tree = new MultiDimTree<R>(items[start].getDim(dim), items[end].getDim(dim),
			createTree(items,start,mid, dc, dim, totalDimensions),
			createTree(items,mid+1,end, dc, dim, totalDimensions)
			);
				
		
		if(dim < totalDimensions-1)
		{
			//note that we are creating the next dimension after *all* the tree has been created
			//in the current dimension. Also, we are creating trees for the next dimension
			//leaf first. This is done so that the order of the items are preserved for the
			//current dimension
			tree.nextDimTree = createTree(items,start, end, dc, dim+1, totalDimensions);
		}
		
		return tree;
	}	
}
