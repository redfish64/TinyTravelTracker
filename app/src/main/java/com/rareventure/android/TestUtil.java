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
package com.rareventure.android;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;

import android.util.Log;

/**
 * Testing and debugging utilities
 */
public class TestUtil {
	//TODO 3 this is a big hack. It used to write in a binary format, but that's silly because
	//we turned it off for the most part, so we are converting back to text. So there is a lot
	// of weird code for binary stuff here that isn't used
	private static final String NAME_FORMAT = "%8d Mode %2d: %-30s ";
	private static String tag = "GpsTrailerTestUtil";
	private static WriteConstants mode;
	
	private static int count = 0;
	
	public static HashSet<WriteConstants> modesToSuppress = new HashSet<WriteConstants>();
	
	private static long startTime = System.currentTimeMillis();
	
	public static enum Type {MODE,BYTE, DOUBLE, FLOAT, LONG, TIME, BOOLEAN, DATA, ENUM, INT, STRING};

	public static void setTag(String tag)
	{
		TestUtil.tag = tag;
	}
	
	public static void writeMode(DataOutputStream os,
			WriteConstants writeConstants) throws IOException {
		if(os != null) {
			os.writeChars("Thread "+Thread.currentThread()+" Mode "+writeConstants+"\n");
		}
		TestUtil.mode = writeConstants;
		if(!modesToSuppress.contains(writeConstants))
			Log.w(tag, "Thread "+Thread.currentThread()+" Mode "+writeConstants);
	}

	public static void writeDouble(String name, DataOutputStream os, double value) throws IOException {
		log(os, name, "%20.10f", value);
	}

	public static void writeFloat(String name, DataOutputStream os, float value) throws IOException {
		log(os, name, "%15.7f", value);
	}

	
	private static void log(DataOutputStream os, String name, String format, Object ... values) throws IOException {
		if(os != null)
			os.writeChars(String.format(NAME_FORMAT, ++count, mode.ordinal(), name) + String.format(format, values)+"\n");
		if(!modesToSuppress.contains(mode))
			Log.w(tag, String.format(NAME_FORMAT, ++count, mode.ordinal(), name) + String.format(format, values));
		if(os != null)
			os.flush();
	}

	public static void writeData(String name, DataOutputStream os, byte[] data, int offset, int length) throws IOException {
		log(os, name, "%10d", length);
	}
	
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	public static void writeTime(String name, DataOutputStream os, long value) throws IOException {
		String date;
		synchronized(sdf)
		{
			date = sdf.format(new Date(value));
		}
		
		log(os, name, "%10d %-40s", value - startTime, date);
	}

	public static void writeByte(String name, DataOutputStream os, byte val) throws IOException {
		log(os, name, "%3d", val);
	}

	public static void writeBoolean(String name, DataOutputStream os,
			boolean val) throws IOException {
		log(os, name, val ? "t" : "f");
	}

	public static void writeInt(String name, DataOutputStream os, int val) throws IOException {
		log(os, name, "%10d", val);
	}

	public static void writeLong(String name, DataOutputStream os, long val) throws IOException {
		log(os, name, "%20d", val);
	}

	public static void writeEnum(String name, DataOutputStream os, Enum val) throws IOException {
		log(os, name, "%-10s", val);
	}

	public static void writeException(DataOutputStream os,Exception e) throws IOException {
		synchronized (TestUtil.class)
		{
			writeString("Exception Name", os, e.toString());
			
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			
			writeString("Exception Stack Trace", os, sw.toString());
			writeThreadName(os);
		}
	}
	
	public static void writeThreadName(DataOutputStream os) throws IOException
	{
		writeString("Thread",os, Thread.currentThread().getName());
	}

	public static void writeString(String name, DataOutputStream os, String string) throws IOException {
		log(os, name, "%10d", string.length());
	}



}
