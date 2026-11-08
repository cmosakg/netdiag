package com.cmos.netdiag.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 本机网络栈视图：路由表、邻居表、TCP/UDP 连接、监听端口 */
public final class NetStack {

    public static final class Block {
        public String title;
        public String body;
        public String hint;
        public Block(String t, String b) { title = t; body = b; }
    }

    private NetStack() {}

    public static List<Block> collect(Context ctx) {
        List<Block> out = new ArrayList<>();
        out.add(new Block("应用层路由（无需 root）", appView(ctx)));

        out.add(new Block("IPv4 路由表", firstNonEmpty(
                () -> Shell.root("ip route show 2>/dev/null", 5000).out,
                () -> Shell.root("cat /proc/net/route 2>/dev/null", 5000).out,
                () -> "需要 root 才能读取完整路由表")));

        out.add(new Block("IPv6 路由表", firstNonEmpty(
                () -> Shell.root("ip -6 route 2>/dev/null", 5000).out,
                () -> "设备可能未启用 IPv6")));

        out.add(new Block("邻居/ARP 表", firstNonEmpty(
                () -> Shell.root("ip neigh 2>/dev/null", 5000).out,
                () -> Shell.root("cat /proc/net/arp 2>/dev/null", 5000).out,
                () -> "需要 root")));

        out.add(new Block("TCP 连接", tcpConnections()));

        out.add(new Block("UDP 监听", firstNonEmpty(
                () -> filterListen(Shell.root("ss -uan 2>/dev/null", 5000).out),
                () -> filterListen(Shell.root("netstat -uan 2>/dev/null", 5000).out),
                () -> "需要 root 或系统无 ss/netstat")));

        out.add(new Block("监听端口（TCP）", firstNonEmpty(
                () -> filterListen(Shell.root("ss -tln 2>/dev/null", 5000).out),
                () -> filterListen(Shell.root("netstat -tln 2>/dev/null", 5000).out),
                () -> procTcp(true))));

        out.add(new Block("IPv6 地址", firstNonEmpty(
                () -> Shell.root("ip -6 addr show scope global 2>/dev/null", 5000).out,
                () -> "无全局 IPv6")));

        return out;
    }

    /** 用系统 API 读取的路由信息（不需要 root，任何情况都有内容） */
    public static String appView(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return "无活动网络";
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp == null) return "无链路信息";
            int mtu = lp.getMtu() > 0 ? lp.getMtu() : 1500;
            sb.append("接口: ").append(lp.getInterfaceName())
                    .append("    MTU: ").append(mtu).append('\n');
            sb.append("地址:\n");
            int n4 = 0, n6 = 0;
            for (LinkAddress la : lp.getLinkAddresses()) {
                InetAddress a = la.getAddress();
                if (a.isLinkLocalAddress()) continue;
                boolean is4 = a instanceof java.net.Inet4Address;
                if (is4) {
                    if (n4 >= 3) continue;
                    n4++;
                } else {
                    if (n6 >= 2) continue;  // IPv6 临时隐私地址很多，只展示前两个
                    n6++;
                }
                sb.append("  ").append(a.getHostAddress()).append('/').append(la.getPrefixLength());
                sb.append('\n');
            }
            sb.append("DNS:\n");
            for (InetAddress a : lp.getDnsServers()) {
                sb.append("  ").append(a.getHostAddress()).append('\n');
            }
            sb.append("路由表:\n");
            for (RouteInfo ri : lp.getRoutes()) {
                InetAddress gw = ri.getGateway();
                sb.append(ri.isDefaultRoute() ? "  [默认] " : "         ")
                        .append(ri.getDestination())
                        .append("  via ").append(gw == null ? "直连" : gw.getHostAddress())
                        .append("  dev ").append(ri.getInterface());
                sb.append('\n');
            }
        } catch (Throwable t) {
            return "读取失败: " + t.getMessage();
        }
        return sb.toString().trim();
    }

    private interface Sup { String get() throws Throwable; }

    private static String firstNonEmpty(Sup... sups) {
        for (Sup s : sups) {
            try {
                String v = s.get();
                if (v != null && !v.trim().isEmpty()) return v.trim();
            } catch (Throwable ignore) {}
        }
        return "无数据";
    }

    private static String filterListen(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String line : Shell.lines(s)) {
            if (line.startsWith("State") || line.startsWith("Active") || line.startsWith("Proto")) {
                sb.append(line).append('\n');
                continue;
            }
            sb.append(line).append('\n');
            n++;
        }
        return n == 0 ? null : sb.toString().trim();
    }

    public static String tcpConnections() {
        String ss = Shell.root("ss -tn state established 2>/dev/null", 6000).out;
        if (ss != null && ss.contains("ESTAB") || (ss != null && ss.trim().length() > 20)) {
            if (!ss.trim().isEmpty()) return decorate(ss.trim());
        }
        String ns = Shell.root("netstat -tn 2>/dev/null", 6000).out;
        if (ns != null && !ns.trim().isEmpty()) return decorate(ns.trim());
        String proc = procTcp(false);
        if (proc != null && !proc.isEmpty()) return proc;
        return "无数据（需要 root）";
    }

    private static String decorate(String s) {
        int est = 0, listen = 0, timeWait = 0;
        for (String line : s.split("\n")) {
            if (line.contains("ESTAB")) est++;
            else if (line.contains("LISTEN")) listen++;
            else if (line.contains("TIME-WAIT") || line.contains("TIME_WAIT")) timeWait++;
        }
        return "建立 " + est + " 条 · 监听 " + listen + " 条 · TIME_WAIT " + timeWait + " 条\n\n" + s;
    }

    /** 直接解析 /proc/net/tcp（不需要 root，但需要读权限；多数系统可读） */
    public static String procTcp(boolean onlyListen) {
        try {
            List<String> lines = Shell.lines(Shell.run("cat /proc/net/tcp 2>/dev/null", 4000).out);
            if (lines.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (int i = 1; i < lines.size() && n < 80; i++) {
                String[] f = lines.get(i).trim().split("\\s+");
                if (f.length < 4) continue;
                String local = hexAddr(f[1]);
                String remote = hexAddr(f[2]);
                String st = f[3];
                String state = tcpState(st);
                if (onlyListen && !"01".equals(st)) continue;
                sb.append(String.format(Locale.US, "%-22s %-22s %s%n", local, remote, state));
                n++;
            }
            return n == 0 ? null : "本地地址               远端地址               状态\n" + sb;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String hexAddr(String s) {
        try {
            String[] p = s.split(":");
            if (p.length != 2) return s;
            long ip = Long.parseLong(p[0], 16);
            int port = Integer.parseInt(p[1], 16);
            String a = String.format(Locale.US, "%d.%d.%d.%d",
                    ip & 0xFF, (ip >> 8) & 0xFF, (ip >> 16) & 0xFF, (ip >> 24) & 0xFF);
            return a + ":" + port;
        } catch (Throwable t) {
            return s;
        }
    }

    private static String tcpState(String hex) {
        switch (hex) {
            case "01": return "ESTABLISHED";
            case "02": return "SYN_SENT";
            case "03": return "SYN_RECV";
            case "04": return "FIN_WAIT1";
            case "05": return "FIN_WAIT2";
            case "06": return "TIME_WAIT";
            case "07": return "CLOSE";
            case "08": return "CLOSE_WAIT";
            case "09": return "LAST_ACK";
            case "0A": return "LISTEN";
            case "0B": return "CLOSING";
            default: return hex;
        }
    }
}
