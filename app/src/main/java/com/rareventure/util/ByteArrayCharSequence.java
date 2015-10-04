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

public class ByteArrayCharSequence implements CharSequence {
	private byte [] data;
	private int start, end;
	
	
	
	public ByteArrayCharSequence(byte[] data, int start, int end) {
		super();
		this.data = data;
		this.start = start;
		this.end = end;
	}

	public ByteArrayCharSequence(byte[] data2) {
		this.data = data;
		this.end = data.length;
	}

	@Override
	public char charAt(int index) {
		return (char) data[index+start];
	}

	@Override
	public int length() {
		return end - start;
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return new ByteArrayCharSequence(data, this.start + start, this.start + end);
	}

}
