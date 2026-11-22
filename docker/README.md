# 网关

网关运行在能访问手机且能接收本地 mDNS 的 Linux 主机。推荐 Google 官方 Platform-Tools。当前 Docker 镜像使用 Linux x86-64 的 Platform-Tools 37.0.3。

| 配置 | 默认值 | 用途 |
|---|---|---|
| `DEVICE_SERIAL` | 必填 | 手机通过 USB ADB 显示的序列号 |
| `PHONE` | 空 | 已知手机 IPv4 地址，通常配合 DHCP 保留地址 |
| `POLL` | `5` | 巡检间隔，单位为秒 |
| `STATUS_PORT` | `8770` | HTTP 状态端口 |
| `ADB_SERVER_PORT` | `5038` | 网关本地 ADB server 端口 |

`GET /status` 的 `fixed_reachable` 表示最近一次巡检已通过手机的 `5555` 执行 ADB 命令并核对设备序列号。`last_action` 和 `detail` 提供恢复进度。

在固定端口可用时，网关也会检查手机恢复服务是否还在运行，必要时通过已授权的 ADB 会话重新启动它。

## Linux 用户服务

把官方 Platform-Tools 的 `adb` 放到 `~/.local/share/adbgw/platform-tools/adb`。从仓库根目录执行：

```bash
install -D -m 0644 docker/adbgw-server.py ~/.local/share/adbgw/adbgw-server.py
install -D -m 0644 docker/adbgw.service ~/.config/systemd/user/adbgw.service
mkdir -p ~/.config/adbgw
```

在 `~/.config/adbgw/gateway.env` 写入实际序列号及手机地址：

```ini
DEVICE_SERIAL=<手机序列号>
PHONE=<手机IP>
```

```bash
loginctl enable-linger "$USER"
systemctl --user daemon-reload
systemctl --user enable --now adbgw
journalctl --user -u adbgw -f
```

用户服务以当前用户运行，共用该用户 `~/.android` 下已授权的 ADB 密钥。

## Docker Compose

```bash
cd docker
DEVICE_SERIAL=<手机序列号> PHONE=<手机IP> docker compose up -d --build
curl http://127.0.0.1:8770/status
```

可把环境变量写进 Compose 的 `.env` 以便后续重启。容器使用 Linux host 网络接收 mDNS。配置 `ADB_KEYS_DIR` 可以指定已授权密钥所在目录，默认为宿主用户的 `~/.android`。已有容器中的密钥可以导出到该目录继续使用。
