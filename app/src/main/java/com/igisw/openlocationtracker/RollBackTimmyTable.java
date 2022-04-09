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


import android.util.SparseBooleanArray;

import com.rareventure.android.database.timmy.ITimmyTable;
import com.rareventure.util.ReadWriteThreadManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.SyncFailedException;
import java.util.Arrays;

/**
 * Like a normal timmy table, but uses a rollback journal instead of a roll forward journal
 * and has soft and hard commits. This helps when dealing with large database files and applying
 * lots of spread out changes during commit. What happens is that a soft commit is done many times and
 * a hard commit a few times. When a soft commit is done, all changes have been written, but not synced
 * to the file system, so that the rows can be removed from the cache (reading them will always return
 * the new row). When a hard commit is finally done, then everything is synced, but this can be done
 * rarely (a rollback will only go to the hard commit, though.
 */
public class RollBackTimmyTable implements ITimmyTable {

	private static final byte VERSION = 1;
	private static final long HEADER_SIZE = 64;
	private static final byte[] MAGIC = "timmy!".getBytes();
	private static final byte[] ROLLBACK_MAGIC = "TIMMY!".getBytes();
	private static final int NEXT_ROW_ID_POS = MAGIC.length + 1; // +1 for version
	private static final int RECORD_SIZE_POS = NEXT_ROW_ID_POS+(Integer.SIZE >> 3);
	private static final int IS_CORRUPTED_FIELD_POS = RECORD_SIZE_POS+(Integer.SIZE >> 3);
	private static final int SYNC_ID = -1;
	
	/**
	 * The number of rows that we saved in the soft commit rollback file
	 */
	private static final long SOFT_COMMIT_NUM_ROWS_INDEX = ROLLBACK_MAGIC.length +
		(Integer.SIZE >> 3); // this first int is for the number of rows commited to the db before we started the 
	public static final String ROLLBACK_EXTENSION = ".rb";
		//transaction
	private int recordSize;

	/**
	 * The size of the database before the transaction is started
	 */
	private int committedNextRowId;
	String filename;
	private int lastTransactionInsertId = Integer.MAX_VALUE;
	private OutputStream rollBackOut;
	private boolean inTransaction;
	private RandomAccessFile rwRaf;
	private FileOutputStream rollBackFileOut;
	private TimmyDatabase database;
	private boolean isTableCorrupt;
	private boolean isNew;
	
	private SparseBooleanArray softModeSavedRows = new SparseBooleanArray();
	
	byte [] tempRecordData;
	
	/**
	 * Whether we should be accepting updateRecordHard() or updateRecordSoft calls.
	 * Starts out as true, since otherwise when we rollforward after a crash, we'd try to close
	 * the soft write journal
	 */
	boolean hardWriteMode = true;
	

	//threadsafe (obviously)
	/**
	 * Created by TimmmyDatabase
	 */
	protected RollBackTimmyTable(String filename, int recordSize, TimmyDatabase d)
			throws SyncFailedException, IOException {
		this.filename = filename;
		
		this.database = d;
		reopenRaf(recordSize);
		
		tempRecordData = new byte[recordSize];
		
		//System.out.println("new end: "+this);
	
	}
	
	protected boolean isNew()
	{
		return isNew;
	}
	
	//all callers are thread safe
	private void readHeader(int expectedRecordSize) throws IOException {
		rwRaf.seek(0);
		byte[] magicBuffer = new byte[MAGIC.length];

		readFully(rwRaf, magicBuffer);
		
		if (!Arrays.equals(MAGIC, magicBuffer))
			throw new IOException("Bad magic for timmy table " + this
					+ ", got " + Arrays.toString(magicBuffer));

		byte version = rwRaf.readByte();
		if (version != VERSION)
			throw new IOException("Wrong version for timmy table " + this
					+ ", got " + version);
		committedNextRowId = rwRaf.readInt();
		this.recordSize = rwRaf.readInt();
		this.isTableCorrupt = rwRaf.readByte() != 0;
		if (expectedRecordSize != this.recordSize)
			throw new IOException("Wrong recordsize for timmy table " + this
					+ ", got " + this.recordSize + ", expected " + expectedRecordSize);
	}

