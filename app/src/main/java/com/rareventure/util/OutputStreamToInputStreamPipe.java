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
package com.rareventure.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This allows two threads to communicate through basically what amounts to a pipe.
 * Bytes can be added by one thread and read from another. This class is thread safe.
 */
public class OutputStreamToInputStreamPipe extends InputStream
{

	protected byte[] buffer;
	protected int head;
	protected int tail;
	private int maxBufferSize = Integer.MAX_VALUE;
	
	public static class PipeClosedException extends IOException
	{

		public PipeClosedException() {
			super();
		}

		public PipeClosedException(String message, Throwable cause) {
			super(message, cause);
		}

		public PipeClosedException(String detailMessage) {
			super(detailMessage);
		}

		public PipeClosedException(Throwable cause) {
			super(cause);
		}
	}

	public OutputStreamToInputStreamPipe() {
		this(32);
	}

	public OutputStreamToInputStreamPipe(int initialSize) {
		if (initialSize <= 0) {
			throw new IllegalArgumentException(
					"The size must be greater than 0");
		}
		buffer = new byte[initialSize + 1];
		head = 0;
		tail = 0;
	}
	
	/**
	 * Creates an output stream that will write to this byte buffer (which 
	 * can then be read from another thread)
	 */
	public OutputStream getOutputStreamToThisByteBuffer()
	{
		return new OutputStream() {
			
			@Override
			public void write(int oneByte) throws IOException {
				OutputStreamToInputStreamPipe.this.add((byte) oneByte);
			}

			/**
			 * This closes but DOES NOT FLUSH. In other words,
			 * the input stream on the other end can still be not
			 * finished with the array when this is closed
			 */
			@Override
			public void close() throws IOException {
				OutputStreamToInputStreamPipe.this.finishWriting();
			}

			@Override
			public void flush() throws IOException {
				//this will throw an ioexception if input stream closes buffer
				OutputStreamToInputStreamPipe.this.blockUntilEmpty();
			}

			@Override
			public void write(byte[] buffer, int offset, int count)
					throws IOException {
				OutputStreamToInputStreamPipe.this.addBytes(buffer, offset, count);
			}

			@Override
			public void write(byte[] buffer) throws IOException {
				OutputStreamToInputStreamPipe.this.addBytes(buffer, 0, buffer.length);
			}
			
			
		};
	}

	public OutputStreamToInputStreamPipe(int initialSize, int maxBufferSize) {
		this(initialSize);
		this.maxBufferSize = maxBufferSize;
	}

	public synchronized int currBufferSize() {
		int size = 0;

		if (tail < head) {
			size = buffer.length - head + tail;
		} else {
			size = tail - head;
		}

		return size;
	}

	public synchronized boolean isEmptyBuf() {
		return (currBufferSize() == 0);
	}

