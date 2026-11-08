package com.cmos.netdiag.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ping 引擎：系统 ICMP ping + 纯 Java TCP ping 双通道 */
public final class Ping {

    public interface Progress {
        void onLine(String line);
        void onReply(int seq, double rtt, int ttl);
        void onTimeout(int seq);
    }

    public static final class Stats {
        public String target = "";
        public String ip = "";
        public String mode = "ICMP";
        public int sent;
        public int recv;
        public double min = Double.NaN, avg = Double.NaN, max = Double.NaN, jitter = Double.NaN;
        public final List<Double> rtts = new ArrayList<>();
        public final List<String> raw = new ArrayList<>();
        public long elapsed;
        public String error;

        public double loss() {
            return sent == 0 ? 100d : (sent - recv) * 100d / sent;
        }

        public String lossText() { return Fmt.pct(loss()); }

        /** 质量评级 */
        public String quality() {
            if (recv == 0) return "不可达";
            double l = loss(), a = Double.isNaN(avg) ? 999 : avg;
            if (l == 0 && a < 30 && jitter < 10) return "优秀";
            if (l < 2 && a < 80) return "良好";
            if (l < 10 && a < 150) return "一般";
            if (l < 30) return "较差";
            return "很差";
        }

        public String summary() {
            if (recv == 0) return mode + " · 全部超时（" + sent + " 包无响应）";
            return String.format(java.util.Locale.US,
                    "%s · %d/%d 收到 · 丢包 %s · RTT %.1f/%.1f/%.1f ms · 抖动 %.1f ms",
                    mode, recv, sent, lossText(), min, avg, max, jitter);
        }
    }

    private static final Pattern P_TIME = Pattern.compile("time[=<]\\s*([\\d.]+)\\s*ms");
    private static final Pattern P_SEQ = Pattern.compile("(?:icmp_)?seq[= ](\\d+)");
    private static final Pattern P_TTL = Pattern.compile("ttl[= ](\\d+)");
    private static final Pattern P_IP = Pattern.compile("from\\s+([0-9a-fA-F:.]+)");
    private static final Pattern P_FROM_IP = Pattern.compile("\\(([0-9.]+)\\)");

    private Ping() {}

    public static String resolve(String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (Throwable t) {
            return null;
        }
    }

    /** ICMP ping，通过系统 ping 二进制；不支持时自动降级为 TCP */
    public static Stats icmp(String host, int count, int intervalMs, int size, int perTimeoutSec, Progress p) {
        Stats s = new Stats();
        s.target = host;
        s.mode = "ICMP";
        long t0 = System.currentTimeMillis();
        String ip = resolve(host);
        s.ip = ip == null ? "解析失败" : ip;
        if (ip == null) {
            s.error = "域名解析失败";
            s.sent = count;
            return s;
        }

        StringBuilder cmd = new StringBuilder("ping -c ").append(count)
                .append(" -i ").append(Math.max(0.2, intervalMs / 1000.0))
                .append(" -W ").append(perTimeoutSec);
        if (size > 0) cmd.append(" -s ").append(size);
        cmd.append(' ').append(ip).append(" 2>&1");

        Shell.R r = Shell.root(cmd.toString(), count * intervalMs + perTimeoutSec * 1000 + 4000);
        s.raw.addAll(Shell.lines(r.out));
        int seq = 0, lastSeq = -1;
        for (String line : s.raw) {
            if (p != null) p.onLine(line);
            Matcher mt = P_TIME.matcher(line);
            if (mt.find() && line.contains("bytes from")) {
                double v;
                try { v = Double.parseDouble(mt.group(1)); } catch (Exception e) { continue; }
                Matcher mq = P_SEQ.matcher(line);
                seq = mq.find() ? parseInt(mq.group(1), seq + 1) : seq + 1;
                s.rtts.add(v);
                s.recv++;
                lastSeq = seq;
                int ttl = -1;
                Matcher mtt = P_TTL.matcher(line);
                if (mtt.find()) ttl = parseInt(mtt.group(1), -1);
                if (p != null) p.onReply(seq, v, ttl);
                Matcher mip = P_IP.matcher(line);
                if (mip.find() && (s.ip == null || s.ip.isEmpty())) s.ip = mip.group(1);
            } else if (line.contains("100% packet loss") || line.contains("100% loss")) {
                s.recv = 0;
            }
        }
        // 从统计行兜底读取丢包
        Matcher ml = Pattern.compile("(\\d+)% packet loss").matcher(r.out);
        int total = count;
        if (ml.find()) {
            try {
                int lossPct = Integer.parseInt(ml.group(1));
                total = Math.max(count, s.rtts.size());
                if (lossPct == 100) s.recv = 0;
            } catch (Exception ignore) {}
        }
        if (s.rtts.isEmpty() && r.out.contains("Unknown")) {
            // 系统 ping 不支持该参数，降级 TCP
            return tcp(host, 443, count, perTimeoutSec * 1000, p);
        }
        s.sent = total;
        s.elapsed = System.currentTimeMillis() - t0;
        compute(s);
        return s;
    }

    /** TCP ping：向指定端口建立连接，测量三次握手耗时（绕过 ICMP 封锁） */
    public static Stats tcp(String host, int port, int count, int timeoutMs, Progress p) {
        Stats s = new Stats();
        s.target = host;
        s.mode = "TCP:" + port;
        long t0 = System.currentTimeMillis();
        String ip = resolve(host);
        s.ip = ip == null ? "解析失败" : ip;
        if (ip == null) {
            s.error = "域名解析失败";
            s.sent = 0;
            return s;
        }
        for (int i = 1; i <= count; i++) {
            long st = System.nanoTime();
            Socket so = new Socket();
            try {
                so.connect(new InetSocketAddress(ip, port), timeoutMs);
                double ms = (System.nanoTime() - st) / 1e6;
                s.rtts.add(ms);
                s.recv++;
                s.sent++;
                if (p != null) p.onReply(i, ms, -1);
            } catch (IOException e) {
                s.sent++;
                if (p != null) p.onTimeout(i);
            } finally {
                try { so.close(); } catch (Throwable ignore) {}
            }
            if (i < count) {
                try { Thread.sleep(200); } catch (InterruptedException ignore) {}
            }
        }
        s.elapsed = System.currentTimeMillis() - t0;
        compute(s);
        return s;
    }

    static void compute(Stats s) {
        if (s.rtts.isEmpty()) return;
        double sum = 0, min = Double.MAX_VALUE, max = -1;
        double prev = Double.NaN, jsum = 0;
        int jn = 0;
        for (double v : s.rtts) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
            if (!Double.isNaN(prev)) { jsum += Math.abs(v - prev); jn++; }
            prev = v;
        }
        s.min = min;
        s.max = max;
        s.avg = sum / s.rtts.size();
        s.jitter = jn == 0 ? 0 : jsum / jn;
    }

    private static int parseInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (Exception e) { return def; }
    }
}
