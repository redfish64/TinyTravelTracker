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
package com.rareventure.android;


import java.util.HashMap;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

public class DbUtil {

	private static HashMap<String, SQLiteStatement> stmts = new HashMap<String, SQLiteStatement>();
	
	private static SQLiteDatabase dbUtilDb;

	public static long runQuery(SQLiteDatabase db, String stmtStr, long ... l) {
		SQLiteStatement s = createOrGetStatement(db, stmtStr);
		for(int i = 0; i < l.length; i++)
		{
			s.bindLong(i+1, l[i]);
		}
		
		return s.simpleQueryForLong();
	}

	public static long runQueryWithStrings(SQLiteDatabase db, String stmtStr, String ... str) {
		SQLiteStatement s = createOrGetStatement(db, stmtStr);
		for(int i = 0; i < str.length; i++)
		{
			s.bindString(i+1, str[i]);
		}
		
		return s.simpleQueryForLong();
	}

	public static void closeCursors(Cursor ... cs) {
		for(Cursor c : cs)
		{
			if(c != null && !c.isClosed())
				c.close();
		}
	}

	//TODO 3: should we even do this????
	public static SQLiteStatement createOrGetStatement(SQLiteDatabase db, String stmtStr) {
		if(db != dbUtilDb)
		{
			dbUtilDb = db;
			synchronized(DbUtil.class)
			{
				stmts.clear();
			}
		}
		
		SQLiteStatement s;
		
		synchronized (DbUtil.class)
		{
			s = stmts.get(stmtStr);
		}
		
		if(s == null)
		{
			s = db.compileStatement(stmtStr);
			synchronized (DbUtil.class)
			{
				stmts.put(stmtStr, s);
			}
		}
		
		return s;
	}

	public static void clearStatements() {
		stmts.clear();
	}


}
