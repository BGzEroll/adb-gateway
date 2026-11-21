package com.dsh.adbkeep;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.provider.Settings;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 设置读写 / 自愈 / IP / 端口发现 / 探活 —— 全部直接调接口，不碰 UI */
final class Core {
    static final String SVC = "com.dsh.adbkeep/com.dsh.adbkeep.GwA11yService";
    static final int FIXED = 5555;

    private static volatile int cPort = -1;
    private static volatile long cAt = 0L;

    static boolean adbWifiOn(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), "adb_wifi_enabled", 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean writeAdbWifi(Context c, int v) {
        try {
            return Settings.Global.putInt(c.getContentResolver(), "adb_wifi_enabled", v);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 用 WRITE_SECURE_SETTINGS 把无障碍服务写回来（ColorOS 重启会清） */
    static boolean selfHeal(Context c) {
        try {
            Settings.Secure.putInt(c.getContentResolver(), "accessibility_enabled", 1);
            Settings.Secure.putString(c.getContentResolver(), "enabled_accessibility_services", SVC);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean a11yOn(Context c) {
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
            return v != null && v.contains("GwA11yService");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 查询对端巡检器的视图（/status），拿不到返回 null */
    static org.json.JSONObject serverView(Context c) {
        String url = Prefs.statusUrl(c);
        if (url == null) return null;
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new org.json.JSONObject(sb.toString());
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
        }
    }
    static String ip() {
        try {
            List<NetworkInterface> nis = Collections.list(NetworkInterface.getNetworkInterfaces());
            String other = null;
            for (NetworkInterface ni : nis) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address)) continue;
                    String s = a.getHostAddress();
                    if (s == null || s.startsWith("127.")) continue;
                    if (ni.getName().startsWith("wlan")) return s;
                    if (other == null) other = s;
                }
            }
            return other != null ? other : "0.0.0.0";
        } catch (Throwable t) {
            return "0.0.0.0";
        }
    }

    static boolean alive(int port) {
        if (port <= 0) return false;
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 600);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (s != null) try { s.close(); } catch (Throwable ignored) { }
        }
    }

    static int cachedPort() {
        if (cPort > 0 && System.currentTimeMillis() - cAt < 60000) return cPort;
        return -1;
    }

    static int discoverPort(Context c, long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger found = new AtomicInteger(-1);
        final NsdManager nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return -1;
        NsdManager.DiscoveryListener dl = new NsdManager.DiscoveryListener() {
            public void onStartDiscoveryFailed(String t, int e) { latch.countDown(); }
            public void onStopDiscoveryFailed(String t, int e) { }
            public void onDiscoveryStarted(String t) { }
            public void onDiscoveryStopped(String t) { }
            public void onServiceFound(NsdServiceInfo info) {
                try {
                    nsd.resolveService(info, new NsdManager.ResolveListener() {
                        public void onResolveFailed(NsdServiceInfo i, int e) { }
                        public void onServiceResolved(NsdServiceInfo i) {
                            if (i != null && i.getPort() > 0) found.set(i.getPort());
                            latch.countDown();
                        }
                    });
                } catch (Throwable ignored) { }
            }
            public void onServiceLost(NsdServiceInfo info) { }
        };
        try {
            nsd.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, dl);
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            try { nsd.stopServiceDiscovery(dl); } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
        int p = found.get();
        if (p > 0) {
            cPort = p;
            cAt = System.currentTimeMillis();
            L.d(c, "nsd port=" + p);
        }
        return p;
    }

    /** 只做直接探活，不点亮屏幕、不调无障碍 */
    static int tlsPort(Context c) {
        int p = cachedPort();
        if (p > 0 && alive(p)) return p;
        p = discoverPort(c, 2500);
        return (p > 0 && alive(p)) ? p : -1;
    }

    /** 健康 = 5555 通 或 TLS 端口通（5555 容错重试覆盖 tcpip 重启窗口） */
    static boolean healthy(Context c) {
        if (alive(FIXED)) return true;
        for (int i = 0; i < 2; i++) {
            sleep(600);
            if (alive(FIXED)) return true;
        }
        return tlsPort(c) > 0;
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Throwable ignored) { }
    }
}
