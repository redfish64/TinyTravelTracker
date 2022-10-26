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
package com.igisw.openlocationtracker;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

import com.rareventure.gps2.database.TAssert;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class AndroidPreferenceSet {
	/**
	 * Empty interface for AndroidPreferences...
	 * 
	 * Rules:
	 *   1. All fields must be public (and must be preferences)
	 */
	public static interface AndroidPreferences
	{
	}

	public synchronized void savePrefs(Context context)
	{
		saveSharedPrefs(context);
	}
	
//	//co: not sure about this... it's meant to support GpsTrailerCache and it's set of individual ints 
//	public void saveIndividualPrefsToDatabase(SQLiteDatabase db, AndroidPreferences prefObject, String ... names)
//	{
//		db.beginTransaction();
//		
//		try {
//			for(String name : names)
//			{
//				if(fieldToPrefInfo.get(createFieldFromJavaRep(prefObject, name)).isSharedPrefsField)
//				{
//					TAssert.fail("only non shared pref fields are supported");
//				}
//				
//				
//				String tableName = ANDROID_PREFS_TABLE;
//				
//				String fieldName = createFieldFromJavaRep(prefObject, name);
//			
//				SQLiteStatement deleteStmt = DbUtil.createOrGetStatement(db, "delete from "+tableName+" where name = ?");
//				
//				deleteStmt.bindString(1, fieldName);
//				deleteStmt.execute();
//				<
//				SQLiteStatement stmt = DbUtil.createOrGetStatement(db, "insert into "+tableName+" (name,value) values (?,?)");
//	
//				savePrefToDatabase(db, stmt, prefObject, fieldName);
//			}
//			
//			db.setTransactionSuccessful();
//		}
//		finally {
//			db.endTransaction();
//		}
//	}

	
	public abstract void writePrefs(Map<String, Object> res);
	
	public synchronized void saveSharedPrefs(Context context) {

		SharedPreferences sp = context.getSharedPreferences(GTG.SHARED_PREFS_NAME, 
				Context.MODE_PRIVATE);
		Editor editor = sp.edit();
		
		Map<String, Object> prefs = new HashMap<String, Object>();
		
		writePrefs(prefs);
		
		//set all the preferences
		for(Entry<String, Object> e : prefs.entrySet())
		{
			savePrefToSharedPrefs(editor, e.getKey(), e.getValue());
		}
		
		if(!editor.commit())
			TAssert.fail("failed storing to shared prefs");
		
	}
	
	private void savePrefToSharedPrefs(Editor editor, String fieldName, Object val)
	{
		if(val == null || val instanceof String)
			editor.putString(fieldName, (String)val);
		else if(val instanceof Enum)
			editor.putString(fieldName, ((Enum)val).toString());
		else if(val instanceof Boolean)
			editor.putBoolean(fieldName, (Boolean)val);
		else if(val instanceof Float)
			editor.putFloat(fieldName, (Float)val);
		else if(val instanceof Double)
			editor.putFloat(fieldName, (float)((Double)val).doubleValue());
		else if(val instanceof Integer)
			editor.putInt(fieldName, (Integer)val);
		else if(val instanceof Long)
			editor.putLong(fieldName, (Long)val);
		else if(Util.isByteArray(val.getClass()))
			editor.putString(fieldName, Util.toHex((byte [])val));
		else if(Util.isIntArray(val.getClass()))
			editor.putString(fieldName, Util.toIntList((int [])val));
		else
			TAssert.fail("What is "+val+"?");
	}		


	/**
	 * 
	 * @param db 
	 * @param tableName
	 * @param testRun if true, will not actually load the prefs, but make sure that they are settable..
	 *   used for doing a factory reset if somehow the prefs get out of sync 
	 */
	public void loadAndroidPreferences(Context context)
	{
		loadAndroidPreferencesFromSharedPrefs(context);
	}

	protected abstract void loadPreference(String name, String value);

	public void loadAndroidPreferencesFromSharedPrefs(Context context) {
		SharedPreferences sp = context.getSharedPreferences(GTG.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
		
		for(Entry<String, ?> e : sp.getAll().entrySet())
		{
			try {
				this.loadPreference(e.getKey(), String.valueOf(e.getValue()));
			}
			catch(RuntimeException e2)
			{
				Log.e(GTG.TAG, "Error loading shared pref: "+e.getKey()+", skipping",e2);
			}
		}
		
	}

	public void loadAndroidPreferencesFromMap(Activity activity,
			Map<String, Object> prefsMap) {
		for(Entry<String, ?> e : prefsMap.entrySet())
		{
			try {
				this.loadPreference(e.getKey(), String.valueOf(e.getValue()));
			}
			catch(RuntimeException e2)
			{
				Log.e(GTG.TAG, "Error loading shared pref: "+e.getKey()+", skipping",e2);
			}
		}
		
	}

}
