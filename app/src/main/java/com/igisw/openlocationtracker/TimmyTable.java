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

public class TimmyTable implements ITimmyTable {

	private static final byte VERSION = 1;
	private static final long HEADER_SIZE = 64;
	private static final byte[] MAGIC = "timmy!".getBytes();
	private static final byte[] ROLLFORWARD_MAGIC = "TIMMY!".getBytes();
	private static final int NEXT_ROW_ID_POS = MAGIC.length + 1; // +1 for version
	private static final int RECORD_SIZE_POS = NEXT_ROW_ID_POS+(Integer.SIZE >> 3);
	private static final int IS_CORRUPTED_FIELD_POS = RECORD_SIZE_POS+(Integer.SIZE >> 3);
	public static final String ROLLFORWARD_EXTENSION = ".rf";
	private int recordSize;
	private int committedNextRowId;
	String filename;
	private int lastTransactionInsertId = Integer.MAX_VALUE;
	private OutputStream rollForwardOut;
	private boolean inTransaction;
	private RandomAccessFile rwRaf;
	
	//TODO 3 we don't need insertRecordOut anymore (we can just use rwRaf)
	private OutputStream insertRecordOut;
	private FileOutputStream insertRecordFileOut;
	private FileOutputStream rollForwardFileOut;
	private TimmyDatabase database;
	private boolean isTableCorrupt;
	private boolean isNew;

