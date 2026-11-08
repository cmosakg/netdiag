package com.cmos.netdiag.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 带宽测速：单连接下载 + 上传，实时回调速率 */
public final class SpeedTest {

    public interface Progress {
        void onTick(double bytesPerSec, long bytes, long elapsedMs);
        void onStage(String stage);
    }

    public static final class Result {
        public double downBytesPerSec;
        public double upBytesPerSec;
        public long downBytes;
        public long upBytes;
        public long downMs;
        public long upMs;
        public String usedUrl = "";
        public String error;
        public double latencyMs = Double.NaN;
        public double jitterMs = Double.NaN;

        public String down() { return Fmt.speed(downBytesPerSec); }
        public String up() { return Fmt.speed(upBytesPerSec); }
    }

    /** 下载测速候选源：国内 CDN 优先，其次境外 */
    private static final String[] DOWN_URLS = {
            "https://cdn.npmmirror.com/binaries/node/v20.11.0/node-v20.11.0-linux-x64.tar.xz",
            "https://mirrors.aliyun.com/ubuntu-releases/22.04/ubuntu-22.04.5-live-server-amd64.iso",
            "https://speed.cloudflare.com/__down?bytes=50000000",
            "http://cachefly.cachefly.net/100mb.test"
    };

    private static final String[] UP_URLS = {
            "http://speedtest.tele2.net/upload.php",
            "https://speed.cloudflare.com/__up"
    };

    private SpeedTest() {}

    public static Result run(int maxMs, Progress p) {
        Result r = new Result();
        measureLatency(r, p);
        download(r, maxMs, p);
        upload(r, maxMs, p);
        return r;
    }

    private static void measureLatency(Result r, Progress p) {
        if (p != null) p.onStage("测量延迟与抖动…");
        double sum = 0, prev = Double.NaN, jsum = 0;
        int n = 0, jn = 0;
        double min = Double.MAX_VALUE;
        for (int i = 0; i < 6; i++) {
            long t = System.nanoTime();
            java.net.Socket s = new java.net.Socket();
            try {
                s.connect(new java.net.InetSocketAddress("223.5.5.5", 443), 1500);
                double ms = (System.nanoTime() - t) / 1e6;
                sum += ms;
                n++;
                min = Math.min(min, ms);
                if (!Double.isNaN(prev)) { jsum += Math.abs(ms - prev); jn++; }
                prev = ms;
            } catch (Throwable ignore) {
            } finally {
                try { s.close(); } catch (Throwable ignore) {}
            }
        }
        if (n > 0) {
            r.latencyMs = sum / n;
            r.jitterMs = jn == 0 ? 0 : jsum / jn;
        }
    }

    private static void download(Result r, int maxMs, Progress p) {
        for (String url : DOWN_URLS) {
            if (p != null) p.onStage("下载测速：" + host(url));
            long t0 = System.currentTimeMillis();
            long bytes = 0;
            long warmBytes = -1, warmAt = -1;
            boolean anyRound = false;
            // 单个文件可能在时限内读完，重连多轮凑满采样时长
            for (int round = 0; round < 4; round++) {
                HttpURLConnection c = null;
                boolean roundOk = false;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "NetDiag/1.0");
                    c.connect();
                    if (c.getResponseCode() >= 400) throw new RuntimeException("HTTP " + c.getResponseCode());
                    InputStream in = c.getInputStream();
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bytes += n;
                        long now = System.currentTimeMillis();
                        if (warmBytes < 0 && now >= t0 + 800) {
                            warmBytes = bytes;
                            warmAt = now;
                        }
                        if (p != null && now - t0 <= maxMs) {
                            double elapsed = (now - t0) / 1000d;
                            if (elapsed > 0.2) p.onTick(bytes / elapsed, bytes, now - t0);
                        }
                        if (now - t0 >= maxMs) break;
                    }
                    in.close();
                    roundOk = true;
                } catch (Throwable e) {
                    if (!anyRound) r.error = host(url) + " 不可用";
                } finally {
                    if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
                }
                anyRound = anyRound || roundOk;
                if (!roundOk && round == 0) break;      // 首轮就连不上，换下一个源
                if (System.currentTimeMillis() - t0 >= maxMs) break;
                if (bytes >= 3_000_000) break;          // 已采到足够数据，不必重连
            }
            long end = System.currentTimeMillis();
            if (bytes <= 0) continue;
            long effBytes = bytes;
            long effMs = end - t0;
            if (warmBytes > 0 && warmAt > t0) {
                effBytes = bytes - warmBytes;
                effMs = end - warmAt;
            }
            r.downBytes = bytes;
            r.downMs = end - t0;
            r.usedUrl = url;
            if (effMs > 100) r.downBytesPerSec = effBytes * 1000d / effMs;
            r.error = null;
            if (r.downBytesPerSec > 0 && bytes > 3_000_000) return;
        }
    }

    private static void upload(Result r, int maxMs, Progress p) {
        if (p != null) p.onStage("上传测速…");
        byte[] payload = new byte[512 * 1024];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 31);
        for (String url : UP_URLS) {
            HttpURLConnection c = null;
            try {
                long t0 = System.currentTimeMillis();
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setConnectTimeout(7000);
                c.setReadTimeout(15000);
                c.setFixedLengthStreamingMode(payload.length * 4);
                c.setRequestProperty("Content-Type", "application/octet-stream");
                c.connect();
                OutputStream os = c.getOutputStream();
                long sent = 0;
                for (int i = 0; i < 4; i++) {
                    os.write(payload);
                    os.flush();
                    sent += payload.length;
                    if (p != null) {
                        long el = System.currentTimeMillis() - t0;
                        if (el > 200) p.onTick(sent / (el / 1000d), sent, el);
                    }
                    if (System.currentTimeMillis() - t0 > maxMs) break;
                }
                os.close();
                long el = System.currentTimeMillis() - t0;
                c.getResponseCode();
                r.upBytes = sent;
                r.upMs = el;
                // 有效窗口去掉建连握手时间
                long effMs = Math.max(200, el - 300);
                r.upBytesPerSec = sent * 1000d / effMs;
                if (r.upBytesPerSec > 0) {
                    r.error = null;
                    return;
                }
            } catch (Throwable e) {
                r.error = "上传测速源不可用";
            } finally {
                if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
            }
        }
    }

    private static String host(String url) {
        try {
            return new URL(url).getHost();
        } catch (Throwable t) {
            return url;
        }
    }
}
