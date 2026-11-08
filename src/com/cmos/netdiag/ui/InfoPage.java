package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.NetInfo;

import java.util.List;
import java.util.Map;

/** 连接信息页 */
public class InfoPage extends Page {

    private LinearLayout holder;
    private TextView updated;
    private boolean loading;

    public InfoPage(MainActivity act) { super(act); }

    public String title() { return "连接"; }

    public View onCreate() {
        begin("本机网络全貌：链路、Wi-Fi、蜂窝、IP、DNS、代理");
        LinearLayout c = card();
        c.addView(Ui.primary(act, "刷新信息", this::load));
        updated = Ui.text(act, "", 11.5f, Ui.SUB);
        c.addView(updated);

        holder = new LinearLayout(act);
        holder.setOrientation(LinearLayout.VERTICAL);
        body.addView(holder);
        return scroll;
    }

    public void onShow() {
        if (holder.getChildCount() == 0) load();
    }

    private void load() {
        if (loading) return;
        loading = true;
        updated.setText("读取中…");
        act.async(() -> {
            final List<NetInfo.Section> sections = NetInfo.collect(act);
            act.ui(() -> {
                render(sections);
                updated.setText("更新于 " + com.cmos.netdiag.core.Fmt.shortTime() + " · 下拉无效，点按钮刷新");
                loading = false;
            });
        });
    }

    private void render(List<NetInfo.Section> sections) {
        holder.removeAllViews();
        for (NetInfo.Section s : sections) {
            if (s.kv.isEmpty()) continue;
            LinearLayout card = Ui.card(act);
            LinearLayout head = new LinearLayout(act);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView t = Ui.h2(act, s.title);
            head.addView(t, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.badge(act, s.kv.size() + " 项", Ui.SUB));
            card.addView(head);
            card.addView(Ui.spacer(act, 6));
            for (Map.Entry<String, String> e : s.kv.entrySet()) {
                card.addView(Ui.kv(act, e.getKey(), e.getValue()));
            }
            holder.addView(card);
        }
    }
}
