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

import android.content.Context;

import com.igisw.openlocationtracker.GTG.GTGEvent;
import com.rareventure.android.WorkerThread;

//import org.acra.ACRA;

import java.util.ArrayList;

public class ProcessThread extends WorkerThread
{

	private DataReader[] dataReaders;
	private String tag;
	private Context context;
	
	public ProcessThread(Context context, String tag, 
			DataReader ... dataReaders)
    {
		super();
		this.context = context;
		this.tag = tag;
		this.dataReaders = dataReaders;
		
		for(DataReader dr : dataReaders)
		{
			dr.setProcessThread(this);
		}
    }
    
    public void run()
    {
		try {
	    	ArrayList<DataReader> itemsToProcess = new ArrayList<DataReader>();
	    	
	    	while(true) {
	
	        	
	        	itemsToProcess.clear();
	        	
	        	synchronized(lock)
	        	{
	        		//wait for data
	        		while(!isShutdownRequested)
	        		{
	        			
	        			for(DataReader rt : dataReaders)
	        			{
	                		if(rt.canProcess())
	                		{
	                			itemsToProcess.add(rt);
	                		}
	        			}
	            		
	        			if(itemsToProcess.isEmpty())
	        			{
		        			try {
								lock.wait();
							} catch (InterruptedException e) {
								throw new IllegalStateException(e); //punt
							}
	        			}
	        			else
	        				break;
	        		} //while waiting for data
	        		
	        	} //sync lock
	        	
	        	if(isShutdownRequested)
	        		break;
	        	
	        	for(DataReader dr : itemsToProcess)
	        	{
	        		dr.process();
	        	}
	        	
	        } //end while running
	    	
	    	for(DataReader dr : dataReaders)
	    	{
	    		dr.notifyShutdown();
	    	}
	
	        synchronized(lock)
	        {
	            isShutdown = true;
	        	lock.notify();
	        }
	        

		}
		catch(Exception e)
		{
			e.printStackTrace();
			GTG.alert(GTGEvent.ERROR_SERVICE_INTERNAL_ERROR);
			//ACRA.getErrorReporter().handleException(e);

			synchronized(lock)
	        {
	            isShutdown = true;
	        	lock.notify();
	        }
	        
			return;
		}
    } // void run()

}