	//all callers are thread safe
	private void createHeader(int recordSize) throws SyncFailedException,
			IOException {
		rwRaf.setLength(HEADER_SIZE);
		rwRaf.seek(0);
		rwRaf.write(MAGIC);
		rwRaf.write(VERSION);
		rwRaf.writeInt(0); // nextRowId;
		rwRaf.writeInt(recordSize);
		rwRaf.getFD().sync();
		
		this.recordSize = recordSize;
	}
	
	//all callers are thread safe
	public static int readFully(RandomAccessFile raf, byte [] buffer) throws IOException
	{
		return readFully(raf, buffer, 0, buffer.length);
	}

	//all callers are thread safe
	public static int readFully(RandomAccessFile raf, byte[] buffer, int offset, int length) throws IOException {
		int totalRead = 0;
		
		while (totalRead < length) {
			int numRead = raf.read(buffer, offset + totalRead, length - totalRead);
			if (numRead < 0) {
				break;
			}
			
			totalRead += numRead;
		}
		return totalRead;
	}
	
	//threadsafe
	public synchronized int getNextRowId()
	{
		if(!database.isOpen())
			throw new IllegalStateException("don't access the table before opening the database");
		if(inTransaction)
			return lastTransactionInsertId+1;
		//this changes whenever a commit is complete (with inserts)
		return committedNextRowId;
	}

	//threadsafe (never changes)
	public int getRecordSize() {
		return recordSize;
	}


	/**
	 * Both a soft write and a hard write must be done in order to update a record.
	 * All soft writes for a transaction must be done, then a soft commit and finally
	 * hard writes are performed. Hard writes must write to the same rows that the soft writes did.
	 * @param id
	 * @param record
	 * @throws IOException
	 */
	//thread safe because only one thread is allowed to update
	public void updateRecordSoft(int id) throws IOException {
		if(!database.isOpen())
			throw new IllegalStateException("don't access the table before opening the database");
		
		if(hardWriteMode)
			throw new IllegalStateException("soft write attempted during hard write mode");
		
		if(id > lastTransactionInsertId)
			throw new IllegalStateException("attempt to update a row past the last inserted row");
		
		//ignore soft updates past the soft record limit (updates to rows that have been inserted)
		//note, this is useful for multiple soft commits in a single transaction
		if(id >= committedNextRowId)
			return;
		
		//if we already saved the old value (we are updating the same row twice)
		if(softModeSavedRows.get(id, false))
			return;
		
		softModeSavedRows.put(id, true);
		
		synchronized(this)
		{
			getRecord(tempRecordData, id);
		}
		
		Util.writeInt(rollBackOut, id);
		rollBackOut.write(tempRecordData);
		
	}
	
	/**
	 * Goes back to writing soft updates after hard commit mode has already been
	 * entered. This will append to the rollback log
	 * @throws IOException
	 */
	public void revertToSoftCommitMode() throws IOException
	{
		if(!hardWriteMode)
			throw new IllegalStateException("Error, already in soft update");
		
		hardWriteMode = false;
		
		File f = getRollBackFile();

		rollBackOut = new BufferedOutputStream(rollBackFileOut = 
			new FileOutputStream(f, true));
	}

	/**
	 * A hard update can only occur during hard mode and must only be the ids from the soft updates,
	 * or an inserted record.
	 * @param id
	 * @param record
	 * @throws IOException
	 */
	public void updateRecordHard(int id, byte[] record) throws IOException {
		if(!database.isOpen())
			throw new IllegalStateException("don't access the table before opening the database");
		
		if(!hardWriteMode)
			throw new IllegalStateException("hard write attempted during soft write mode");
		
		if(id < committedNextRowId && !softModeSavedRows.get(id, false))
			throw new IllegalStateException("hard write attempted for row not soft written: "+id);

		synchronized(this)
		{
			rwRaf.seek(HEADER_SIZE + id * recordSize);
			rwRaf.write(record);
		}
	}

	//threadsafe since it doesn't touch main db file
	private void writeRollBackHeader(int numRows) throws IOException {
		rollBackOut.write(ROLLBACK_MAGIC);
		Util.writeInt(rollBackOut, numRows);
		
		//this is the number of soft committed rows
		Util.writeInt(rollBackOut, 0);
	}

