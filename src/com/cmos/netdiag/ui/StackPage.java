package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.NetStack;

import java.util.ArrayList;
import java.util.List;

/** 本机网络栈页：路由表 / 邻居表 / 连接 / 监听端口 */
public class StackPage extends Page {

    private LinearLayout content;
    private LinearLayout tabRow;
    private List<NetStack.Block> blocks = new ArrayList<>();
    private int current = 0;
    private boolean loaded;

    public StackPage(MainActivity act) { super(act); }

    public String title() { return "网络栈"; }

    public View onCreate() {
        begin("路由表、ARP/邻居表、TCP 连接、监听端口");

        LinearLayout c = card();
        c.addView(Ui.primary(act, "读取网络栈", this::load));
        tabRow = new LinearLayout(act);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(act, 8);
        c.addView(tabRow, tlp);

        LinearLayout cc = card();
        content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        cc.addView(content);
        content.addView(Ui.text(act, "点上面按钮读取（部分项需要 root）", 12.5f, Ui.SUB));
        return scroll;
    }

    public void onShow() {
        if (!loaded) load();
    }

    private void load() {
        loaded = true;
        content.removeAllViews();
        content.addView(Ui.text(act, "读取中…", 12.5f, Ui.SUB));
        act.async(() -> {
            final List<NetStack.Block> bs = NetStack.collect(act);
            act.ui(() -> {
                blocks = bs;
                buildTabs();
                show(0);
            });
        });
    }

    private void buildTabs() {
        tabRow.removeAllViews();
        tabRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < blocks.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 0, 0, Ui.dp(act, 6));
                tabRow.addView(row);
            }
            final int idx = i;
            TextView p = Ui.pill(act, blocks.get(i).title, i == 0 ? Ui.ACCENT : Ui.SUB,
                    () -> show(idx));
            row.addView(p);
        }
    }

    private void show(int idx) {
        current = idx;
        content.removeAllViews();
        if (blocks.isEmpty()) return;
        NetStack.Block b = blocks.get(Math.min(idx, blocks.size() - 1));
        content.addView(Ui.h2(act, b.title));
        content.addView(Ui.spacer(act, 6));
        String bodyText = b.body == null || b.body.trim().isEmpty() ? "无数据" : b.body;
        TextView tv = Ui.mono(act, bodyText, 10.5f, Ui.TEXT);
        tv.setHorizontallyScrolling(true);
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(act);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(tv, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(hsv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
