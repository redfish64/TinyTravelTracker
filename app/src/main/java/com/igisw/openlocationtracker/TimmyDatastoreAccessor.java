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

import java.io.IOException;

public class TimmyDatastoreAccessor<T extends EncryptedRow> implements Cache.DatastoreAccessor<T> {

	private TimmyTable t;
	private byte [] retrieveRowData;

	private byte [] writeRowData;

	public TimmyDatastoreAccessor(TimmyTable t) {
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
			t.updateRecord(row.id, row.encryptRow(writeRowData));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void insertRow(T row) {
		try {
			t.insertRecord(row.id, row.encryptRow(writeRowData));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public boolean getRow(T outRow, int id) {
		t.getRecord(retrieveRowData, id);
		outRow.id = id;
		outRow.decryptRow(GTG.crypt.userDataKeyId, retrieveRowData);
		return true;
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
