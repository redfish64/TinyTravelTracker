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

import java.io.IOException;
import java.io.InputStream;

public class ByteCounterInputStream extends InputStream{
	private InputStream r;
	public long counter;

	public ByteCounterInputStream(InputStream r)
	{
		this.r = r;
	}

	@Override
	public int available() throws IOException {
		return r.available();
	}

	@Override
	public void close() throws IOException {
		r.close();
	}

	@Override
	public int read() throws IOException {
		int c = r.read();
		if(c != -1)
			counter++;
		
		return c;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		int c = r.read(buffer, offset, length);
		
		if(c != -1)
			counter += c;
		
		return c;
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return read(buffer, 0, buffer.length);
	}

	@Override
	public long skip(long byteCount) throws IOException {
		long c = r.skip(byteCount);
		
		counter += c;
		
		return c;
	}
	

	
}
