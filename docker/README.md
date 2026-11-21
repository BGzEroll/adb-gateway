# docker —— 服务端

常驻巡检服务：接收手机注册、把 adbd 切成固定端口 5555、对外提供 `/status`。

## 运行

```bash
docker compose up -d --build
docker compose logs -f
curl -s http://127.0.0.1:8770/status | python3 -m json.tool
```

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `POLL` | `20` | 巡检间隔（秒） |
| `FIXED` | `5555` | 目标固定端口 |
| `STATUS_PORT` | `8770` | `/status` 监听端口 |
| `PHONE` | 空 | 可选，预置手机地址；一般等待手机 `/register` 上报 |

## 接口

- `POST /register` 手机上报：`{"ip":"...","tls_port":1234,"boot_id":"...","a11y":true,"pair_code":"...","pair_port":"..."}`
- `GET /status` 返回当前状态、是否可达、最近动作
- 手机侧接口：`GET /state`、`GET /repair`、`GET /pair`

## adbkey 说明

adb 鉴权是**服务端持有私钥、手机保存授权公钥**，所以私钥必须在服务端：

- 容器首次运行会自动在卷 `keys` 中生成 `~/.android/adbkey`（无需手动准备）
- 新 key 未被手机信任时，服务端会请求手机 `/pair` 获取配对码，执行 `adb pair` 完成授权
- 若自动配对不可用，可退化为一次性把已授权电脑的 `adbkey{,.pub}` 放进卷：

```bash
docker compose down
docker run --rm -v adbgw_keys:/keys -v ~/.android:/src alpine cp /src/adbkey /src/adbkey.pub /keys/
docker compose up -d
```

## 排错

```bash
docker exec adbgw adb devices          # 是否 unauthorized
docker logs --tail 50 adbgw
curl -s http://127.0.0.1:8770/status
```

- `unauthorized`：key 未被手机信任，等待/触发配对
- `poll fail`：手机 `/state` 不可达（可能未解锁、App 被强停）