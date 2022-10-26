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
package com.rareventure.android;

import java.util.Arrays;

/**
 * A int array where the biggest elements are last.
 * If it gets too big, the beginning elements are dropped off,
 * so only the highest ones remain.
 * The size is not kept track of. Instead, all data
 * is initialized to 0
 *
 */
public class SortedBestOfIntArray {
	public int[] data;

	public SortedBestOfIntArray(int size)
	{
		data = new int[size];
		
		clear();
	}
	
	public void clear()
	{
		for(int i = 0; i < data.length; i++)
		{
			data[i] = 0;
		}
		
	}
	
	
	public void add(int item)
	{
		int index = Arrays.binarySearch(data, item);
		
		if(index < 0)
		{
			//index = -(insertion point) - 1
			//insertion point = -index -1
			index = -index - 1;
		}
		
		//if it's off the lowest edge
		if(index == 0)
			return; //ignore it;
		
		//kill the smallest entry and make space
		System.arraycopy(data, 1, data, 0, index-1);
		
		data[index-1] = item;
	}
	
}
