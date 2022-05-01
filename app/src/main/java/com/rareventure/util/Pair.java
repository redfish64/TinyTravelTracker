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


/**
 * A Pair of two objects. Two pairs containing identical objects will be
 * considered identical using equals and have identical hashcodes.
 */
public class Pair<V1,V2> {
	public V1 o1;
	public V2 o2;

	public Pair(V1 o1, V2 o2) {
		this.o1 = o1;
		this.o2 = o2;
	}

	public Pair() {
	}

	public int hashCode() {
		if(o1 == null)
			return o2.hashCode();
		if(o2 == null)
			return o1.hashCode();
		
		// use both objects to determine a hashcode.
		return o1.hashCode() ^ o2.hashCode();
	}

	public boolean equals(Object o) {
		if (!(o instanceof Pair))
			return false;

		Pair p = (Pair) o;

		return (o1 == null && p.o1 == null || p.o1.equals(o1)) && (o2 == null && p.o2 == null || p.o2.equals(o2));
	}

	public String toString()
	{
		return "Pair("+o1.toString()+","+o2.toString()+")";
	}
}
