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
/**
 * 
 */
package com.igisw.openlocationtracker;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.rareventure.android.AudioDataBuffer;
import com.rareventure.android.TestUtil;
import com.rareventure.android.WriteConstants;

import java.io.DataOutputStream;
import java.io.IOException;

//PERF: ask for a handler in the constructor, then use onPeriodicMarker or whatever with it
public class AudioReaderThread extends ReadThread
{
	private boolean micOn;
	private Object lock = new Object();
	private String tag;
	private AudioRecord ar = null;
	private AudioDataBuffer audioDataBuffer;
	protected boolean toStopReading;
	private ProcessThread processThread;
	private int androidInternalBufSize;
	private AudioProcessor audioProcessor;
	
	private boolean isNotifyShutdown;
	
	private DataOutputStream os;

	/**
	 * @param accelAudioRecorder
	 */
	public AudioReaderThread(DataOutputStream os, AudioProcessor audioProcessor, AudioDataBuffer audioDataBuffer, String tag,
			int androidInternalBufSize) {
		this.audioProcessor = audioProcessor;
		this.audioDataBuffer = audioDataBuffer;
		this.tag = tag;
		this.androidInternalBufSize = androidInternalBufSize;
		
		this.os = os;
	}
	
	public void setProcessThread(ProcessThread pt)
	{
		this.processThread = pt;
	}

	public void run()
	{
        
        boolean readyToRead = false;
        
        int bytesRead;

        long timeRead = 0;
        
        while(true)
        {
        	byte processingMode;

        	//if we are planning to read
        	if(readyToRead)
        	{
        		//if the audiorecord isn't set up yet
        		if(ar == null)
        		{
        			Log.d(this.tag, "Creating new audio record!");
                    ar = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            audioDataBuffer.sampleFreq,
                            AudioFormat.CHANNEL_CONFIGURATION_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            androidInternalBufSize);
                    
                	ar.startRecording();

                	//this is our first read after a break, so the mode is start up
                	processingMode = AudioDataBuffer.PROC_MODE_STARTUP;
            	
                	timeRead = System.currentTimeMillis();
        		}
        		else
                	processingMode = AudioDataBuffer.PROC_MODE_CONTINUOUS;

            	bytesRead = ar.read(this.audioDataBuffer.data, this.audioDataBuffer.rawReadIndex * this.audioDataBuffer.segmentSize, 
            			this.audioDataBuffer.segmentSize);
                
                if (bytesRead <= 0)
                {
                	throw new IllegalStateException("Cannot read buffer: "+bytesRead);
                }
                
                this.audioDataBuffer.bytesRead[this.audioDataBuffer.rawReadIndex] = bytesRead;
                this.audioDataBuffer.timeRead[this.audioDataBuffer.rawReadIndex] = timeRead;
                timeRead += bytesRead * 1000 / 2 /* bytes per sample */ / this.audioDataBuffer.sampleFreq; 

                boolean localMicOn;
                
            	synchronized(lock)
                {
            		localMicOn = this.micOn;
                }
            	
        		//if we are no longer on to gather data
        		if(!localMicOn || isNotifyShutdown) {
        			//we are going to have to shutdown
        			ar.release();
        			ar = null;
        			if(processingMode == AudioDataBuffer.PROC_MODE_CONTINUOUS)
        				processingMode = AudioDataBuffer.PROC_MODE_SHUTDOWN;
        			else
        				processingMode = AudioDataBuffer.PROC_MODE_STARTUP_AND_SHUTDOWN;
                	readyToRead = false;
        		}	                		

        		//now set the processing mode
            	this.audioDataBuffer.processingMode[this.audioDataBuffer.rawReadIndex] = processingMode;
            	
            	//we're done with the block, notify the clients
            	synchronized(this.processThread.lock)
                {
                	this.audioDataBuffer.updateReadIndex();
                	this.processThread.lock.notify();
                }

        	} //if ready to read
        	else { //not ready to read anything
        		
        		//wait around until either the system is shutdown or we should read
            	synchronized(lock)
                {
            		while(!this.micOn && !isNotifyShutdown) {
						try {
							lock.wait();
						} catch (InterruptedException e) {
							throw new IllegalStateException(e);
						}
            		}
            		
            		//in this case, micOn is false, so the AudioRecord is already shutdown. Because so, there is nothing to do,
            		//and we can stop now.
            		if(isNotifyShutdown)
            			break;
            		
                }
            	
            	//now since we didn't break, we know we are starting to read audio
            	readyToRead = true;
        	} //if we were not reading audio

        }//while true
        
        synchronized(lock)
        {
            isShutdown = true;
        	lock.notify();
        }
    } //run

	public void turnOffMic() {
		synchronized (this.lock) {
			if(!micOn)
				return;
			Log.d(tag,"Mic turn off request "+(System.currentTimeMillis()));
			
			micOn = false;
			
			lock.notify();
		}
	}

	public void turnOnMic() {
		synchronized(this.lock)
		{
			if(micOn)
				return;
			Log.d(tag,"Mic turn on request "+(System.currentTimeMillis()));
			micOn = true;
			
			lock.notify();
		}
	}

	@Override
	public boolean canProcess() {
		synchronized(this.lock)
		{
			return audioDataBuffer.rawProcessIndex != audioDataBuffer.rawReadIndex;
		}
	}

	@Override
	public void process() {
		audioProcessor.processAudio();
		
		if(os != null)
			writeTestData();
	}

	public interface AudioProcessor {

		void processAudio();

	}

	@Override
	public void notifyShutdown() {
		synchronized(this.lock)
		{
			this.lock.notify();
		}
	}

	private void writeTestData() {
		synchronized(TestUtil.class)
		{
			try {
				TestUtil.writeMode(os, WriteConstants.MODE_WRITE_AUDIO_DATA);
				
				TestUtil.writeData("data", os, audioDataBuffer.data, audioDataBuffer.rawProcessIndex * audioDataBuffer.segmentSize, 
		    			audioDataBuffer.bytesRead[audioDataBuffer.rawProcessIndex]);
		    	TestUtil.writeTime("timeRead", os, audioDataBuffer.timeRead[audioDataBuffer.rawProcessIndex]);
		    	TestUtil.writeByte("processingMode", os, (byte)audioDataBuffer.processingMode[audioDataBuffer.rawProcessIndex]);
			}
			catch(IOException e)
			{
				throw new IllegalStateException(e); //this is test code
			}
		}
	}
}