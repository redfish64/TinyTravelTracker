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


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.util.Log;

import com.igisw.openlocationtracker.GTG;

/**
 * A super thread is used to share threads between multiple blocking tasks.
 * This way one thread can handle several operations, each which may need to block.
 * <p>
 * It prevents having to create a million different threads for every tiny little operation.
 * Also, super threads are managed by a SuperThreadManager, which allows them to be shutdown,
 * paused, resumed, etc. when the corresponding activity goes through these states.
 *</p>
 *
 */
public class SuperThread extends Thread
{
	boolean isSTDead;
	public SuperThreadManager manager;

	private Set<Task> tasks = new HashSet<>();

	Task currentlyRunningTask;

	public static abstract class Task
	{
		private boolean _runnable = true;
		
		//higher has more priority and will always be run first
		int priority;
		
		public SuperThread superThread;

		long timeToWakeUp = Long.MAX_VALUE;

		boolean isDead;

		boolean promisesToDieSoon;

		/**
		 * Creates a task for the thread.
		 * @param priority higher will run first
         */
		public Task(int priority)
		{
			this.priority = priority;
		}
		
		/**
		 * The implementation is expected to do a reasonable amount of work
		 * (considering there are other tasks to run). This method
		 * will be called again and again until stExit() is called.
		 * The task is expected to save its state in field variables
		 * for the next run.
		 * <p>
		 *     It may wait on an object by calling stWait() and returning.
		 *     doWork() will not be called again until stNotify() is called
		 *     on the object.
		 * </p>
		 */
		abstract protected void doWork();
		
		/**
		 * specifies what the task is waiting on next before it can do
		 * more work.
		 * <p> Does not need to synchronize on the object to wait on it</p>
		 * <p>
		 * WARNING: this doesn't pause the current task.
		 * The task is expected to exit after calling this method. It
		 * will be recalled when stNotify is called for the given item
		 * </p>
		 * @param time time to wait if none of the objects are stNotified (if zero or less,
		 * will never wake up)
		 * @param item item to wait on, may be null
		 */
		protected void stWait(long time, Object item)
		{
			if(time > 0)
				timeToWakeUp = System.currentTimeMillis() + time;
			
			if(item != null)
				superThread.manager.taskWillWaitOn(this, item);
		}
		
		/**
		 * called when the task in finshed and should never take on more work
		 */
		protected void stExit()
		{
			isDead = true;
			setRunnable(false);
		}
		
		public void setRunnable(boolean runnable)
		{
			synchronized(superThread.manager)
			{
				if(runnable != this._runnable)
				{
					this._runnable = runnable;
					superThread.manager.notify();
				}
			}
		}
		
		public void promiseToDieWithoutBadEffect(boolean flag)
		{
			synchronized(SuperThread.class)
			{
				this.promisesToDieSoon = flag;
				SuperThread.class.notify();
			}
		}

		public void abortOrPauseIfNecessary() {
			superThread.abortOrPauseIfNecessary();
		}

		/**
		 * Notify the listening tasks that they should do work.
		 * Note that even if a task is not waiting, it will restart
		 * immediately after finishing its current work if this
		 * is set (unlike a normal notify which will do nothing
		 *  if a thread isn't waiting)
		 * @param o object to notify
		 */
		public void stNotify(Object o)
		{
			superThread.manager.stNotify(o);
		}
	}

	public SuperThread(SuperThreadManager manager)
	{
		manager.addSuperThread(this);
	}
	
	public void addTask(Task task)
	{
		synchronized(manager)
		{
			task.superThread = this;
			this.tasks.add(task);
			
			manager.notifyAll();
		}
	}
	
	public final void run()
	{
		for(;;)
		{
			//choose a task to run
			synchronized(manager)
			{
				int maxPriority = Integer.MIN_VALUE;
				Task maxTask = null;
				
				long minTimeToWakeUp = Long.MAX_VALUE;
				
				long currentTime = System.currentTimeMillis();

				ArrayList<Task> tasksToRun = new ArrayList<>();
				tasksToRun.addAll(tasks);

//				Log.d(GTG.TAG,"Super task "+this+" has "+tasksToRun.size()+" tasks");
				
				for(Task t : tasksToRun)
				{
					if(t.isDead)
						tasks.remove(t);
					else
					{
						if(t.timeToWakeUp <= currentTime)
						{
							t.setRunnable(true);
							t.timeToWakeUp = Long.MAX_VALUE;
						}
						
						if(t._runnable && t.priority > maxPriority)
						{
							maxTask = t;
							maxPriority = t.priority;
						}
						
						if(minTimeToWakeUp > t.timeToWakeUp)
							minTimeToWakeUp = t.timeToWakeUp;
					}
				}
				
				if(manager.isShutdown)
					break;
				
				//if there is nothing to run at the moment
				if(maxTask == null )
				{
					try {
						//wait to be notified (the manager will set one or more tasks to be runnable)
						//note that minTimeToWakeUp should always be later than currentTime at this point
						if(minTimeToWakeUp <= currentTime)
							throw new IllegalStateException("minTimeToWakeUp should always be later than currentTime at this point");
						manager.addDeltaToSleepingTheads(1);
						manager.wait(minTimeToWakeUp- currentTime);
						manager.addDeltaToSleepingTheads(-1);
					} catch (InterruptedException e) {
						break; //if we we're interupted, (due to a shutdown) just quit
					}
					continue;
				}

				currentlyRunningTask = maxTask;
			}
			
			//we need to run an actual task
			
			try {
				abortOrPauseIfNecessary();
				currentlyRunningTask.doWork();
			}
			catch(SIException e)
			{
				synchronized(manager)
				{
					currentlyRunningTask.isDead = true;
					break;
				}
			}
			catch(RuntimeException e)
			{
				Util.printAllStackTraces();
				throw e;
			}
			catch(Error e)
			{
				Util.printAllStackTraces();
				throw e;
			}
			
			synchronized(manager)
			{
				currentlyRunningTask = null;
			}
		}
		
		//tell any thread thats waiting for us all to shutdown that
		//I have
		synchronized (manager) {
			isSTDead = true;
			for(Task t : tasks)
				t.isDead = true;
			
			manager.notify();
		}
		
		Log.d(GTG.TAG,"Thread "+this+" exited");
	}
	
	public static void abortOrPauseIfNecessary()
	{
		if(Thread.currentThread() instanceof SuperThread)
		{
			SuperThread st = (SuperThread) Thread.currentThread();
			
			synchronized(st.manager)
			{
				while(st.manager.isPaused)
					try {
						st.manager.wait();
					} catch (InterruptedException e) {
						throw new IllegalStateException();
					}
				
				if(st.manager.isShutdown)
					throw new SIException();
			}
		}
	}
	
	/**
	 * Like a regular wait, but throws runtime exception,
	 * siexception if interrupted
	 */
	public static void siWait(Object o, long time)
	{
		try {
			if(time == -1)
				o.wait();
			else
				o.wait(time);
		}
		catch(InterruptedException e)
		{
			throw new SIException();
		}
		
		abortOrPauseIfNecessary();
	}
	
	public void siWait(long time)
	{
		siWait(this, time);
	}

	public void siWait()
	{
		siWait(this, -1);
	}
	
	public void siSleep(long time)
	{
		try {
			sleep(time);
		}
		catch(InterruptedException e)
		{
			throw new SIException();
		}
		
		abortOrPauseIfNecessary();
	}

	public static void siWait(Object o) {
		siWait(o, -1);
	}
	
	public static class SIException extends RuntimeException
	{}


	public String toString()
	{
		return this.getClass()+ "("+super.toString()+")";
	}
}
	

