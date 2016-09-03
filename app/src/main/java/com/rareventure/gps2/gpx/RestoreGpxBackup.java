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
package com.rareventure.gps2.gpx;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.lamerman.FileDialog;
import com.lamerman.SelectionMode;
import com.rareventure.android.DbUtil;
import com.rareventure.android.ProgressDialogActivity;
import com.rareventure.android.database.DbDatastoreAccessor;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerDb;
import com.rareventure.gps2.GpsTrailerDbProvider;
import com.rareventure.gps2.GpsTrailerService;
import com.rareventure.gps2.R;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.gpx.RestoreGpxBackup.ValidateAndRestoreGpxBackupTask.TaskList;
import com.rareventure.gps2.reviewer.PasswordDialogFragment;
import com.rareventure.util.ByteCounterInputStream;

import de.idyl.winzipaes.AesZipFileDecrypter;
import de.idyl.winzipaes.CrcIgnoringZipInputStream;
import de.idyl.winzipaes.impl.AESDecrypterBC;
import de.idyl.winzipaes.impl.ExtZipEntry;

public class RestoreGpxBackup extends ProgressDialogActivity {

	public class FileInfo {

		private File file;
		public CharSequence errorStr;
		private boolean isZip;
		private AesZipFileDecrypter azfd;
		private ExtZipEntry entry;
		protected CharSequence password;
		private byte [] fileZippedData;
		protected long entrySize;

		public FileInfo(File file) {
			this.file = file;
			
			if(file.toString().matches(".*\\.(?i:zip)"))
			{
				isZip = true;
			}
			else if(file.toString().matches(".*\\.(?i:gpx)"))
			{
			}
			else
				errorStr = getText(R.string.restore_gps_bad_file_ext);
			
			try {
				if(isZip)
				{
					azfd = new AesZipFileDecrypter(file, new AESDecrypterBC());
					entry = findZipEntry(".*\\.(?i:gpx)");
					
					if(entry == null)
					{
						errorStr = getText(R.string.restore_gpx_no_gpx_file_in_zip);
						return;
					}
					
				}
			}
			catch(IOException e)
			{
				Log.e(GTG.TAG,"Can't read zip file", e);
				errorStr = getText(R.string.restore_gpx_cannot_read_zip_file);
				return;
			}
		}

		private ExtZipEntry findZipEntry(String regex) throws IOException {
			for(ExtZipEntry ze : azfd.getEntryList())
			{
				if(ze.getName().matches(regex))
					return ze;
			}
			
			return null;
		}

		public boolean needsPassword() {
			return isZip && entry.isEncrypted();
		}

		public boolean hasError() {
			return errorStr != null;
		}

		/**
		 * Task and tasknumber are only used if we haven't already loaded the data in memory
		 * @param validateAndRestoreGpxBackupTask 
		 * @param t
		 * @param taskNumber
		 * @return
		 * @throws ZipException
		 * @throws IOException
		 * @throws DataFormatException
		 */
		public InputStream getBufferedInputStream(final ValidateAndRestoreGpxBackupTask task, final TaskList tl) throws ZipException, IOException, DataFormatException {
			if(!isZip)
			{
				entrySize = file.length();
				return new BufferedInputStream(new FileInputStream(file));
			}
			else 
			{
				entrySize = entry.getSize();
				if(entry.isEncrypted())
				{
					if(fileZippedData == null)
						fileZippedData = azfd.extractEntryToZippedByteArray(entry, password.toString(),
								new AesZipFileDecrypter.ProgressListener() {
									
									@Override
									public void notifyProgress(int count, int total) {
										tl.updateCurrTaskProgress(count, total);
										
									}

									@Override
									public boolean isCanceled() {
										return task.isCanceled;
									}
								});
					
					//if is canceled
					if(fileZippedData == null)
						return null;
					
					CrcIgnoringZipInputStream is = new CrcIgnoringZipInputStream(new ByteArrayInputStream(fileZippedData));
					
					is.getNextEntry();

					return is;
				}
				else
				{
					ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));
					for(;;)
					{
						ZipEntry ze = zis.getNextEntry();
						
						if(ze.getName().equals(entry.getName()))
							return zis;
					}
				}
			}
		}

	}

	private EditText filenameView;
	private Pattern xmlPattern;
	private Button restoreButton;
	private FileInfo fileInfo;
	private ValidateAndRestoreGpxBackupTask restoreTask;
	private boolean isPaused = true;
	
	@Override
	public void doOnCreate(Bundle b) {
		super.doOnCreate(b);

		setContentView(R.layout.restore_gpx_backup);

		filenameView = (EditText) findViewById(R.id.filename);

		filenameView.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				onFileBrowserButton(filenameView);
			}
		});
		
		//co: we no longer bother letting the user type in a filename, it's such a weird thing to try and do
