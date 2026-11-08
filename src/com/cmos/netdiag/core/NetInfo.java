package com.cmos.netdiag.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.CellSignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** 网络与环境信息采集 */
public final class NetInfo {

    public static final class Section {
        public String title;
        public LinkedHashMap<String, String> kv = new LinkedHashMap<>();
        public Section(String t) { title = t; }
        public Section put(String k, String v) {
            if (v != null && !v.trim().isEmpty()) kv.put(k, v.trim());
            return this;
        }
    }

    private NetInfo() {}

    public static List<Section> collect(Context ctx) {
        List<Section> out = new ArrayList<>();
        try { out.add(connSection(ctx)); } catch (Throwable t) { out.add(err("连接状态", t)); }
        try { Section s = wifiSection(ctx); if (s != null) out.add(s); } catch (Throwable t) { out.add(err("Wi-Fi", t)); }
        try { Section s = cellSection(ctx); if (s != null) out.add(s); } catch (Throwable t) { out.add(err("蜂窝网络", t)); }
        try { out.add(ipSection(ctx)); } catch (Throwable t) { out.add(err("IP 与 DNS", t)); }
        try { out.add(publicIpSection()); } catch (Throwable t) { out.add(err("公网出口", t)); }
        try { out.add(proxySection(ctx)); } catch (Throwable t) { out.add(err("代理 / VPN", t)); }
        try { out.add(ifaceSection()); } catch (Throwable t) { out.add(err("网卡", t)); }
        try { out.add(deviceSection()); } catch (Throwable t) { out.add(err("设备", t)); }
        return out;
    }

    private static Section err(String title, Throwable t) {
        Section s = new Section(title);
        s.put("采集失败", String.valueOf(t.getMessage()));
        return s;
    }

    // ---------------- 连接状态 ----------------
    public static Section connSection(Context ctx) {
        Section s = new Section("连接状态");
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return s;
        Network n = cm.getActiveNetwork();
        if (n == null) {
            s.put("状态", "❌ 无活动网络");
            return s;
        }
        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (nc != null) {
            s.put("传输类型", transportName(nc));
            s.put("联网能力", (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ? "有互联网 " : "无互联网 ")
                    + (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "✓已验证" : "✗未验证(可能需认证)"));
            s.put("估计带宽", "↓" + (nc.getLinkDownstreamBandwidthKbps() / 1000) + " Mbps / ↑"
                    + (nc.getLinkUpstreamBandwidthKbps() / 1000) + " Mbps");
            s.put("计费网络", nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ? "否(不限量)" : "是(计费)");
            s.put("VPN", nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "是" : "否");
        }
        if (ni != null) {
            s.put("网络类型", ni.getTypeName() + (ni.getSubtypeName().isEmpty() ? "" : " / " + ni.getSubtypeName()));
        }
        return s;
    }

