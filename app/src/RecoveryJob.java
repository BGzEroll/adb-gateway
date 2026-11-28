package com.dsh.adbkeep;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

public final class RecoveryJob extends JobService {
    private static final int PERIODIC_JOB_ID = 1;
    private static final int SETTING_CHANGE_JOB_ID = 2;

    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs.getPendingJob(PERIODIC_JOB_ID) == null) {
            jobs.schedule(new JobInfo.Builder(PERIODIC_JOB_ID, new ComponentName(context, RecoveryJob.class))
                    .setPeriodic(15 * 60 * 1000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build());
        }
        if (jobs.getPendingJob(SETTING_CHANGE_JOB_ID) == null) {
            scheduleSettingChange(context, jobs);
        }
    }

    private static int scheduleSettingChange(Context context, JobScheduler jobs) {
        return jobs.schedule(new JobInfo.Builder(SETTING_CHANGE_JOB_ID, new ComponentName(context, RecoveryJob.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        Settings.Global.getUriFor(GatewayService.SETTING), 0))
                .setRequiredNetwork(GatewayService.WIFI_REQUEST)
                .build());
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean settingChange = params.getJobId() == SETTING_CHANGE_JOB_ID;
                try {
                    Log.i(GatewayService.TAG, settingChange ? "运行设置变化补救任务" : "运行周期补救任务");
                    GatewayService.restoreWirelessAdb(RecoveryJob.this);
                } finally {
                    // Replacing the content-triggered job carries any pending changes forward.
                    if (!settingChange || scheduleSettingChange(RecoveryJob.this,
                            getSystemService(JobScheduler.class)) != JobScheduler.RESULT_SUCCESS) {
                        jobFinished(params, false);
                    }
                }
            }
        }, "adb-recovery-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return params.getJobId() == SETTING_CHANGE_JOB_ID;
    }
}
