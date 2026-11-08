package com.cmos.netdiag.core;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** TCP 端口探测 / 连通性扫描 */
public final class PortScan {

    public static final class Item {
        public int port;
        public boolean open;
        public double ms;
        public String service = "";
        public String banner = "";
        public String error = "";
    }

    public interface Progress {
        void onItem(Item it, int done, int total);
    }

    /** 常见端口 -> 服务名 */
    public static final Map<Integer, String> COMMON = new LinkedHashMap<>();
    static {
        COMMON.put(21, "FTP");
        COMMON.put(22, "SSH");
        COMMON.put(23, "Telnet");
        COMMON.put(25, "SMTP");
        COMMON.put(53, "DNS");
        COMMON.put(80, "HTTP");
        COMMON.put(110, "POP3");
        COMMON.put(135, "MSRPC");
        COMMON.put(139, "NetBIOS");
        COMMON.put(143, "IMAP");
        COMMON.put(443, "HTTPS");
        COMMON.put(445, "SMB");
        COMMON.put(465, "SMTPS");
        COMMON.put(587, "SMTP-sub");
        COMMON.put(853, "DNS-over-TLS");
        COMMON.put(993, "IMAPS");
        COMMON.put(1080, "SOCKS");
        COMMON.put(1433, "MSSQL");
        COMMON.put(1521, "Oracle");
        COMMON.put(3306, "MySQL");
        COMMON.put(3389, "RDP");
        COMMON.put(5432, "PostgreSQL");
        COMMON.put(5900, "VNC");
        COMMON.put(6379, "Redis");
        COMMON.put(7890, "Clash");
        COMMON.put(8080, "HTTP-alt");
        COMMON.put(8443, "HTTPS-alt");
        COMMON.put(9090, "Clash-API");
        COMMON.put(27017, "MongoDB");
    }

    private PortScan() {}

    public static Item probe(String host, int port, int timeoutMs, boolean grabBanner) {
        Item it = new Item();
        it.port = port;
        it.service = COMMON.containsKey(port) ? COMMON.get(port) : "";
        long t0 = System.nanoTime();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            it.open = true;
            it.ms = (System.nanoTime() - t0) / 1e6;
            if (grabBanner) {
                try {
                    s.setSoTimeout(600);
                    byte[] buf = new byte[256];
                    int n = s.getInputStream().read(buf);
                    if (n > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) {
                            char c = (char) (buf[i] & 0xFF);
                            sb.append(c >= 32 && c < 127 ? c : '.');
                        }
                        it.banner = sb.toString().trim();
                    }
                } catch (Throwable ignore) {}
            }
        } catch (java.net.SocketTimeoutException e) {
            it.error = "超时";
        } catch (java.net.ConnectException e) {
            it.error = "拒绝连接";
        } catch (Throwable e) {
            it.error = "不可达";
        } finally {
            try { s.close(); } catch (Throwable ignore) {}
        }
        return it;
    }

    public static List<Item> scan(String host, List<Integer> ports, int timeoutMs, int threads, Progress p) {
        final List<Item> result = Collections.synchronizedList(new ArrayList<Item>());
        final String h = host;
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        final int total = ports.size();
        final int[] done = {0};
        for (final int port : ports) {
            pool.submit(new Runnable() {
                public void run() {
                    Item it = probe(h, port, timeoutMs, false);
                    result.add(it);
                    int d;
                    synchronized (done) { d = ++done[0]; }
                    if (p != null) p.onItem(it, d, total);
                }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(total * (timeoutMs / 1000L + 2), TimeUnit.SECONDS); }
        catch (InterruptedException ignore) {}
        List<Item> sorted = new ArrayList<>(result);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.port, b.port));
        return sorted;
    }

    public static List<Integer> parsePorts(String spec) {
        List<Integer> out = new ArrayList<>();
        if (spec == null) return out;
        for (String part : spec.split("[,\\s]+")) {
            if (part.isEmpty()) continue;
            if (part.contains("-")) {
                String[] ab = part.split("-");
                try {
                    int a = Integer.parseInt(ab[0].trim());
                    int b = Integer.parseInt(ab[1].trim());
                    if (a > b) { int t = a; a = b; b = t; }
                    for (int i = a; i <= b && out.size() < 2048; i++) out.add(i);
                } catch (Exception ignore) {}
            } else {
                try {
                    int v = Integer.parseInt(part.trim());
                    if (v > 0 && v < 65536) out.add(v);
                } catch (Exception ignore) {}
            }
        }
        return out;
    }
}
