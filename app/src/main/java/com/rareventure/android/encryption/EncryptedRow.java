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
package com.rareventure.android.encryption;

import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.SecureRandom;

import com.rareventure.android.Util;
import com.rareventure.android.database.CachableRow;
import com.rareventure.android.database.Cache;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerCrypt;

public abstract class EncryptedRow extends CachableRow {

	/**
	 * Amout of extra data to add to the array when inserting an int (or other things, I guess)
	 */
	private static final int EXTRA_DATA_TO_ADD_AT_A_TIME = 8 * (Integer.SIZE>>3);

	public static final int EXTRA_BYTES_FOR_USER_DATA_KEY = (Integer.SIZE>>3);

	public byte[] data2;
	
	public static class Column
	{
		public static final int INTEGER_BYTE_SIZE = (Integer.SIZE >> 3);
		public int pos = Integer.MIN_VALUE;
		int size = -1;
		public Class type;
		public String name = "<unk>";
		
		public Column(Class<?> type) {
			this.type = type;
		}

		public Column(String name, Class<?> type) {
			this.name = name;
			this.type = type;
		}
		
		public Column(int size)
		{
			this.size = size;
		}
		
		public Column(String name, int size)
		{
			this.name = name;
			this.size = size;
		}

		/**
		 * This is for debugging
		 */
		public String getValue(byte[] data) {
			if(type == null)
			{
				return Util.toHex(data,pos,size);
			}
			if(type == Integer.class)
				return String.valueOf(Util.byteArrayToInt(data, pos));
			if(type == Long.class)
				return String.valueOf(Util.byteArrayToLong(data, pos));
			if(type == Float.class)
				return String.valueOf(Float.intBitsToFloat(Util.byteArrayToInt(data, pos)));
			if(type == Double.class)
				return String.valueOf(Double.longBitsToDouble(Util.byteArrayToLong(data, pos)));
			if(type == Byte.class)
				return Util.toHex(data, pos, 1);
			if(type == String.class)
				return Util.toHex(data, pos, 99999); //TODO 3: hack... assumes String column is always the last
			return null; 
		}
	}
	
	public static int figurePosAndSizeForColumns(Column [] columns)
	{
		int totalSize = 0;
		
		for(Column c : columns)
		{
			c.pos = totalSize;
			
			//if size wasn't already determined
			if(c.size == -1)
			{
				if(String.class.isAssignableFrom(c.type))
				{
					c.size = 0;
				}
				else
				{
					Field f;
					try {
						f = c.type.getField("SIZE");
						c.size = (int) Math.ceil(((Integer) f.get(null)) / 8f);
					} catch (Exception e) {
						throw new IllegalStateException(e);
					} 
				}
			}
			totalSize += c.size;
		}
		
		return totalSize; //we want size in bytes
	}
	
	
	public EncryptedRow()
	{
		this.id = -1;
	}

	/**
	 * Copies the data from other row to this row
	 */
	public void copyRow2(EncryptedRow row) {
		System.arraycopy(row.data2, 0, data2, 0 , row.data2.length);
		this.id = -1;
	}
	
	protected void clearFk(int pos)
	{
		setInt(pos, Integer.MIN_VALUE);
	}
	

	/**
	 * Returns the actual length of data. Note we can't just use data2.length because
	 *   the decryption utilities will demand more space then actual result, so the
	 *   length of data2 will be greater than the actual length when decrypting.
	 */
	protected abstract int getDataLength();
	

	protected int getInt(int pos) {
		return Util.byteArrayToInt(data2, pos);
	}

	protected int getInt(Column c)
	{
		return Util.byteArrayToInt(data2, c.pos);
	}

	protected byte getByte(Column c)
	{
		return data2[c.pos];
	}

	protected long getLong(Column c) {
		return Util.byteArrayToLong(data2, c.pos);
	}
	
	protected double getDouble(Column c) {
		return Double.longBitsToDouble(Util.byteArrayToLong(data2, c.pos));
	}
	
