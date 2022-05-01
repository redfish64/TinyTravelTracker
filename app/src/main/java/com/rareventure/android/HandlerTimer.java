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

import android.os.Handler;
import android.util.Log;

/**
 * Mostly used for a timer in the UI thread
 */
public class HandlerTimer {
	private Runnable runnable;

	private Handler mHandler = new Handler();

	private long delay;

	private boolean running;

	/**
	 * 
	 * @param runnable
	 * @param delay if zero, will not repeat
	 */
	public HandlerTimer(Runnable runnable, long delay) {
		this.runnable = runnable;
		this.delay = delay;
	}
	
	public void start(long initialDelay)
	{
		running = true;
		mHandler.removeCallbacks(ourRunnable);
		mHandler.postDelayed(ourRunnable, initialDelay);
	}	
	
	public void stop()
	{
		running = false;
	}

	private Runnable ourRunnable = new Runnable() {

		public void run() {
			if(running)
			{
				runnable.run();
				
				if(delay != 0)
					mHandler.postDelayed(this, delay);
			}
		}
	};
}
