package com.cmos.netdiag.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一键诊断：按 OSI 分层逐级验证，定位故障点。
 * L1 链路 -> L2 局域网(网关) -> L3 公网出口 -> L4 DNS -> L5 应用层(HTTP)
 */
public final class DiagEngine {

    public static final int OK = 0, WARN = 1, FAIL = 2, INFO = 3;

    public static final class Item {
        public String name;
        public int level;
        public String detail = "";
        public String advice = "";
        public Item(String n, int l, String d) { name = n; level = l; detail = d; }
        public String icon() {
            switch (level) {
                case OK: return "✓";
                case WARN: return "!";
                case FAIL: return "✗";
                default: return "·";
            }
        }
    }

    public static final class Report {
        public final List<Item> items = new ArrayList<>();
        public final List<String> advice = new ArrayList<>();
        public String conclusion = "";
        public long elapsed;
        public String text;

        public int fails() {
            int n = 0;
            for (Item i : items) if (i.level == FAIL) n++;
            return n;
        }

        public int warns() {
            int n = 0;
            for (Item i : items) if (i.level == WARN) n++;
            return n;
        }

        public int score() {
            int s = 100 - fails() * 22 - warns() * 7;
            return Math.max(0, Math.min(100, s));
        }

        public String grade() {
            int s = score();
            if (s >= 90) return "优秀";
            if (s >= 75) return "良好";
            if (s >= 60) return "一般";
            if (s >= 40) return "较差";
            return "很差";
        }
    }

    public interface Progress {
        void onStage(String stage);
        void onItem(Item item);
    }

    private DiagEngine() {}

