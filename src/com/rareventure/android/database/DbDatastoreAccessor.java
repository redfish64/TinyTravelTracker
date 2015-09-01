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
package com.rareventure.android.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.rareventure.android.DbUtil;
import com.rareventure.android.Util;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.util.Pair;

public class DbDatastoreAccessor<T extends EncryptedRow> implements Cache.DatastoreAccessor<T> {

	private TableInfo tableInfo;

	public static final String[] COLUMNS = new String [] { /* ttt_installer:obfuscate_str */"_ID", 
		/* ttt_installer:obfuscate_str */"USER_DATA_KEY_FK", 
		/* ttt_installer:obfuscate_str */"DATA" };

	private static final String INSERT_STMT = /* ttt_installer:obfuscate_str */"insert into ${table} (_ID, USER_DATA_KEY_FK, DATA) " +
	/* ttt_installer:obfuscate_str */"values (?,?,?)";
	
	private static final String UPDATE_STMT = /* ttt_installer:obfuscate_str */"update ${table} set USER_DATA_KEY_FK = ?, DATA = ? where _ID = ?";
	
	private byte [] retrieveRowData;

	private byte [] writeRowData;


	public DbDatastoreAccessor(TableInfo ti)
	{
		this.tableInfo = ti;
	}
	
	@Override
	public int getNextRowId() {
		return (int) DbUtil.runQuery(GTG.db, "select max(_id) from "+ tableInfo.tableName)+1;
	}

	@Override
	public synchronized void updateRow(T row) {
		if(row.id == -1)
			TAssert.fail();

		if(writeRowData == null)
		{
			writeRowData = new byte[GTG.crypt.crypt
									.getNumOutputBytesForEncryption(row.data2.length)];
		}
		
		byte [] encryptedData = row.encryptRow(writeRowData);
		SQLiteStatement s = DbUtil.createOrGetStatement(GTG.db, tableInfo.updateStatementStr);
		s.bindLong(1, GTG.crypt.userDataKeyId);
		s.bindBlob(2, encryptedData);
		s.bindLong(3, row.id);
//		doExtraUpdateBinds(s);
		s.execute();
	}

	/**
	 * Inserts a row. encryptedData and userDataKeyFk must already be set up.
	 * id will be set
	 *  
	 * @param db
	 */
	@Override
	public synchronized void insertRow(T row) {
		if(writeRowData == null)
		{
			writeRowData = new byte[GTG.crypt.crypt
									.getNumOutputBytesForEncryption(row.data2.length)];
		}
		
		byte [] encryptedData = row.encryptRow(writeRowData);
		SQLiteStatement s = DbUtil.createOrGetStatement(GTG.db, tableInfo.insertStatementStr);
		s.bindLong(1, row.id);
		s.bindLong(2, GTG.crypt.userDataKeyId);
		s.bindBlob(3, encryptedData);
//		doExtraInsertBinds(s);
		row.id = (int) s.executeInsert();
		
	}

	@Override
	public synchronized boolean getRow(T outRow, int id) {
		//believe it or not, there is no way to cache a query that uses a cursor (which is necessary if you want
		//to select multiple rows or even multiple columns)
		Cursor c = GTG.db.query(tableInfo.tableName, COLUMNS, "_id = ?", new String [] { String.valueOf(id) }, null, null, null);
		
		try {
			if(!c.moveToNext())
			{
				return false;
			}
			
			readRow(outRow, c);
		}
		finally 
		{
			DbUtil.closeCursors(c);
		}
		
		return true;
	}

	public void readRow(T row, Cursor c) {
		row.id = c.getInt(0);
		int userDataKeyFk = c.getInt(1);
		byte [] encryptedData = c.getBlob(2);
		
		row.decryptRow(userDataKeyFk, encryptedData);
	}
	


	public static String createInsertStatement(String tableName)
	{
		return Util.varReplace(INSERT_STMT, "table", tableName);
	}
	
	public static String createUpdateStatement(String tableName)
	{
		return Util.varReplace(UPDATE_STMT, "table", tableName);
	}
	
	public static String createDeleteStatement(String tableName) {
		return "delete from "+tableName+" where _id = ?;";
	}
	
	public void deleteRow(int id) {
		SQLiteStatement s = DbUtil.createOrGetStatement(GTG.db,
		tableInfo.deleteStatementStr);

		s.bindLong(1, id);
		s.execute();
	}

	public Cursor query(String where, String orderBy, String ... whereData) {
		return GTG.db.query(tableInfo.tableName, COLUMNS, where, whereData, null, null, orderBy);
	}

	public void deleteRow(Cursor c) {
		deleteRow(c.getInt(0));
	}

	@Override
	public void softUpdateRow(T row) {
		throw new IllegalStateException("Soft update not needed");
	}

	@Override
	public boolean needsSoftUpdate() {
		return false;
	}

	
}
