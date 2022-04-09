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


import android.util.Log;

import com.igisw.openlocationtracker.SuperThread.Task;
import com.rareventure.util.MultiValueHashMap;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * This manages a set of super threads. It can coordinate them all to stop doing tasks
 * and shutdown (when the activity is finished, for example), pause and resume them
 * all (when the activity is paused and resumed), etc.
 */
public class SuperThreadManager {
//object lock order:
//  SuperThreadManager -> SuperThread
// (items to the left must be synchronized first to prevent deadlocks)
//also note that we never lock objects we're waiting on or are notified for
// (since they could be synchronized on when calling this class)

	private ArrayList<SuperThread> superThreads = new ArrayList<SuperThread>();

	private MultiValueHashMap<Object, SuperThread.Task> objectToWaitingTasks 
			= new MultiValueHashMap<Object, SuperThread.Task>();

	private HashSet<Object> notifiedObjects = new HashSet<Object>();
	
	int sleepingThreads;

	boolean isPaused;

	private SleepingThreadsListener sleepingThreadsListener;

	public boolean isShutdown;
	
	public void addSuperThread(SuperThread st)
	{
		superThreads.add(st);
		st.manager = this;
	}
	
	public void setSleepingThreadsListener(SleepingThreadsListener stl)
	{
		this.sleepingThreadsListener = stl;
	}

	synchronized void taskWillWaitOn(SuperThread.Task task, Object item) {
		//if we were pre notified, just ignore the wait,
		//otherwise we turn off the task
		if(!notifiedObjects.remove(item))
		{
			task.setRunnable(false);
			objectToWaitingTasks.put(item, task);
		}
	}

	/**
	 * Notify the listening tasks that they should do work.
	 * Note that even if a task is not waiting, it will restart
	 * immediately after finishing its current work if this
	 * is set (unlike a normal notify which will do nothing
	 *  if a thread isn't waiting)
	 * @param o object to notify
	 */
	public synchronized void stNotify(Object o)
	{
		
		Task t = objectToWaitingTasks.getFirst(o);
		
		//PERF: we could synchronize on individual threads rather than the manager to improve performance.
		// no reason to wake up all threads every time a notification happens
		//if there is at least one task that is already waiting
		if(t != null)
		{
			t.setRunnable(true);
			this.notifyAll();
			
			objectToWaitingTasks.remove(o, t);
		}
		//put it on a list so the next task that waits for it 
		// wakes up 
		else notifiedObjects.add(o);
	}

	public void pauseAllSuperThreads()
	{
		synchronized (this)
		{
			isPaused = true;
			this.notifyAll();
		}
	}

	public void resumeAllSuperThreads()
	{
		synchronized (this)
		{
			isPaused = false;
			this.notifyAll();
		}
	}

	private static final int MAX_WAIT_FOR_DEATH = Integer.MAX_VALUE;
	
	public void shutdownAllSuperThreads() {
		//wait for all threads to die
		synchronized (this)
		{
			isPaused = false;
			
			isShutdown = true;
			
			this.notifyAll();
			
			for(SuperThread t : superThreads)
			{
				int i;
				
				for(i = 0; i < MAX_WAIT_FOR_DEATH; i++)
				{
					if(t.isSTDead || t.currentlyRunningTask != null && t.currentlyRunningTask.promisesToDieSoon)
						break;
					
					if(t == Thread.currentThread())
						throw new IllegalStateException("Why are you trying to interrupt all " +
								"SuperThreads from a SuperThread? "+
								Thread.currentThread());
					
					try {
						this.wait(5000);
					} catch (InterruptedException e) {
						throw new IllegalStateException(e);
					}

					if(t.isSTDead || t.currentlyRunningTask != null && t.currentlyRunningTask.promisesToDieSoon)
						break;
					
					Log.w(GTG.TAG,"Thread "+t+" is not being super... still alive when told to exit "+i+", task is "+t.currentlyRunningTask);
				}
				
				if(i == MAX_WAIT_FOR_DEATH)
				{
					throw new IllegalStateException("Thread "+t+" forgot to exit");
				}
			}
		}

		Log.d(GTG.TAG,"All threads exited");
	}

	synchronized void addDeltaToSleepingTheads(int delta) {
		boolean oldAllThreadsAreSleeping = sleepingThreads == superThreads.size();
		sleepingThreads += delta;
		if(sleepingThreads == superThreads.size() != oldAllThreadsAreSleeping)
		{
			if(sleepingThreadsListener != null)
				sleepingThreadsListener.notifySleepingThreadsChanged(!oldAllThreadsAreSleeping);
		}
			
	}
	
	public static interface SleepingThreadsListener
	{
		void notifySleepingThreadsChanged(boolean allThreadsAreSleeping);
	}

}