    public static Report run(Context ctx, boolean full, Progress p) {
        Report rep = new Report();
        long t0 = System.currentTimeMillis();

        // ---- L1 / L2 基础 ----
        stage(p, "检查网络连接状态…");
        boolean hasNet = false;
        String transport = "";
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null) {
                hasNet = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                transport = NetInfo.transportName(nc);
            }
        } catch (Throwable ignore) {}

        if (!hasNet) {
            add(rep, p, new Item("网络连接", FAIL, "未检测到可用网络"));
            rep.advice.add("检查 Wi-Fi 或移动数据开关是否打开");
            rep.advice.add("若已连接，尝试开关一次飞行模式重新注册网络");
            rep.conclusion = "设备当前没有可用的网络连接。";
            rep.elapsed = System.currentTimeMillis() - t0;
            rep.text = com.cmos.netdiag.core.Report.build(ctx, rep, full);
            return rep;
        }
        add(rep, p, new Item("网络连接", OK, "已连接 · " + transport));

        String band = NetInfo.wifiBand(ctx);
        if (!band.isEmpty()) {
            WifiManagerCheck wc = new WifiManagerCheck(ctx);
            if (wc.ok) {
                int rssi = wc.rssi;
                int level = Fmt.rssiLevel(rssi);
                add(rep, p, new Item("Wi-Fi 信号", level >= 2 ? OK : WARN,
                        wc.ssid + " · " + rssi + " dBm (" + Fmt.rssi(rssi) + ") · " + band));
                if (level < 2) rep.advice.add("Wi-Fi 信号偏弱，靠近路由器或改连 5G 频段");
            }
        }

        // ---- L2 网关 ----
        String gwRaw = NetInfo.gateway(ctx);
        if (gwRaw != null) {
            String pingGw = NetInfo.pingableGateway(ctx, gwRaw);
            if (pingGw == null) {
                add(rep, p, new Item("局域网网关", INFO, gwRaw + " · IPv6 链路本地地址，跳过 ICMP 测试"));
            } else {
                stage(p, "Ping 网关 " + gwRaw + "（局域网连通性）");
                Ping.Stats g = Ping.icmp(pingGw, 4, 300, 0, 1, null);
                add(rep, p, new Item("局域网网关", g.recv == 0 ? FAIL :
                        (g.loss() > 0 || g.avg > 20 ? WARN : OK),
                        gwRaw + " · " + g.summary()));
                if (g.recv == 0) {
                    // ICMP 可能被路由器屏蔽，用 TCP 二次确认
                    Ping.Stats gt = Ping.tcp(pingGw, 80, 2, 1200, null);
                    if (gt.recv > 0) {
                        add(rep, p, new Item("局域网网关(ICMP 被拦)", OK,
                                gwRaw + " · TCP 可达 " + Fmt.ms(gt.avg) + "，路由器屏蔽了 ICMP"));
                    } else {
                        rep.advice.add("网关不通：路由器可能死机、Wi-Fi 关联异常或 IP 冲突，建议重启路由器");
                    }
                } else if (g.avg > 30) {
                    rep.advice.add("网关延迟偏高（" + Fmt.ms(g.avg) + "），局域网可能存在干扰或占用");
                }
            }
        } else {
            add(rep, p, new Item("局域网网关", WARN, "未获取到默认网关"));
        }

        // ---- L3 公网 ----
        stage(p, "Ping 公网 DNS（出口连通性）");
        Ping.Stats pub = Ping.icmp("223.5.5.5", 4, 300, 0, 1, null);
        boolean pubOk = pub.recv > 0;
        add(rep, p, new Item("公网连通性", pubOk ? (pub.loss() > 0 ? WARN : OK) : FAIL,
                "223.5.5.5 · " + pub.summary()));
        if (!pubOk) {
            Ping.Stats pub2 = Ping.tcp("223.5.5.5", 443, 3, 1500, null);
            if (pub2.recv > 0) {
                pubOk = true;
                add(rep, p, new Item("公网连通性(ICMP 被拦)", WARN,
                        "ICMP 不通但 TCP 443 可达 · " + pub2.summary()));
                rep.advice.add("网络对 ICMP 有限制，属于正常现象，不影响上网");
            } else {
                rep.advice.add("公网不可达：上游线路故障、欠费停机或需要认证（Portal 登录）");
            }
        } else if (pub.avg > 150) {
            rep.advice.add("公网延迟偏高（" + Fmt.ms(pub.avg) + "），可能存在跨网或拥塞");
        }

        // ---- L4 DNS ----
        stage(p, "测试 DNS 解析…");
        List<String> servers = serverList(ctx);
        List<Dns.Result> dnsResults = new ArrayList<>();
        int dnsOk = 0, dnsFail = 0;
        double bestMs = Double.MAX_VALUE;
        String bestServer = "";
        for (String srv : servers) {
            Dns.Result r = Dns.query(srv, "www.baidu.com", "A", 2000);
            dnsResults.add(r);
            if (r.ok()) {
                dnsOk++;
                if (r.ms < bestMs) { bestMs = r.ms; bestServer = srv; }
            } else dnsFail++;
        }
        Dns.Result sys = Dns.systemResolve("www.baidu.com", 2500);
        if (sys.ok()) dnsOk++;
        else dnsFail++;

        Item dnsItem = new Item("DNS 解析", dnsFail == 0 ? OK : (dnsOk > 0 ? WARN : FAIL),
                String.format(java.util.Locale.US, "%d/%d 台服务器可用 · 最快 %s (%s)",
                        dnsOk, servers.size() + 1, Fmt.ms(bestMs), bestServer.isEmpty() ? "-" : bestServer));
        add(rep, p, dnsItem);
        if (dnsOk == 0) {
            rep.advice.add("DNS 全部解析失败：可能是 DNS 被劫持/运营商 DNS 故障，建议改用 223.5.5.5 或 119.29.29.29");
        } else if (!sys.ok()) {
            rep.advice.add("系统 DNS 解析失败但公共 DNS 正常，检查手机 DNS 设置或私有 DNS 配置");
        }

        // DNS 一致性（劫持）检查
        stage(p, "校验各 DNS 返回结果一致性…");
        Set<String> distinct = new LinkedHashSet<>();
        for (Dns.Result r : dnsResults) {
            String ip = r.firstIp();
            if (ip != null) distinct.add(ip);
        }
        if (distinct.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (String d : distinct) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(d);
            }
            add(rep, p, new Item("DNS 一致性", INFO,
                    "各服务器返回不同 IP：" + sb + "（通常为 CDN 就近调度）"));
        } else if (!distinct.isEmpty()) {
            add(rep, p, new Item("DNS 一致性", OK, "所有服务器返回一致：" + distinct.iterator().next()));
        }

        // ---- L5 应用层 ----
        stage(p, "HTTP 应用层探测…");
        HttpProbe.Result http = HttpProbe.probe("http://connect.rom.miui.com/generate_204", 5000, false);
        if (http.code <= 0) http = HttpProbe.probe("http://www.baidu.com", 5000, false);
        boolean httpOk = http.code > 0 && http.code < 400;
        add(rep, p, new Item("HTTP 访问", httpOk ? OK : FAIL,
                httpOk ? ("HTTP " + http.code + " · TTFB " + Fmt.ms(http.ttfbMs))
                        : ("失败：" + (http.error == null ? "无响应" : http.error))));
        if (!httpOk) {
            rep.advice.add("HTTP 不通：可能有 Portal 认证页、代理配置错误或被防火墙拦截");
        } else if (http.ttfbMs > 1500) {
            rep.advice.add("HTTP 首字节耗时较长（" + Fmt.ms(http.ttfbMs) + "），网络质量欠佳");
        }

        stage(p, "HTTPS / TLS 握手…");
        String tls = HttpProbe.tlsVersion("www.baidu.com", 443, 5000);
        add(rep, p, new Item("HTTPS 握手", tls.startsWith("TLS") ? OK : WARN, tls));

        if (full) {
            stage(p, "探测路径 MTU…");
            String ip = Ping.resolve("223.5.5.5");
            if (ip != null) {
                Mtu.Result m = Mtu.probe("223.5.5.5", 1500);
                add(rep, p, new Item("路径 MTU", m.mtu >= 1400 ? OK : WARN, m.text()));
            }
            stage(p, "端口连通性抽样…");
            int open = 0;
            StringBuilder sb = new StringBuilder();
            int[] ports = {80, 443, 53, 22};
            for (int port : ports) {
                PortScan.Item it = PortScan.probe("223.5.5.5", port, 1500, false);
                if (it.open) open++;
                if (sb.length() > 0) sb.append(" ");
                sb.append(port).append(it.open ? "✓" : "✗");
            }
            add(rep, p, new Item("TCP 端口抽样", open > 0 ? OK : WARN, "223.5.5.5 → " + sb));
        }

        // ---- 结论 ----
        rep.conclusion = conclusion(rep, pubOk, dnsOk > 0, httpOk);
        rep.elapsed = System.currentTimeMillis() - t0;
        rep.text = com.cmos.netdiag.core.Report.build(ctx, rep, full);
        return rep;
    }

    private static void stage(Progress p, String s) {
        if (p != null) p.onStage(s);
    }

    private static void add(Report r, Progress p, Item i) {
        r.items.add(i);
        if (p != null) p.onItem(i);
    }

    private static String conclusion(Report r, boolean pubOk, boolean dnsOk, boolean httpOk) {
        if (r.fails() == 0 && r.warns() == 0) return "网络各项指标正常，可以正常使用。";
        if (!pubOk) {
            for (Item i : r.items) {
                if ("局域网网关".equals(i.name) && i.level == FAIL) {
                    return "故障定位：局域网层。设备与路由器之间不通，问题在 Wi-Fi 或路由器本身。";
                }
            }
            return "故障定位：出口/运营商层。局域网正常但公网不可达。";
        }
        if (!dnsOk) return "故障定位：DNS 层。网络可达但域名无法解析。";
        if (!httpOk) return "故障定位：应用层。DNS 正常但 HTTP 访问失败。";
        return "网络基本可用，但存在 " + r.warns() + " 项异常指标，建议查看下方建议。";
    }

    public static List<String> serverList(Context ctx) {
        Set<String> set = new LinkedHashSet<>();
        for (String d : NetInfo.dnsServers(ctx)) set.add(d);
        set.add("223.5.5.5");
        set.add("119.29.29.29");
        set.add("114.114.114.114");
        set.add("8.8.8.8");
        set.add("1.1.1.1");
        return new ArrayList<>(set);
    }

    public static String serverLabel(String ip) {
        switch (ip) {
            case "223.5.5.5": return "阿里 DNS";
            case "119.29.29.29": return "腾讯 DNS";
            case "114.114.114.114": return "114 DNS";
            case "8.8.8.8": return "Google DNS";
            case "1.1.1.1": return "Cloudflare";
            case "180.76.76.76": return "百度 DNS";
            default: return "运营商/本地";
        }
    }

    private static final class WifiManagerCheck {
        boolean ok;
        int rssi;
        String ssid;
        @SuppressWarnings("deprecation")
        WifiManagerCheck(Context ctx) {
            try {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                        ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                android.net.wifi.WifiInfo wi = wm.getConnectionInfo();
                if (wi != null && wi.getNetworkId() != -1) {
                    ok = true;
                    rssi = wi.getRssi();
                    ssid = wi.getSSID() == null ? "" : wi.getSSID().replace("\"", "");
                }
            } catch (Throwable ignore) {}
        }
    }
}
