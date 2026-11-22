# Android 手机助手

支持 Android 11（API 30）以上，目标 SDK 为 34。App 无第三方库，使用 Android 的 `connectedDevice` 前台服务及对应的清单权限。

## 构建和安装

从仓库根目录执行。

```bash
./app/build.sh
adb -d install -r --no-incremental app/adbgw.apk
adb -d shell pm grant com.dsh.adbkeep android.permission.WRITE_SECURE_SETTINGS
adb -d shell am start -n com.dsh.adbkeep/.MainActivity
```

构建依赖 JDK 17、Android build-tools、`android.jar` 和调试签名。脚本可通过 `TOOLS`、`ANDROID_JAR` 和 `KEYSTORE` 指定路径，产物为 `app/adbgw.apk`。首次安装可能需要在手机上确认，覆盖更新沿用包名和签名。

## 工作方式

- `BootReceiver` 接收 `LOCKED_BOOT_COMPLETED`、`BOOT_COMPLETED` 和 `MY_PACKAGE_REPLACED`，启动 `GatewayService`。
- 前台服务监听 Wi-Fi，并每 15 秒检查一次。Wi-Fi 有 IPv4 地址且 App 有设置权限时，仅在无线调试未开启的情况下写入 `adb_wifi_enabled=1`。
- `RecoveryJob` 为前台服务异常退出准备了持久化的周期检查，周期为 15 分钟，实际执行时间由 Android 调度。
- 状态页显示设置权限、Wi-Fi IPv4 状态和无线调试设置状态。系统配对由手机的「无线调试」页面完成。

通过已连通的 ADB 执行 `adb logcat -d -s AdbGateway:I`，可查看启动来源和设置写入结果。强行停止 App 后，重新打开 App 可恢复后台检查。
