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

import com.igisw.openlocationtracker.EncryptedRow.Column;

public class TableInfo {
	public String insertStatementStr;
	public String updateStatementStr;
	public String deleteStatementStr;

	public String tableName;
	
	/**
	 * Encrypted columns
	 */
	public Column [] encColumns;
	public String selectStatementStr;

	public TableInfo(String tableName, Column[] encColumns, String insertStatementStr, String updateStatementStr,
			String deleteStatementStr) {
		super();
		this.insertStatementStr = insertStatementStr;
		this.updateStatementStr = updateStatementStr;
		this.deleteStatementStr = deleteStatementStr;
		this.tableName = tableName;
		this.encColumns = encColumns;
	}
	
	
}
