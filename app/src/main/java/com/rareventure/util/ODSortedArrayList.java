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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/*
 * "order of" be damned sorted array. Meant for small simple cases. Can add multiple objects that using the 
 * comparator are the same, which a tree set won't allow. And a treeset is heavyweight for small lists
 * (array of 64!)
 */
//PERF: could implement contains(), indexOf() better
public class ODSortedArrayList<T> extends ArrayList<T>
{
	private Comparator<T> comp;
	
	public ODSortedArrayList(Comparator<T> comp)
	{
		this.comp = comp;
	}
	
	public T first()
	{
		return get(0);
	}

	public T last()
	{
		return get(size()-1);
	}

	@Override
	public void add(int location, T object) {
		throw new IllegalStateException("Don't call this, we are sorting everything");
	}

	@Override
	public boolean add(T object) {
		int index = Collections.binarySearch(this, object, comp);
		if(index < 0) index = -index -1;
		
		super.add(index, object);
		
		return true;
	}

	@Override
	public boolean addAll(int location, Collection<? extends T> collection) {
		throw new IllegalStateException("Don't call this, we are sorting everything");
	}

	@Override
	public boolean addAll(Collection<? extends T> collection) {
		super.addAll(collection);
		Collections.sort(this, comp);
		
		return true;
	}

}
