# ADB 网关

让闲置安卓手机在 Wi‑Fi 上长期提供 `手机 IP:5555`，供其他 ADB 客户端和 Agent 直接使用。

- 手机 App 在开机和 Wi‑Fi 恢复后通过系统设置开启原生无线调试。它使用 `WRITE_SECURE_SETTINGS`，无需无障碍。
- 常开的 Linux 网关通过 mDNS 发现手机的动态无线调试端口，再用 `adb tcpip 5555` 恢复手机上的固定端口。已经能访问固定端口时，网关不会重启 `adbd`。
- USB 用于首次安装、授予权限和授权网关的 ADB 密钥。正常恢复通过 Wi‑Fi 完成。

## 部署

1. 构建并配置[手机 App](app/README.md)。
2. 在同一个局域网启动[网关](docker/README.md)。可选择 Linux 用户服务或 Docker Compose。
3. 给手机在路由器上保留一个 DHCP 地址，其他客户端就可以一直用同一个 IP。

```bash
adb connect <手机IP>:5555
adb -s <手机IP>:5555 shell getprop ro.product.model
curl http://<网关IP>:8770/status
```

每个使用独立 ADB 密钥的客户端首次连接时，需要在手机上确认调试授权。网关的 ADB 密钥应保留，以便它在手机重启后继续连接。

## 验收

解除开发机的 USB 挂载后，分别验证正常锁屏、Wi‑Fi 断开重连、网关进程重启和手机重启。每轮都用独立的 ADB 客户端连接 `手机 IP:5555`，执行一条 shell 命令。冷启动时分别记录首次解锁前和解锁后的结果。

恢复依赖手机重新加入 Wi‑Fi，以及网关能发现或访问手机。换到未授权的 Wi‑Fi、撤销调试授权、强行停止 App 且 ADB 已断开时，需要在手机上处理。冷启动时手机 App 支持 Direct Boot，首次解锁前的可用性以机型和系统实测为准。
