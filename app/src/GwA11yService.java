package com.dsh.adbkeep;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 唯一能真正启动 adbd 的途径：点开系统「无线调试」开关（以系统身份写设置）。
 * 只做两件事：绑定后/事件触发时检查健康（失败才修），以及流程跑完回桌面熄屏。
 */
public class GwA11yService extends AccessibilityService {
    private static volatile GwA11yService instance;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean busy = false;
    private volatile long lastEventTry = 0L;
    private volatile long lastFix = 0L;

    static GwA11yService get() { return instance; }

    @Override
    protected void onServiceConnected() {
        instance = this;
        try {
            AccessibilityServiceInfo si = getServiceInfo();
            if (si != null) {
                si.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
                setServiceInfo(si);
            }
        } catch (Throwable ignored) { }
        Server.start(this);
        L.d(this, "a11y up");
        pushRegister();
        h.postDelayed(new Runnable() { public void run() { pushRegister(); h.postDelayed(this, 300000L); } }, 300000L);
        h.postDelayed(new Runnable() {
            public void run() {
                if (!busy) fixAsync();
            }
        }, 2500L);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        try {
            if (e == null || busy) return;
            long now = System.currentTimeMillis();
            if (now - lastEventTry < 20000L || now - lastFix < 90000L) return;
            lastEventTry = now;
            L.d(this, "event trigger pkg=" + e.getPackageName());
            h.postDelayed(new Runnable() {
                public void run() { fixAsync(); }
            }, 1200L);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onInterrupt() { }

    void fixAsync() {
        if (busy) return;
        busy = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (Core.healthy(GwA11yService.this)) {
                        L.d(GwA11yService.this, "skip: local healthy");
                        return;
                    }
                    JSONObject sv = Core.serverView(GwA11yService.this);
                    if (sv != null && sv.optBoolean("fixed_reachable", false)) {
                        L.d(GwA11yService.this, "skip: peer confirms fixed ok");
                        return;
                    }
                    lastFix = System.currentTimeMillis();
                    Core.writeAdbWifi(GwA11yService.this, 1);
                    goHome();
                    if (openWirelessPage()) {
                        Core.sleep(2200);
                        clickSwitch();
                        Core.sleep(1500);
                    }
                    reportPairingCode();
                    L.d(GwA11yService.this, "fix done port=" + Core.tlsPort(GwA11yService.this));
                    settleHome();
                    pushRegister();
                } catch (Throwable t) {
                    L.d(GwA11yService.this, "fix err " + t);
                } finally {
                    busy = false;
                }
            }
        }).start();
    }

    void pushRegister() { pushRegister(null, null); }

    /** 主动向服务端注册自身位置（可带上配对码，供服务端自动 adb pair） */
    void pushRegister(final String pairCode, final String pairPort) {
        new Thread(new Runnable() {
            public void run() {
                java.net.HttpURLConnection conn = null;
                try {
                    String base = Prefs.base(GwA11yService.this);
                    if (base == null) return;
                    String url = Prefs.hasPort(base) ? base + "/register" : base + ":8770/register";
                    JSONObject o = new JSONObject();
                    o.put("ip", Core.ip());
                    int tls = Core.tlsPort(GwA11yService.this);
                    o.put("tls_port", tls > 0 ? tls : JSONObject.NULL);
                    o.put("boot_id", String.valueOf(System.currentTimeMillis()
                            - android.os.SystemClock.elapsedRealtime()));
                    o.put("a11y", Core.a11yOn(GwA11yService.this));
                    if (pairCode != null) o.put("pair_code", pairCode);
                    if (pairPort != null) o.put("pair_port", pairPort);
                    byte[] body = o.toString().getBytes("UTF-8");
                    conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(4000);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    java.io.OutputStream os = conn.getOutputStream();
                    os.write(body);
                    os.flush();
                    os.close();
                    L.d(GwA11yService.this, "register -> " + conn.getResponseCode()
                            + " " + Core.ip() + ":" + tls);
                } catch (Throwable t) {
                    L.d(GwA11yService.this, "register err " + t);
                } finally {
                    if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
                }
            }
        }).start();
    }
    /** 打开「使用配对码配对设备」对话框，读配对码/端口并上报，然后关闭对话框 */
    private void reportPairingCode() {
        try {
            AccessibilityNodeInfo row = findByTexts("使用配对码配对设备", "Pair device with pairing code");
            L.d(this, "pair row found=" + (row != null));
            if (row == null || !clickAncestor(row, 4)) {
                L.d(this, "pair row click failed");
                return;
            }
            L.d(this, "pair row clicked");
            AccessibilityNodeInfo codeNode = null;
            AccessibilityNodeInfo addrNode = null;
            for (int i = 0; i < 10; i++) {
                Core.sleep(500);
                codeNode = findById("com.android.settings:id/pairing_code");
                addrNode = findById("com.android.settings:id/ip_addr");
                if (codeNode != null && addrNode != null) break;
            }
            if (codeNode == null || addrNode == null) {
                L.d(this, "pair dialog not found");
                return;
            }
            String code = String.valueOf(codeNode.getText());
            String addr = String.valueOf(addrNode.getText());
            String port = addr.contains(":") ? addr.substring(addr.lastIndexOf(':') + 1) : null;
            L.d(this, "pair code=" + code + " port=" + port);
            pushRegister(code, port);
            AccessibilityNodeInfo cancel = findByTexts("取消", "Cancel");
            if (cancel != null) clickAncestor(cancel, 3);
        } catch (Throwable t) {
            L.d(this, "pair err " + t);
        }
    }
    /** 无密码锁屏时，用无障碍手势上滑解锁 */
    private boolean unlockIfLocked() {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km == null || !km.isKeyguardLocked()) return true;
            L.d(this, "keyguard locked, try gesture unlock");
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(w / 2f, h * 0.85f);
            path.lineTo(w / 2f, h * 0.25f);
            android.accessibilityservice.GestureDescription gd =
                    new android.accessibilityservice.GestureDescription.Builder()
                            .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))
                            .build();
            dispatchGesture(gd, null, null);
            Core.sleep(1800);
            return km == null || !km.isKeyguardLocked();
        } catch (Throwable t) {
            L.d(this, "unlock err " + t);
            return false;
        }
    }
    /** 服务端请求配对：打开无线调试页 -> 配对对话框 -> 上报配对码 */
    void startPairing() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (busy) return;
                    busy = true;
                    unlockIfLocked();
                    dismissOverlays();
                    if (openWirelessPage()) {
                        Core.sleep(1500);
                        reportPairingCode();
                    }
                    goHome();
                } catch (Throwable t) {
                    L.d(GwA11yService.this, "pair flow err " + t);
                } finally {
                    busy = false;
                }
            }
        }).start();
    }
    /** 流程结束：回桌面 + 熄屏（优先免设备管理员的全局动作） */
    private void settleHome() {
        goHome();
        Core.sleep(1200);
        boolean locked = false;
        try {
            locked = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (Throwable ignored) { }
        L.d(this, "settle locked=" + locked);
    }

    private void goHome() {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME);
            Core.sleep(1200);
        } catch (Throwable ignored) { }
    }

    /** 当前前台是不是系统设置 */
    private boolean inSettings() {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            return r != null && "com.android.settings".contentEquals(r.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 关掉通知栏/弹窗，再回桌面，保证起点确定 */
    private void dismissOverlays() {
        try {
            for (int i = 0; i < 3; i++) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                Core.sleep(350);
            }
        } catch (Throwable ignored) { }
        goHome();
    }
    private boolean openWirelessPage() {
        dismissOverlays();
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            L.d(this, "open dev opts err " + t);
            return false;
        }
        if (findByTexts("使用配对码配对设备", "Pair device with pairing code") != null) {
            L.d(this, "already on wireless page");
            return true;
        }
        int wait = 0;
        while (wait < 16 && !inSettings()) {
            Core.sleep(500);
            wait++;
        }
        L.d(this, "settings focus=" + inSettings() + " after " + (wait * 500) + "ms");
        Core.sleep(1200);
        for (int s = 0; s < 10; s++) {
            AccessibilityNodeInfo row = findByTexts("无线调试", "Wireless debugging");
            if (row != null && clickAncestor(row, 4)) {
                L.d(this, "clicked row");
                return true;
            }
            scrollForward();
            Core.sleep(900);
        }
        L.d(this, "openWirelessPage failed");
        return false;
    }

    private void clickSwitch() {
        for (int t = 0; t < 8; t++) {
            AccessibilityNodeInfo sw = findById("android:id/switch_widget");
            if (sw != null) {
                try {
                    if (sw.isChecked()) return;
                } catch (Throwable ignored) { }
                if (clickAncestor(sw, 5)) {
                    Core.sleep(1200);
                    return;
                }
            }
            Core.sleep(1000);
        }
    }

    private AccessibilityNodeInfo findByTexts(String... texts) {
        for (AccessibilityNodeInfo root : roots()) {
            for (String t : texts) {
                try {
                    List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByText(t);
                    if (l != null && !l.isEmpty()) return l.get(0);
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findById(String id) {
        for (AccessibilityNodeInfo root : roots()) {
            try {
                List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(id);
                if (l != null && !l.isEmpty()) return l.get(0);
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private List<AccessibilityNodeInfo> roots() {
        ArrayList<AccessibilityNodeInfo> out = new ArrayList<>();
        try {
            List<android.view.accessibility.AccessibilityWindowInfo> ws = getWindows();
            if (ws != null) for (android.view.accessibility.AccessibilityWindowInfo w : ws) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null) out.add(r);
            }
        } catch (Throwable ignored) { }
        if (out.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) out.add(r);
        }
        return out;
    }

    private boolean clickAncestor(AccessibilityNodeInfo n, int depth) {
        AccessibilityNodeInfo cur = n;
        for (int d = 0; d < depth && cur != null; d++) {
            try {
                if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                cur = cur.getParent();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private void scrollForward() {
        try {
            for (AccessibilityNodeInfo root : roots()) if (scrollDeep(root)) return;
        } catch (Throwable ignored) { }
    }

    private boolean scrollDeep(AccessibilityNodeInfo n) {
        if (n == null) return false;
        try {
            if (n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
            for (int i = 0; i < n.getChildCount(); i++) if (scrollDeep(n.getChild(i))) return true;
        } catch (Throwable ignored) { }
        return false;
    }
}