    public static String transportName(NetworkCapabilities nc) {
        List<String> t = new ArrayList<>();
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) t.add("Wi-Fi");
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) t.add("蜂窝数据");
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) t.add("以太网");
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) t.add("VPN");
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) t.add("蓝牙");
        if (Build.VERSION.SDK_INT >= 31 && nc.hasTransport(NetworkCapabilities.TRANSPORT_USB)) t.add("USB");
        return t.isEmpty() ? "未知" : TextUtils.join(" + ", t);
    }

    // ---------------- Wi-Fi ----------------
    @SuppressWarnings("deprecation")
    public static Section wifiSection(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return null;
        WifiInfo wi = wm.getConnectionInfo();
        Section s = new Section("Wi-Fi");
        if (wi == null || wi.getNetworkId() == -1) {
            s.put("状态", "未连接 Wi-Fi");
            s.put("Wi-Fi 开关", wm.isWifiEnabled() ? "已开启" : "已关闭");
            return s;
        }
        String ssid = wi.getSSID();
        if (ssid != null) ssid = ssid.replace("\"", "");
        s.put("SSID", ssid);
        s.put("BSSID", wi.getBSSID());
        int rssi = wi.getRssi();
        s.put("信号强度", rssi + " dBm · " + Fmt.rssi(rssi));
        int freq = wi.getFrequency();
        s.put("频率", Fmt.freqBand(freq));
        int ch = Fmt.freqToChannel(freq);
        if (ch > 0) s.put("信道", String.valueOf(ch));
        if (Build.VERSION.SDK_INT >= 30 && wi.getRxLinkSpeedMbps() > 0) {
            s.put("链接速率", "接收 " + wi.getRxLinkSpeedMbps() + " / 发送 " + wi.getTxLinkSpeedMbps() + " Mbps");
        } else if (wi.getLinkSpeed() > 0) {
            s.put("链接速率", wi.getLinkSpeed() + " Mbps");
        }
        if (Build.VERSION.SDK_INT >= 30) {
            s.put("Wi-Fi 标准", wifiStandard(wi.getWifiStandard()));
            if (wi.getMaxSupportedRxLinkSpeedMbps() > 0) {
                s.put("最大能力", "↓" + wi.getMaxSupportedRxLinkSpeedMbps() + " / ↑" + wi.getMaxSupportedTxLinkSpeedMbps() + " Mbps");
            }
        }
        s.put("IP 地址", intToIp(wi.getIpAddress()));
        s.put("隐藏 SSID", wi.getHiddenSSID() ? "是" : "否");
        DhcpInfo d = wm.getDhcpInfo();
        if (d != null) {
            s.put("网关", intToIp(d.gateway));
            s.put("子网掩码", intToIp(d.netmask));
            s.put("DHCP 服务器", intToIp(d.serverAddress));
            s.put("DNS1", intToIp(d.dns1));
            if (d.dns2 != 0) s.put("DNS2", intToIp(d.dns2));
            if (d.leaseDuration > 0) s.put("租期", Fmt.duration(d.leaseDuration * 1000L));
        }
        return s;
    }

    public static String wifiStandard(int std) {
        switch (std) {
            case 1: return "802.11a";
            case 2: return "802.11b";
            case 3: return "802.11g";
            case 4: return "802.11n (Wi-Fi 4)";
            case 5: return "802.11ac (Wi-Fi 5)";
            case 6: return "802.11ax (Wi-Fi 6)";
            case 7: return "802.11be (Wi-Fi 7)";
            default: return "未知";
        }
    }

    // ---------------- 蜂窝 ----------------
    public static Section cellSection(Context ctx) {
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return null;
        String op = tm.getNetworkOperatorName();
        if (TextUtils.isEmpty(op) && TextUtils.isEmpty(tm.getSimOperatorName())) return null;
        Section s = new Section("蜂窝网络");
        s.put("运营商", op);
        s.put("SIM 运营商", tm.getSimOperatorName() + " (" + tm.getSimOperator() + ")");
        s.put("SIM 状态", simState(tm.getSimState()));
        s.put("网络类型", netType(tm.getDataNetworkType()));
        s.put("漫游", tm.isNetworkRoaming() ? "是" : "否");
        try {
            android.telephony.SignalStrength ss = tm.getSignalStrength();
            if (ss != null) {
                List<CellSignalStrength> list = ss.getCellSignalStrengths();
                for (CellSignalStrength c : list) {
                    String name = c.getClass().getSimpleName();
                    String label;
                    if (c instanceof android.telephony.CellSignalStrengthNr) label = "5G NR";
                    else if (c instanceof android.telephony.CellSignalStrengthLte) label = "LTE";
                    else if (c instanceof android.telephony.CellSignalStrengthWcdma) label = "WCDMA";
                    else if (c instanceof android.telephony.CellSignalStrengthGsm) label = "GSM";
                    else if (c instanceof android.telephony.CellSignalStrengthCdma) label = "CDMA";
                    else label = name;
                    s.put(label + " 信号", c.getDbm() + " dBm (level " + c.getLevel() + "/4, asu " + c.getAsuLevel() + ")");
                }
            }
        } catch (Throwable ignore) {}
        try {
            List<CellInfo> cells = tm.getAllCellInfo();
            if (cells != null) {
                int n = 0;
                for (CellInfo ci : cells) {
                    if (!ci.isRegistered()) continue;
                    n++;
                    String extra = "";
                    if (ci instanceof android.telephony.CellInfoLte) {
                        android.telephony.CellIdentityLte id = ((android.telephony.CellInfoLte) ci).getCellIdentity();
                        extra = "TAC " + id.getTac() + " / CI " + id.getCi();
                    } else if (Build.VERSION.SDK_INT >= 29 && ci instanceof android.telephony.CellInfoNr) {
                        android.telephony.CellIdentity cid =
                                ((android.telephony.CellInfoNr) ci).getCellIdentity();
                        if (cid instanceof android.telephony.CellIdentityNr) {
                            android.telephony.CellIdentityNr id = (android.telephony.CellIdentityNr) cid;
                            extra = "TAC " + id.getTac() + " / NCI " + id.getNci()
                                    + " / NRARFCN " + id.getNrarfcn();
                        }
                    }
                    if (!extra.isEmpty()) s.put("服务小区", extra);
                    if (n >= 1) break;
                }
            }
        } catch (Throwable ignore) {}
        try {
            s.put("数据开关", tm.isDataEnabled() ? "开启" : "关闭");
        } catch (Throwable ignore) {}
        return s;
    }

    public static String simState(int st) {
        switch (st) {
            case TelephonyManager.SIM_STATE_READY: return "就绪";
            case TelephonyManager.SIM_STATE_ABSENT: return "无 SIM 卡";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "需要 PIN";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "需要 PUK";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "网络锁定";
            default: return "状态" + st;
        }
    }

    public static String netType(int t) {
        switch (t) {
            case TelephonyManager.NETWORK_TYPE_NR: return "5G NR";
            case TelephonyManager.NETWORK_TYPE_LTE: return "4G LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "3G HSPA+";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "3G UMTS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "2G EDGE";
            case TelephonyManager.NETWORK_TYPE_GPRS: return "2G GPRS";
            case TelephonyManager.NETWORK_TYPE_CDMA: return "2G CDMA";
            case TelephonyManager.NETWORK_TYPE_EVDO_0: return "3G EVDO";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN: return "未知";
            default: return "类型" + t;
        }
    }

    // ---------------- IP / DNS ----------------
    public static Section ipSection(Context ctx) {
        Section s = new Section("IP 与 DNS");
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return s;
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) {
            s.put("状态", "无链路信息");
            return s;
        }
        s.put("接口", lp.getInterfaceName());
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress a = la.getAddress();
            String prefix = "/" + la.getPrefixLength();
            if (a instanceof Inet4Address) s.put("IPv4", a.getHostAddress() + prefix);
            else if (a instanceof Inet6Address) {
                if (!a.isLinkLocalAddress()) s.put("IPv6", a.getHostAddress() + prefix);
            }
        }
        List<InetAddress> dns = lp.getDnsServers();
        for (int i = 0; i < dns.size() && i < 3; i++) {
            s.put("DNS " + (i + 1), dns.get(i).getHostAddress());
        }
        for (RouteInfo ri : lp.getRoutes()) {
            if (ri.isDefaultRoute()) {
                InetAddress gw = ri.getGateway();
                if (gw != null && !gw.isAnyLocalAddress()) {
                    s.put("默认网关", gw.getHostAddress());
                    break;
                }
            }
        }
        s.put("MTU", String.valueOf(lp.getMtu()));
        if (!TextUtils.isEmpty(lp.getDomains())) s.put("搜索域", lp.getDomains());
        String pd = null;
        try {
            pd = Settings.Global.getString(ctx.getContentResolver(), "private_dns_mode");
        } catch (Throwable ignore) {}
        if (pd != null) {
            String spec = null;
            try {
                spec = Settings.Global.getString(ctx.getContentResolver(), "private_dns_specifier");
            } catch (Throwable ignore) {}
            s.put("私有 DNS", ("hostname".equals(pd) ? "指定主机 " + (spec == null ? "" : spec)
                    : "off".equals(pd) ? "关闭" : "自动"));
        }
        return s;
    }

    // ---------------- 公网出口 ----------------
    private static String cachedPublic = null;
    private static long cachedAt = 0;

    public static Section publicIpSection() {
        Section s = new Section("公网出口");
        String[] urls = {
                "http://ip-api.com/json/?lang=zh-CN&fields=status,country,regionName,city,isp,as,query",
                "https://api.ip.sb/jsonip",
                "https://myip.ipip.net"
        };
        for (String u : urls) {
            String body = httpGet(u, 4000);
            if (body == null) continue;
            String ip = find(body, "\"query\"\\s*:\\s*\"([^\"]+)\"");
            if (ip == null) ip = find(body, "\"ip\"\\s*:\\s*\"([^\"]+)\"");
            if (ip == null) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})").matcher(body);
                if (m.find()) ip = m.group(1);
            }
            if (ip != null) {
                s.put("公网 IP", ip);
                cachedPublic = ip;
                cachedAt = System.currentTimeMillis();
                String country = find(body, "\"country\"\\s*:\\s*\"([^\"]+)\"");
                String region = find(body, "\"regionName\"\\s*:\\s*\"([^\"]+)\"");
                String city = find(body, "\"city\"\\s*:\\s*\"([^\"]+)\"");
                if (country != null) s.put("位置", country + " " + nz(region) + " " + nz(city));
                String isp = find(body, "\"isp\"\\s*:\\s*\"([^\"]+)\"");
                if (isp != null) s.put("运营商", isp);
                String as = find(body, "\"as\"\\s*:\\s*\"([^\"]+)\"");
                if (as != null) s.put("AS", as);
                return s;
            }
        }
        s.put("公网 IP", "获取失败（可能无外网）");
        return s;
    }

    public static String cachedPublicIp() {
        if (cachedPublic != null && System.currentTimeMillis() - cachedAt < 120_000) return cachedPublic;
        return null;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String find(String s, String regex) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(s);
            if (m.find()) return m.group(1);
        } catch (Throwable ignore) {}
        return null;
    }

    public static String httpGet(String url, int timeoutMs) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "NetDiag/1.0");
            if (c.getResponseCode() >= 400) return null;
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 65536) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
        }
    }

    // ---------------- 代理 / VPN ----------------
    @SuppressWarnings("deprecation")
    public static Section proxySection(Context ctx) {
        Section s = new Section("代理 / VPN");
        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");
        s.put("系统代理", host == null ? "未设置" : host + ":" + (port == null ? "?" : port));
        try {
            String ph = Settings.Global.getString(ctx.getContentResolver(), "http_proxy");
            s.put("全局 HTTP 代理", ph == null || ph.isEmpty() ? "未设置" : ph);
        } catch (Throwable ignore) {}
        boolean vpn = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true;
            }
        } catch (Throwable ignore) {}
        s.put("VPN", vpn ? "已连接" : "未连接");
        // 检测常见代理端口是否在本机监听
        StringBuilder sb = new StringBuilder();
        for (int p : new int[]{7890, 7891, 1080, 10808, 10809, 8889, 9090}) {
            PortScan.Item it = PortScan.probe("127.0.0.1", p, 200, false);
            if (it.open) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p);
            }
        }
        s.put("本机代理端口", sb.length() == 0 ? "无监听" : sb.toString() + " 已监听");
        return s;
    }

    // ---------------- 网卡 ----------------
    public static Section ifaceSection() {
        Section s = new Section("网卡");
        try {
            List<NetworkInterface> list = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : list) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                StringBuilder sb = new StringBuilder();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a.isLinkLocalAddress()) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(a.getHostAddress());
                }
                if (sb.length() == 0) continue;
                s.put(ni.getName(), sb + " · MTU " + ni.getMTU()
                        + (ni.isVirtual() ? " · 虚拟" : ""));
            }
        } catch (Throwable t) {
            s.put("错误", String.valueOf(t.getMessage()));
        }
        return s;
    }

    public static Section deviceSection() {
        Section s = new Section("设备");
        s.put("型号", Build.MANUFACTURER + " " + Build.MODEL);
        s.put("系统", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        s.put("指纹", Build.FINGERPRINT);
        s.put("时间", Fmt.time());
        Shell.R r = Shell.run("getprop ro.build.version.security_patch");
        if (r.ok()) s.put("安全补丁", r.out.trim());
        Shell.R k = Shell.root("uname -r", 3000);
        if (k.ok()) s.put("内核", k.out.trim());
        s.put("Root", Shell.hasRoot() ? "可用" : "不可用");
        return s;
    }

    public static String intToIp(int ip) {
        if (ip == 0) return null;
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    /** 默认网关（优先 IPv4，IPv6 链路本地地址无法直接 ping） */
    public static String gateway(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) {
                String v6 = null;
                for (RouteInfo ri : lp.getRoutes()) {
                    if (!ri.isDefaultRoute()) continue;
                    InetAddress gw = ri.getGateway();
                    if (gw == null || gw.isAnyLocalAddress()) continue;
                    if (gw instanceof Inet4Address) return gw.getHostAddress();
                    if (v6 == null) v6 = gw.getHostAddress();
                }
                if (v6 != null) return v6;
            }
        } catch (Throwable ignore) {}
        Shell.R r = Shell.root("ip route 2>/dev/null | grep default", 3000);
        for (String line : Shell.lines(r.out)) {
            String[] parts = line.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("via".equals(parts[i])) return parts[i + 1];
            }
        }
        return null;
    }

    /** 网关是否可直接 ping（IPv6 链路本地地址需要接口 scope，单独处理） */
    public static String pingableGateway(Context ctx, String gw) {
        if (gw == null) return null;
        if (gw.startsWith("fe80:") || gw.startsWith("FE80:")) {
            String iface = currentInterface(ctx);
            if (iface == null) return null;
            return gw + "%" + iface;
        }
        return gw;
    }

    public static String currentInterface(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) return lp.getInterfaceName();
        } catch (Throwable ignore) {}
        return null;
    }

    /** 运营商 DNS（Dialer 里配置的），用于对比 */
    public static List<String> dnsServers(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) {
                for (InetAddress a : lp.getDnsServers()) {
                    String h = a.getHostAddress();
                    if (h != null && h.contains(".") && !out.contains(h)) out.add(h);
                }
            }
        } catch (Throwable ignore) {}
        if (out.isEmpty()) {
            Shell.R r = Shell.run("getprop net.dns1", 2000);
            if (r.ok() && !r.out.trim().isEmpty()) out.add(r.out.trim());
        }
        return out;
    }

    public static String interfaceMtu(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) return String.valueOf(lp.getMtu());
        } catch (Throwable ignore) {}
        return "1500";
    }

    public static String wifiBand(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            if (wi == null) return "";
            int f = wi.getFrequency();
            if (f <= 0) return "";
            return f < 3000 ? "2.4G" : "5G";
        } catch (Throwable t) {
            return "";
        }
    }

    public static String locale(double d) {
        return String.format(Locale.US, "%.1f", d);
    }
}
