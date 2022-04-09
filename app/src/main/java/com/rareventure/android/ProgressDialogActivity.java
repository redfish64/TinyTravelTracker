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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.util.Log;

import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTGFragmentActivity;

/**
 * Allows an activity to run one or more background tasks and show a spinner dialog or progress bar
 */
public abstract class ProgressDialogActivity extends GTGFragmentActivity {
	

	public WTask task;

	protected boolean dialogShowing;

	protected MyProgressDialogFragment fragment;
	
	private int max;

	public ProgressDialogActivity ()
	{
	}

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
	}

	protected void openDialogForWTask(WTask task, final boolean isCancelable, final boolean indeterminate, final int titleId, final int msgId)
	{
		this.task = task;
		showDialog(isCancelable, indeterminate, titleId, msgId);
	}
	
	private void showDialog(final boolean isCancelable, final boolean indeterminate, final int titleId, final int msgId) {
		dialogShowing = true;
		
		fragment = new MyProgressDialogFragment(this, isCancelable, indeterminate, titleId, msgId);
		
		fragment.show(ProgressDialogActivity.this.getSupportFragmentManager(), "dialog");
	}

	protected void runLongTask(Task task, final boolean isCancelable, final boolean indeterminate, final int titleId, final int msgId)
	{
			
		this.task = task;

		showDialog(isCancelable, indeterminate, titleId, msgId);

		new Thread(task).start();
	}
	
	
	/**
	 * A class implementing this will run using it's own threads/callbacks
	 * and notify this task when it's work is done. It will be notified if
	 * canceled with cancel()
	 */
	public abstract class WTask
	{
		protected boolean isShutdown = false;
		
		public WTask()
		{
		}
		
		public void setMaxProgress(int total) {
			max = total;
			
			runOnUiThread(new Runnable()
			{
				@Override
				public void run() {
					fragment.dialog.setMax(max);
				}
			});
		}
		
		public void updateProgress(final int start, final int end, final int progress)
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run() {
					fragment.dialog.setMax(end - start);
					fragment.dialog.setProgress(progress - start);
				}
			});
				
		}
		
		protected void setMessage(int msgId)
		{
			setMessage(getText(msgId));
		}
		
		protected void setMessage(final CharSequence msg)
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run() {
					if(fragment != null && fragment.dialog != null)
						fragment.dialog.setMessage(msg);
				}
			});
				
		}
		
		/**
		 * Should be called when the task is finished
		 */
		public void notifyFinish()
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run() {
					if(fragment != null && fragment.dialog != null)
						fragment.dialog.dismiss();
					
					dialogShowing = false;
					fragment = null;
				}
			});
			
		}
		
		/**
		 * Cancels the dialog. 
		 */
		public abstract void cancel();
		
	}
	
	/**
	 * A class implementing this will run as a thread.
	 */
	public abstract class Task extends WTask implements Runnable
	{
		//if true, dialog is canceled and progress should quit
		public boolean isCanceled = false;
		
		public Task()
		{
		}
		
		/**
		 * Should be called when the task is finished
		 */
		public void notifyFinish()
		{
			synchronized (this) {
				isShutdown = true;
				notify();
			}
			
			ProgressDialogActivity.this.task = null;
			
			runOnUiThread(new Runnable()
			{
				@Override
				public void run() {
					if(fragment != null)
						fragment.dialog.dismiss();
					
					dialogShowing = false;
					fragment = null;
					
					doAfterFinish();
				}
			});
				
		}
		
		/**
		 * Cancels the dialog. 
		 */
		public void cancel()
		{
			synchronized (this) {
				//if it was shutdown for any other reason, including being finished or canceled before,
				//we do not want to cancel it again
				if(!isShutdown)
				{
					isCanceled = true;
					notify();
					try {
						//TODO 2.1 for journal rollback/forward at least, instead of waiting, we should let the process go and then reconnect with
						//it so we can exit immediately instead of freezing.
						while(!isShutdown )
							wait();
					} catch (InterruptedException e) {
						throw new IllegalStateException(e);
					}
				}
			}
		}
		
		public final void run()
		{
			doIt();
			notifyFinish();
		}
		
		public abstract void doIt();
		
		public abstract void doAfterFinish();

	}
	
	//this needs to be static to allow for an empty constructor
	public static class MyProgressDialogFragment extends DialogFragment
	{

		private boolean isCancelable;
		private boolean indeterminate;
		private int titleId;
		private int msgId;
		private ProgressDialog dialog;
		private ProgressDialogActivity activity;
		
		public MyProgressDialogFragment()
		{
			//TODO 2.5 I'm not sure what to do here, sometimes fragments are destroyed and recreated by the android os? 
			//Not sure of the process or what we should do in this case because if a progress dialog fragment is destroyed
			// doesn't it mean the task being run was unfinished?
			Log.e(GTG.TAG,"no arg constructor called");
		}

		public MyProgressDialogFragment(ProgressDialogActivity activity, boolean isCancelable, boolean indeterminate, int titleId, int msgId) {
			this.activity = activity;
			this.isCancelable = isCancelable;
			this.indeterminate = indeterminate;
			this.titleId = titleId;
			this.msgId = msgId;
		}

		@Override
		public Dialog onCreateDialog(final Bundle savedInstanceState) {
			
		    dialog = new ProgressDialog(getActivity());

		    dialog.setTitle(titleId);
		    dialog.setMessage(getString(msgId));
		    dialog.setIndeterminate(indeterminate);
		    dialog.setCancelable(isCancelable);

		    if(isCancelable)
		    {
		    	dialog.setButton(ProgressDialog.BUTTON_NEGATIVE, getText(android.R.string.cancel), 
		    			new DialogInterface.OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if(which == ProgressDialog.BUTTON_NEGATIVE)
									activity.task.cancel();
							}
						});
		    }
		    
		    if(!indeterminate)
		    {
		    	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				if(activity.max != 0)
					dialog.setMax(activity.max);
		    }
		    else
		    	dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		   
		    
		    return dialog;
		}
		
		

		@Override
		public void onStart() {
			super.onStart();
			
			if(!activity.dialogShowing)
				dialog.cancel();
		}

		@Override
		public void onCancel(DialogInterface dialog) {
			activity.task.cancel();
			super.onCancel(dialog);
		}
		
	}
}
