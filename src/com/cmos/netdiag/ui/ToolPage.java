package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.DiagEngine;
import com.cmos.netdiag.core.Dns;
import com.cmos.netdiag.core.Fmt;
import com.cmos.netdiag.core.Mtu;
import com.cmos.netdiag.core.NetInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 工具页：MTU 探测、公网 IP、DNS 优选 */
public class ToolPage extends Page {

    private EditText mtuHost;
    private LinearLayout mtuOut, ipOut, dnsOut;
    private volatile boolean busy;

    public ToolPage(MainActivity act) { super(act); }

    public String title() { return "工具"; }

    public View onCreate() {
        begin("MTU 探测、出口 IP 查询、DNS 优选");

        // ---- MTU ----
        LinearLayout mc = card("路径 MTU 探测");
        mc.addView(Ui.text(act, "找出不分片时链路上能通过的最大包，MTU 不对会导致大文件卡住、部分站点打不开。",
                11.5f, Ui.SUB));
        mc.addView(Ui.spacer(act, 6));
        mtuHost = Ui.input(act, "目标（建议用公网 IP）", "223.5.5.5");
        mc.addView(mtuHost);
        mc.addView(Ui.primary(act, "开始探测", this::runMtu));
        mtuOut = new LinearLayout(act);
        mtuOut.setOrientation(LinearLayout.VERTICAL);
        mc.addView(mtuOut);

        // ---- 公网 IP ----
        LinearLayout ic = card("出口 IP 与归属");
        ic.addView(Ui.primary(act, "查询公网 IP", this::runIp));
        ipOut = new LinearLayout(act);
        ipOut.setOrientation(LinearLayout.VERTICAL);
        ic.addView(ipOut);

        // ---- DNS 优选 ----
        LinearLayout dc = card("DNS 优选");
        dc.addView(Ui.text(act, "测试各公共 DNS 的解析速度，给出最优选择。", 11.5f, Ui.SUB));
        dc.addView(Ui.spacer(act, 6));
        dc.addView(Ui.primary(act, "测试所有 DNS 速度", this::runDns));
        dnsOut = new LinearLayout(act);
        dnsOut.setOrientation(LinearLayout.VERTICAL);
        dc.addView(dnsOut);

        return scroll;
    }

    private void runMtu() {
        if (busy) return;
        String h = mtuHost.getText().toString().trim();
        if (h.isEmpty()) { act.toast("请输入目标"); return; }
        busy = true;
        mtuOut.removeAllViews();
        mtuOut.addView(Ui.text(act, "探测中（约 10 秒）…", 12.5f, Ui.SUB));
        act.async(() -> {
            final Mtu.Result r = Mtu.probe(h, 1500);
            act.ui(() -> {
                busy = false;
                mtuOut.removeAllViews();
                TextView v = Ui.text(act, r.text(), 14,
                        r.error != null ? Ui.FAIL : r.mtu >= 1400 ? Ui.OK : Ui.WARN);
                v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                mtuOut.addView(v);
                mtuOut.addView(Ui.spacer(act, 6));
                for (String l : r.log) {
                    mtuOut.addView(Ui.text(act, "· " + l, 11, Ui.SUB));
                }
                if (r.error == null && r.mtu < 1400) {
                    mtuOut.addView(Ui.spacer(act, 6));
                    mtuOut.addView(Ui.text(act, "MTU 偏小通常意味着 PPPoE / VPN / 隧道开销，若网页打不开可尝试把路由器 MTU 调至 "
                            + r.mtu, 11.5f, Ui.WARN));
                }
            });
        });
    }

    private void runIp() {
        if (busy) return;
        busy = true;
        ipOut.removeAllViews();
        ipOut.addView(Ui.text(act, "查询中…", 12.5f, Ui.SUB));
        act.async(() -> {
            final NetInfo.Section s = NetInfo.publicIpSection();
            act.ui(() -> {
                busy = false;
                ipOut.removeAllViews();
                for (java.util.Map.Entry<String, String> e : s.kv.entrySet()) {
                    ipOut.addView(Ui.kv(act, e.getKey(), e.getValue()));
                }
                ipOut.addView(Ui.spacer(act, 6));
                ipOut.addView(Ui.ghost(act, "在新窗口查询 IP 详情", () -> {
                    String ip = NetInfo.cachedPublicIp();
                    act.openUrl("https://ipinfo.io/" + (ip == null ? "" : ip));
                }));
            });
        });
    }

    private void runDns() {
        if (busy) return;
        busy = true;
        dnsOut.removeAllViews();
        dnsOut.addView(Ui.text(act, "测试中…", 12.5f, Ui.SUB));
        act.async(() -> {
            List<String> servers = DiagEngine.serverList(act);
            final List<Object[]> rows = new ArrayList<>();
            for (String s : servers) {
                double sum = 0;
                int ok = 0;
                Dns.Result last = null;
                for (int i = 0; i < 3; i++) {
                    Dns.Result r = Dns.query(s, "www.baidu.com", "A", 2000);
                    last = r;
                    if (r.ok()) { sum += r.ms; ok++; }
                }
                rows.add(new Object[]{s, ok == 0 ? Double.MAX_VALUE : sum / ok, ok, last});
            }
            Collections.sort(rows, (a, b) -> Double.compare((Double) a[1], (Double) b[1]));
            act.ui(() -> {
                busy = false;
                dnsOut.removeAllViews();
                int i = 1;
                for (Object[] row : rows) {
                    String srv = (String) row[0];
                    double ms = (Double) row[1];
                    int ok = (Integer) row[2];
                    Dns.Result r = (Dns.Result) row[3];
                    LinearLayout rw = new LinearLayout(act);
                    rw.setOrientation(LinearLayout.HORIZONTAL);
                    rw.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    rw.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));
                    TextView rank = Ui.text(act, "#" + i, 11, i == 1 ? Ui.OK : Ui.SUB);
                    rank.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    rw.addView(rank, new LinearLayout.LayoutParams(Ui.dp(act, 26),
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    LinearLayout col = new LinearLayout(act);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.addView(Ui.text(act, DiagEngine.serverLabel(srv) + "  " + srv, 12.5f, Ui.TEXT));
                    if (r != null && r.firstIp() != null) {
                        col.addView(Ui.text(act, "解析 → " + r.firstIp(), 10.5f, Ui.SUB));
                    }
                    rw.addView(col, new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    rw.addView(Ui.text(act, ok == 0 ? "不可用" : Fmt.ms(ms), 12,
                            ok == 0 ? Ui.FAIL : ms < 50 ? Ui.OK : ms < 150 ? Ui.WARN : Ui.FAIL));
                    dnsOut.addView(rw);
                    i++;
                }
                dnsOut.addView(Ui.spacer(act, 6));
                dnsOut.addView(Ui.ghost(act, "打开系统私有 DNS 设置", () -> {
                    try {
                        act.startActivity(new android.content.Intent(
                                android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                    } catch (Throwable t) {
                        act.toast("无法打开设置");
                    }
                }));
            });
        });
    }
}
