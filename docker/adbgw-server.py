#!/usr/bin/env python3
"""ADB 网关 —— 服务端（容器内运行）

职责：
  1) 被动接收手机注册：POST /register  {ip, tls_port, boot_id, a11y}
     —— 只要手机启动并连上网，就会主动上报自己是谁、在哪。
  2) 主动巡检：每 POLL 秒查询手机 http://<ip>:8765/state
     - 5555 可达            -> 什么都不做
     - 有 tls_port          -> adb connect + tcpip 5555 + 验证
     - 都没有               -> GET /repair 让手机自己重开无线调试
  3) GET /status 给人和脚本看

环境变量：
  POLL         巡检间隔秒（默认 20）
  FIXED        固定端口（默认 5555）
  STATUS_PORT  状态接口端口（默认 8770）
  PHONE        可选：预置手机地址（一般不需要，等手机注册即可）
"""
import json
import os
import subprocess
import threading
import time
import urllib.request
import http.server
import urllib.parse

ADB = "/usr/bin/adb"
FIXED = int(os.environ.get("FIXED", "5555"))
POLL = int(os.environ.get("POLL", "20"))
STATUS_PORT = int(os.environ.get("STATUS_PORT", "8770"))

_lock = threading.Lock()
_state = {
    "phone": os.environ.get("PHONE", "").strip(),
    "phone_last_seen": None,
    "boot_id": None,
    "last_state": None,
    "last_action": "-",
    "fixed_reachable": False,
    "pair_last": "-",
    "ticks": 0,
}
_wake = threading.Event()


def log(msg):
    print(time.strftime("%Y-%m-%d %H:%M:%S ") + msg, flush=True)


def sh(cmd, timeout=25):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or "") + (p.stderr or "")
    except Exception as e:
        return f"<err {e}>"


def adb_ok(target):
    out = sh(f"{ADB} -s {target} shell true", 12).lower()
    return not any(x in out for x in ("error", "unable", "not found", "offline", "unauthorized"))


def http_json(url, timeout=8):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "ignore") or "{}")



def try_pair(ip):
    """用手机上报的配对码做 adb pair（首次连接自动授权，无需预置 adbkey）"""
    code = _state.get("pair_code")
    pport = _state.get("pair_port")
    if not code or not pport:
        return False
    if time.time() - _state.get("pair_at", 0) < 30:
        return False
    _state["pair_at"] = time.time()
    out = sh(f"{ADB} pair {ip}:{pport} {code}", 30)
    _state["pair_last"] = out.strip()
    log(f"  -> pair {ip}:{pport} code={code}: {out.strip()}")
    return "Successfully paired" in out


def ask_pair(phone):
    """tcpip 失败说明本机 adbkey 未授权 -> 请手机上报配对码"""
    if time.time() - _state.get("ask_pair_at", 0) < 60:
        return
    _state["ask_pair_at"] = time.time()
    try:
        with urllib.request.urlopen(f"http://{phone}:8765/pair", timeout=8) as r:
            ack = r.read().decode("utf-8", "ignore").strip()
        _state["pair_state"] = ack
        if ack == "locked":
            _state["ask_pair_at"] = time.time() - 30  # 30s 后重试
            log("phone locked, will retry pairing after unlock")
        else:
            log("asked phone for pairing code -> " + ack)
    except Exception as e:
        log(f"ask pair failed {type(e).__name__}")

def ensure_fixed(ip, tls_port):
    log(f"  -> connect tls {ip}:{tls_port}")
    log(f"     {sh(f'{ADB} connect {ip}:{tls_port}', 15).strip()}")
    log(f"  -> tcpip: {sh(f'{ADB} -s {ip}:{tls_port} tcpip {FIXED}', 30).strip()}")
    time.sleep(4)
    sh(f"{ADB} connect {ip}:{FIXED}", 20)
    ok = adb_ok(f"{ip}:{FIXED}")
    log(f"  -> verify fixed: {'OK' if ok else 'FAIL'}")
    if ok:
        sh(f"{ADB} disconnect {ip}:{tls_port}", 8)
    return ok


