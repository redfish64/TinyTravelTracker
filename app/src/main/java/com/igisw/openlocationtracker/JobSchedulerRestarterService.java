package com.igisw.openlocationtracker;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;

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
