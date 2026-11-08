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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** DNS 诊断页：多服务器解析对比、记录查询、劫持检测 */
public class DnsPage extends Page {

    private EditText domain;
    private String qtype = "A";
    private LinearLayout table, detail;
    private TextView summary;
    private LinearLayout typeBar;
    private final String[] TYPES = {"A", "AAAA", "CNAME", "MX", "TXT", "NS"};
    private volatile boolean running;

    public DnsPage(MainActivity act) { super(act); }

    public String title() { return "DNS"; }

    public View onCreate() {
        begin("多服务器解析对比测速、全记录类型查询、DNS 劫持检测");

        LinearLayout c = card("域名");
        domain = Ui.input(act, "要查询的域名", "www.baidu.com");
        c.addView(domain);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        typeBar = new LinearLayout(act);
        typeBar.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(typeBar);
        c.addView(hs);
        buildTypes();
        c.addView(Ui.primary(act, "全部 DNS 服务器测速对比", this::runAll));
        c.addView(Ui.ghost(act, "查询该域名全部记录类型", this::runDetail));

        LinearLayout sc = card("对比结果");
        summary = Ui.text(act, "点上方按钮开始", 12.5f, Ui.SUB);
        sc.addView(summary);
        sc.addView(Ui.spacer(act, 6));
        table = new LinearLayout(act);
        table.setOrientation(LinearLayout.VERTICAL);
        sc.addView(table);

        LinearLayout dc = card("记录详情");
        detail = new LinearLayout(act);
        detail.setOrientation(LinearLayout.VERTICAL);
        dc.addView(detail);

        return scroll;
    }

    private void buildTypes() {
        typeBar.removeAllViews();
        for (final String t : TYPES) {
            final TextView p = Ui.pill(act, t, t.equals(qtype) ? Ui.ACCENT : Ui.SUB, () -> {
                qtype = t;
                buildTypes();
            });
            typeBar.addView(p);
        }
    }

    private void runAll() {
        if (running) return;
        final String d = domain.getText().toString().trim();
        if (d.isEmpty()) { act.toast("请输入域名"); return; }
        running = true;
        table.removeAllViews();
        summary.setText("查询中…");
        act.async(() -> {
            List<String> servers = DiagEngine.serverList(act);
            servers.add(0, "系统解析器");
            Set<String> ips = new LinkedHashSet<>();
            int ok = 0;
            double best = Double.MAX_VALUE;
            String bestS = "";
            List<Dns.Result> all = new ArrayList<>();
            for (String s : servers) {
                Dns.Result r = "系统解析器".equals(s)
                        ? Dns.systemResolve(d, 2500)
                        : Dns.query(s, d, qtype, 2000);
                all.add(r);
                if (r.ok()) {
                    ok++;
                    if (r.ms < best) { best = r.ms; bestS = s; }
                    String ip = r.firstIp();
                    if (ip != null) ips.add(ip);
                }
                final Dns.Result fr = r;
                final String fs = s;
                act.ui(() -> addRow(fs, fr));
            }
            final int fok = ok, ftotal = servers.size();
            final double fbest = best;
            final String fbestS = bestS;
            final int nips = ips.size();
            act.ui(() -> {
                running = false;
                summary.setText(String.format(java.util.Locale.US,
                        "%d/%d 台可用 · 最快 %.0f ms (%s) · %d 个不同 IP",
                        fok, ftotal, fbest == Double.MAX_VALUE ? 0 : fbest,
                        fbestS.isEmpty() ? "-" : fbestS, nips));
                summary.setTextColor(fok == 0 ? Ui.FAIL : nips > 1 ? Ui.WARN : Ui.OK);
                if (nips > 1) {
                    TextView warn = Ui.text(act, "⚠ 不同服务器返回的 IP 不一致，可能是 CDN 就近调度，也可能存在劫持（可结合 HTTP 探测判断）",
                            11.5f, Ui.WARN);
                    warn.setPadding(0, Ui.dp(act, 8), 0, 0);
                    table.addView(warn);
                }
            });
        });
    }

    private void addRow(String server, Dns.Result r) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(act, DiagEngine.serverLabel(server), 12.5f, Ui.TEXT);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView ip = Ui.text(act, server, 10.5f, Ui.SUB);
        col.addView(name);
        col.addView(ip);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f));

        LinearLayout col2 = new LinearLayout(act);
        col2.setOrientation(LinearLayout.VERTICAL);
        String v;
        if (r.error != null) v = r.error;
        else if (!r.answers.isEmpty()) {
            StringBuilder sb2 = new StringBuilder();
            int i = 0;
            for (Dns.Answer a : r.answers) {
                if (i++ >= 3) { sb2.append(" …"); break; }
                if (sb2.length() > 0) sb2.append(", ");
                sb2.append(a.value);
            }
            v = sb2.toString();
        } else v = r.rcodeText();
        col2.addView(Ui.text(act, v, 12, r.ok() ? Ui.TEXT : Ui.FAIL));
        col2.addView(Ui.text(act, r.ok() ? (r.answers.size() + " 条 · TTL "
                + r.answers.get(0).ttl + "s") : r.rcodeText(), 10.5f, Ui.SUB));
        row.addView(col2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));

        TextView t = Ui.text(act, r.error != null ? "-" : Fmt.ms(r.ms), 12,
                r.error != null ? Ui.FAIL : r.ms < 60 ? Ui.OK : r.ms < 200 ? Ui.WARN : Ui.FAIL);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.14f));

        table.addView(row);
        View div = new View(act);
        div.setBackgroundColor(Ui.STROKE);
        table.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void runDetail() {
        final String d = domain.getText().toString().trim();
        if (d.isEmpty()) { act.toast("请输入域名"); return; }
        detail.removeAllViews();
        detail.addView(Ui.text(act, "查询中…", 12.5f, Ui.SUB));
        act.async(() -> {
            final String[] types = {"A", "AAAA", "CNAME", "MX", "NS", "TXT", "SOA"};
            final List<String[]> rows = new ArrayList<>();
            for (String t : types) {
                Dns.Result r = Dns.query("223.5.5.5", d, t, 2500);
                StringBuilder sb2 = new StringBuilder();
                if (r.error != null) sb2.append(r.error);
                else if (r.answers.isEmpty()) sb2.append(r.rcodeText());
                else {
                    for (Dns.Answer a : r.answers) {
                        if (sb2.length() > 0) sb2.append("\n");
                        sb2.append(a.value).append("  (TTL ").append(a.ttl).append("s)");
                    }
                }
                rows.add(new String[]{t, sb2.toString(), String.valueOf((int) r.ms)});
            }
            act.ui(() -> {
                detail.removeAllViews();
                for (String[] r : rows) {
                    LinearLayout box = new LinearLayout(act);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));
                    LinearLayout h = new LinearLayout(act);
                    h.setOrientation(LinearLayout.HORIZONTAL);
                    h.addView(Ui.badge(act, r[0], Ui.ACCENT));
                    TextView sp = Ui.text(act, "  " + r[2] + " ms", 11, Ui.SUB);
                    h.addView(sp);
                    box.addView(h);
                    TextView v = Ui.mono(act, r[1], 11.5f, Ui.TEXT);
                    v.setPadding(0, Ui.dp(act, 4), 0, 0);
                    box.addView(v);
                    detail.addView(box);
                }
            });
        });
    }
}
