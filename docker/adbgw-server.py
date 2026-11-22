#!/usr/bin/env python3
"""Maintain a direct ADB connection to a phone on TCP port 5555."""

import http.server
import ipaddress
import json
import os
import re
import subprocess
import threading
import time


FIXED_PORT = 5555
HELPER_SERVICE = "com.dsh.adbkeep/.GatewayService"
TLS_SERVICE = "_adb-tls-connect._tcp"
LEGACY_SERVICE = "_adb._tcp"
SERVICE_LINE = re.compile(
    r"^(\S+)(?: \(\d+\))?\s+(_adb(?:-tls-connect)?\._tcp)\.?\s+"
    r"(?:\S+\s+)*?(\d+\.\d+\.\d+\.\d+):(\d+)(?:\s|$)"
)


def discovery_rows(output, serial):
    device = f"adb-{serial}"
    for line in output.splitlines():
        match = SERVICE_LINE.match(line.strip())
        if not match:
            continue
        name, service, address, port = match.groups()
        if service == LEGACY_SERVICE and name != device:
            continue
        if service == TLS_SERVICE and not name.startswith(device + "-"):
            continue
        yield service, address, int(port)


class Adb:
    def __init__(self, path, port):
        self.command = [path, "-P", str(port)]

    def run(self, *args):
        try:
            result = subprocess.run(
                self.command + list(args), capture_output=True, text=True, timeout=5
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            return False, str(error)
        return result.returncode == 0, (result.stdout or result.stderr).strip()

    def identify(self, target, serial):
        ok, value = self.run("-s", target, "shell", "getprop", "ro.serialno")
        return ok and value == serial

    def connect(self, target, serial):
        if self.identify(target, serial):
            return True
        _, reply = self.run("connect", target)
        if self.identify(target, serial):
            return True
        if reply.startswith("already connected"):
            self.run("disconnect", target)
            self.run("connect", target)
            return self.identify(target, serial)
        return False

    def ensure_helper(self, target):
        ok, report = self.run("-s", target, "shell", "dumpsys", "activity", "services", HELPER_SERVICE)
        if ok and "isForeground=true" in report:
            return None
        ok, reply = self.run(
            "-s", target, "shell", "am", "start-foreground-service", "--user", "0",
            "-a", "com.dsh.adbkeep.RECOVER", "-n", HELPER_SERVICE,
        )
        return None if ok else reply


class Gateway:
    def __init__(self, adb, serial, address):
        self.adb = adb
        self.serial = serial
        self.address = address
        self.status = {
            "device_serial": serial,
            "phone": address or None,
            "fixed_port": FIXED_PORT,
            "fixed_reachable": False,
            "last_action": "starting",
            "checked_at": None,
        }

    def record(self, action, reachable=False, detail=None):
        status = {
            **self.status,
            "phone": self.address or None,
            "fixed_reachable": reachable,
            "last_action": action,
            "detail": detail,
            "checked_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }
        if any(status[key] != self.status.get(key)
               for key in ("phone", "last_action", "detail", "fixed_reachable")):
            message = f"{self.serial} {action} phone={self.address or '-'}"
            print(f"{status['checked_at']} {message} {detail or ''}".rstrip(), flush=True)
        self.status = status

    def use_fixed(self, address):
        target = f"{address}:{FIXED_PORT}"
        if not self.adb.connect(target, self.serial):
            return False
        self.address = address
        self.record("ready", reachable=True, detail=self.adb.ensure_helper(target))
        return True

    def tick(self):
        if self.address and self.use_fixed(self.address):
            return

        ok, services = self.adb.run("mdns", "services")
        if not ok:
            self.record("discovery_error", detail=services)
            return
        rows = list(discovery_rows(services, self.serial))
        addresses = dict.fromkeys(address for _, address, _ in rows)
        for address in addresses:
            if address != self.address and self.use_fixed(address):
                return

        attempted = []
        for service, address, port in dict.fromkeys(rows):
            if service != TLS_SERVICE:
                continue
            target = f"{address}:{port}"
            attempted.append(target)
            if not self.adb.connect(target, self.serial):
                continue
            self.address = address
            switched, reply = self.adb.run("-s", target, "tcpip", str(FIXED_PORT))
            self.record("switching" if switched else "switch_failed", detail=reply)
            return

        if attempted:
            self.record("tls_unavailable", detail=", ".join(attempted))
        else:
            self.record("waiting_for_phone")

    def monitor(self, poll):
        while True:
            self.tick()
            time.sleep(poll)


def status_handler(gateway):
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/status":
                self.send_error(404)
                return
            body = json.dumps(gateway.status).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_):
            pass

    return Handler


def main():
    serial = os.environ.get("DEVICE_SERIAL", "").strip()
    if not serial:
        raise SystemExit("DEVICE_SERIAL is required. Read it from adb devices -l over USB.")
    phone = os.environ.get("PHONE", "").strip()
    if phone:
        phone = str(ipaddress.IPv4Address(phone))
    poll = float(os.environ.get("POLL", "5"))
    if poll <= 0:
        raise SystemExit("POLL must be positive.")
    adb = Adb(os.environ.get("ADB", "adb"), int(os.environ.get("ADB_SERVER_PORT", "5038")))
    gateway = Gateway(adb, serial, phone)
    listen = os.environ.get("STATUS_BIND", "0.0.0.0")
    port = int(os.environ.get("STATUS_PORT", "8770"))
    with http.server.ThreadingHTTPServer((listen, port), status_handler(gateway)) as server:
        threading.Thread(target=gateway.monitor, args=(poll,), daemon=True).start()
        server.serve_forever()


if __name__ == "__main__":
    main()
