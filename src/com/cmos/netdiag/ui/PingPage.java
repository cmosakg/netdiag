package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.Fmt;
import com.cmos.netdiag.core.NetInfo;
import com.cmos.netdiag.core.Ping;

/** Ping 页：ICMP + TCP 双模式 */
public class PingPage extends Page {

    private EditText host, count, port, size;
    private TextView log, statLine;
    private LinearLayout statCards;
    private volatile boolean running;
    private final StringBuilder sb = new StringBuilder();

    public PingPage(MainActivity act) { super(act); }

    public String title() { return "Ping"; }

    public View onCreate() {
        begin("ICMP / TCP 双通道测延迟、丢包与抖动");

        LinearLayout c = card("目标");
        host = Ui.input(act, "域名或 IP", "www.baidu.com");
        c.addView(host);
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        count = Ui.input(act, "次数", "10");
        count.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        port = Ui.input(act, "端口(TCP)", "443");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        plp.leftMargin = Ui.dp(act, 8);
        port.setLayoutParams(plp);
        row.addView(count);
        row.addView(port);
        c.addView(row);

        c.addView(Ui.hbar(act,
                quick("网关", this::useGateway),
                quick("阿里 223.5.5.5", () -> host.setText("223.5.5.5")),
                quick("腾讯 119.29.29.29", () -> host.setText("119.29.29.29")),
                quick("百度", () -> host.setText("www.baidu.com")),
                quick("Apple", () -> host.setText("www.apple.com")),
                quick("GitHub", () -> host.setText("github.com")),
                quick("8.8.8.8", () -> host.setText("8.8.8.8"))));
        c.addView(Ui.primary(act, "开始 Ping（ICMP）", () -> start(false)));
        c.addView(Ui.ghost(act, "TCP Ping（绕过 ICMP 封锁）", () -> start(true)));

        statCards = new LinearLayout(act);
        statCards.setOrientation(LinearLayout.VERTICAL);
        body.addView(statCards);

        LinearLayout oc = card("实时输出");
        log = Ui.mono(act, "等待开始…", 11.5f, Ui.SUB);
        oc.addView(log);
        return scroll;
    }

    private TextView quick(String label, Runnable r) {
        return Ui.pill(act, label, Ui.ACCENT, r);
    }

    private void useGateway() {
        String gw = NetInfo.gateway(act);
        if (gw == null) act.toast("未获取到网关");
        else host.setText(gw);
    }

    private void start(final boolean tcp) {
        if (running) return;
        final String h = host.getText().toString().trim();
        if (h.isEmpty()) {
            act.toast("请输入目标");
            return;
        }
        int n = parseInt(count.getText().toString(), 10);
        int p = parseInt(port.getText().toString(), 443);
        if (n < 1) n = 1;
        if (n > 200) n = 200;
        running = true;
        sb.setLength(0);
        act.ui(() -> {
            log.setText("正在 Ping " + h + " …\n");
            statCards.removeAllViews();
        });
        final int times = n;
        final int portNum = p;
        act.async(() -> {
            Ping.Stats st;
            if (tcp) {
                st = Ping.tcp(h, portNum, times, 2000, new Ping.Progress() {
                    public void onLine(String line) {}
                    public void onReply(int seq, double rtt, int ttl) {
                        append(String.format(java.util.Locale.US, "[%d] 连接成功 time=%.1f ms%n", seq, rtt));
                    }
                    public void onTimeout(int seq) {
                        append(String.format(java.util.Locale.US, "[%d] 超时%n", seq));
                    }
                });
            } else {
                st = Ping.icmp(h, times, 500, 0, 2, new Ping.Progress() {
                    public void onLine(String line) {
                        if (line.contains("bytes from") || line.contains("Request timeout")
                                || line.contains("timeout") || line.contains("Unreachable")) {
                            append(line + "\n");
                        }
                    }
                    public void onReply(int seq, double rtt, int ttl) {}
                    public void onTimeout(int seq) {}
                });
            }
            final Ping.Stats fst = st;
            act.ui(() -> {
                running = false;
                showStats(fst);
            });
        });
    }

    private void append(String line) {
        sb.append(line);
        if (sb.length() > 12000) sb.delete(0, sb.length() - 12000);
        final String text = sb.toString();
        act.ui(() -> {
            log.setText(text);
            if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void showStats(Ping.Stats st) {
        if (st.error != null) {
            log.append("\n错误：" + st.error + "\n");
        }
        statCards.removeAllViews();
        LinearLayout c = Ui.card(act);
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView big = Ui.text(act, Fmt.ms(st.avg), 26,
                st.recv == 0 ? Ui.FAIL : st.avg < 50 ? Ui.OK : st.avg < 120 ? Ui.WARN : Ui.FAIL);
        big.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(big);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(act, 10), 0, 0, 0);
        col.addView(Ui.text(act, "平均延迟 · " + st.ip, 12, Ui.SUB));
        TextView q = Ui.badge(act, st.quality(), st.recv == 0 ? Ui.FAIL
                : "优秀".equals(st.quality()) ? Ui.OK : "良好".equals(st.quality()) ? Ui.OK : Ui.WARN);
        col.addView(q);
        head.addView(col);
        c.addView(head);
        c.addView(Ui.spacer(act, 10));
        c.addView(Ui.kv(act, "丢包率", st.lossText() + "  (" + st.recv + "/" + st.sent + ")"));
        c.addView(Ui.kv(act, "最小 / 平均 / 最大", Fmt.ms(st.min) + " / " + Fmt.ms(st.avg) + " / " + Fmt.ms(st.max)));
        c.addView(Ui.kv(act, "抖动", Fmt.ms(st.jitter)));
        c.addView(Ui.kv(act, "模式", st.mode));
        c.addView(Ui.kv(act, "总耗时", Fmt.duration(st.elapsed)));
        statCards.addView(c);
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        statCards.post(() -> {
            if (scroll != null) scroll.smoothScrollTo(0, statCards.getTop());
        });
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    public void onHide() { running = false; }
}
