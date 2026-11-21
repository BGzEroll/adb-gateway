package com.dsh.adbkeep;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** 扁平化 + iOS 风格的设置页 */
public class MainActivity extends Activity {
    private static final int BG = 0xFFF2F2F7;
    private static final int CARD = 0xFFFFFFFF;
    private static final int INK = 0xFF000000;
    private static final int MUTED = 0xFF8E8E93;
    private static final int LINE = 0xFFE5E5EA;
    private static final int BLUE = 0xFF007AFF;
    private static final int GREEN = 0xFF34C759;
    private static final int RED = 0xFFFF3B30;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout group() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(10));
        g.setBackground(bg);
        g.setClipToOutline(true);
        return g;
    }

    /** iOS 分组里的一行：左标题 右状态 */
    private View row(LinearLayout parent, String title, TextView value, boolean last) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(13), dp(16), dp(13));
        TextView t = label(title, 16, INK);
        r.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            value.setTextSize(16);
            value.setGravity(Gravity.END);
            r.addView(value);
        }
        parent.addView(r);
        if (!last) {
            View ln = new View(this);
            ln.setBackgroundColor(LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
            lp.leftMargin = dp(16);
            parent.addView(ln, lp);
        }
        return r;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int side = dp(16);
        root.setPadding(side, dp(24), side, dp(32));

        TextView title = label("ADB 网关", 32, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = label("无线调试状态同步 · 端口守护", 13, MUTED);
        sub.setPadding(0, dp(4), 0, dp(18));
        root.addView(sub);

        // ---------- 状态 ----------
        root.addView(sectionTitle("运行状态"));
        LinearLayout st = group();
        final TextView vWifi = label("", 16, MUTED);
        final TextView vFixed = label("", 16, MUTED);
        final TextView vPeer = label("", 16, MUTED);
        final TextView vA11y = label("", 16, MUTED);
        row(st, "无线调试", vWifi, false);
        row(st, "固定端口 5555", vFixed, false);
        row(st, "对端连接", vPeer, false);
        row(st, "无障碍服务", vA11y, true);
        root.addView(st);

        // ---------- 对端 ----------
        root.addView(sectionTitle("对端巡检器"));
        LinearLayout cf = group();
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(16), dp(12), dp(16), dp(12));
        final EditText input = new EditText(this);
        input.setHint("例如 192.168.1.20");
        input.setText(Prefs.peer(this));
        input.setTextSize(17);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(0, dp(6), 0, dp(6));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        pad.addView(input);
        TextView hint = label("填写巡检器地址，默认端口 8770；留空则仅本机自检", 12, MUTED);
        pad.addView(hint);
        cf.addView(pad);
        row(cf, "保存地址", label("保存", 16, BLUE), true);
        cf.getChildAt(cf.getChildCount() - 1).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setPeer(MainActivity.this, input.getText().toString());
                Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                refresh(vWifi, vFixed, vPeer, vA11y);
            }
        });
        root.addView(cf);

        // ---------- 操作 ----------
        root.addView(sectionTitle("操作"));
        LinearLayout ac = group();
        row(ac, "刷新状态", null, false).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refresh(vWifi, vFixed, vPeer, vA11y); }
        });
        row(ac, "立即修复", label("重新开启无线调试", 14, MUTED), true)
                .setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        GwA11yService s = GwA11yService.get();
                        if (s != null) {
                            s.fixAsync();
                            Toast.makeText(MainActivity.this, "已请求修复", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "无障碍服务未就绪", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        root.addView(ac);

        TextView foot = label("对端可轮询：http://" + Core.ip() + ":" + Server.PORT + "/state", 12, MUTED);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(24), 0, 0);
        root.addView(foot);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        setContentView(sv);

        Core.selfHeal(this);
        refresh(vWifi, vFixed, vPeer, vA11y);
    }

    private TextView sectionTitle(String s) {
        TextView t = label(s, 13, MUTED);
        t.setPadding(dp(16), dp(22), 0, dp(7));
        return t;
    }

    private void refresh(final TextView vWifi, final TextView vFixed,
                         final TextView vPeer, final TextView vA11y) {
        new Thread(new Runnable() {
            public void run() {
                final boolean wifi = Core.adbWifiOn(MainActivity.this);
                final boolean fixed = Core.alive(Core.FIXED);
                final boolean a11y = Core.a11yOn(MainActivity.this);
                JSONObject sv = Core.serverView(MainActivity.this);
                final boolean peerOk = sv != null;
                final boolean peerFixed = peerOk && sv.optBoolean("fixed_reachable", false);
                final boolean peerConfigured = Prefs.base(MainActivity.this) != null;
                runOnUiThread(new Runnable() {
                    public void run() {
                        vWifi.setText(wifi ? "已开启" : "未开启");
                        vWifi.setTextColor(wifi ? GREEN : RED);
                        vFixed.setText(fixed ? "可达" : "不可达");
                        vFixed.setTextColor(fixed ? GREEN : RED);
                        if (!peerConfigured) {
                            vPeer.setText("未配置");
                            vPeer.setTextColor(MUTED);
                        } else if (!peerOk) {
                            vPeer.setText("不可达");
                            vPeer.setTextColor(RED);
                        } else {
                            vPeer.setText(peerFixed ? "已确认" : "已连接");
                            vPeer.setTextColor(peerFixed ? GREEN : MUTED);
                        }
                        vA11y.setText(a11y ? "运行中" : "未启用");
                        vA11y.setTextColor(a11y ? GREEN : RED);
                    }
                });
            }
        }).start();
    }
}
