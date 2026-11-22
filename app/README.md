# 手机端

## 构建和安装

```bash
./app/build.sh
adb -d install -r --no-incremental app/adbgw.apk
adb -d shell pm grant com.dsh.adbkeep android.permission.WRITE_SECURE_SETTINGS
adb -d shell am start -n com.dsh.adbkeep/.MainActivity
adb -d shell getprop ro.serialno
```

构建需要 JDK 17、Android SDK build-tools、Android 平台的 `android.jar` 和调试签名。脚本使用 `TOOLS`、`ANDROID_JAR` 和 `KEYSTORE` 定位工具与密钥，默认路径见 [build.sh](build.sh)。

首次安装可能需要在手机上确认。后续保持 `com.dsh.adbkeep` 包名和同一签名，使用相同的覆盖更新命令。

## 工作方式

`BootReceiver` 在 Direct Boot、首次解锁完成和覆盖更新时启动 `GatewayService`。前台服务监听 Wi‑Fi，定期检查本机无线调试状态。Wi‑Fi 有 IPv4 地址且 `5555` 尚未监听时，服务可以重新开启原生无线调试，随后由网关把 `adbd` 切到固定端口。

如果前台服务退出，网关可用现有的 ADB 会话重新启动它。手机还会向 Android 注册一个持久化的周期任务，为暂时没有 ADB 会话时补做无线调试检查。该任务的实际运行时间由系统调度。

首次部署时，在开发电脑上确认 USB 调试授权，以及系统对当前 Wi‑Fi 的无线调试授权。网关应使用已授权的 ADB 密钥，也可以从网关用系统的配对码配对一次。

## 真机诊断

```bash
adb -s <手机IP>:5555 logcat -d -s AdbGateway:I
adb -s <手机IP>:5555 shell dumpsys activity services com.dsh.adbkeep
```

日志记录启动来源、首次解锁状态和无线调试设置的写入结果。
