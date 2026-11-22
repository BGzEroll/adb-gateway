package com.dsh.adbkeep;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GatewayService.start(this, "activity");
        TextView message = new TextView(this);
        message.setPadding(32, 48, 32, 32);
        message.setTextIsSelectable(true);
        message.setTextSize(16);
        if (checkSelfPermission(GatewayService.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            boolean enabled = Settings.Global.getInt(
                    getContentResolver(), GatewayService.SETTING, 0) == 1;
            message.setText("ADB 网关\n\n已授予设置权限\n无线调试当前"
                    + (enabled ? "已开启" : "未开启") + "\n固定连接端口 5555");
        } else {
            message.setText("ADB 网关\n\n请通过开发电脑授权后重新打开 App\n\n"
                    + "adb shell pm grant com.dsh.adbkeep android.permission.WRITE_SECURE_SETTINGS");
        }
        setContentView(message);
    }
}
