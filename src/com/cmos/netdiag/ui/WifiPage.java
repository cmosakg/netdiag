package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.Fmt;
import com.cmos.netdiag.core.NetInfo;
import com.cmos.netdiag.core.WifiScan;

import java.util.List;

/** Wi-Fi 分析页 */
public class WifiPage extends Page {

    private LinearLayout curBox, apList, chBox;
    private TextView scanInfo;
    private volatile boolean scanning;

    public WifiPage(MainActivity act) { super(act); }

    public String title() { return "Wi-Fi"; }

    public View onCreate() {
        begin("当前连接质量、周边 AP 扫描、信道干扰分析");

        LinearLayout c = card();
        c.addView(Ui.primary(act, "刷新 / 扫描周边 Wi-Fi", this::load));
        scanInfo = Ui.text(act, "点上方按钮扫描（需要定位权限）", 11.5f, Ui.SUB);
        c.addView(scanInfo);

        LinearLayout cc = card("当前连接");
        curBox = new LinearLayout(act);
        curBox.setOrientation(LinearLayout.VERTICAL);
        cc.addView(curBox);

        LinearLayout chc = card("信道干扰");
        chBox = new LinearLayout(act);
        chBox.setOrientation(LinearLayout.VERTICAL);
        chc.addView(chBox);

        LinearLayout ac = card("周边 Wi-Fi");
        apList = new LinearLayout(act);
        apList.setOrientation(LinearLayout.VERTICAL);
        ac.addView(apList);

        return scroll;
    }

    public void onShow() {
        if (curBox.getChildCount() == 0) load();
    }

    @SuppressWarnings("deprecation")
    private void load() {
        if (scanning) return;
        scanning = true;
        scanInfo.setText("扫描中…");
        final WifiManager wm = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        act.async(() -> {
            final NetInfo.Section sec = NetInfo.wifiSection(act);
            List<WifiScan.Ap> aps = wm == null ? null : WifiScan.scan(act, wm);
            final List<WifiScan.Ap> faps = aps;
            final List<WifiScan.ChannelStat> stats = aps == null ? null : WifiScan.channelStats(aps);
            final String rec = aps == null ? null : WifiScan.recommend(aps);
            act.ui(() -> {
                scanning = false;
                renderCurrent(sec);
                renderAps(faps);
                renderChannels(stats, rec);
                scanInfo.setText("更新于 " + Fmt.shortTime()
                        + (faps == null || faps.isEmpty() ? " · 没有扫描结果（检查定位权限是否开启）"
                        : " · 发现 " + faps.size() + " 个 AP"));
            });
        });
    }

    private void renderCurrent(NetInfo.Section sec) {
        curBox.removeAllViews();
        if (sec == null || sec.kv.isEmpty()) {
            curBox.addView(Ui.text(act, "未连接 Wi-Fi", 12.5f, Ui.SUB));
            return;
        }
        for (java.util.Map.Entry<String, String> e : sec.kv.entrySet()) {
            curBox.addView(Ui.kv(act, e.getKey(), e.getValue()));
        }
        String rssi = sec.kv.get("信号强度");
        if (rssi != null) {
            int dbm = -100;
            try {
                dbm = Integer.parseInt(rssi.split(" ")[0]);
            } catch (Exception ignore) {}
            curBox.addView(Ui.spacer(act, 8));
            curBox.addView(Ui.badge(act, Fmt.rssi(dbm) + " · " + dbm + " dBm",
                    Fmt.rssiLevel(dbm) >= 3 ? Ui.OK : Fmt.rssiLevel(dbm) >= 2 ? Ui.WARN : Ui.FAIL));
        }
    }

    private void renderAps(List<WifiScan.Ap> aps) {
        apList.removeAllViews();
        if (aps == null || aps.isEmpty()) {
            apList.addView(Ui.text(act, "无扫描结果", 12.5f, Ui.SUB));
            return;
        }
        int shown = 0;
        for (WifiScan.Ap a : aps) {
            if (shown++ >= 25) break;
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));

            // 信号条
            LinearLayout bars = new LinearLayout(act);
            bars.setOrientation(LinearLayout.HORIZONTAL);
            bars.setGravity(android.view.Gravity.BOTTOM);
            int lv = Fmt.rssiLevel(a.rssi);
            for (int i = 0; i < 4; i++) {
                View b = new View(act);
                int h = Ui.dp(act, 4 + i * 3);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(act, 3), h);
                blp.rightMargin = Ui.dp(act, 2);
                b.setBackground(Ui.round(i < lv ? (lv >= 3 ? Ui.OK : lv >= 2 ? Ui.WARN : Ui.FAIL)
                        : 0xFF2A323E, 1, act, 0));
                bars.addView(b, blp);
            }
            LinearLayout.LayoutParams barsLp = new LinearLayout.LayoutParams(
                    Ui.dp(act, 22), Ui.dp(act, 16));
            barsLp.rightMargin = Ui.dp(act, 8);
            row.addView(bars, barsLp);

            LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView ssid = Ui.text(act, a.ssid, 12.5f, a.sameAsConnected ? Ui.ACCENT : Ui.TEXT);
            ssid.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            col.addView(ssid);
            String sub = a.channel > 0 ? ("CH" + a.channel + " · " + (a.freq < 3000 ? "2.4G" : "5G")
                    + " · " + a.standard) : a.bssid;
            if (a.open) sub += " · ⚠开放";
            col.addView(Ui.text(act, sub, 10.5f, Ui.SUB));
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(Ui.text(act, a.rssi + " dBm", 11.5f,
                    lv >= 3 ? Ui.OK : lv >= 2 ? Ui.WARN : Ui.FAIL));
            apList.addView(row);
            View div = new View(act);
            div.setBackgroundColor(Ui.STROKE);
            apList.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private void renderChannels(List<WifiScan.ChannelStat> stats, String rec) {
        chBox.removeAllViews();
        if (stats == null || stats.isEmpty()) {
            chBox.addView(Ui.text(act, "无数据", 12.5f, Ui.SUB));
            return;
        }
        if (rec != null) {
            chBox.addView(Ui.text(act, "建议切换到 " + rec, 12.5f, Ui.ACCENT));
            chBox.addView(Ui.spacer(act, 8));
        }
        int max = 1;
        for (WifiScan.ChannelStat s : stats) max = Math.max(max, s.count);
        for (WifiScan.ChannelStat s : stats) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));

            TextView ch = Ui.text(act, "CH" + s.channel, 11.5f, Ui.TEXT);
            ch.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.addView(ch, new LinearLayout.LayoutParams(Ui.dp(act, 44),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // 柱状
            LinearLayout barBox = new LinearLayout(act);
            barBox.setOrientation(LinearLayout.HORIZONTAL);
            View bar = new View(act);
            int color = s.count <= 1 ? Ui.OK : s.count <= 3 ? Ui.WARN : Ui.FAIL;
            bar.setBackground(Ui.round(color, 3, act, 0));
            float ratio = s.count / (float) max;
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, Ui.dp(act, 10),
                    Math.max(0.05f, ratio));
            LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(0, Ui.dp(act, 10),
                    Math.max(0.001f, 1 - ratio));
            barBox.addView(bar, blp);
            barBox.addView(new View(act), emptyLp);
            row.addView(barBox, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView cnt = Ui.text(act, s.count + " 个", 11, Ui.SUB);
            row.addView(cnt);
            chBox.addView(row);
        }
    }
}