	//threadsafe (obviously)
	/**
	 * Created by TimmmyDatabase
	 */
	protected TimmyTable(String filename, int recordSize, TimmyDatabase d)
			throws SyncFailedException, IOException {
		////System.out.println("new: "+this);
		this.filename = filename;
		
		this.database = d;
		reopenRaf(recordSize);
		
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

	//threadsafe since it doesn't touch main db file
	public void updateRecord(int id, byte[] record) throws IOException {
		if(!database.isOpen())
			throw new IllegalStateException("don't access the table before opening the database");

		Util.writeInt(rollForwardOut, id);
		rollForwardOut.write(record);
	}

	//threadsafe since it doesn't touch main db file
	private void writeRollForwardHeader() throws IOException {
		rollForwardOut.write(ROLLFORWARD_MAGIC);
	}

	/**
	 * Within a transaction, all inserts must be done in sequential order
	 * @param id
	 * @throws IOException
	 */
	//threadsafe since it doesn't touch map
	public void insertRecord(int id, byte[] record) throws IOException {
		if(!database.isOpen())
			throw new IllegalStateException("don't access the table before opening the database");

		if(id < committedNextRowId)
			throw new IllegalStateException("Trying to insert a row below nextRowId: "+id+", this: "+this);
		if(id != lastTransactionInsertId+1)
			throw new IllegalStateException("Trying to insert a row that is not one above the last transaction id: "
					+lastTransactionInsertId +", id: "+id);
		if(record.length != recordSize)
			throw new IllegalStateException("Record is wrong size: "+record.length+", this: "+this);
		
		insertRecordOut.write(record);
		
		lastTransactionInsertId = id;
	}

	/**
	 * Only one thread is allowed to begin a transaction.
	 */
	//thread safety: only one thread allowed to do a transaction
	public void beginTransaction() throws IOException {
		//System.out.println("beginTransction: "+this);
		if(inTransaction)
			throw new IllegalStateException("You can't start a transaction twice. this: "+this);
		inTransaction = true;
		
		lastTransactionInsertId = committedNextRowId-1;

		//truncate the database to the last committed row so inserts
		// will be placed correctly
		synchronized(this) {
			rwRaf.setLength(HEADER_SIZE + recordSize * committedNextRowId);
		}

		//inserts are appended at the end of the file
		//note, we could just add them to the roll forward log, but then we're copying them
		//twice to disk, and so it's a little faster this way. Also it means there is only
		//one type of transaction in the roll forward log (update) so it simplifies that
		//quite a lot
		insertRecordOut = new BufferedOutputStream(insertRecordFileOut = new FileOutputStream(filename, true));

		rollForwardOut = new BufferedOutputStream(rollForwardFileOut = 
			new FileOutputStream(getRollForwardFile(true)));
		writeRollForwardHeader();
		//System.out.println("beginTransction end: "+this);
	}

	//threadsafe - doesnt touch map
	private File getRollForwardFile(boolean isTemp) {
		return getRollForwardFile(filename, isTemp);
	}

	private static File getRollForwardFile(String filename, boolean isTemp) {
		return new File(filename+(isTemp ? ROLLFORWARD_EXTENSION+".tmp" : ROLLFORWARD_EXTENSION));
	}

	public void commitTransactionStage1() throws SyncFailedException, IOException
	{
		//System.out.println("commitTransactionStage1: "+this);
		//stage 1
		//flush and sync everything
		insertRecordOut.flush();
		insertRecordFileOut.getFD().sync();
		insertRecordOut.close();
		
		rollForwardOut.flush();
		rollForwardFileOut.getFD().sync();
		rollForwardOut.close();
		
		//mark the roll back file as a real roll back file
		getRollForwardFile(true).renameTo(getRollForwardFile(false));
		//System.out.println("commitTransactionStage1 end: "+this);
	}
		

	//threadsafe - only one thread allowed to touch a transaction
	/**
	 * Rolls back as long as we haven't gone beyond the point of no return
	 * (the database tells us this)
	 * @throws IOException 
	 */
	public boolean rollbackTransaction() throws IOException {
		//System.out.println("rollbackTransaction: "+this);
		
		if(inTransaction)
		{
			rollForwardOut.close();
			insertRecordOut.close();
			finishTransaction();
		}
		
		getRollForwardFile(true).delete();
		//we delete the real roll forward file because the database might be requesting
		//the rollback, which can occur even if the rollforward log is ready to go
		getRollForwardFile(false).delete();
		
		synchronized(this)
		{
			rwRaf.setLength(HEADER_SIZE + recordSize * committedNextRowId);
		}
		//System.out.println("rollbackTransaction end: "+this);
		
		return true;
	}

	private void finishTransaction()
	{
		insertRecordFileOut = null;
		insertRecordOut = null;
		rollForwardFileOut = null;
		rollForwardOut = null;
		inTransaction = false;
	}


	//thread safe - we synchronize all access to map
	/**
	 * Splitting commits in multiple stages allows the database to coordinate transactions
	 * across multiple tables. 
	 * @param rwtm 
	 */
	public boolean commitTransactionStage2(ReadWriteThreadManager rwtm) throws IOException {
		//System.out.println("commitTransactionStage2: "+this);
		InputStream rfIn = new BufferedInputStream(new FileInputStream(getRollForwardFile(false)));
		readRollForwardHeader(rfIn);
		
		byte [] recordData = new byte[recordSize];
		byte [] idBytes = new byte[4];
		
		for(;;)
		{
			if(database.isCancelOpen)
				return false;
			
			//read roll forward. if there are no more records then quit
			if(!readFullyForRollForward(rfIn, idBytes, true))
				break;
			
			int id = Util.byteArrayToInt(idBytes, 0);
			
			if(id > committedNextRowId)
				throw new IllegalStateException("rollforward log attempted to write to a row not in committed set,"
						+" row id requested: "+id+", this: "+this);
			
			readFullyForRollForward(rfIn, recordData, false);
			
			synchronized (this)
			{
				//write the record to the database
				rwRaf.seek(recordSize * id + HEADER_SIZE);
				rwRaf.write(recordData);
			}
			
			if(rwtm != null && rwtm.isReadingThreadsActive())
				rwtm.pauseForReadingThreads();
			
		}
		
		rfIn.close();
		
		//here we finish updating the database
		//and finally closing and reopening the map with
		//the newly inserted rows
		synchronized (this)
		{
			//if any rows were inserted we have to reset the maps
			if(committedNextRowId != (int) ((rwRaf.length() - HEADER_SIZE) / recordSize))
			{
				committedNextRowId = (int) ((rwRaf.length() - HEADER_SIZE) / recordSize);
				
				rwRaf.seek(NEXT_ROW_ID_POS);
				rwRaf.writeInt(committedNextRowId);
				
			}
		}
		
		//this is supposed to sync to the file system
		//We let this sync happen outside of the synchronization loop, so that reads can still occur
		//while we sync. It appears to work
		rwRaf.getFD().sync();

//			reopenRaf(recordSize);

		//System.out.println("commitTransactionStage2 end: "+this);
		
		return true;
	}
	
	/**
	 * Finally clean up stage2
	 * @throws IOException 
	 */
	public void commitTransactionStage3() throws IOException
	{
		//System.out.println("commitTransactionStage3: "+this);
		
		//delete the log file indicating that we're all done
		getRollForwardFile(false).delete();
		
		//we may be just opening the table and in that case there is no
		//transaction
		if(inTransaction)
			finishTransaction();

		//System.out.println("commitTransactionStage3 end: "+this);
	}
	
	//threadsafe - should not be called while reading
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
	private boolean readFullyForRollForward(InputStream rfIn, byte[] buf,
			boolean recordBoundary) throws IOException {
		int c = Util.readFully(rfIn, buf);
		if(c != buf.length)
		{ 
			if(c == 0 && recordBoundary)
				return false;
			
			throw new IllegalStateException("database corrupted on rollforward, got "+c+" bytes, expected "+recordSize+
					", bytes: "+Arrays.toString(buf));
		}
		
		return true;
	}

	//all callers are thread safe
	private void readRollForwardHeader(InputStream is) throws IOException {
		byte[] magicBuffer = new byte[ROLLFORWARD_MAGIC.length];

		Util.readFully(is, magicBuffer);
		
		if (!Arrays.equals(ROLLFORWARD_MAGIC, magicBuffer))
			throw new IOException("Bad magic for rollforward file of timmy table " + this
					+ ", got " + Arrays.toString(magicBuffer));

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
					throw new IllegalStateException("id is "+id+" recordSize is "+recordSize+" pos is "+
							(id * recordSize)+", rwRaf.limit is "+rwRaf.length(), e);
				} catch (IOException e1) {
					throw new IllegalStateException("id is "+id+" recordSize is "+recordSize+" pos is "+
							(id * recordSize)+", can't read rwRaf.limit", e);
				}
			}
			
		}
		//System.out.println("end getRecord: "+this.filename);
	}
	
	public boolean isTableCorrupt()
	{
		boolean result = GTG.HACK_MAKE_TT_CORRUPT || isTableCorrupt;
		
		GTG.HACK_MAKE_TT_CORRUPT = false;
		
		return result;
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
	public void deleteTableFiles() {
		new File(filename).delete();
		getRollForwardFile(filename, true).delete();
		getRollForwardFile(filename, false).delete();
	}

	@Override
	public String toString() {
		return "TimmyTable [filename="+filename + ", recordSize=" + recordSize
				+ ", committedNextRowId=" + committedNextRowId + ", filename="
				+ filename + ", lastTransactionInsertId="
				+ lastTransactionInsertId + ", rollForwardOut="
				+ rollForwardOut + ", inTransaction=" + inTransaction
				+ ", raf=" + rwRaf + ", insertRecordOut=" + insertRecordOut
				+ ", insertRecordFileOut=" + insertRecordFileOut
				+ ", rollForwardFileOut=" + rollForwardFileOut + "]";
	}


	/**
	 * 
	 * @return true if the table is past stage1 but stage 2 hasn't yet complete.
	 */
	public boolean inStage2() {
		return getRollForwardFile(filename, false).exists();
	}

	@Override
	public String getFilename() {
		return filename;
	}

	@Override
	public boolean needsProcessingTime(boolean needsRollforward) {
		return needsRollforward;
	}

}
