package com.cmos.netdiag.core;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Wi-Fi 周边扫描与信道分析 */
public final class WifiScan {

    public static final class Ap {
        public String ssid;
        public String bssid;
        public int rssi;
        public int freq;
        public int channel;
        public String cap;
        public boolean sameAsConnected;
        public boolean open;
        public String standard = "";
    }

    public static final class ChannelStat {
        public int channel;
        public String band;
        public int count;
        public int bestRssi = -127;
        public String dominantSsid;
    }

    private WifiScan() {}

    @SuppressWarnings("deprecation")
    public static List<Ap> scan(Context ctx, WifiManager wm) {
        List<Ap> out = new ArrayList<>();
        List<ScanResult> rs = null;
        try {
            wm.startScan();
        } catch (Throwable ignore) {}
        try {
            rs = wm.getScanResults();
        } catch (Throwable ignore) {}
        String curBssid = null;
        try {
            curBssid = wm.getConnectionInfo().getBSSID();
        } catch (Throwable ignore) {}
        if (rs == null) return out;
        for (ScanResult r : rs) {
            Ap a = new Ap();
            a.ssid = r.SSID == null || r.SSID.isEmpty() ? "(隐藏网络)" : r.SSID;
            a.bssid = r.BSSID;
            a.rssi = r.level;
            a.freq = r.frequency;
            a.channel = Fmt.freqToChannel(r.frequency);
            a.cap = r.capabilities == null ? "" : r.capabilities;
            a.open = !a.cap.contains("WPA") && !a.cap.contains("WEP") && !a.cap.contains("EAP");
            a.sameAsConnected = curBssid != null && curBssid.equalsIgnoreCase(r.BSSID);
            try {
                if (r.frequency > 5900) a.standard = "Wi-Fi 6E/7";
                else if (a.cap.contains("HE")) a.standard = "Wi-Fi 6";
                else if (a.cap.contains("VHT")) a.standard = "Wi-Fi 5";
                else if (a.cap.contains("HT")) a.standard = "Wi-Fi 4";
                else a.standard = "老标准";
            } catch (Throwable ignore) {}
            out.add(a);
        }
        Collections.sort(out, (x, y) -> Integer.compare(y.rssi, x.rssi));
        return out;
    }

    /** 按信道聚合，2.4G 相邻信道重叠严重，统计拥挤度 */
    public static List<ChannelStat> channelStats(List<Ap> aps) {
        Map<Integer, ChannelStat> map = new HashMap<>();
        for (Ap a : aps) {
            if (a.channel <= 0) continue;
            ChannelStat cs = map.get(a.channel);
            if (cs == null) {
                cs = new ChannelStat();
                cs.channel = a.channel;
                cs.band = a.freq < 3000 ? "2.4G" : "5G";
                cs.dominantSsid = a.ssid;
                map.put(a.channel, cs);
            }
            cs.count++;
            if (a.rssi > cs.bestRssi) {
                cs.bestRssi = a.rssi;
                cs.dominantSsid = a.ssid;
            }
        }
        List<ChannelStat> list = new ArrayList<>(map.values());
        Collections.sort(list, (x, y) -> {
            if (x.band.equals(y.band)) return Integer.compare(x.channel, y.channel);
            return x.band.compareTo(y.band);
        });
        return list;
    }

    /** 推荐最空闲的 2.4G 信道（1/6/11 三选一） */
    public static String recommend(List<Ap> aps) {
        int[] cand = {1, 6, 11};
        int best = -1, bestScore = Integer.MAX_VALUE;
        for (int c : cand) {
            int score = 0;
            for (Ap a : aps) {
                if (a.freq >= 3000 || a.channel <= 0) continue;
                int dist = Math.abs(a.channel - c);
                if (dist == 0) score += 100 + Math.max(0, a.rssi + 100);
                else if (dist <= 2) score += 40;
            }
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best < 0) return "无数据";
        return "信道 " + best + "（干扰评分 " + bestScore + "，越低越好）";
    }

    public static String describeChannelCrowd(int count) {
        if (count <= 1) return "空闲";
        if (count <= 3) return "轻微";
        if (count <= 6) return "拥挤";
        return "非常拥挤";
    }

    public static String channelOverlapHint(int ch) {
        if (ch >= 1 && ch <= 13) {
            return String.format(Locale.US, "2.4G 信道 %d，与相邻信道存在重叠干扰", ch);
        }
        if (ch >= 36) return "5G 信道，干扰小";
        return "";
    }
}
