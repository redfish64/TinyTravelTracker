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


import java.util.ArrayList;

import android.util.Log;

import com.rareventure.gps2.GTG;

public class SuperThread extends Thread
{
	boolean isSTDead;
	public SuperThreadManager manager;

	private ArrayList<Task> tasks = new ArrayList<SuperThread.Task>();

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

		public Task(int priority)
		{
			this.priority = priority;
		}
		
		public boolean isDead()
		{
			return isDead;
		}
		
		
		/**
		 * The implementation is expected to do all the work it can
		 * do and finish when it needs to wait on an object.
		 */
		abstract protected void doWork();
		
		/**
		 * specifies what the task is waiting on next before it can do
		 * more work
		 * 
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
		 * called when the task should never take on more work
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
			
			manager.notify();
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
				
				boolean allTasksDead = true;
				
				long minTimeToWakeUp = Long.MAX_VALUE;
				
				long currentTime = System.currentTimeMillis();
				
				for(Task t : tasks)
				{
					if(!t.isDead)
					{
						allTasksDead = false;
						
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
	