	/**
	 * Within a transaction, all inserts must be done in sequential order.
	 * They may be done during any commit stage, hard or soft (note,
	 * the option to write during hard mode is needed by PropertyTimmyTable.writeProperties())
	 *
	 * @param id
	 * @param encryptRowWithEncodedUserDataKey
	 * @throws IOException 
	 */
	public void insertRecord(int id, byte[] record) throws IOException {
		if(!database.isOpen())
			throw new IllegalStateException("don't access the table before opening the database");

		if(id < committedNextRowId)
		{
			throw new IllegalStateException("Trying to insert a row below nextRowId: "+id+", this: "+this);
		}
		if(id != lastTransactionInsertId+1) {
			throw new IllegalStateException("Trying to insert a row that is not one above the last transaction id: "
					+lastTransactionInsertId +", id: "+id);
		}
		if(record.length != recordSize) {
			throw new IllegalStateException("Record is wrong size: "+record.length+", this: "+this);
		}
		
		synchronized (this) {
			rwRaf.seek(HEADER_SIZE + recordSize * id);
			rwRaf.write(record);
		}
		
		lastTransactionInsertId = id;
	}

	/**
	 * Only one thread is allowed to begin a transaction.
	 */
	//thread safety: only one thread allowed to do a transaction
	@Override
	public void beginTransaction() throws IOException {
		//System.out.println("beginTransction: "+this);
		if(inTransaction)
			throw new IllegalStateException("You can't start a transaction twice. this: "+this);
		inTransaction = true;
		
		lastTransactionInsertId = committedNextRowId-1;
	
		hardWriteMode = false;
		
		File f = getRollBackFile();

		rollBackOut = new BufferedOutputStream(rollBackFileOut = 
			new FileOutputStream(f, true));
		writeRollBackHeader(committedNextRowId);
				
		//System.out.println("beginTransction end: "+this);
	}

	//threadsafe - doesnt touch map
	private File getRollBackFile() {
		return getRollBackFile(filename);
	}

	private static File getRollBackFile(String filename) {
		return new File(filename+ROLLBACK_EXTENSION);
	}

	/**
	 * After this is called, all soft updates must be rerun as hard updates.
	 * At that point the memory associated with update may be removed.
	 * There may be no more inserts after this is called
	 * @throws SyncFailedException
	 * @throws IOException
	 */
	protected void softCommitTransaction() throws SyncFailedException, IOException
	{
		//System.out.println("commitTransactionStage1: "+this);
		//stage 1
		//flush and sync rollback file		
		
		rollBackOut.flush();
		rollBackFileOut.getFD().sync();
		
		if(softModeSavedRows.size() != 0)
		{
			//record the actualy number of rows in this commit block (at the beginning of it)
			RandomAccessFile rbRaf = new RandomAccessFile(getRollBackFile(), "rws");
			rbRaf.seek(SOFT_COMMIT_NUM_ROWS_INDEX);
			rbRaf.writeInt(softModeSavedRows.size());
			rbRaf.getFD().sync();
			rbRaf.close();
		}
		
		rollBackOut.close();

		rollBackOut = null; rollBackFileOut = null;
		
		hardWriteMode = true;
	
	}
		

