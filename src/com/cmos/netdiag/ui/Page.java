package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 页面基类：懒加载 + 保留状态 + 常用布局辅助 */
public abstract class Page {

    protected final MainActivity act;
    protected View view;
    protected ScrollView scroll;
    protected LinearLayout body;

    public Page(MainActivity act) {
        this.act = act;
    }

    public abstract String title();

    public abstract View onCreate();

    public View get() {
        if (view == null) view = onCreate();
        return view;
    }

    public void onShow() {}

    public void onHide() {}

    protected MainActivity act() { return act; }

    /** 初始化滚动页面 */
    protected void begin(String subtitle) {
        scroll = Ui.page(act);
        body = Ui.body(scroll);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = Ui.text(act, subtitle, 12.5f, Ui.SUB);
            s.setPadding(Ui.dp(act, 2), Ui.dp(act, 2), Ui.dp(act, 2), Ui.dp(act, 12));
            body.addView(s);
        }
    }

    protected LinearLayout card() {
        LinearLayout c = Ui.card(act);
        body.addView(c);
        return c;
    }

    protected LinearLayout card(String title) {
        LinearLayout c = card();
        if (title != null && !title.isEmpty()) {
            c.addView(Ui.h2(act, title));
            c.addView(Ui.spacer(act, 7));
        }
        return c;
    }

    protected void gap() {
        body.addView(Ui.spacer(act, 4));
    }
}
