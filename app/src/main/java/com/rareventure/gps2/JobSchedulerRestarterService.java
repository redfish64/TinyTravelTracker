package com.rareventure.gps2;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;

import com.rareventure.gps2.bootup.GpsTrailerReceiver;
import com.rareventure.util.DebugLogFile;

public class JobSchedulerRestarterService extends JobService {

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        DebugLogFile.log("JobSchedulerRestarterService notifying receiver to restart service if necessary");
        Intent broadcastIntent = new Intent(this, GpsTrailerReceiver.class);
        sendBroadcast(broadcastIntent);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
