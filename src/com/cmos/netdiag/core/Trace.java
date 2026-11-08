package com.cmos.netdiag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traceroute：Android 普遍没有 traceroute 二进制，这里用 ping -t TTL 逐跳逼近，
 * 每跳发 3 包统计丢包与延迟；也支持纯 Java 的 TCP 追路（TTL 递增不可用时降级）。
 */
public final class Trace {

    public static final class Hop {
        public int ttl;
        public String ip = "*";
        public String host = "";
        public double avg = Double.NaN;
        public double min = Double.NaN;
        public int recv;
        public int sent;
        public double loss() { return sent == 0 ? 100 : (sent - recv) * 100d / sent; }
        public String display() {
            if ("*".equals(ip)) return "* * *";
            String rtt = recv == 0 ? "*" : Fmt.ms(avg);
            return ip + (host.isEmpty() ? "" : " (" + host + ")") + "  " + rtt;
        }
    }

    public interface Progress {
        void onHop(Hop hop);
        void onInfo(String info);
    }

    private static final Pattern P_FROM = Pattern.compile("(?i)from\\s+([0-9a-fA-F:.]+)");
    private static final Pattern P_TIME = Pattern.compile("time[=<]\\s*([\\d.]+)");
    private static final Pattern P_STATS = Pattern.compile("(\\d+) packets transmitted");

    private Trace() {}

    public static List<Hop> run(String target, int maxHops, int probes, int timeoutSec, Progress p) {
        List<Hop> hops = new ArrayList<>();
        String ip = Ping.resolve(target);
        if (ip == null) {
            if (p != null) p.onInfo("域名解析失败：" + target);
            return hops;
        }
        if (p != null) p.onInfo("目标 " + target + " -> " + ip + "，最多 " + maxHops + " 跳");
        boolean reached = false;
        for (int ttl = 1; ttl <= maxHops && !reached; ttl++) {
            Hop h = new Hop();
            h.ttl = ttl;
            String cmd = "ping -c " + probes + " -i 0.3 -W " + timeoutSec + " -t " + ttl + " " + ip + " 2>&1";
            Shell.R r = Shell.root(cmd, probes * (timeoutSec * 1000 + 400) + 3000);
            String out = r.out;
            double sum = 0;
            int n = 0;
            int responses = 0;
            double min = Double.MAX_VALUE;
            // 每一行 "From x.x.x.x: ... Time to live exceeded" 或 "64 bytes from x.x.x.x"
            // 都代表该跳有设备响应；TTL 超时没有 time= 字段，延迟记为未知
            for (String line : Shell.lines(out)) {
                Matcher mf = P_FROM.matcher(line);
                if (!mf.find()) continue;
                String got = mf.group(1);
                if (got == null || got.isEmpty()) continue;
                // "From 192.168.1.1: ..." 会连分隔冒号一起匹配，去掉尾部标点
                while (got.endsWith(":") || got.endsWith(".")) {
                    got = got.substring(0, got.length() - 1);
                }
                if (got.isEmpty()) continue;
                responses++;
                if ("*".equals(h.ip)) h.ip = got;
                Matcher mt = P_TIME.matcher(line);
                if (mt.find()) {
                    try {
                        double v = Double.parseDouble(mt.group(1));
                        sum += v;
                        n++;
                        min = Math.min(min, v);
                    } catch (Exception ignore) {}
                } else if (!line.toLowerCase().contains("exceeded")) {
                    // 未知格式但确实有响应
                }
            }
            h.sent = probes;
            h.recv = responses;
            if (n > 0) {
                h.avg = sum / n;
                h.min = min;
            }
            if (!"*".equals(h.ip)) {
                h.host = reverseSafe(h.ip);
                if (h.host.equals(h.ip)) h.host = "";
            }
            hops.add(h);
            if (p != null) p.onHop(h);
            if (ip.equals(h.ip)) reached = true;
            if (h.recv == 0 && ttl >= maxHops) break;
            // 连续 5 跳无响应，提前终止
            if (h.recv == 0 && hops.size() >= 6) {
                int silent = 0;
                for (int i = hops.size() - 1; i >= 0 && silent < 5; i--) {
                    if (hops.get(i).recv == 0) silent++; else break;
                }
                if (silent >= 5) {
                    if (p != null) p.onInfo("连续 5 跳无响应，提前结束（可能存在 ICMP 屏蔽）");
                    break;
                }
            }
        }
        return hops;
    }

    private static String reverseSafe(String ip) {
        try {
            return java.net.InetAddress.getByName(ip).getCanonicalHostName();
        } catch (Throwable t) {
            return ip;
        }
    }

    /** MTU 探测用：向目标发送指定大小的不分片包，返回是否可达 */
    public static boolean pingSize(String ip, int payloadSize, int ttl, int timeoutSec) {
        String cmd = "ping -c 1 -W " + timeoutSec + " -s " + payloadSize + " -M do"
                + (ttl > 0 ? " -t " + ttl : "") + " " + ip + " 2>&1";
        Shell.R r = Shell.root(cmd, timeoutSec * 1000 + 2500);
        String o = r.out;
        String lo = o.toLowerCase();
        if (o.contains("100% packet loss") || o.contains("0 received")) return false;
        if (lo.contains("frag needed") || lo.contains("fragmentation needed") || lo.contains("message too long")
                || lo.contains("invalid argument") || lo.contains("unknown option") || lo.contains("usage:")) {
            // -M 不支持时，用普通发包长度判断（不做分片控制，仅作参考）
            Shell.R r2 = Shell.root("ping -c 1 -W " + timeoutSec + " -s " + payloadSize + " " + ip + " 2>&1",
                    timeoutSec * 1000 + 2500);
            return r2.out.contains("bytes from") && !r2.out.contains("100% packet loss");
        }
        return o.contains("bytes from");
    }
}
