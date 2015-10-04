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
	private static final String NAME_FORMAT = "%8d Mode %2d: %-30s ";
	private static String tag = "TestUtil(set me!)";
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
			os.write(Type.MODE.ordinal());
			os.writeByte(writeConstants.ordinal());
		}
		TestUtil.mode = writeConstants;
		if(!modesToSuppress.contains(writeConstants))
			Log.w(tag, "Thread "+Thread.currentThread()+" Mode "+writeConstants);
	}

	public static void writeDouble(String name, DataOutputStream os, double value) throws IOException {
		if(os != null) {
			os.write(Type.DOUBLE.ordinal());
			os.writeDouble(value);
		}
		log(name, "%20.10f", value);
	}

	public static void writeFloat(String name, DataOutputStream os, float value) throws IOException {
		if(os != null) {
			os.write(Type.FLOAT.ordinal());
			os.writeFloat(value);
		}
		log(name, "%15.7f", value);
	}

	
	private static void log(String name, String format, Object ... values) {
		if(!modesToSuppress.contains(mode))
			Log.w(tag, String.format(NAME_FORMAT, ++count, mode.ordinal(), name) + String.format(format, values));
	}

	public static void writeData(String name, DataOutputStream os, byte[] data, int offset, int length) throws IOException {
		if(os != null) {
			os.write(Type.DATA.ordinal());
			os.writeInt(length);
			os.write(data,offset,length);
		}
		
		log(name, "%10d", length);
	}
	
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	public static void writeTime(String name, DataOutputStream os, long value) throws IOException {
		if(os != null) {
			os.write(Type.TIME.ordinal());
			os.writeLong(value);
		}
		
		String date;
		synchronized(sdf)
		{
			date = sdf.format(new Date(value));
		}
		
		log(name, "%10d %-40s", value - startTime, date);
	}

	public static void writeByte(String name, DataOutputStream os, byte val) throws IOException {
		if(os != null) {
			os.write(Type.BYTE.ordinal());
			os.writeByte(val);
		}
		log(name, "%3d", val);
	}

	public static void writeBoolean(String name, DataOutputStream os,
			boolean val) throws IOException {
		if(os != null) {
			os.write(Type.BOOLEAN.ordinal());
			os.writeByte(val ? 1 : 0);
		}
		log(name, val ? "t" : "f");
	}

	public static void writeInt(String name, DataOutputStream os, int val) throws IOException {
		if(os != null) {
			os.write(Type.INT.ordinal());
			os.writeInt(val);
		}
		log(name, "%10d", val);
	}

	public static void writeLong(String name, DataOutputStream os, long val) throws IOException {
		if(os != null) {
			os.write(Type.LONG.ordinal());
			os.writeLong(val);
		}
		log(name, "%20d", val);
	}

	public static void writeEnum(String name, DataOutputStream os, Enum val) throws IOException {
		if(os != null) {
			os.write(Type.ENUM.ordinal());
			os.writeInt(val.ordinal());
		}
		log(name, "%-10s", val);
	}

	public static void writeException(DataOutputStream os,Exception e) throws IOException {
		synchronized (TestUtil.class)
		{
			writeMode(os, WriteConstants.EXCEPTION);
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
		if(os != null) {
			os.write(Type.STRING.ordinal());
			os.writeInt(string.length());
			os.write(string.getBytes());
		}
		log(name, "%10d", string.length());
	}



}
