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

import com.rareventure.android.Util;
import com.rareventure.util.MultiValueHashMap;
import com.rareventure.util.ReadWriteThreadManager;

/**
 * A timmy table is a special database-ish flat file. It's data must follow the
 * following rules
 * <ul>
 * <li>Records must be a fixed size
 * <li>Access to updates is not guaranteed to be before or after the commit
 * when accessing at the same time as a commit (in a different thread)
 * <li>No deletes.
 * <li>Transactions must be done with inserts in sequential order. 
 * <li>Only one thread is allowed to manage a transaction, including performing
 *   inserts and updates and commiting or rolling it back
 * <li>Have fun!
 * <ul>
 * Because of the above its not meant for inter process usage if writes are being
 * made. 
 * <p>
 * Each table has a table file with a header containing the max row id.
 * When a transaction is done, inserts are placed after the max row id, and updates
 * are made initially to a rollforward journal file, which also includes the new 
 * max row id
 * <p>
 * Then, the rollforward journal is run, the database is updated to the max id, and
 * the rollfoward is then deleted.
 * <p>
 * In the result of a crash while the inserts are being made, they'll just be ignored.
 * If a crash occurs when creating the update log, it will be ignored (we move it 
 * from "rollfoward.tmp" to "rollfoward.live" or something when done)
 * If a crash occurs when rolling the live rollforward log, we just reroll it the
 * next time we start. 
 * <p>
 * A database links up all timmy tables together, so that a rollforward will only 
 * occur if they all have live rollforward journals. It's unbreakable!
 */
public class TimmyDatabase {
	private static final int MASTER_TABLE_NAME_SIZE = 256;
	private static final int MASTER_TABLE_VALUE_SIZE = 1024 - MASTER_TABLE_NAME_SIZE;
	private static final String VERSION_KEYWORD = /* ttt_installer:obfuscate_str */"_tt_version";
	private PropertyTimmyTable masterTable;
	private MultiValueHashMap<String, String> propertyMap;
	private ArrayList<ITimmyTable> tables;
	private boolean isOpen;
	private boolean inTransaction;
	private boolean transactionSuccessful;
	
	public boolean isCancelOpen;

	public TimmyDatabase(String dbFilename) throws SyncFailedException, IOException
	{
		masterTable = new PropertyTimmyTable(dbFilename, MASTER_TABLE_NAME_SIZE, MASTER_TABLE_VALUE_SIZE, this);
		
		tables = new ArrayList<ITimmyTable>();
		
		tables.add(masterTable);

	}
	
	private void initProperties() throws IOException {
		//if a fresh database
		if(propertyMap.get(VERSION_KEYWORD) == null)
		{
			//setup initial properties
			propertyMap.put(VERSION_KEYWORD, "0");
			
			//save them
			masterTable.beginTransaction();
			masterTable.writeProperties(propertyMap);
			masterTable.commitTransactionStage1();
			masterTable.commitTransactionStage2(null);
			masterTable.commitTransactionStage3();
		}
	}

	public int getVersion()
	{
		return Integer.parseInt(propertyMap.getFirst(VERSION_KEYWORD));
	}

	/**
	 * Marks the database as corrupt. Useful if something has gone horribly wrong and
	 * the application can recover somehow.
	 */
	public void setCorrupt()
	{
		for(ITimmyTable tt: this.tables) {
			tt.setTableCorrupt();
		}
	}
	public boolean isCorrupt()
	{
//		if(2==2) return false; //xODO 2 hack!!
		
		for(ITimmyTable tt: this.tables)
		{
			if(tt.isTableCorrupt())
				return true;
		}
		
		return false;
	}
	
	/**
	 * This or addRollBackTimmyTable must be called for all tables in database before open()
	 * @param filename
	 * @param recordSize
	 * @return
	 * @throws SyncFailedException
	 * @throws IOException
	 */
	public TimmyTable addTimmyTable(String filename, int recordSize)
	throws SyncFailedException, IOException {
		if(isOpen)
			throw new IllegalStateException("table can't be added after database is open");
		
		TimmyTable tt = new TimmyTable(filename, recordSize, this);
		
		tables.add(tt);
		
		return tt;
	}

	public RollBackTimmyTable addRollBackTimmyTable(String filename, int recordSize)
	throws SyncFailedException, IOException {
		if(isOpen)
			throw new IllegalStateException("table can't be added after database is open");
		
		RollBackTimmyTable tt = new RollBackTimmyTable(filename, recordSize, this);
		
		tables.add(tt);
		
		return tt;
	}

