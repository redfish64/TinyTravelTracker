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
package com.rareventure.util;

import java.util.AbstractList;
import java.util.Comparator;

import com.rareventure.gps2.database.TAssert;

public class CircularList<T> extends AbstractList<T> {
	private T[] items;
	private int maxSize;
	private int size = 0;
	private int start;

	public CircularList(int maxSize) {
		items = (T[]) new Object[maxSize];
		this.maxSize = maxSize;
	}

	@Override
	public T get(int index) {
		if (index >= size)
			throw new ArrayIndexOutOfBoundsException("Yo!");

		return items[(start + index) % maxSize];
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void clear()
	{
		start = 0;
		size = 0;
	}
	
	/**
	 * Adds an item to the queue, which if at max size will remove the last item and return it
	 * @param item
	 * @return
	 */
	public T addNoExpand(T item) {
		if (size < maxSize) {
			items[(start + size) % maxSize] = item;
			size++;
			return null;
		} else {
			T removedItem = items[start];
			items[start] = item;
			start = (start + 1) % maxSize;
			return removedItem;
		}

	}
	
	/**
	 * 
	 * @return first item in queue, removing it
	 */
	public T shift()
	{
		if(size == 0)
			throw new IllegalStateException("shift when zero size");
		
		T removedItem = items[start];
		start = (start + 1) % maxSize;
		size --;
		return removedItem;
	}
	
	@Override
	public boolean isEmpty()
	{
		return size == 0;
	}

	@Override
	public boolean add(T item) {
		if (size >= maxSize) {
			growForInsert(1);
		}
		items[(start + size) % maxSize] = item;
		size++;

		return true;
	}

    private void growForInsert(int required) {
        int increment = size / 2;
        if (required > increment) {
            increment = required;
        }
        if (increment < 12) {
            increment = 12;
        }
        T[] newArray = (T[]) new Object[size + increment];
        
        System.arraycopy(items, start, newArray, 0, items.length - start);
        
        if(start != 0)
            System.arraycopy(items, 0, newArray, items.length-start, start);

        start = 0;
        maxSize = newArray.length;
        items = newArray;
    }

	public int binarySearch(T key, Comparator<T> c) {
		int low = 0;
		int high = size - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			T midVal = items[(mid + start) % maxSize];
			int cmp = c.compare(midVal, key);

			if (cmp < 0)
				low = mid + 1;
			else if (cmp > 0)
				high = mid - 1;
			else
				return mid; // key found
		}
		return -(low + 1); // key not found.
	}

	/**
	 * pushes the last element in the circular list to the first
	 */
	public void moveLastToFirst() {
		if (maxSize == size) {
			start = (start - 1 + maxSize) % maxSize;
		} else {
			throw new IllegalStateException(
					"not implemented unless at max size");
		}
	}

	public T getLast() {
		if(size == 0)
			TAssert.fail("get last with size 0");
		return items[size == maxSize ? (start + maxSize - 1) % maxSize
				: size - 1];
	}
	
	public void replaceLast(T item)
	{
		items[size == maxSize ? (start + maxSize - 1) % maxSize
				: size - 1] = item;
	}

}