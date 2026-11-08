package com.cmos.netdiag;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.ui.Ui;
import com.cmos.netdiag.ui.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final ExecutorService bg = Executors.newCachedThreadPool();
    private FrameLayout content;
    private LinearLayout tabBar;
    private final List<Page> pages = new ArrayList<>();
    private int current = 0;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        w.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        View decor = w.getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR == 0 ? 0 : 0);

        pages.add(new DiagPage(this));
        pages.add(new InfoPage(this));
        pages.add(new PingPage(this));
        pages.add(new DnsPage(this));
        pages.add(new TracePage(this));
        pages.add(new PortPage(this));
        pages.add(new HttpPage(this));
        pages.add(new SpeedPage(this));
        pages.add(new WifiPage(this));
        pages.add(new StackPage(this));
        pages.add(new ToolPage(this));
        pages.add(new ReportPage(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(0, statusBarHeight(), 0, 0);

        // ---- 顶栏 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 4));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = Ui.text(this, "NetDiag", 22, Ui.TEXT);
        logo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView dot = Ui.text(this, " · 网络诊断", 13, Ui.ACCENT);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(logo);
        left.addView(dot);
        titleRow.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        subtitle = Ui.text(this, "", 11.5f, Ui.SUB);
        titleRow.addView(subtitle);
        header.addView(titleRow);

        // ---- Tab 栏 ----
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 6));
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            TextView tab = Ui.text(this, pages.get(i).title(), 13, Ui.SUB);
            tab.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 4);
            tab.setLayoutParams(lp);
            tab.setClickable(true);
            tab.setOnClickListener(v -> select(idx));
            tabBar.addView(tab);
        }
        hs.addView(tabBar);
        header.addView(hs);
        root.addView(header);

        content = new FrameLayout(this);
        content.setBackgroundColor(Ui.BG);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        select(0);
        requestPerms();
    }

    private int statusBarHeight() {
        try {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return getResources().getDimensionPixelSize(id);
        } catch (Throwable ignore) {}
        return Ui.dp(this, 24);
    }

    private void select(int idx) {
        if (idx == current && content.getChildCount() > 0) {
            pages.get(idx).onShow();
            return;
        }
        if (current >= 0 && current < pages.size()) pages.get(current).onHide();
        current = idx;
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            TextView t = (TextView) tabBar.getChildAt(i);
            boolean on = i == idx;
            t.setTextColor(on ? 0xFF06181D : Ui.SUB);
            t.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            t.setBackground(on ? Ui.round(Ui.ACCENT, 9, this, 0)
                    : Ui.round(0x00000000, 9, this, 0));
        }
        Page p = pages.get(idx);
        content.removeAllViews();
        content.addView(p.get(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        p.onShow();
        refreshSubtitle();
    }

    public void refreshSubtitle() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            android.net.Network n = cm.getActiveNetwork();
            android.net.NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            String s = nc == null ? "离线" : com.cmos.netdiag.core.NetInfo.transportName(nc);
            if (nc != null && !nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                s += " 未验证";
            }
            subtitle.setText(s);
        } catch (Throwable t) {
            subtitle.setText("");
        }
    }

    /** 后台执行 */
    public void async(Runnable r) {
        bg.submit(r);
    }

    /** 回主线程 */
    public void ui(Runnable r) {
        runOnUiThread(r);
    }

    public void toast(String msg) {
        ui(() -> android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show());
    }

    public void share(String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "NetDiag 网络诊断报告");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "分享诊断报告"));
        } catch (Throwable t) {
            toast("分享失败");
        }
    }

    public void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            toast("无法打开链接");
        }
    }

    private void requestPerms() {
        List<String> need = new ArrayList<>();
        String[] ps = {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.READ_PHONE_STATE
        };
        for (String p : ps) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.NEARBY_WIFI_DEVICES") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.NEARBY_WIFI_DEVICES");
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1001);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refreshSubtitle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSubtitle();
    }

    @Override
    public void onBackPressed() {
        if (current != 0) select(0);
        else super.onBackPressed();
    }
}