	/**
	 * Must be called before interacting with the database and
	 * after all addTables() are done
	 * 
	 * May be canceled by calling cancelOpen() (in another thread)
	 * 
	 * @return true if opened successfully or false if canceled
	 * @throws IOException 
	 */
	public boolean open() throws IOException
	{
		if(isCancelOpen)
			return false;
		
		//we don't know if we crashed or not so we have to cleanup
		
		boolean inStage2 = needsRollforward();

		if(inStage2)
		{
			for(ITimmyTable tt: tables)
			{
				if(!tt.commitTransactionStage2(null))
					return false;
			}
			for(ITimmyTable tt: tables)
				tt.commitTransactionStage3();
		}
		else
			for(ITimmyTable tt: tables)
			{
				if(!tt.rollbackTransaction())
					return false;
			}
		
		isOpen = true;
		
		this.propertyMap = masterTable.readProperties();
		
		initProperties();

		return true;
	}
	
	/**
	 * Will cause the current open call (if any) running in another thread to cancel within a few
	 * seconds (hopefully). Any future
	 * open() calls will cancel immediately. Finishes immediately. Call resetCancelOpen() to
	 * allow open() calls to work again .
	 */
	public void cancelOpen()
	{
		isCancelOpen = true;
	}
	
	/**
	 * Once this method is called, all opens will work again.
	 */
	public void resetCancelOpen()
	{
		isCancelOpen = false;
	}
	
	
	/**
	 * 
	 * @return true if the database may take awhile to open. Should be called before open
	 */
	public boolean needsProcessingTime()
	{
		boolean needsRollforward = needsRollforward();
		
		for(ITimmyTable tt : tables)
		{
			if(tt.needsProcessingTime(needsRollforward))
			{
				return true;
			}
		}

		return false;
	}

	private boolean needsRollforward() {
		boolean inStage2 = true;
		for(ITimmyTable tt : tables)
		{
			if(!tt.inStage2())
			{
				inStage2 = false;
				break;
			}
		}

		return inStage2;
	}

	//threadsafe - only one thread allowed to touch a transaction
	/**
	 * Begins a transaction. Only one thread is allowed to begin a
	 * transaction or do any update or insert on a timmy table 
	 * at a time.
	 */
	public void beginTransaction() throws IOException
	{
		if(!isOpen)
			throw new IllegalStateException("database must be open before beginning a new transaction");
		if(inTransaction)
		{
			throw new IllegalStateException("only one transaction can exist at a time");
		}
		
		inTransaction = true;
		transactionSuccessful = false;
		for(ITimmyTable tt : tables)
		{
			tt.beginTransaction();
		}
	}
	
	//threadsafe - only one thread allowed to touch a transaction
	public void setTransactionSuccessful() throws IOException {
		transactionSuccessful = true;
	}

	public void endTransaction() throws IOException {
		endTransaction(null);
	}
	
	//threadsafe - only one thread allowed to touch a transaction
	public void endTransaction(ReadWriteThreadManager rwtm) throws IOException {
		if(transactionSuccessful)
		{
			for(ITimmyTable tt : tables)
			{
				tt.commitTransactionStage1();
			}
			
			for(ITimmyTable tt : tables)
			{
				tt.commitTransactionStage2(rwtm);
			}

			for(ITimmyTable tt: tables)
				tt.commitTransactionStage3();
		}
		else
			for(ITimmyTable tt : tables)
			{
				tt.rollbackTransaction();
			}
		
		inTransaction = false;
		
		//this is done because the memory maps have no way to release the 
		//mapping, except by letting the garbage collector get them
		System.gc();
	}

	/**
	 * Deletes all database files. Database must be already closed
	 */
	public void deleteDatabase() throws IOException {
		if(isOpen)
			throw new IllegalStateException("db must be already closed");
		for(ITimmyTable tt: tables)
			tt.deleteTableFiles();
	}

	public void close() throws IOException {
		for(ITimmyTable tt: tables)
			tt.close();
		isOpen = false;
	}

	public void setProperty(String name, Object value) {
		propertyMap.remove(name);
		propertyMap.put(name, String.valueOf(value));
	}

	public void saveProperties() throws IOException {
		masterTable.writeProperties(propertyMap);
	}

	public boolean inTransaction() {
		return inTransaction;
	}

	/**
	 * Returns the first value of a name or null
	 * if it doesn't exist
	 */
	public String getProperty(String name) {
		return propertyMap.getFirst(name);
	}

	public boolean isOpen() {
		return isOpen;
	}

	public TimmyTable getTable(String filename) {
		for(ITimmyTable t : tables)
		{
			if(t.getFilename().equals(filename))
				return (TimmyTable)t;
		}
		return null;
	}

	public RollBackTimmyTable getRollBackTable(String filename) {
		for(ITimmyTable t : tables)
		{
			if(t.getFilename().equals(filename))
				return (RollBackTimmyTable)t;
		}
		return null;
	}

	public int getIntProperty(String name, int defaultValue) {
		return Util.parseIntIfPresent(getProperty(name), defaultValue);
	}

	public boolean isNew() {
		return masterTable.isNew();
	}


}
