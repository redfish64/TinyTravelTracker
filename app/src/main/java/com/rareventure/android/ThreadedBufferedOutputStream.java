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


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that contains a thread to write data after it has been
 * written to an internal buffer, so that any write
 * will return immediately and will not block. 
 * 
 * Uses a circular buffer to process data. Data may overrun, and oh well, that's
 * the way it is
 */
public class ThreadedBufferedOutputStream extends OutputStream
{
	private OutputStream os;
	
	private int dataEndIndex, wroteIndex;
	
	private byte [] data;

	protected IOException exception;

	private int minDataToWrite;
	
	private boolean flushing;

	/**
	 * @param minDataToWrite will not schedule a write unless the amount of data to write is equal to or above
	 * the minDataToWrite (or flushing)
	 * @param os
	 */
	public ThreadedBufferedOutputStream(int size, int minDataToWrite, OutputStream os)
	{
		this.data = new byte[size];
		this.os = os;
		this.intThread.setDaemon(true);
		this.minDataToWrite = minDataToWrite;
		
		intThread.start();
	}
	
	
	
	@Override
	public void close() throws IOException {
		flush();
		os.close();
	}



	@Override
	public void flush() throws IOException {
		try {
			synchronized(intThread)
			{
				flushing = true;
				intThread.notify();
				while(dataEndIndex != wroteIndex)
				{
					intThread.wait();
				}
				os.flush();
				
				flushing = false;
			}
		} catch(InterruptedException e)
		{
			throw new IllegalStateException(e);
		}
		
	}



	/**
	 * Will never block. Always write to memory
	 */
	@Override
	public void write(byte[] buffer, int offset, int count) throws IOException {
		synchronized(intThread)
		{
			int avail = (wroteIndex - dataEndIndex + data.length) % data.length;
			
			if(avail == 0) avail = data.length;
			
			if(avail < count)
			{
				throw new IOException("Buffer overrun, have "+avail+" bytes available and want to write "+count);
			}
			
			//if we have to split into two copies
			if(count > data.length - dataEndIndex)
			{
				int firstPart = data.length - dataEndIndex;
				int secondPart = count - firstPart;
				
				System.arraycopy(buffer, offset, data, dataEndIndex, 
						firstPart);
				System.arraycopy(buffer, offset + firstPart, 
						data, 0, 
						secondPart);
				
				dataEndIndex = secondPart;
			}
			else
			{
				System.arraycopy(buffer, offset, data, dataEndIndex, 
						count);
				
				dataEndIndex += count;
			}
			
			//notify that we changed the position so the thread can start
			//writing it
			intThread.notify();
		}
	}


	private static byte [] writeOne = new byte[1];

	/**
	 * Will never block. Always write to memory
	 */
	@Override
	public void write(int oneByte) throws IOException {
		writeOne[0] = (byte)oneByte;
		write(writeOne, 0, 1);
	}
	
	public Thread intThread = new Thread() {
		public void run()
		{
			try {
				while(true)
				{
					int writeEnd;
					synchronized(this)
					{
						//if we don't have enough data that we care to try to write it. If we are flushing, we will write
						//any amount of data, even one byte
						while((dataEndIndex - wroteIndex + data.length) % data.length < (flushing ? 1 : minDataToWrite) )
						{
							wait();
						}
						
						writeEnd = dataEndIndex;
						if(writeEnd < wroteIndex) writeEnd = data.length;
					}
					
					os.write(data, wroteIndex, writeEnd - wroteIndex);
					
					synchronized(this)
					{
						wroteIndex = writeEnd;
						if(wroteIndex == data.length)
							wroteIndex = 0;
	
						//notify that we updated the wrote index (incase we are flushing)
						notify();
					}
				}
			}
			catch(InterruptedException e)
			{
				throw new IllegalStateException(e);
			} catch (IOException e) {
				exception = e;
			}
		}
	};
	
	//TEST
	public static void main(String [] argv) throws IOException, InterruptedException
	{
		ThreadedBufferedOutputStream os = new ThreadedBufferedOutputStream(1000, 300, new FileOutputStream("/tmp/foo"));
		
		for(int i = 0; i < 4096; i++)
		{
			os.write(("i is "+i+"\n").getBytes());
			
			System.out.println("i is "+i);
			
//			if(i % 100 == 0)
//				os.flush();
		}
		
		
		os.close();
		
		System.out.println("done with test 1");

		os = new ThreadedBufferedOutputStream(1024, 512, new FileOutputStream("/tmp/foo2"));
		while(true)
		{
			os.write(("writing to overrun").getBytes());
		}
	}
}
