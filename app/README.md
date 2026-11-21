# app —— 手机端

Java 实现，无第三方依赖（不使用 Gradle，直接用 aapt2/d8 构建）。

## 构建

```bash
./build.sh
```

可选环境变量：`TOOLS`（build-tools 根目录）、`ANDROID_JAR`、`KEYSTORE`、`WORK`（中间产物目录）。

产物：`app/adbgw.apk`。

## 目录

```
AndroidManifest.xml   清单
src/*.java            源码
res/xml/a11y.xml      无障碍服务配置
res/values/strings.xml
icon_res/mipmap-*     各密度图标
build.sh              构建脚本
```

## 组件

| 类 | 职责 |
|---|---|
| `Core` | 设置读写、无障碍自愈、IP、mDNS 端口发现、TCP 探活 |
| `Server` | 被动 HTTP：`/state`、`/repair`、`/pair` |
| `GwA11yService` | 无障碍服务；打开「无线调试」开关、上报配对码、结束回桌面熄屏 |
| `BootReceiver` | 开机写 `adb_wifi=1`、写回无障碍配置 |
| `MainActivity` | 状态页 + 服务端地址配置 |
| `Prefs` | 服务端地址（SharedPreferences，回退 `settings global adbgw_peer`） |
| `L` | 轻量日志（logcat + 应用外部目录） |

## 权限

- `WRITE_SECURE_SETTINGS`（需 `pm grant`）：写 `adb_wifi_enabled`、写回无障碍
- `INTERNET`、`RECEIVE_BOOT_COMPLETED`
- 无障碍服务（`BIND_ACCESSIBILITY_SERVICE`）

## 关键流程

1. 开机 → `BootReceiver` 写设置 + 自愈无障碍
2. 解锁/系统事件 → `GwA11yService` 健康检查（**纯接口**：settings / TCP / mDNS）
3. 不健康 → 导航：回桌面 → 开发者选项 → 无线调试 → 点开关（开关本身不可点，需点其可点祖先）
4. 结束 → 回桌面 + 熄屏（`GLOBAL_ACTION_LOCK_SCREEN`）
5. 收到服务端 `/pair` → 打开「使用配对码配对设备」读码上报

## 限制

- 「无线调试」控件与手势注入受 ColorOS 限制，必须开启「禁用系统优化」
- 配对流程要求设备已解锁（锁屏时返回 `locked`）