	protected float getFloat(Column c) {
		return Float.intBitsToFloat(Util.byteArrayToInt(data2, c.pos));
	}
	
	
	public final void decryptRow(int userDataKeyFk, byte [] encryptedData) {
		GpsTrailerCrypt c = GpsTrailerCrypt.instance(userDataKeyFk);
		
		int dataLength = c.getDecryptedSize(encryptedData.length);
		if(data2 == null || data2.length < dataLength)
			data2 = new byte [dataLength];
		
	   try {
			   c.crypt.decryptData(data2, encryptedData);
	   }
	   catch(Exception e)
	   {
			   Log.e(GTG.TAG,"Decryption failed for row "+this.id,e);
			   Log.e(GTG.TAG,"... row is "+this.toString(),e);
	   }
	}

	
	public final byte [] encryptRow(byte [] outEncryptedData) {
		
		GTG.crypt.crypt.encryptData(outEncryptedData, 0, data2, 0, getDataLength());
		
		return outEncryptedData;
	}
	
	public final byte[] encryptRowWithEncodedUserDataKey(byte [] outEncryptedData) {
		
		Util.intToByteArray2(GTG.crypt.userDataKeyId, outEncryptedData, 0);
		
		GTG.crypt.crypt.encryptData(outEncryptedData, (Integer.SIZE>>3), data2, 0, getDataLength());
		
		return outEncryptedData;
	}
	
	public final void decryptRowWithEncodedUserDatakey(byte [] encryptedDataWUD)
	{
		int userDataKeyFk = Util.byteArrayToInt(encryptedDataWUD, 0);
		
		GpsTrailerCrypt c = GpsTrailerCrypt.instance(userDataKeyFk);
		
		int dataLength = c.getDecryptedSize(encryptedDataWUD.length - (Integer.SIZE>>3));
		if(data2 == null)
			data2 = new byte [dataLength];
		
		c.crypt.decryptData(data2, encryptedDataWUD, (Integer.SIZE>>3), encryptedDataWUD.length - (Integer.SIZE>>3));
	}
	
	
	protected abstract Cache getCache();

	protected void setInt(int pos, int val) {
		if(getCache() != null)
			getCache().notifyRowUpdated((CachableRow) this);
		Util.intToByteArray2(val, data2, pos);
	}

	protected void setByte(int pos, byte val) {
		if(getCache() != null)
			getCache().notifyRowUpdated((CachableRow) this);
		data2[pos] = val;
	}

	protected void setFloat(int pos, float val) {
		if(getCache() != null)
			getCache().notifyRowUpdated((CachableRow) this);
		Util.floatToByteArray2(val, data2, pos);
	}

	protected void setLong(int pos, long val) {
		if(getCache() != null)
			getCache().notifyRowUpdated((CachableRow) this);
		Util.longToByteArray2(val, data2, pos);
	}

	protected void setString(int pos, String input, int maxLength) {
		byte [] in = input.getBytes();
		if(in.length >= maxLength)
			throw new IllegalStateException("Can't set string, too long, max is "+maxLength
					+", got "+input.length());
		if(maxLength > 255)
		{
			throw new IllegalStateException("max length can only be up to 255");
		}
		data2[pos] = (byte) in.length;
		
		System.arraycopy(in, 0, data2, pos+1, in.length);
	}
	
	public String getString(Column column)
	{
		int length = data2[column.pos];
		return new String(data2, column.pos+1, length);
	}


	
	protected void setDouble(int pos, double val) {
		if(getCache() != null)
			getCache().notifyRowUpdated((CachableRow) this);
		Util.doubleToByteArray2(val, data2, pos);
	}


	public boolean isSameAs(EncryptedRow ap) {
		if(ap.id != id)
			return false;
		
		for(int i = 0; i < ap.data2.length; i++)
		{
			if(data2[i] != ap.data2[i])
				return false;
		}
		
		return true;
	}

	public String toString()
	{
		StringBuffer sb = new StringBuffer(this.getClass().getSimpleName()+"(id="+id+")");
		return sb.toString();
	}

}
