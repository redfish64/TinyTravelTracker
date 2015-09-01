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
package com.rareventure.android.database.timmy;


import java.io.IOException;
import java.io.SyncFailedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rareventure.util.MultiValueHashMap;

public class PropertyTimmyTable extends TimmyTable {

	private int nameSize;
	private int valueSize;

	public PropertyTimmyTable(String dbFilename, int nameSize,
			int valueSize, TimmyDatabase d) throws SyncFailedException, IOException {
		super(dbFilename, nameSize+valueSize, d);
		
		this.nameSize = nameSize;
		this.valueSize = valueSize;
		
		buf = new byte[super.getRecordSize()];
	}
	
	public MultiValueHashMap<String, String> readProperties()
	{
		MultiValueHashMap<String, String> map = new MultiValueHashMap<String, String>();
		
		for(int i = 0; i < getNextRowId(); i++)
		{
			readRecordIntoMap(i,map);
		}
		
		return map;
	}
	
	private static byte [] buf;
	
	private void readRecordIntoMap(int i, MultiValueHashMap<String, String> map) {
		
		getRecord(buf, i);
		
		String name = readField(buf, 0, nameSize);
		String value = readField(buf, nameSize, valueSize);
		
		//since there is no deletes, we just use an empty name to clear
		//out rows
		if(name.length() != 0)		
			map.put(name, value);
	}

	private String readField(byte[] field, int start, int length) {
		
		int count = 0;
		for(; count < length; count++)
			if(field[start+count] == 0)
				break;
		
		
		return new String(field, start, count);
	}

	/**
	 * Must be done within a transaction, writes out new properties set, clearing
	 * out any existing property set
	 * @param props
	 * @throws IOException 
	 */
	//PERF maybe add the ability to write individual properties
	public void writeProperties(MultiValueHashMap<String, String> props) throws IOException
	{
		int row = 0;
		
		byte[] writeBuf = new byte[super.getRecordSize()];
		
		for(Map.Entry<String, List<String>> e : props.entrySet())
		{
			for(String val : e.getValue())
			{
				writeMapEntryIntoRecord(e.getKey(), val, writeBuf);
				if(row < getNextRowId())
					updateRecord(row, writeBuf);
				else
					insertRecord(row, writeBuf);
				
				row++;
			}
		}
	}
	
	private void writeMapEntryIntoRecord(String name, String val, byte[] writeBuf) {
		writeField(writeBuf, name, 0, nameSize);
		writeField(writeBuf, val, nameSize, valueSize);
	}

	private void writeField(byte[] writeBuf, String val, int start, int length) {
		byte [] in = val.getBytes();
		if(in.length > length)
			throw new IllegalStateException("trying to write a val that is too long to a field, val "+val+", field length "+length+" start "+start+" this "+this);
		
		System.arraycopy(in, 0, writeBuf, start, in.length);
		//null terminate it
		Arrays.fill(writeBuf, start+in.length, start+length, (byte)0);
	}

}
