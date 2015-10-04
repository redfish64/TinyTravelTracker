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
package com.rareventure.gps2.reviewer.map;


/**
 * Handles detmerming when the app is busy
 */
public class BusyIndicationManager {
	private int busyinessLevel;
	
	private Listener listener;
	
	public BusyIndicationManager()
	{
	}
	
	public synchronized void setListener(Listener listener)
	{
		this.listener = listener;
	}

	/**
	 * this will turn on or off busyiness. If setBusy(true) is 
	 * called multiple times, setBusy(false) must be called the same
	 * number of times. 
	 * 
	 * Is thread safe
	 */
	public synchronized void setBusy(boolean isBusy)
	{
		boolean notify = false;
		
		if(isBusy)
		{
			busyinessLevel ++;
			if(busyinessLevel == 1)
				notify=true;
		}
		else
		{
			busyinessLevel --;
			if(busyinessLevel == 0)
				notify=true;
		}
		
		if(busyinessLevel < 0)
			throw new IllegalStateException("busyiness is too low! "+busyinessLevel);
		
		if(notify)
		{
			if(listener != null)
				listener.notifyBusy(busyinessLevel != 0);
		}
	}
	
	public static interface Listener
	{
		public void notifyBusy(boolean isBusy);
	}
	
}
