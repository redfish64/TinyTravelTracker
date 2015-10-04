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
package com.rareventure.android;

public interface DataReader {
	/**
	 * @return True if there is data to process
	 */
	public boolean canProcess();

	/**
	 * Called by process thread to process the data.
	 * This is called in a separate thread to decouple reading from
	 * processing data items.
	 */
	public void process();
	
	/**
	 * Sets the thread to process the data. Must be called before
	 * reading is activated
	 */
	public void setProcessThread(ProcessThread pt);

	/**
	 * Notifies the data reader that the system is being shutdown,
	 * so it can clean up any resources
	 */
	public void notifyShutdown();
}
