package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.Fmt;
import com.cmos.netdiag.core.SpeedTest;

/** 带宽测速页 */
public class SpeedPage extends Page {

    private TextView live, stage;
    private LinearLayout result;
    private volatile boolean running;

    public SpeedPage(MainActivity act) { super(act); }

    public String title() { return "测速"; }

    public View onCreate() {
        begin("下载 / 上传带宽、延迟与抖动实测（约 15 秒）");

        LinearLayout c = card();
        c.addView(Ui.text(act, "测速会消耗流量（约 25~100MB），请确认流量充足。", 12, Ui.WARN));
        c.addView(Ui.spacer(act, 6));
        c.addView(Ui.primary(act, "开始测速", this::start));
        live = Ui.text(act, "0.00 Mbps", 30, Ui.ACCENT);
        live.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        live.setGravity(android.view.Gravity.CENTER);
        live.setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 4));
        c.addView(live);
        stage = Ui.text(act, "等待开始", 11.5f, Ui.SUB);
        stage.setGravity(android.view.Gravity.CENTER);
        c.addView(stage);

        result = new LinearLayout(act);
        result.setOrientation(LinearLayout.VERTICAL);
        body.addView(result);
        return scroll;
    }

    private void start() {
        if (running) return;
        running = true;
        result.removeAllViews();
        stage.setText("准备中…");
        act.async(() -> {
            SpeedTest.Result r = SpeedTest.run(7000, new SpeedTest.Progress() {
                public void onTick(double bps, long bytes, long ms) {
                    final String s = Fmt.speed(bps);
                    act.ui(() -> live.setText(s));
                }
                public void onStage(String s) {
                    act.ui(() -> stage.setText(s));
                }
            });
            act.ui(() -> {
                running = false;
                render(r);
            });
        });
    }

    private void render(SpeedTest.Result r) {
        live.setText(r.down());
        stage.setText("完成 · 来源 " + shortHost(r.usedUrl));
        result.removeAllViews();

        LinearLayout c = Ui.card(act);
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout c1 = new LinearLayout(act);
        c1.setOrientation(LinearLayout.VERTICAL);
        TextView d = Ui.text(act, r.down(), 22, Ui.OK);
        d.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c1.addView(d);
        c1.addView(Ui.text(act, "下载", 11.5f, Ui.SUB));
        row.addView(c1, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout c2 = new LinearLayout(act);
        c2.setOrientation(LinearLayout.VERTICAL);
        TextView u = Ui.text(act, r.up(), 22, Ui.ACCENT);
        u.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c2.addView(u);
        c2.addView(Ui.text(act, "上传", 11.5f, Ui.SUB));
        row.addView(c2, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        c.addView(row);
        c.addView(Ui.divider(act));
        c.addView(Ui.kv(act, "下载量", Fmt.bytes(r.downBytes) + " / " + Fmt.duration(r.downMs)));
        c.addView(Ui.kv(act, "上传量", Fmt.bytes(r.upBytes) + " / " + Fmt.duration(r.upMs)));
        c.addView(Ui.kv(act, "延迟", Fmt.ms(r.latencyMs)));
        c.addView(Ui.kv(act, "抖动", Fmt.ms(r.jitterMs)));

        String grade;
        int color;
        double mbps = r.downBytesPerSec * 8 / 1e6;
        if (mbps >= 200) { grade = "极佳，可流畅 4K/8K"; color = Ui.OK; }
        else if (mbps >= 80) { grade = "很好，可流畅 4K"; color = Ui.OK; }
        else if (mbps >= 30) { grade = "良好，可流畅 1080P"; color = Ui.OK; }
        else if (mbps >= 10) { grade = "一般，适合 720P"; color = Ui.WARN; }
        else if (mbps >= 2) { grade = "偏慢，仅适合标清"; color = Ui.WARN; }
        else { grade = "很慢，建议检查网络"; color = Ui.FAIL; }
        c.addView(Ui.spacer(act, 8));
        c.addView(Ui.badge(act, grade, color));
        result.addView(c);

        if (r.error != null) {
            LinearLayout ec = Ui.card(act);
            ec.addView(Ui.text(act, "提示：" + r.error + "（部分测速源在特定网络下不可用，可多试几次）", 12, Ui.WARN));
            result.addView(ec);
        }
    }

    private String shortHost(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Throwable t) {
            return "-";
        }
    }

    public void onHide() { running = false; }
}
