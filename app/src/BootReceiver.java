package com.dsh.adbkeep;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String action = i == null ? "null" : i.getAction();
        L.d(c, action);
        Context ctx = c.getApplicationContext();
        Core.writeAdbWifi(ctx, 1);
        Core.selfHeal(ctx);
    }
}