	//threadsafe - only one thread allowed to touch a transaction
	/**
	 * Rolls back the last transaction. Also used when the db
	 * is reopened
	 * (the database tells us this)
	 * @throws IOException 
	 */
	@Override
	public boolean rollbackTransaction() throws IOException {
		//System.out.println("rollbackTransaction: "+this);
		
		if(inTransaction)
		{
			inTransaction = false;
			if(!hardWriteMode)
			{
				rollBackOut.close();
				finishTransaction();
				return true;
			}
		}
		
		//now we need to run the rollback file, if necessary
		File rollBackFile = getRollBackFile();
		
		//if there is no rollback file then no permenant change was done to the file
		if(!rollBackFile.exists() || rollBackFile.length() == 0)
			return true;
		
		//System.out.println("commitTransactionStage2: "+this);
		InputStream rfIn = new BufferedInputStream(new FileInputStream(rollBackFile));
		committedNextRowId = readRollBackHeader(rfIn);
		
		synchronized (this) 
		{
			rwRaf.seek(NEXT_ROW_ID_POS);
			rwRaf.writeInt(committedNextRowId);
			rwRaf.setLength(HEADER_SIZE + recordSize * committedNextRowId);
		}
		
		int numSoftCommitRecords = Util.readInt(rfIn);

		byte [] recordData = new byte[recordSize];
		byte [] idBytes = new byte[4];
		
		for(int softCommitRecordsCount = 0; softCommitRecordsCount < numSoftCommitRecords; softCommitRecordsCount++)
		{
			if(database.isCancelOpen)
				return false;
			
			//read next roll back entry id. if there are no more records then quit
			if(!readFullyForRollBack(rfIn, idBytes, true))
				break;
			
			int id = Util.byteArrayToInt(idBytes, 0);
			
			if(id > committedNextRowId)
			{
				setTableCorrupt();
				throw new IllegalStateException("rollback log attempted to write to a row not in committed set,"
						+" row id requested: "+id+", this: "+this);
			}
			
			//read row data
			readFullyForRollBack(rfIn, recordData, false);
			
			synchronized (this)
			{
				//write the record to the database
				rwRaf.seek(recordSize * id + HEADER_SIZE);
				rwRaf.write(recordData);
			}
			
		}
	
		//sync all our writes before we delete the rollback file
		rwRaf.getFD().sync();
		
		rfIn.close();
		
		getRollBackFile().delete();
		
		//System.out.println("rollbackTransaction end: "+this);
		
		return true;
	}

	private void finishTransaction()
	{
		rollBackFileOut = null;
		rollBackOut = null;
		inTransaction = false;
		hardWriteMode = false;
		softModeSavedRows.clear();
	}


	//thread safe - we synchronize all access to map
	/**
	 * This commits after hard updates are done. It will do a sync against the table data,
	 * but (TODO I think?)
	 * that we can sync while we are reading which will allow us to access the table regardless
	 * of whether we are commiting or no 
	 * @param rwtm 
	 */
	@Override
	public boolean commitTransactionStage2(ReadWriteThreadManager rwtm) throws IOException {
		if(!hardWriteMode)
		{
			rollBackOut.close();
			finishTransaction();
		}
		
		//here we finish updating the database, and sync the file
		synchronized (this)
		{
			//if any rows were inserted we have to reset the maps
			if(committedNextRowId != (int) ((rwRaf.length() - HEADER_SIZE) / recordSize))
			{
				committedNextRowId = (int) ((rwRaf.length() - HEADER_SIZE) / recordSize);
				
				//note that it's ok to write the number of rows before we
				// sync the data to the database only because we store the original value
				// in the rollback file
				rwRaf.seek(NEXT_ROW_ID_POS);
				rwRaf.writeInt(committedNextRowId);
				
			}
		}
		
		//this is supposed to sync to the file system
		//I hope we can do this while we read
		rwRaf.getFD().sync();
		
		return true;
	}
		
	@Override
	public boolean needsProcessingTime(boolean needsRollforward) {
		if (!needsRollforward)
		{
			File rollBackFile = getRollBackFile();
			
			if(rollBackFile.exists() && rollBackFile.length() >0)
				return true;
		}
		
		return false;
	}


	/**
	 * Delete the rollback file to finish the commit
	 * @throws IOException 
	 */
	@Override
	public void commitTransactionStage3() throws IOException
	{
		//System.out.println("commitTransactionStage3: "+this);
		
		//delete the log file indicating that we're all done
		getRollBackFile().delete();
		
		//we may be just opening the table and in that case there is no
		//transaction
		if(inTransaction)
			finishTransaction();

		//System.out.println("commitTransactionStage3 end: "+this);
	}
	
