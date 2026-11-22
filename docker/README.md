# Docker ADB Server

镜像使用 Google 官方 Platform-Tools 36.0.2，支持 Linux x86-64。容器通过 host 网络进行 mDNS 发现，镜像构建和 Compose 部署分别执行。

## 构建或发布镜像

在仓库根目录构建本地镜像：

```bash
docker build -t adb-gateway:latest ./docker
```

如果需要发布到 Docker Hub，替换下面的示例命名空间，再构建并推送：

```bash
docker build -t YOUR_NAMESPACE/adb-gateway:latest ./docker
docker push YOUR_NAMESPACE/adb-gateway:latest
```

部署已发布镜像时，把 [Compose](docker-compose.yml) 中的 `image` 改成发布时使用的完整标签。

## 启动和更新

启动前按[访问范围](../README.md#访问范围)限制共享端口。Compose 使用已构建或可从仓库拉取的镜像。

```bash
cd docker
docker compose up -d
```

从 Docker Hub 更新时，先运行 `docker compose pull`，再运行 `docker compose up -d`。

`adb-data` 命名卷挂载到容器的 `/root/.android`。ADB 首次启动生成密钥，配对记录也保存在该卷。更新镜像或重建容器时保留此卷，避免使用 `docker compose down -v`。

## 端口配置

唯一的可选运行参数是 `ADB_SERVER_PORT`，默认 **5038**，由镜像提供。需要修改时在 Compose 的服务下添加：

```yaml
environment:
  ADB_SERVER_PORT: "5040"
```

容器监听端口就是宿主机的实际端口。改端口后，同步更新防火墙规则和客户端的 `-P` 或 `ADB_SERVER_SOCKET`。

## 首次配对

在手机的系统无线调试页面选择「使用配对码配对设备」。保持页面打开，把示例地址替换为页面显示的配对地址与端口。

```bash
PAIR_ADDRESS=192.0.2.20:37000
docker compose exec adb-gateway adb -P 5038 pair "$PAIR_ADDRESS"
docker compose exec adb-gateway adb -P 5038 devices -l
```

配对端口仅用于首次配对。后续 ADB Server 使用 mDNS 发现已配对手机的连接端口。

## 客户端连接

将下面的地址替换为网关主机的地址。可在命令中显式指定网关，也可以为当前终端设置 `ADB_SERVER_SOCKET`。

```bash
GATEWAY_IP=192.0.2.10
adb -H "$GATEWAY_IP" -P 5038 devices -l
export ADB_SERVER_SOCKET="tcp:$GATEWAY_IP:5038"
adb devices -l
```

通过共享网关可照常使用 `adb shell` 和 `adb install`。有多台设备时，用 `-s` 加上 `devices -l` 列出的设备标识选择目标。

## 发现与重连

设备未出现时，运行 `docker compose exec adb-gateway adb -P 5038 mdns services`，检查网关能否收到手机发出的 mDNS 公告。

真机对比中，37.0.3 曾在 Wi-Fi 恢复后发现新端口却漏连，因此固定使用已验证断网重连的 36.0.2。36.0.2 也有一次在手机重启后漏连。为覆盖这一情况，容器健康检查约每 15 秒读取官方 ADB 的发现结果，对已配对且未在线的设备补一次 `adb connect`。
