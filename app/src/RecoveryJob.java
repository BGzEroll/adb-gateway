package com.dsh.adbkeep;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class RecoveryJob extends JobService {
    private static final int JOB_ID = 1;

    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs.getPendingJob(JOB_ID) != null) {
            return;
        }
        // Keep a single persisted recovery job when upgrading.
        jobs.cancelAll();
        jobs.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, RecoveryJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    GatewayService.restoreWirelessAdb(RecoveryJob.this);
                } finally {
                    jobFinished(params, false);
                }
            }
        }, "adb-recovery-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
