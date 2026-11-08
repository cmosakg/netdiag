package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.Fmt;
import com.cmos.netdiag.core.Trace;

import java.util.List;

/** 路由追踪页 */
public class TracePage extends Page {

    private EditText host, hops;
    private LinearLayout list;
    private TextView stage;
    private volatile boolean running;

    public TracePage(MainActivity act) { super(act); }

    public String title() { return "路由"; }

    public View onCreate() {
        begin("逐跳追踪数据包路径，定位在哪一跳开始丢包/变慢");

        LinearLayout c = card("目标");
        host = Ui.input(act, "域名或 IP", "223.5.5.5");
        c.addView(host);
        hops = Ui.input(act, "最大跳数", "20");
        c.addView(hops);
        c.addView(Ui.hbar(act,
                Ui.pill(act, "阿里 DNS", Ui.ACCENT, () -> host.setText("223.5.5.5")),
                Ui.pill(act, "百度", Ui.ACCENT, () -> host.setText("www.baidu.com")),
                Ui.pill(act, "腾讯", Ui.ACCENT, () -> host.setText("www.qq.com")),
                Ui.pill(act, "Apple", Ui.ACCENT, () -> host.setText("www.apple.com")),
                Ui.pill(act, "Cloudflare", Ui.ACCENT, () -> host.setText("1.1.1.1"))));
        c.addView(Ui.primary(act, "开始追踪", this::start));
        stage = Ui.text(act, "每跳发送 3 个探测包，约需 10~40 秒", 11.5f, Ui.SUB);
        c.addView(stage);

        LinearLayout lc = card("路径");
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        lc.addView(list);
        return scroll;
    }

    private void start() {
        if (running) return;
        final String h = host.getText().toString().trim();
        if (h.isEmpty()) { act.toast("请输入目标"); return; }
        int max = 20;
        try { max = Integer.parseInt(hops.getText().toString().trim()); } catch (Exception ignore) {}
        if (max < 1 || max > 40) max = 20;
        final int fmax = max;
        running = true;
        list.removeAllViews();
        stage.setText("正在追踪…");
        act.async(() -> {
            List<Trace.Hop> hs = Trace.run(h, fmax, 3, 1, new Trace.Progress() {
                public void onHop(Trace.Hop hop) {
                    act.ui(() -> addHop(hop));
                }
                public void onInfo(String info) {
                    act.ui(() -> stage.setText(info));
                }
            });
            act.ui(() -> {
                running = false;
                int silent = 0;
                for (Trace.Hop x : hs) if (x.recv == 0) silent++;
                stage.setText("完成 · " + hs.size() + " 跳 · 无响应 " + silent + " 跳"
                        + (silent > 0 ? "（部分路由器不响应 ICMP，属正常）" : ""));
            });
        });
    }

    private void addHop(Trace.Hop h) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));

        TextView ttl = Ui.text(act, String.valueOf(h.ttl), 11.5f, Ui.SUB);
        ttl.setGravity(android.view.Gravity.CENTER);
        ttl.setBackground(Ui.round(Ui.blend(Ui.SUB, 0.12f), 8, act, 0));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(act, 26), Ui.dp(act, 22));
        tlp.rightMargin = Ui.dp(act, 10);
        row.addView(ttl, tlp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        if (h.recv == 0) {
            col.addView(Ui.text(act, "* * * 无响应", 12.5f, Ui.SUB));
        } else {
            col.addView(Ui.text(act, h.ip, 12.5f, Ui.TEXT));
            if (h.host != null && !h.host.isEmpty()) {
                col.addView(Ui.text(act, h.host, 10.5f, Ui.SUB));
            }
        }
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (h.recv > 0) {
            if (Double.isNaN(h.avg)) {
                // 该跳返回的是 ICMP Time Exceeded，不带 RTT
                row.addView(Ui.text(act, "TTL 超时", 10.5f, Ui.SUB));
            } else {
                TextView rtt = Ui.text(act, Fmt.ms(h.avg), 12,
                        h.avg < 30 ? Ui.OK : h.avg < 100 ? Ui.TEXT : Ui.WARN);
                row.addView(rtt);
            }
            if (h.loss() > 0) {
                TextView l = Ui.text(act, "  " + (int) h.loss() + "%", 11, Ui.FAIL);
                row.addView(l);
            }
        }
        list.addView(row);
        View div = new View(act);
        div.setBackgroundColor(Ui.STROKE);
        list.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    public void onHide() { running = false; }
}
