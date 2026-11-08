package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.Fmt;
import com.cmos.netdiag.core.PortScan;

import java.util.ArrayList;
import java.util.List;

/** 端口探测页 */
public class PortPage extends Page {

    private EditText host, ports;
    private LinearLayout list;
    private TextView stage;
    private volatile boolean running;

    public PortPage(MainActivity act) { super(act); }

    public String title() { return "端口"; }

    public View onCreate() {
        begin("TCP 端口连通性探测：判断服务是否可达、防火墙是否放行");

        LinearLayout c = card("目标");
        host = Ui.input(act, "域名或 IP", "www.baidu.com");
        c.addView(host);
        ports = Ui.input(act, "端口（逗号或范围，如 80,443,8000-8100）", "80,443,22,8080");
        c.addView(ports);
        c.addView(Ui.hbar(act,
                Ui.pill(act, "常用端口", Ui.ACCENT, () -> ports.setText(commonList())),
                Ui.pill(act, "Web", Ui.ACCENT, () -> ports.setText("80,443,8080,8443")),
                Ui.pill(act, "代理端口", Ui.ACCENT, () -> ports.setText("1080,7890,7891,9090,10808")),
                Ui.pill(act, "局域网网关", Ui.ACCENT, this::fillGateway)));
        c.addView(Ui.primary(act, "开始探测", this::start));
        stage = Ui.text(act, "并发探测，超时 1 秒", 11.5f, Ui.SUB);
        c.addView(stage);

        LinearLayout lc = card("结果");
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        lc.addView(list);
        return scroll;
    }

    private String commonList() {
        StringBuilder sb = new StringBuilder();
        for (Integer p : PortScan.COMMON.keySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        return sb.toString();
    }

    private void fillGateway() {
        String gw = com.cmos.netdiag.core.NetInfo.gateway(act);
        if (gw != null) host.setText(gw);
        ports.setText("53,80,443,8080,22,23,7547");
    }

    private void start() {
        if (running) return;
        final String h = host.getText().toString().trim();
        if (h.isEmpty()) { act.toast("请输入目标"); return; }
        final List<Integer> ps = PortScan.parsePorts(ports.getText().toString());
        if (ps.isEmpty()) { act.toast("端口格式不正确"); return; }
        running = true;
        list.removeAllViews();
        stage.setText("正在探测 " + ps.size() + " 个端口…");
        act.async(() -> {
            final List<PortScan.Item> res = PortScan.scan(h, ps, 1200, 16, null);
            act.ui(() -> {
                running = false;
                render(res);
            });
        });
    }

    private void render(List<PortScan.Item> items) {
        list.removeAllViews();
        int open = 0;
        for (PortScan.Item it : items) if (it.open) open++;
        stage.setText("完成 · " + items.size() + " 个端口 · " + open + " 个开放");

        // 汇总条
        LinearLayout sum = new LinearLayout(act);
        sum.setOrientation(LinearLayout.HORIZONTAL);
        sum.setPadding(0, 0, 0, Ui.dp(act, 10));
        sum.addView(Ui.badge(act, "开放 " + open, Ui.OK));
        sum.addView(Ui.spacer(act, 6));
        sum.addView(Ui.badge(act, "关闭 " + (items.size() - open), Ui.SUB));
        list.addView(sum);

        List<PortScan.Item> opens = new ArrayList<>();
        for (PortScan.Item it : items) if (it.open) opens.add(it);
        // 开放的排前面
        for (PortScan.Item it : items) {
            if (it.open) addRow(it);
        }
        for (PortScan.Item it : items) {
            if (!it.open) addRow(it);
        }
    }

    private void addRow(PortScan.Item it) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(act, 7), 0, Ui.dp(act, 7));

        TextView st = Ui.text(act, it.open ? "开放" : "关闭", 11.5f, it.open ? Ui.OK : Ui.SUB);
        st.setGravity(android.view.Gravity.CENTER);
        st.setBackground(Ui.round(Ui.blend(it.open ? Ui.OK : Ui.SUB, 0.15f), 8, act, 0));
        st.setPadding(Ui.dp(act, 8), Ui.dp(act, 3), Ui.dp(act, 8), Ui.dp(act, 3));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.rightMargin = Ui.dp(act, 10);
        row.addView(st, slp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView p = Ui.text(act, String.valueOf(it.port), 13, it.open ? Ui.TEXT : Ui.SUB);
        p.setTypeface(android.graphics.Typeface.MONOSPACE);
        col.addView(p);
        if (!it.service.isEmpty()) col.addView(Ui.text(act, it.service, 10.5f, Ui.SUB));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (it.open) row.addView(Ui.text(act, Fmt.ms(it.ms), 12, Ui.OK));
        else if (it.error != null && !it.error.isEmpty()) {
            row.addView(Ui.text(act, it.error, 11, Ui.SUB));
        }
        list.addView(row);
        View div = new View(act);
        div.setBackgroundColor(Ui.STROKE);
        list.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    public void onHide() { running = false; }
}
