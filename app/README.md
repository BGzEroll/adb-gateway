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

在 ColorOS 上，将 ADB 网关的「应用详情 → 耗电管理」设为「完全允许后台行为」，并在「设置 → 应用 → 自启动」里打开 ADB 网关。在 PYE110 上，耗电设置会把助手加入 Android 的电池优化豁免名单。可用 `adb shell cmd deviceidle whitelist` 检查列表中是否有 `com.dsh.adbkeep`。

## 工作方式

- `BootReceiver` 接收 `LOCKED_BOOT_COMPLETED`、`BOOT_COMPLETED` 和 `MY_PACKAGE_REPLACED`，注册补救任务并启动 `GatewayService`。
- 前台服务监听 Wi-Fi 和无线调试开关，并每 15 秒检查一次。Wi-Fi 有 IPv4 地址且 App 有设置权限时，仅在无线调试未开启的情况下写入 `adb_wifi_enabled=1`。
- `RecoveryJob` 在系统观察到无线调试开关变化且 Wi-Fi 可用时补一次检查，即使前台进程已退出也可由系统启动。另有一个持久化的 15 分钟周期任务。两个任务的执行时间均受 Android 调度和厂商后台策略约束。
- 状态页显示设置权限、Wi-Fi IPv4 状态和无线调试设置状态。系统配对由手机的「无线调试」页面完成。

通过已连通的 ADB 执行 `adb logcat -d -s AdbGateway:I`，可查看启动来源、补救任务和设置写入结果。系统「强行停止」会撤销任务，重新打开 App 可重新注册后台检查。
