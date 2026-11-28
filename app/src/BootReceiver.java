package com.dsh.adbkeep;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        RecoveryJob.schedule(context);
        GatewayService.start(context, intent.getAction());
    }
}
