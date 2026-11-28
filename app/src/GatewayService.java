package com.dsh.adbkeep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import java.net.Inet4Address;

public final class GatewayService extends Service {
    static final String TAG = "AdbGateway";
    static final String SETTING = "adb_wifi_enabled";
    static final String WRITE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";
    static final NetworkRequest WIFI_REQUEST = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
    private static final String CHANNEL = "adb_gateway";
    private static final long CHECK_INTERVAL_MS = 15000;

    private HandlerThread thread;
    private Handler worker;
    private ConnectivityManager connectivity;
    private ContentObserver adbSettingObserver;
    private long nextEnableAttempt;
    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            if (now >= nextEnableAttempt && restoreWirelessAdb(GatewayService.this)) {
                nextEnableAttempt = now + CHECK_INTERVAL_MS;
            }
            worker.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    scheduleCheck();
                }

                @Override
                public void onLost(Network network) {
                    scheduleCheck();
                }
            };

    static void start(Context context, String reason) {
        Intent intent = new Intent(context, GatewayService.class).setAction(reason);
        try {
            context.startForegroundService(intent);
        } catch (IllegalStateException e) {
            Log.w(TAG, "无法从 " + reason + " 启动服务", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(
                new NotificationChannel(CHANNEL, "ADB 网关", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("ADB 网关")
                .setContentText("Wi-Fi 可用时恢复无线调试")
                .setContentIntent(open)
                .setOngoing(true)
                .build());

        thread = new HandlerThread("adb-wifi");
        thread.start();
        worker = new Handler(thread.getLooper());
        connectivity = getSystemService(ConnectivityManager.class);
        adbSettingObserver = new ContentObserver(worker) {
            @Override
            public void onChange(boolean selfChange) {
                scheduleCheck();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(SETTING), false, adbSettingObserver);
        connectivity.registerNetworkCallback(WIFI_REQUEST, networkCallback, worker);
        RecoveryJob.schedule(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "启动原因=" + (intent == null ? "service restart" : intent.getAction())
                + " 首次解锁=" + getSystemService(UserManager.class).isUserUnlocked());
        scheduleCheck();
        return START_STICKY;
    }

    private void scheduleCheck() {
        worker.removeCallbacks(check);
        worker.post(check);
    }

    static boolean restoreWirelessAdb(Context context) {
        if (context.checkSelfPermission(WRITE_SETTINGS) != PackageManager.PERMISSION_GRANTED
                || Settings.Global.getInt(context.getContentResolver(), SETTING, 0) == 1
                || !hasWifiAddress(context.getSystemService(ConnectivityManager.class))) {
            return false;
        }
        boolean written = Settings.Global.putInt(context.getContentResolver(), SETTING, 1);
        Log.i(TAG, "开启无线调试=" + written);
        return true;
    }

    static boolean hasWifiAddress(ConnectivityManager connectivity) {
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                continue;
            }
            LinkProperties properties = connectivity.getLinkProperties(network);
            if (properties != null) {
                for (LinkAddress address : properties.getLinkAddresses()) {
                    if (address.getAddress() instanceof Inet4Address) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        getContentResolver().unregisterContentObserver(adbSettingObserver);
        connectivity.unregisterNetworkCallback(networkCallback);
        worker.removeCallbacksAndMessages(null);
        thread.quitSafely();
        super.onDestroy();
    }
}