	public synchronized void addBytes(byte[] bytes, int start, int len) throws PipeClosedException {
		//while there isn't enough room
		while (currBufferSize() + len >= buffer.length) {
			if(closed)
				throw new PipeClosedException("input stream closed pipe");
			
			// if we are at the maximum buf size, wait for the reading thread to
			// catch up
			if (buffer.length >= maxBufferSize)
				try {
					wait();
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
			else {
				// increase the size of the buffer
				byte[] tmp = new byte[Math.min(((buffer.length - 1) * 2) + 1,
						maxBufferSize)];

				if (head > tail) {
					System.arraycopy(buffer, head, tmp, 0, buffer.length - head);
					System.arraycopy(buffer, 0, tmp, buffer.length - head, tail);
					tail = tail + buffer.length - head;
					head = 0;
				} else {
					System.arraycopy(buffer, head, tmp, 0, tail - head);
					tail = tail - head;
					head = 0;
				}

				buffer = tmp;
			}
		}

		if(tail + len > buffer.length)
		{
			System.arraycopy(bytes, start, buffer, tail, buffer.length - tail);
			System.arraycopy(bytes, start + buffer.length - tail, buffer, 0, len - (buffer.length - tail));
			
			tail = len - (buffer.length - tail);
		}
		else
		{
			System.arraycopy(bytes, start, buffer, tail, len);
			tail += len;
		}
		
		//notify any reader waiting that we have more bytes
		notifyAll();
	}

	private byte[] tempByte1 = new byte[1];
	private boolean doneWriting;
	private boolean closed;
	private Throwable pendingExceptionFromOutputStream;

	public synchronized void add(final byte b) throws IOException {
		tempByte1[0] = b;
		addBytes(tempByte1,0,1);
	}

	@Override
	public synchronized int read() throws IOException {
		if(doneWriting && head == tail)
			return -1;
		
		read(tempByte1, 0, 1);
		
		return tempByte1[0];
	}

	@Override
	public synchronized int read(byte []data, int start, int inpLen) throws IOException
	{
		int actLen;
		
		for(;;) {
			if(pendingExceptionFromOutputStream != null)
				throw new IOException("Output to this pipe had an exception", pendingExceptionFromOutputStream);
	
			if(doneWriting && head == tail)
				return -1;
			
			if((actLen = Math.min(inpLen, this.currBufferSize())) != 0)
				break;
			
			try {
				wait();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
		
		if(head + actLen > buffer.length)
		{
			System.arraycopy(buffer, head, data, start, buffer.length - head);
			System.arraycopy(buffer, 0, data, start + (buffer.length - head), actLen - (buffer.length - head));
			
			head = actLen - (buffer.length - head);
		}
		else
		{
			System.arraycopy(buffer, head, data, start, actLen);
			head += actLen;
		}
		
		//notify any writer blocked because the buffer filled completely up
		// or any writer waiting for the buffer to be flushed
		notifyAll();
		
		return actLen;
	}

	/**
	 * Stream will close after the already written bytes are read.
	 */
	public synchronized void finishWriting()
	{
		doneWriting = true;
		
		//notify any readers that we are done writing (writeTo(OutputStream) uses this)
		notifyAll();
	}
	
	public synchronized void blockUntilClosed()
	{
		while(!closed)
		{
			try {
				wait();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * Blocks until all the current bytes have been read.
	 * If input stream is closed, throws an IOException
	 * @throws IOException 
	 */
	protected synchronized void blockUntilEmpty() throws PipeClosedException {
		while(head != tail)
		{
			if(closed)
				throw new PipeClosedException("input stream closed pipe");
			try {
				wait();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@Override
	public synchronized void close()
	{
		closed = true;
		notifyAll();
	}

	public synchronized boolean atEof() {
		return head == tail && doneWriting;
	}

	/**
	 * Writes all the data from os until eof to the output stream
	 * @param os
	 * @throws IOException 
	 */
	public synchronized void writeTo(OutputStream os, int minWriteAmount) throws IOException {
		while(!atEof())
		{
			int writeAmount = minWriteAmount;
			
			//wait until the buffer is at least minWriteAmount
			while(!doneWriting && currBufferSize() < minWriteAmount)
			{
				try {
					wait();
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
			}
			
			writeAmount = currBufferSize();
			
			//we write amount minWriteAmount even if there is more data
			//to prevent weird buffer amounts downstream of us
			if(head + writeAmount > buffer.length)
			{
				os.write(buffer, head, buffer.length - head);
				os.write(buffer, 0, writeAmount - (buffer.length - head));
				
				head = writeAmount - (buffer.length - head);
			}
			else
			{
				os.write(buffer, head, writeAmount);
				head += writeAmount;
			}
			
		}
	}

	/**
	 * Notifies the input stream that the output stream failed on the 
	 * next input streams read
	 * @param e error
	 */
	public synchronized void notifyExceptionFromOutputStream(Throwable e) {
		pendingExceptionFromOutputStream = e;
		
		//notify any readers waiting for data
		notifyAll();
	}

}