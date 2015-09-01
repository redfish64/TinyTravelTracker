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

import com.rareventure.android.database.Cache;
import com.rareventure.android.encryption.EncryptedRow;
import com.rareventure.gps2.GTG;

public class RollBackTimmyDatastoreAccessor<T extends EncryptedRow> implements Cache.DatastoreAccessor<T> {

	private RollBackTimmyTable t;
	private byte [] retrieveRowData;
	private byte [] writeRowData;


	public RollBackTimmyDatastoreAccessor(RollBackTimmyTable t) {
		this.t = t;
		retrieveRowData = new byte[t.getRecordSize()];
		writeRowData = new byte[t.getRecordSize()];
	}

	@Override
	public int getNextRowId() {
		return t.getNextRowId();
	}

	@Override
	public void updateRow(T row) {
		try {
			if(!t.hardWriteMode)
			{
				t.softCommitTransaction();
			}
			
//			t.updateRecordHard(row.id, row.encryptRowWithEncodedUserDataKey(writeRowData));
			t.updateRecordHard(row.id, row.encryptRow(writeRowData));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void softUpdateRow(T row) {
		try {
			if(t.hardWriteMode)
			{
				t.revertToSoftCommitMode();
			}
			
			t.updateRecordSoft(row.id);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	@Override 
	public boolean needsSoftUpdate()
	{
		return true;
	}

	@Override
	public void insertRow(T row) {
		try {
			//co: we don't need to know which userdatakey because it will always be the master,
			// since cache creation can only occur when we can decrypt
			t.insertRecord(row.id, row.encryptRow(writeRowData));
//			t.insertRecord(row.id, row.encryptRowWithEncodedUserDataKey(writeRowData));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public boolean getRow(T outRow, int id) {
		t.getRecord(retrieveRowData, id);
		outRow.id = id;
		//co: we don't need to know which userdatakey because it will always be the master,
		// since cache creation can only occur when we can decrypt
//		outRow.decryptRowWithEncodedUserDatakey(retrieveRowData);
		outRow.decryptRow(GTG.crypt.userDataKeyId, retrieveRowData);
		return true;
	}

}
