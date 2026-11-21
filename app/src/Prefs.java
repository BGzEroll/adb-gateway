package com.dsh.adbkeep;

import android.content.Context;

/** 对端（LXC 巡检器）地址配置 */
final class Prefs {
    private static final String K = "peer";

    /** 优先用界面里填的，其次读 settings global adbgw_peer（方便对端下发/调试） */
    static String peer(Context c) {
        try {
            String v = c.getSharedPreferences("gw", 0).getString(K, "");
            if (v != null && !v.trim().isEmpty()) return v;
        } catch (Throwable ignored) { }
        try {
            String g = android.provider.Settings.Global.getString(c.getContentResolver(), "adbgw_peer");
            if (g != null) return g;
        } catch (Throwable ignored) { }
        return "";
    }

    static void setPeer(Context c, String v) {
        try {
            c.getSharedPreferences("gw", 0).edit().putString(K, v == null ? "" : v.trim()).apply();
        } catch (Throwable t) {
        }
    }

    /** http://host 或 http://host:port（已去掉尾部斜杠）；未配置返回 null */
    static String base(Context c) {
        String p = peer(c);
        if (p == null) return null;
        p = p.trim();
        if (p.isEmpty()) return null;
        if (!p.startsWith("http://") && !p.startsWith("https://")) p = "http://" + p;
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    static boolean hasPort(String base) {
        int i = base.indexOf("://");
        return i > 0 && base.indexOf(':', i + 3) > 0;
    }

    static String statusUrl(Context c) {
        String b = base(c);
        if (b == null) return null;
        return hasPort(b) ? b + "/status" : b + ":8770/status";
    }
}
