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
package com.rareventure.util;


/**
 * This is meant to handle the case where a bunch of high priority reading threads and
 * low priority writing threads must compete for access to an object.
 * <p>
 * Reading threads are allows simultaneous access, whereas only one writing thread is
 * allowed access at a time, and when it does, every other thread is paused.
 * While this happens, the writing thread can ask if its holding another thread up and
 * if so, quit early if possible.
 */
public class ReadWriteThreadManager {
	private int numReadingThreads;
	private boolean isWriting;
	private int numWaitingWritingThreads;
	private Object currentThread;
	private Thread writingThread;
	
//	private StackTraceElement[] lastWriterStackTrace;

	public synchronized void registerReadingThread()
	{
		numReadingThreads++;
		
		while(isWriting)
			try {
				wait();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
	}
	
	public synchronized void unregisterReadingThread()
	{
		numReadingThreads--;
		if(numReadingThreads <0)
			throw new IllegalStateException("less than zero reading threads");
		
		if(numReadingThreads == 0)
		{
			notify();
		}
	}
	
	public synchronized void registerWritingThread()
	{
		numWaitingWritingThreads++;
		
		while(isWriting || numReadingThreads > 0)
			try {
				wait();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		
		numWaitingWritingThreads--;
		isWriting = true;
		writingThread = Thread.currentThread();

		//		lastWriterStackTrace = Thread.currentThread().getStackTrace();
	}

	public synchronized void unregisterWritingThread()
	{
		if(!isWriting)
			throw new IllegalStateException("unregistering writing thread when not writing");
		isWriting = false;
		writingThread = null;
		notifyAll();
	}
	
	public synchronized boolean isWritingHoldingUpReadingThreads()
	{
		if(!isWriting)
			throw new IllegalStateException("looking for writing thread holding up others when not writing");
		
		return numReadingThreads > 0;
	}

	public synchronized boolean isWritingHoldingUpWritingThreads()
	{
		if(!isWriting)
			throw new IllegalStateException("looking for writing thread holding up other writing threads when not writing");
		
		return numWaitingWritingThreads > 0;
	}

	public synchronized boolean isReadingThreadsActive()
	{
		return numReadingThreads > 0;
	}

	/**
	 * Pause execution until reading threads are finished with their stuff.
	 * Does not necessarily have to be registered as a writer thread. If so,
	 * then isWriting is updated, otherwise no.
	 */
	public synchronized void pauseForReadingThreads() {
		boolean updateIsWriting = isWriting;

		if(updateIsWriting)
		{
			isWriting = false;
			notifyAll();
		}
		
		while(numReadingThreads > 0)
		{
			try {
				wait();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
		
		if(updateIsWriting)
		{
			isWriting = true;
		}
	}

	public void assertInWriteMode() {
		if(Thread.currentThread() != writingThread)
			throw new IllegalStateException("Why thread not in write mode, our thread is "+Thread.currentThread()+" and  thread in write mode is "+writingThread);
		
	}
	
}
