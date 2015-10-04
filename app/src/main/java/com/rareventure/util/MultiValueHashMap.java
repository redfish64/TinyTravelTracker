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

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * A HashMap that saves multiple values for several puts with the same key
 * 
 * @author Tim Engler
 */
public class MultiValueHashMap<K,V> {
	public static final LinkedList EMPTY_LIST = new LinkedList();

	private HashMap<K,List<V>> map = new HashMap<K,List<V>>();

	protected int valuesSize = 0;

	public MultiValueHashMap() {
	}

	public void clear() {
		map.clear();
 		valuesSize = 0;
    }
	
	/**
	 * Returns true if the hashmap contains the given value
	 */
	public boolean contains(Object value) {
		for (Iterator i = map.entrySet().iterator(); i.hasNext();) {
			List v = (List) i.next();

			for (Iterator i2 = v.iterator(); i2.hasNext();) {
				if (i2.next().equals(value))
					return true;
			}

		}

		return false;
	}

	/**
	 * Returns list of values for a key
	 */
	public List<V> get(K key) {
		List<V> values = map.get(key);

		if (values == null || values.size() == 0) // Could happen if value was
													// removed
			return null;

		return values;
	}
	
	/**
	 * Returns first value for a key, or null if there aren't any
	 */
	public V getFirst(K key) {
		List<V> values = get(key);
		
		if(values == null)
			return null;
		
		return values.get(0);
    }

	/**
	 * Returns a list of all values for a key. If key doesn't exist, returns an
	 * empty list.
	 */
	public List<V> gets(K key) {
		List<V> values = (List<V>) map.get(key);

		if (values == null) {
			return EMPTY_LIST;
		}

		return values;
	}

	public Set<K> keySet() {
		return map.keySet();
	}

	/**
	 * Puts a value for a key. If there is more then one value, each additional
	 * value will be added as another value for the key.
	 */
	public void put(K key, V value) {
		List<V> values = (List<V>) map.get(key);
		if (values == null) {
			values = new ArrayList<V>();
			map.put(key, values);
		}

		values.add(value);
		valuesSize++;
	}

	/**
	 * Removes the first value for a key. If there is only one value for a key,
	 * the key, value pair is removed.
	 */
	public V remove(K key, V value) {
		List<V> values = (List<V>) map.get(key);
		if (values == null)
			return null;

		if (values.remove(value)) {
			valuesSize--;
			if(values.size() == 0)
				map.remove(key); //remove the key, so it won't be present in the key set
			return value;
		}

		return null;
	}

	public int size() {
		return map.size();
	}

	/**
	 * @return all the values for the multi value hash map
	 */
	public Collection<V> values() {
		return new AbstractCollection<V>()
		{
			@Override
            public Iterator<V> iterator() {
				return new Iterator<V>()
				{
					
					private boolean gotNextValue;
					private boolean atEnd;
					private V next;
					
					private Iterator<List<V>> mapValuesIterator = map.values().iterator();
					private Iterator<V> subIterator = null;

					private void _getNextValue() {
						while((subIterator == null || !subIterator.hasNext()) && mapValuesIterator.hasNext())
							subIterator = mapValuesIterator.next().iterator();
						
						if(!subIterator.hasNext() && !mapValuesIterator.hasNext())
							atEnd = true;
						else
							next = subIterator.next();
						gotNextValue = true;
                    }

					public boolean hasNext() {
						if(gotNextValue)
							return !atEnd;
						
						_getNextValue();
						
						return !atEnd;
                    }

					public V next() {
						if(gotNextValue)
						{
							gotNextValue = false;
							return next;
						}

						_getNextValue();

						gotNextValue = false;
						return next;
                    }

					public void remove() {
	                    throw new IllegalStateException("You can't do that!");
                    }
				};	            
            }

			@Override
            public int size() {
				return valuesSize ;
            }
		};
    }

	public Set<Map.Entry<K,List<V>>> entrySet() {
		return map.entrySet();
	}

	/**
	 * Removes all values associated with key
	 */
	public void remove(K name) {
		map.remove(name);
		
	}

	
}