def tick():
    phone = _state["phone"]
    if not phone:
        return
    try:
        st = http_json(f"http://{phone}:8765/state")
    except Exception as e:
        log(f"poll fail ({type(e).__name__}) phone={phone}")
        return
    _state["last_state"] = st
    _state["phone_last_seen"] = time.strftime("%Y-%m-%d %H:%M:%S")
    ip = st.get("ip") or phone
    if ip != phone:
        _state["phone"] = ip
    tls = st.get("tls_port")
    if adb_ok(f"{ip}:{FIXED}"):
        if not _state["fixed_reachable"]:
            log(f"state ok: fixed {ip}:{FIXED} reachable (a11y={st.get('a11y')})")
        _state["fixed_reachable"] = True
        _state["last_action"] = "fixed"
        return
    _state["fixed_reachable"] = False
    if try_pair(ip):
        st = http_json(f"http://{phone}:8765/state") if False else st
        _state["last_state"] = st
        tls = st.get("tls_port") or tls
    if isinstance(tls, int) and tls > 0:
        _state["last_action"] = "tcpip"
        _state["fixed_reachable"] = ensure_fixed(ip, tls)
        if not _state["fixed_reachable"]:
            ask_pair(phone)
        return
    try:
        with urllib.request.urlopen(f"http://{phone}:8765/repair", timeout=8) as r:
            ack = r.read().decode("utf-8", "ignore").strip()
        _state["last_action"] = f"repair:{ack}"
        log(f"asked phone to repair -> {ack} (a11y={st.get('a11y')} adb_wifi={st.get('adb_wifi')})")
    except Exception as e:
        _state["last_action"] = f"repair fail {type(e).__name__}"


class Handler(http.server.BaseHTTPRequestHandler):
    def _send(self, code, obj, ctype="application/json; charset=utf-8"):
        body = (json.dumps(obj, ensure_ascii=False) if not isinstance(obj, str) else obj) + "\n"
        b = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(b)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        if u.path == "/status":
            with _lock:
                self._send(200, _state)
        else:
            self._send(200, "adb gateway server: POST /register, GET /status")

    def do_POST(self):
        u = urllib.parse.urlparse(self.path)
        if u.path != "/register":
            self._send(404, {"error": "not found"})
            return
        try:
            n = int(self.headers.get("Content-Length") or 0)
            d = json.loads(self.rfile.read(n).decode("utf-8", "ignore") or "{}")
        except Exception as e:
            self._send(400, {"error": str(e)})
            return
        ip = str(d.get("ip") or "").strip()
        if not ip:
            self._send(400, {"error": "no ip"})
            return
        new_boot = d.get("boot_id")
        if _state["phone"] != ip or _state["boot_id"] != new_boot:
            log(f"REGISTER phone={ip} tls={d.get('tls_port')} boot={new_boot}")
        _state["phone"] = ip
        _state["boot_id"] = new_boot
        _state["phone_last_seen"] = time.strftime("%Y-%m-%d %H:%M:%S")
        if d.get("pair_code") and d.get("pair_port"):
            _state["pair_code"] = str(d.get("pair_code"))
            _state["pair_port"] = str(d.get("pair_port"))
            _pc = str(d.get("pair_code"))
            _pp = str(d.get("pair_port"))
            log("got pairing code " + _pc + " port " + _pp)
        self._send(200, {"ok": True})
        _wake.set()

    def log_message(self, *a):
        pass


def main():
    log(f"server start: FIXED={FIXED} POLL={POLL}s STATUS_PORT={STATUS_PORT}")
    threading.Thread(
        target=lambda: http.server.ThreadingHTTPServer(("0.0.0.0", STATUS_PORT), Handler).serve_forever(),
        daemon=True).start()
    while True:
        with _lock:
            try:
                tick()
                _state["ticks"] += 1
            except Exception as e:
                log(f"tick err {e}")
        _wake.wait(timeout=POLL)
        _wake.clear()


if __name__ == "__main__":
    main()
