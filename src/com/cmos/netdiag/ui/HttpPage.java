package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.Fmt;
import com.cmos.netdiag.core.HttpProbe;

import java.util.Map;

/** HTTP / HTTPS 探测页 */
public class HttpPage extends Page {

    private EditText url;
    private LinearLayout resultBox;
    private volatile boolean running;

    public HttpPage(MainActivity act) { super(act); }

    public String title() { return "HTTP"; }

    public View onCreate() {
        begin("状态码、响应头、重定向链、TLS 证书、首字节耗时");

        LinearLayout c = card("URL");
        url = Ui.input(act, "https://...", "https://www.baidu.com");
        c.addView(url);
        c.addView(Ui.hbar(act,
                Ui.pill(act, "百度", Ui.ACCENT, () -> url.setText("https://www.baidu.com")),
                Ui.pill(act, "腾讯", Ui.ACCENT, () -> url.setText("https://www.qq.com")),
                Ui.pill(act, "204 探测", Ui.ACCENT, () -> url.setText("http://connect.rom.miui.com/generate_204")),
                Ui.pill(act, "Google", Ui.ACCENT, () -> url.setText("https://www.google.com")),
                Ui.pill(act, "GitHub", Ui.ACCENT, () -> url.setText("https://github.com")),
                Ui.pill(act, "Apple", Ui.ACCENT, () -> url.setText("https://www.apple.com"))));
        c.addView(Ui.primary(act, "开始探测", this::start));

        resultBox = new LinearLayout(act);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(resultBox);
        return scroll;
    }

    private void start() {
        if (running) return;
        String u = url.getText().toString().trim();
        if (u.isEmpty()) { act.toast("请输入 URL"); return; }
        running = true;
        resultBox.removeAllViews();
        LinearLayout load = Ui.card(act);
        load.addView(Ui.text(act, "探测中…", 12.5f, Ui.SUB));
        resultBox.addView(load);
        act.async(() -> {
            final HttpProbe.Result r = HttpProbe.probe(u, 8000, true);
            final String tls = r.https() ? HttpProbe.tlsVersion(hostOf(r.finalUrl == null ? u : r.finalUrl), 443, 5000)
                    : null;
            act.ui(() -> {
                running = false;
                render(r, tls);
            });
        });
    }

    private String hostOf(String u) {
        try {
            return new java.net.URL(u).getHost();
        } catch (Throwable t) {
            return u;
        }
    }

    private void render(HttpProbe.Result r, String tls) {
        resultBox.removeAllViews();

        LinearLayout head = Ui.card(act);
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        int color = r.code <= 0 ? Ui.FAIL : r.code < 300 ? Ui.OK : r.code < 400 ? Ui.WARN : Ui.FAIL;
        TextView code = Ui.text(act, r.code <= 0 ? "×" : String.valueOf(r.code), 34, color);
        code.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(code);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(act, 12), 0, 0, 0);
        col.addView(Ui.text(act, r.code <= 0 ? "请求失败" : r.codeMsg, 13.5f, Ui.TEXT));
        col.addView(Ui.text(act, r.https() ? "HTTPS" : "HTTP", 11, Ui.SUB));
        row.addView(col);
        head.addView(row);

        if (r.error != null) {
            head.addView(Ui.spacer(act, 8));
            head.addView(Ui.text(act, "错误：" + r.error, 12, Ui.FAIL));
        }
        head.addView(Ui.spacer(act, 10));
        head.addView(Ui.kv(act, "首字节 TTFB", Fmt.ms(r.ttfbMs)));
        head.addView(Ui.kv(act, "建连耗时", Fmt.ms(r.connectMs)));
        head.addView(Ui.kv(act, "总耗时", Fmt.ms(r.totalMs)));
        if (r.size > 0) head.addView(Ui.kv(act, "读取", Fmt.bytes(r.size) + "（仅采样）"));
        if (r.server() != null && !r.server().isEmpty()) head.addView(Ui.kv(act, "Server", r.server()));
        head.addView(Ui.kv(act, "最终地址", r.finalUrl));
        resultBox.addView(head);

        if (!r.redirects.isEmpty()) {
            LinearLayout rc = Ui.card(act);
            rc.addView(Ui.h2(act, "重定向链"));
            rc.addView(Ui.spacer(act, 6));
            int i = 1;
            for (String s : r.redirects) {
                TextView t = Ui.mono(act, i++ + ". " + s, 11.5f, Ui.WARN);
                t.setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 2));
                rc.addView(t);
            }
            resultBox.addView(rc);
        }

        if (r.https()) {
            LinearLayout tc = Ui.card(act);
            tc.addView(Ui.h2(act, "TLS 证书"));
            tc.addView(Ui.spacer(act, 6));
            if (tls != null) tc.addView(Ui.kv(act, "协商", tls));
            tc.addView(Ui.kv(act, "Subject", shortDn(r.certSubject)));
            tc.addView(Ui.kv(act, "Issuer", shortDn(r.certIssuer)));
            tc.addView(Ui.kv(act, "有效期至", r.certNotAfter));
            tc.addView(Ui.kv(act, "签名算法", r.certSigAlg));
            tc.addView(Ui.kv(act, "证书链", r.certChainLen + " 级"));
            resultBox.addView(tc);
        }

        LinearLayout hc = Ui.card(act);
        hc.addView(Ui.h2(act, "响应头"));
        hc.addView(Ui.spacer(act, 6));
        if (r.headers.isEmpty()) {
            hc.addView(Ui.text(act, "无", 12, Ui.SUB));
        } else {
            for (Map.Entry<String, String> e : r.headers.entrySet()) {
                LinearLayout kv = new LinearLayout(act);
                kv.setOrientation(LinearLayout.VERTICAL);
                kv.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
                kv.addView(Ui.text(act, e.getKey(), 11, Ui.ACCENT));
                TextView v = Ui.mono(act, e.getValue(), 11, Ui.TEXT);
                kv.addView(v);
                hc.addView(kv);
            }
        }
        resultBox.addView(hc);

        if (r.bodyHead != null && !r.bodyHead.isEmpty()) {
            LinearLayout bc = Ui.card(act);
            bc.addView(Ui.h2(act, "响应体片段"));
            bc.addView(Ui.spacer(act, 6));
            bc.addView(Ui.mono(act, r.bodyHead, 11, Ui.SUB));
            resultBox.addView(bc);
        }
    }

    private String shortDn(String dn) {
        if (dn == null || dn.isEmpty()) return "-";
        String cn = null, o = null;
        for (String part : dn.split(",")) {
            String p = part.trim();
            if (p.startsWith("CN=")) cn = p.substring(3);
            if (p.startsWith("O=")) o = p.substring(2);
        }
        if (cn != null) return cn + (o != null ? " (" + o + ")" : "");
        return dn.length() > 80 ? dn.substring(0, 80) + "…" : dn;
    }

    public void onHide() { running = false; }
}