//		filenameView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//
//			@Override
//			public void onFocusChange(View v, boolean hasFocus) {
//				if (!hasFocus) {
//					checkChosenFile();
//				}
//			}
//
//		});

		restoreButton = (Button) findViewById(R.id.restoreButton);

		checkChosenFile();
	}

	private void checkChosenFile() {
		if (filenameView.getText().toString().trim().isEmpty())
			restoreButton.setEnabled(false);
		else
			restoreButton.setEnabled(true);
	}

	public void onRestoreButton(View view) {
		String filename = filenameView.getText().toString().trim();

		if (!filename.startsWith("/"))
			filename = Environment.getExternalStorageDirectory() + "/"
					+ filename;

		final File f = new File(filename);

		fileInfo = new FileInfo(f);
		
		if (fileInfo.hasError()) {
			new AlertDialog.Builder(this)
					.setCancelable(false)
					.setTitle(R.string.error)
					.setMessage(fileInfo.errorStr)
					.setNeutralButton(R.string.ok, null).show();
			return;
		}

		new AlertDialog.Builder(this)
		.setCancelable(false)
		.setTitle(R.string.restore)
		.setMessage(R.string.restore_confirmation_dialog_msg)
		.setNegativeButton(R.string.cancel, null)
		.setPositiveButton(R.string.perform_restore,
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog,
							int which) {
						onRestore2();
						
					}

				}).show();
	}
		
	private void onRestore2()
	{
		if(fileInfo.needsPassword())
		{
			new PasswordDialogFragment.Builder(this)
				.setTitle("Enter Password")
				.setMessage(String.format(getString(R.string.restore_dialog_enter_password_for_filename_fmt),
						fileInfo.entry.getName()))
				.setOnOk(new PasswordDialogFragment.OnOkListener() {
					
					@Override
					public void onOk(CharSequence password) {
						fileInfo.password = password;
						performValidateAndThenCallRestore3InLongTask();
					}
				}).show();
		}
		else
			performValidateAndThenCallRestore3InLongTask();
	}
	
	private void performValidateAndThenCallRestore3InLongTask() {
		//we don't want the thing timing out, because leaving this page will cause the restore to quit
		//(cleared in doAfterFinish() of the task
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		GTG.setIsInRestore(true);
		
		runLongTask(restoreTask = new ValidateAndRestoreGpxBackupTask()
		, true, false, R.string.restore_backup_dialog_title, R.string.clearing_in_memory_cache);
	}

	private static final int REQUEST_BROWSE = 13;


	@Override
	public void doOnResume() {
		super.doOnResume();
		isPaused = false;
		showRestoreCompleteMessage();		
		
	}
	
	
	@Override
	public void doOnPause(boolean doOnResumeCalled) {
		super.doOnPause(doOnResumeCalled);
		isPaused = true;
	}
	
	@Override
	public void finish()
	{
		//if they try and enter a password and hit cancel, GTGActivityHelper has no choice but to back up through all the activities on the stack
		//so we have to cancel the backup, because our activity is about to finished.
		//Note that using singleTask doesn't help here because we are finishing the task regardless.
		if(restoreTask != null)
			restoreTask.cancel();
		restoreTask = null;
		
		super.finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if(restoreTask != null)
			restoreTask.cancel();
		restoreTask = null;
	}

	public void onFileBrowserButton(View v) {
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
		intent.putExtra(FileDialog.START_PATH, Environment
				.getExternalStorageDirectory().toString());

		// can user select directories or not
		intent.putExtra(FileDialog.CAN_SELECT_DIR, false);

		// alternatively you can set file filter
		intent.putExtra(FileDialog.FORMAT_FILTER, new String[] { "zip", "gpx", "ZIP", "GPX" });
		
		intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

		startInternalActivityForResult(intent, REQUEST_BROWSE);
	}

	@Override
	public void onActivityResult(final int requestCode, int resultCode,
			final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == REQUEST_BROWSE) {
				File chosenFile = new File(data
				.getStringExtra(FileDialog.RESULT_PATH));

				File sdcardPath = Environment.getExternalStorageDirectory();
				try {
					String canonicalSdcardPath = sdcardPath.getCanonicalPath();
					String canonicalChosenFile = chosenFile.getCanonicalPath();
					
					if(canonicalChosenFile.startsWith(canonicalSdcardPath))
						//set canonicalChosenFile to be relative to the external storage directory, +1 for '/
						canonicalChosenFile = canonicalChosenFile.substring(canonicalSdcardPath.length()+1);

					filenameView.setText(canonicalChosenFile);
				}
				catch(IOException e)
				{
					Log.e(GTG.TAG,"Error finding canonical paths", e);
					filenameView.setText(chosenFile.toString());
				}
					
				checkChosenFile();
			}

		}
		// else if (resultCode == Activity.RESULT_CANCELED) {
		// Logger.getLogger(AccelerationChartRun.class.getName()).log(
		// Level.WARNING, "file not selected");
		// }

	}

	public void onCancelButton(View v) {
		finish();
	}

	//TODO 2.1 make text not dark gray on select in file browse
	
	private String errorMessage;
	private boolean isRestoreCompletedAndNeedsMessage;
	/**
	 * This task validates that we can read the gpx file and finds the 
	 * time spans of the TrkSeg's inside of it. Since we need to write
	 * out the gps points in time order, we need to know this before
	 * we can actually perform the restore.
	 *
	 */
	public class ValidateAndRestoreGpxBackupTask extends ProgressDialogActivity.Task {
	
		public static final int NUM_TASKS = 5;

		protected static final int UPDATE_PROGRESS_BAR_INCREMENT = 100;
		
		private int taskNumber = 0;

		private TaskList tl;

		public class TaskList
		{
			private final int [] [] tasks = {
				{R.string.clearing_in_memory_cache, 1000},
				{R.string.restore_dialog_reading_backup_file, 1000},
				{R.string.restore_dialog_moving_old_gps_data, 500},
				{R.string.restore_dialog_restoring, 4000},
				{R.string.restore_dialog_cleaning_previous_points, 1000}
				};
			private int tasksTotal;
			
			private int taskIndex = -1;
			private int currPos;
			
			public TaskList() {
				tasksTotal = 0;
				for(int [] task : tasks)
				{
					tasksTotal += task[1];
				}
			}
			
			public void advanceToNextTask(int expectedTaskId)
			{
				if(taskIndex > 0)
					currPos += tasks[taskIndex][1];
				
				taskIndex++;
				int [] task = tasks[taskIndex];
				
				if(task[0] != expectedTaskId)
				{
					throw new IllegalStateException("Why you ask for "+expectedTaskId+", when we have "+task[0]+", hmm what?");
				}
				
				updateProgress(0, tasksTotal, currPos);

				setMessage(task[0]);
				
			}
			
			public void updateCurrTaskProgress(double v, double t)
			{
				updateProgress(0, tasksTotal, (int)(currPos + v * (tasks[taskIndex][1]) / t));
			}
		}
		
		@Override
		public void doIt() {
			final ByteCounterInputStream is;
			
			tl = new TaskList();

			tl.advanceToNextTask(R.string.clearing_in_memory_cache);
			
			GTG.shutdownTimmyDb();
			
			tl.advanceToNextTask(R.string.restore_dialog_reading_backup_file);
		
			try {
				InputStream bis = fileInfo.getBufferedInputStream(this, tl);
				if(bis == null) //if canceled
					return;
				is = new ByteCounterInputStream(bis);
			} catch (Exception e) {
				//TODO 2.5: i18nize
				Log.e(GTG.TAG, "Error opening file", e);
				errorMessage = e.getMessage();
				return ;
			}
			
			taskNumber++;
			
			stopService(new Intent(RestoreGpxBackup.this, GpsTrailerService.class));
			
			//first lock all the caches
			// so nothing can alter the gps related database tables or
			// timmy cache
			
	
			tl.advanceToNextTask(R.string.restore_dialog_moving_old_gps_data);
			
			GpsTrailerDb.moveToBakAndRecreateTable(GpsLocationRow.TABLE_NAME);
			GpsTrailerDb.moveToBakAndRecreateTable(TimeZoneTimeRow.TABLE_NAME);
			
			tl.advanceToNextTask(R.string.restore_dialog_restoring);
			
			GTG.db.beginTransaction();
			
			final DbDatastoreAccessor<GpsLocationRow> da = new DbDatastoreAccessor<GpsLocationRow>(GpsLocationRow.TABLE_INFO);
			final DbDatastoreAccessor<TimeZoneTimeRow> tztDa = 
				new DbDatastoreAccessor<TimeZoneTimeRow>(TimeZoneTimeRow.TABLE_INFO);
			final TimeZoneTimeRow tztr = new TimeZoneTimeRow();
			tztr.id = 0;
			
			GpxReader gpxr = new GpxReader(new GpxReader.GpxReaderCallback() {
				
				private int currIndex=1;
				
				private int count = 0;
				
				private long lastMs = 0;
				
				private GpsLocationRow gpr = new GpsLocationRow();
				private TimeZone lastTz;

				@Override
				public void readTrkSeg() {
				}
				
				@Override
				public void readTrkPt(double lon, double lat, double elevation, long timeMs,
									  TimeZone tz, float hdop) {
					if(GpxReader.isValidLocation(lon, lat) && timeMs > lastMs)
					{
						gpr.setData(timeMs, (int)(lat*1000000), (int)(lon*1000000), elevation,
								hdop*GpsLocationRow.GPS_HDOP_TO_ACCURACY);
						gpr.id = currIndex;
						
						da.insertRow(gpr);
						currIndex++;
						
						lastMs = timeMs;
					}
					else 
					{
						Log.e(GTG.TAG,"Invalid point lon "+lon+", lat "+lat+", timeMs "+timeMs+", lastMs "+lastMs);
					}
					
					if(++count % UPDATE_PROGRESS_BAR_INCREMENT == 0)
						tl.updateCurrTaskProgress(is.counter, fileInfo.entrySize);
					
					if(tz != lastTz)
					{
						tztr.id++;
						tztr.setData(timeMs, tz);
						tztDa.insertRow(tztr);
						lastTz = tz;
						
					}
					
//							if(count > 100)
//								throw new IllegalStateException("HACK DELETE ME");
					if(isCanceled)
					{
						throw new CanceledException();
					}
				}
				
				@Override
				public void readTrk() {
				}
				
				@Override
				public void readGpx() {
				}
			});
			
			try {
				gpxr.doIt(is);
								
			}
			catch(CanceledException e)
			{
				setMessage("Canceling...");
				
				cancelDoIt();
				return;
				
			}
			catch(SAXException e)
			{
				e.printStackTrace();
				cancelDoIt();

				//TODO 2.5: i18nize
				errorMessage = e.getMessage();
				
				return;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				cancelDoIt();
				
				//TODO 2.5: i18nize
				errorMessage = gpxr.createErrorMessage(e.getMessage());
				
				return;
			}
			
			//we might as well delete the user data keys that we are no longer using
			GTG.db.execSQL("delete from USER_DATA_KEY where _id != "+GTG.crypt.userDataKeyId);
			
			GTG.db.setTransactionSuccessful();

			//note that we delete the cache just before we drop the GPS_LOCATION_ROW table. This should
			//ensure that there is never a cache when the points themselves do not exist
			//TODO 2.5: PERF: We can save the medial loc time cache and just set isTempLoc to true for all rows
			GpsTrailerDbProvider.deleteUnopenedCache();

			GTG.db.endTransaction();
			
			tl.advanceToNextTask(R.string.restore_dialog_cleaning_previous_points);

			GpsTrailerDb.dropBakTable(GpsLocationRow.TABLE_NAME);
			GpsTrailerDb.dropBakTable(TimeZoneTimeRow.TABLE_NAME);
			
			GTG.gpsLocCache.clear();
			GTG.userLocationCache.clear();
			GTG.tztSet.loadSet();
			
		}
		
		

		private void cancelDoIt() {
			GTG.db.endTransaction();
			
			GpsTrailerDb.dropTable(GpsLocationRow.TABLE_NAME);
			GpsTrailerDb.dropTable(TimeZoneTimeRow.TABLE_NAME);
			
			GpsTrailerDb.replaceTableWithBakIfNecessary(GTG.db, GpsLocationRow.TABLE_NAME);
			GpsTrailerDb.replaceTableWithBakIfNecessary(GTG.db, TimeZoneTimeRow.TABLE_NAME);
			
			//we've got to clear the statements since the database has changed
			DbUtil.clearStatements();
			
			GTG.gpsLocCache.clear();
			GTG.userLocationCache.clear();
			GTG.tztSet.loadSet();
		}



		@Override
		public void doAfterFinish() {
			GTG.setIsInRestore(false);
			
			//restart the gps service now that we're no longer restoring
	        startService(new Intent(RestoreGpxBackup.this,
	                GpsTrailerService.class));
	        
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			
			if(isCanceled)
				return;
			
			isRestoreCompletedAndNeedsMessage = true;

			//since we keep the restore thread running even if we paused, we can't display
			// an alert dialog (doing so causes the restore gpx backup window to freeze
			// on reentry). So we set a flag and then in onResume, them message is shown
			if(isPaused)
			{
				return;
			}
			else
				showRestoreCompleteMessage();
			return;
		}




	}
	
	private void showRestoreCompleteMessage() {
		
		if(!isRestoreCompletedAndNeedsMessage)
			return;
		
		if(errorMessage != null)
		{
			new AlertDialog.Builder(RestoreGpxBackup.this)
			.setCancelable(false)
			.setTitle(R.string.error)
			.setMessage(errorMessage)
			.setNeutralButton(R.string.ok, new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			}).show();
		}
		else
			new AlertDialog.Builder(RestoreGpxBackup.this)
		.setCancelable(false)
		.setTitle(R.string.finished)
		.setMessage(R.string.restore_finished)
		.setNeutralButton(R.string.ok, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				finish();
			}
		}).show();
		
		isRestoreCompletedAndNeedsMessage = false;
	}
	
	private static class CanceledException extends RuntimeException
	{
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_RESTORE;
	}

}
