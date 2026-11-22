package com.dsh.adbkeep;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver settingObserver = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            updateStatus();
        }
    };
    private final ConnectivityManager.NetworkCallback wifiCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    updateStatus();
                }

                @Override
                public void onLost(Network network) {
                    updateStatus();
                }
            };
    private ConnectivityManager connectivity;
    private TextView message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectivity = getSystemService(ConnectivityManager.class);
        message = new TextView(this);
        message.setPadding(32, 48, 32, 32);
        message.setTextIsSelectable(true);
        message.setTextSize(16);
        setContentView(message);
        GatewayService.start(this, "activity");
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.registerNetworkCallback(GatewayService.WIFI_REQUEST, wifiCallback, main);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(GatewayService.SETTING), false, settingObserver);
        updateStatus();
    }

    @Override
    protected void onStop() {
        connectivity.unregisterNetworkCallback(wifiCallback);
        getContentResolver().unregisterContentObserver(settingObserver);
        super.onStop();
    }

    private void updateStatus() {
        boolean allowed = checkSelfPermission(GatewayService.WRITE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
        boolean wifi = GatewayService.hasWifiAddress(connectivity);
        boolean enabled = Settings.Global.getInt(getContentResolver(), GatewayService.SETTING, 0) == 1;
        String status = "系统设置权限　" + (allowed ? "已授权" : "未授权")
                + "\nWi-Fi　" + (wifi ? "有 IPv4 地址" : "无 IPv4 地址")
                + "\n无线调试　" + (enabled ? "已开启" : "未开启");
        if (!allowed) {
            status += "\n\n请通过开发电脑授权\n"
                    + "adb shell pm grant " + getPackageName() + " " + GatewayService.WRITE_SETTINGS;
        }
        message.setText(status);
    }
}
