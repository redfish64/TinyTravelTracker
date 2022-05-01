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

import com.rareventure.gps2.database.TAssert;

//import junit.framework.Assert;



public class DataBuffer {
	
	/**
	 * Index of data that we processed
	 */
	public int rawProcessIndex;
	
	/**
	 * Index of data that we read (fresh data)
	 */
	public int rawReadIndex;

	/**
	 * True if we haven't processed data fast enough and have run out of buffer
	 */
	public boolean processDataOverflow;
	
	private int buffSizeMinusOne;

	public long[] timeRead;
	
	/**
	 * 
	 * @param buffSize size of buffer, WARNING: must always be a power of 2!
	 */
	public DataBuffer(int buffSize)
	{
		//make sure that size is a power of 2
		boolean foundIt = false;
		
		for(int i = 1; i < (1 << 30);  i = i << 1)
		{
			if(buffSize == i)
			{
				foundIt = true;
				break;
			}
		}
		
		if(!foundIt)
			TAssert.fail("Bad size, "+buffSize+". Must be a power of 2"); 
		
		this.buffSizeMinusOne = buffSize - 1;
		timeRead = new long[buffSize];
	}

	/**
	 * Updates the read index, indicating that the current read index now has
	 * valid data to be processed
	 */
	public boolean updateReadIndex() {
		rawReadIndex = (rawReadIndex+1) & (buffSizeMinusOne);
		if(rawReadIndex == rawProcessIndex)
			processDataOverflow = true;
		
		return false;
	}

	/**
	 * Updates the process index, indicating that the current thread has been
	 * processed
	 */
	public void updateProcessIndex() {
		rawProcessIndex = (rawProcessIndex+1) & (buffSizeMinusOne);
	}


}
