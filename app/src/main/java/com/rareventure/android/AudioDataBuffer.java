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


public class AudioDataBuffer extends DataBuffer
{
	/**
	 * Audio reading just started up
	 */
	public static final byte PROC_MODE_STARTUP = 0;

	/**
	 * Continuously reading from the audio
	 */
	public static final byte PROC_MODE_CONTINUOUS = 1;

	/**
	 * Audio was shutoff after this read
	 */
	public static final byte PROC_MODE_SHUTDOWN = 2;
	

	/**
	 * Audio started up and shutdown all in one read
	 */
	public static final byte PROC_MODE_STARTUP_AND_SHUTDOWN = 3;
	
	public byte [] data;
	public int[] bytesRead;
	public byte[] processingMode;

	public int segmentSize;

	public int sampleFreq;
	
	public AudioDataBuffer(int sampleFreq, int segmentSize, int segmentCount)
	{
		super(segmentCount);
		
		this.sampleFreq = sampleFreq;
		
		data = new byte[segmentSize * segmentCount];
		bytesRead = new int[segmentCount];
		processingMode = new byte[segmentCount];
		
		this.segmentSize = segmentSize;
	}
	
	
}
