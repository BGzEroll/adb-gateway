# ADB 网关（adb-gateway）

在**无 root** 的 ColorOS（OPPO/一加等）手机上，让「无线 ADB」长期可用：

- 开机后自动把系统「无线调试」打开（adbd 真正启动）
- 常驻服务端自动发现手机、把 adbd 切换成**固定端口 5555**
- 手机短路/重启后自动恢复，配对、熄屏、状态同步全自动
- 手机端只提供极简 HTTP 供服务端巡检，自身不跑定时任务

## 架构

```
┌──────────────── 手机（app/，Android）────────────────┐
│ BootReceiver   开机：写 adb_wifi=1 + 写回无障碍        │
│ GwA11yService  无障碍：必要时导航打开「无线调试」开关    │
│                （这是无 root 下唯一能让 adbd 真启动的路）│
│ Server:8765    GET /state（状态） /repair（修复）       │
│                /pair（配对码）                         │
└──────────────────────────┬────────────────────────────┘
                           │ 服务端轮询 / 手机注册
┌──────────────────────────▼────────────────────────────┐
│ 服务端（docker/，容器）                                │
│ POST /register  手机上报 {ip, tls_port, pair_code...}  │
│ 每 20s 巡检：5555 可达？→ 否则 adb tcpip 5555          │
│ GET  /status    最近状态、是否可达、动作记录            │
└───────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 构建手机 App

```bash
cd app
./build.sh            # 需要 JDK17 + Android build-tools + android.jar
# 产物 app/adbgw.apk
```

### 2. 启动服务端

```bash
cd docker
docker compose up -d --build
curl http://127.0.0.1:8770/status
```

### 3. 一次性配置手机

```bash
adb shell pm grant com.dsh.adbkeep android.permission.WRITE_SECURE_SETTINGS
adb shell pm install -r adbgw.apk
# 首次需在手机上确认一次无障碍（系统弹窗），并在 App 里填写服务端地址
```

### 4. 验证

```bash
adb connect <手机IP>:5555
curl http://<手机IP>:8765/state
```

## 重要前提

1. **开发者选项里的「禁用系统优化」必须打开**（中文界面下该开关被隐藏，切英文可见）。
   关掉它会导致：无障碍读不到设置页控件、`pm grant` 被拒。
2. 需要一次 `pm grant WRITE_SECURE_SETTINGS`（无 root 下靠它写系统设置）。
3. 手机首次配对/首次部署需要**解锁一次**（无障碍拉起设置页需要前台）。
4. 无线调试端口**无法固定**（`persist.adb.tls_server.port` 等都有权限限制），
   固定端口只能靠服务端执行 `adb tcpip 5555`。

## 目录

```
app/      手机端源码（Java + 构建脚本 + 图标）
docker/   服务端（Python + Dockerfile + compose）
```

## 已知限制

- 无障碍 UI 自动化依赖 ColorOS 设置页控件结构，系统大版本升级可能失效
- adb 端口切换（tcpip）会短暂断开连接，服务端有容错重试
- 首次自动配对仍在完善中，可退化为「一次性复制 adbkey 到服务端」