	//threadsafe - should not be called while reading
	@Override
	public void close() throws IOException {
		//System.out.println("close: "+this);
		if(inTransaction)
		{
			rollbackTransaction();
			finishTransaction();
		}
		
		if(rwRaf != null)
		{
			rwRaf.close();
			rwRaf = null;			
		}
		
		
		//System.out.println("close end: "+this);
	}

	
	//all callers are thread safe
	private void reopenRaf(int expectedRecordSize) throws IOException {
		synchronized (this)
		{
		
			if(rwRaf != null)
			{
				rwRaf.close();
				rwRaf = null;
			}
			
			rwRaf = new RandomAccessFile(filename, "rw");
	
			if (rwRaf.length() == 0) {
				createHeader(expectedRecordSize);
				
				//we do this so we can open the read only map
				rwRaf.getFD().sync();
				
				isNew = true;
			}
	
			readHeader(expectedRecordSize);
			
		}
		
	}

	//all callers are thread safe
	private boolean readFullyForRollBack(InputStream rfIn, byte[] buf,
			boolean recordBoundary) throws IOException {
		int c = Util.readFully(rfIn, buf);
		if(c != buf.length)
		{ 
			if(c == 0 && recordBoundary)
				return false;
			
			setTableCorrupt();
			throw new IllegalStateException("database corrupted on rollback, got "+c+" bytes, expected "+recordSize+
					", bytes: "+Arrays.toString(buf));
		}
		
		return true;
	}

	//all callers are thread safe
	/**
	 * @return number of rows in timmy table at last commit
	 */
	private int readRollBackHeader(InputStream is) throws IOException {
		byte[] magicBuffer = new byte[ROLLBACK_MAGIC.length];

		Util.readFully(is, magicBuffer);
		
		if (!Arrays.equals(ROLLBACK_MAGIC, magicBuffer))
		{
			setTableCorrupt();
			throw new IOException("Bad magic for rollback file of timmy table " + this
					+ ", got " + Arrays.toString(magicBuffer));
		}

		return Util.readInt(is);
	}


	//threadsafe - map can be altered when writing a transaction out
	public void getRecord(byte[] buf, int id) {
		//System.out.println("getRecord: "+this.filename);
		if(!database.isOpen())
			throw new IllegalStateException("don't access the table before opening the database");
		
		synchronized (this)
		{
			try {
				if(((long)id) * recordSize + HEADER_SIZE >= rwRaf.length() || id < 0)
				{
					setTableCorrupt();
					throw new IllegalStateException("record out of bounds for "+this+". table marked corrupt, got "+id);
				}
				
				rwRaf.seek(HEADER_SIZE + id * recordSize);
				
				Util.readFully(rwRaf, buf);
			}
			catch(IOException e)
			{
				try {
					setTableCorrupt();
					throw new IllegalStateException("id is "+id+" recordSize is "+recordSize+" pos is "+
							(id * recordSize)+", rwRaf.limit is "+rwRaf.length(), e);
				} catch (IOException e1) {
					setTableCorrupt();
					throw new IllegalStateException("id is "+id+" recordSize is "+recordSize+" pos is "+
							(id * recordSize)+", can't read rwRaf.limit", e);
				}
			}
			
		}
		//System.out.println("end getRecord: "+this.filename);
	}
	
	public boolean isTableCorrupt()
	{
		return isTableCorrupt;
	}
	
	//threadsafe, called from synchronized block
	public void setTableCorrupt() {
		this.isTableCorrupt = true;
		try {
			rwRaf.seek(IS_CORRUPTED_FIELD_POS);
			rwRaf.write(1);
			rwRaf.getFD().sync();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}


	/**
	 * Deletes database and all temporary files
	 */
	@Override
	public void deleteTableFiles() {
		new File(filename).delete();
		getRollBackFile(filename).delete();
	}

	@Override
	public String toString() {
		return "TimmyTable [filename="+filename + ", recordSize=" + recordSize
				+ ", committedNextRowId=" + committedNextRowId + ", filename="
				+ filename + ", lastTransactionInsertId="
				+ lastTransactionInsertId + ", rollBackOut="
				+ rollBackFileOut + ", inTransaction=" + inTransaction
				+ ", raf=" + rwRaf
				+ ", rollBackFileOut=" + rollBackFileOut + "]";
	}

	@Override
	public boolean inStage2() {
		File f = getRollBackFile();
		
		return !f.exists() || f.length() == 0;
	}

	@Override
	public void commitTransactionStage1() throws SyncFailedException,
			IOException {
		//noop, softCommit should already be run at this point
	}

	@Override
	public String getFilename() {
		return filename;
	}


}
