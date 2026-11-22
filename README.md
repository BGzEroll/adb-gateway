# adb-gateway

在可信局域网内共享 Android 11+ 的原生无线 ADB。手机端使用 Java 和 Android 系统 API，无需 Root 或无障碍权限。

```text
Agent / ADB 客户端 ── TCP 5038 ──→ Docker ADB Server ── TLS / 动态端口 ──→ Android 手机
```

手机助手在开机和 Wi-Fi 恢复后开启无线调试。容器中的官方 ADB Server 负责 mDNS 发现、配对和连接。每台手机和网关配对一次，Agent 和其他客户端共用网关的配对身份。

## 部署和使用

1. [构建并安装手机助手](app/README.md)，通过 ADB 授予 `WRITE_SECURE_SETTINGS`。
2. 在与手机处于同一局域网的 Linux x86-64 主机上[准备镜像并启动 Compose](docker/README.md#构建或发布镜像)。
3. [首次配对并验证客户端连接](docker/README.md#首次配对)。

## 访问范围

**共享端口只应部署在可信网络，禁止直接暴露到公网。** 手机与网关之间使用原生 TLS。客户端到网关的 ADB Server 协议没有独立鉴权或传输加密。通过宿主机防火墙、Tailscale ACL 或 SSH 隧道限制来源。

## 真机验收

1. 和容器配对后，保持 USB 断开，用远程客户端的 `devices -l` 确认状态为 `device`，再执行 `shell getprop ro.product.model`。
2. 断开手机 Wi-Fi，再接入已授权的网络。检查设备能否重新出现并执行命令。
3. 重启手机，分别记录首次解锁前和解锁后的结果。Direct Boot 能提前启动助手，实际无线调试可用时间取决于 ROM、锁屏和 Wi-Fi 状态。
4. 保留数据卷并重建容器，再检查设备能否自动恢复连接。配对身份保存在该卷。

已在 PYE110（Android 16 / ColorOS 16.1）验证配对、Wi-Fi 重连、手机重启和容器重建。其他 ROM 和设置开机 PIN 后的首次解锁前连接需另行验证。 [发现与重连排查](docker/README.md#发现与重连)
