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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.rareventure.android.SensorDataBuffer;

import java.io.DataOutputStream;
import java.util.List;

public class SensorReader implements DataReader
{
	private SensorEventListener mListener;
	private ProcessThread processThread;
	private SensorDataBuffer sensorDataBuffer;
	private SensorProcessor sensorProcessor;
	private SensorManager sm;
	private Object lock = new Object();
	private int[] sensorTypeSensorDelay;
	private DataOutputStream os;

	/**
	 * 
	 * @param os
	 * @param sensorProcessor
	 * @param context
	 * @param adb
	 * @param sensorTypeSensorDelay Sensor.TYPE_*, SensorManager.*DELAY. Note that the first argument is not SensorManager.SENSOR_*, 
	 * which sucks when you mistaken one for the other.
	 */
	public SensorReader(DataOutputStream os, SensorProcessor sensorProcessor, Context context, SensorDataBuffer adb, int ... sensorTypeSensorDelay)
    {
    	this.os = os;
    	this.sensorProcessor = sensorProcessor;
    	this.sensorDataBuffer = adb;
    	
    	mListener = new SensorEventListener() {

			public void onAccuracyChanged(Sensor sensor, int accuracy) {
				
			}
	
			public void onSensorChanged(SensorEvent event) {
				//write to queue and update the read index
				synchronized(processThread.lock)
				{
					sensorDataBuffer.type[sensorDataBuffer.rawReadIndex] = event.sensor.getType();
					sensorDataBuffer.v1[sensorDataBuffer.rawReadIndex] = event.values[0];
					sensorDataBuffer.v2[sensorDataBuffer.rawReadIndex] = event.values[1];
					sensorDataBuffer.v3[sensorDataBuffer.rawReadIndex] = event.values[2];
					sensorDataBuffer.timeRead[sensorDataBuffer.rawReadIndex] = System.currentTimeMillis();
					
					sensorDataBuffer.updateReadIndex();
					processThread.lock.notify(); //notify there is some new data to read
				}
			}
    	};
    	
        this.sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        
        this.sensorTypeSensorDelay = sensorTypeSensorDelay;
    }
    
    public void turnOn()
    {
        for(int i = 0; i < sensorTypeSensorDelay.length; i+= 2)
        {
	        List<Sensor> sensors = sm.getSensorList(sensorTypeSensorDelay[i]);
	
	        sm.registerListener(mListener, sensors.get(0),
	                sensorTypeSensorDelay[i+1]);
        }
    }
    
    public void turnOff()
    {
    	sm.unregisterListener(mListener);
    }


	@Override
	public boolean canProcess() {
		return sensorDataBuffer.rawProcessIndex != sensorDataBuffer.rawReadIndex;
	}

	@Override
	public void process() {
		sensorProcessor.processSensorData(sensorDataBuffer.type[sensorDataBuffer.rawProcessIndex],
				sensorDataBuffer.v1[sensorDataBuffer.rawProcessIndex],
				sensorDataBuffer.v2[sensorDataBuffer.rawProcessIndex],
				sensorDataBuffer.v3[sensorDataBuffer.rawProcessIndex],
				sensorDataBuffer.timeRead[sensorDataBuffer.rawProcessIndex]);
		
		if(os != null)
			writeTestData();
		
		synchronized(processThread.lock)
		{
			sensorDataBuffer.updateProcessIndex();
		}
	}


	@Override
	public void setProcessThread(ProcessThread processThread) {
		this.processThread = processThread;
	}

	public interface SensorProcessor {

		void processSensorData(int type, float v1, float v2, float v3, long time);
	}

	@Override
	public void notifyShutdown() {
		turnOff();
	}


	private void writeTestData() {
		//TODO x1: hack to see if the strategy thread is starving
//		synchronized(TestUtil.class)
//		{
//			try {
//				TestUtil.writeMode(os, WriteConstants.MODE_WRITE_SENSOR_DATA);
//		
//				TestUtil.writeInt("type", os, sensorDataBuffer.type[sensorDataBuffer.rawProcessIndex]);
//				TestUtil.writeFloat("v1", os, sensorDataBuffer.v1[sensorDataBuffer.rawProcessIndex]);
//				TestUtil.writeFloat("v2", os, sensorDataBuffer.v2[sensorDataBuffer.rawProcessIndex]);
//				TestUtil.writeFloat("v3", os, sensorDataBuffer.v3[sensorDataBuffer.rawProcessIndex]);
//		    	TestUtil.writeTime("timeRead", os, sensorDataBuffer.timeRead[sensorDataBuffer.rawProcessIndex]);
//			}
//			catch(IOException e)
//			{
//				throw new IllegalStateException(e); //punt but not a todo because this is test code
//			}
//		}
	}

}

