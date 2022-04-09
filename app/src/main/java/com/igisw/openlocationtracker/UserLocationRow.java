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
package com.igisw.openlocationtracker;

import com.rareventure.android.database.CachableRow;
import com.igisw.openlocationtracker.Cache;
import com.igisw.openlocationtracker.DbDatastoreAccessor;
import com.igisw.openlocationtracker.TableInfo;
import com.igisw.openlocationtracker.EncryptedRow;
import com.igisw.openlocationtracker.GTG;

public class UserLocationRow extends EncryptedRow
{
	public static final Column LATM = new Column("LATM",Integer.class);
	public static final Column LONM = new Column("LONM",Integer.class);
	public static final Column CREATED_ON = new Column("CREATED_ON",Long.class);
	public static final Column NAME = new Column("NAME",String.class);
	
	public static final Column [] COLUMNS = new Column [] {
		LATM, LONM, CREATED_ON, NAME };
	
	/**
	 * size of data without the user name on the end, which can be any length
	 */
	public static final int DATA_LENGTH_WITHOUT_STRING = 
		EncryptedRow.figurePosAndSizeForColumns(COLUMNS);
	
	public static final String TABLE_NAME = "user_location";
	
	public static final String INSERT_STATEMENT = DbDatastoreAccessor.createInsertStatement(TABLE_NAME);
	public static final String UPDATE_STATEMENT = DbDatastoreAccessor.createUpdateStatement(TABLE_NAME);
	public static final String DELETE_STATEMENT = DbDatastoreAccessor.createDeleteStatement(TABLE_NAME);
	
	public static final TableInfo TABLE_INFO = new TableInfo(TABLE_NAME, COLUMNS, INSERT_STATEMENT, UPDATE_STATEMENT,
			DELETE_STATEMENT);
	
	public UserLocationRow()
	{
		super();
	}
	public int getDataLength()
	{
		//TODO 4 fix this for working UserLocationRow
		return -1;
//		return DATA_LENGTH;
	}
	

	public void setData(int latm, int lonm, String name) {
		byte [] nameBytes = name.getBytes();

		//TODO 4 if you want this stuff, you have to fix this
//		if(data2 == null || data2.length < dataLength)
//			data2 = new byte[dataLength];
//		
		setInt(LATM.pos,latm);
		setInt(LONM.pos,lonm);
		System.arraycopy(nameBytes, 0, data2, NAME.pos, nameBytes.length);
	}
	
	public String getName()
	{
		return new String(data2,NAME.pos,this.data2.length/2-NAME.pos);
	}

	public int getLonm() {
		return getInt(this.LONM);
	}

	public int getLatm() {
		return getInt(this.LATM);
	}

	@Override
	public Cache getCache() {
		return (Cache)GTG.userLocationCache;
	}
}
