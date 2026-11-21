package com.dsh.adbkeep;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 被动 HTTP 服务（给 LXC 轮询用）：
 *   GET /state   -> 状态 JSON
 *   GET /repair  -> 请求手机修一次（重新开无线调试）
 * 不做定时任务，完全被动。
 */
final class Server {
    static final int PORT = 8765;

    private static volatile ServerSocket sock;

    static synchronized void start(final Context c) {
        if (sock != null) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    sock = new ServerSocket(PORT);
                    L.d(c, "server up :" + PORT);
                    while (true) {
                        Socket s = null;
                        try {
                            s = sock.accept();
                            s.setSoTimeout(3000);
                            handle(c, s);
                        } catch (Throwable ignored) {
                        } finally {
                            if (s != null) try { s.close(); } catch (Throwable ignored) { }
                        }
                    }
                } catch (Throwable t) {
                    L.d(c, "server err " + t);
                    sock = null;
                }
            }
        }).start();
    }

    static String stateJson(Context c) {
        JSONObject o = new JSONObject();
        try {
            int tls = Core.tlsPort(c);
            o.put("ok", Core.healthy(c));
            o.put("ip", Core.ip());
            o.put("tls_port", tls > 0 ? tls : JSONObject.NULL);
            o.put("fixed_alive", Core.alive(Core.FIXED));
            o.put("adb_wifi", Core.adbWifiOn(c));
            o.put("a11y", Core.a11yOn(c));
            o.put("screen_on", screenOn(c));
            o.put("boot_id", bootId());
        } catch (Throwable t) {
            try { o.put("err", String.valueOf(t)); } catch (Throwable ignored) { }
        }
        return o.toString();
    }

    private static boolean screenOn(Context c) {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String bootId() {
        return String.valueOf(System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime());
    }

    private static void handle(Context c, Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String first = in.readLine();
        String path = "/";
        if (first != null) {
            String[] p = first.split(" ");
            if (p.length >= 2) path = p[1];
        }
        String body;
        if (path.startsWith("/pair")) {
            android.app.KeyguardManager km = (android.app.KeyguardManager)
                    c.getSystemService(Context.KEYGUARD_SERVICE);
            android.os.PowerManager pm = (android.os.PowerManager)
                    c.getSystemService(Context.POWER_SERVICE);
            boolean locked = (km != null && km.isKeyguardLocked())
                    || (pm != null && !pm.isInteractive());
            GwA11yService svc = GwA11yService.get();
            if (locked) {
                body = "locked";
            } else if (svc == null) {
                body = "no-a11y";
            } else {
                svc.startPairing();
                body = "ok";
            }
        } else if (path.startsWith("/repair")) {
            GwA11yService svc = GwA11yService.get();
            if (svc != null) svc.fixAsync();
            body = svc != null ? "ok" : "no-a11y";
        } else {
            body = stateJson(c);
        }
        byte[] b = (body + "\n").getBytes("UTF-8");
        OutputStream os = s.getOutputStream();
        PrintWriter w = new PrintWriter(os, true);
        w.print("HTTP/1.1 200 OK\r\n");
        w.print("Content-Type: application/json; charset=utf-8\r\n");
        w.print("Content-Length: " + b.length + "\r\n");
        w.print("Connection: close\r\n\r\n");
        w.flush();
        os.write(b);
        os.flush();
        L.d(c, "http " + path + " -> " + body);
    }
}
