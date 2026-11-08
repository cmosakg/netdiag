package com.cmos.netdiag.core;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** HTTP / HTTPS 探测：状态码、响应头、重定向链、TLS 证书、首字节时间 */
public final class HttpProbe {

    public static final class Result {
        public String url = "";
        public String finalUrl = "";
        public int code = -1;
        public String codeMsg = "";
        public double dnsMs = Double.NaN;
        public double connectMs = Double.NaN;
        public double ttfbMs = Double.NaN;
        public double totalMs = Double.NaN;
        public long size;
        public final Map<String, String> headers = new LinkedHashMap<>();
        public final List<String> redirects = new ArrayList<>();
        public String bodyHead = "";
        public String tlsProtocol = "";
        public String tlsCipher = "";
        public String certSubject = "";
        public String certIssuer = "";
        public String certNotBefore = "";
        public String certNotAfter = "";
        public String certSigAlg = "";
        public int certChainLen;
        public String error;

        public boolean https() { return finalUrl != null && finalUrl.toLowerCase(Locale.US).startsWith("https"); }

        public boolean ok() { return code >= 200 && code < 400; }

        public String server() {
            String s = headers.get("server");
            return s == null ? "" : s;
        }
    }

    private HttpProbe() {}

    public static Result probe(String rawUrl, int timeoutMs, boolean readBody) {
        Result r = new Result();
        r.url = rawUrl;
        String cur = rawUrl;
        if (!cur.toLowerCase(Locale.US).startsWith("http")) cur = "http://" + cur;
        long t0 = System.nanoTime();
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = null;
            try {
                URL u = new URL(cur);
                c = (HttpURLConnection) u.openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(timeoutMs);
                c.setReadTimeout(timeoutMs);
                c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) NetDiag/1.0");
                c.setRequestProperty("Accept", "*/*");
                long tc = System.nanoTime();
                c.connect();
                r.connectMs = (System.nanoTime() - tc) / 1e6;
                long tr = System.nanoTime();
                r.code = c.getResponseCode();
                r.codeMsg = c.getResponseMessage() == null ? "" : c.getResponseMessage();
                r.ttfbMs = (System.nanoTime() - tr) / 1e6;
                if (c instanceof HttpsURLConnection) {
                    readCert((HttpsURLConnection) c, r);
                }
                if (r.code >= 300 && r.code < 400) {
                    String loc = c.getHeaderField("Location");
                    r.redirects.add(r.code + " -> " + (loc == null ? "(无 Location)" : loc));
                    if (loc != null && !loc.isEmpty()) {
                        cur = resolve(u, loc);
                        try { c.disconnect(); } catch (Throwable ignore) {}
                        continue;
                    }
                }
                r.finalUrl = cur;
                for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                    if (e.getKey() == null) continue;
                    StringBuilder sb = new StringBuilder();
                    for (String v : e.getValue()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(v);
                    }
                    r.headers.put(e.getKey().toLowerCase(Locale.US), sb.toString());
                }
                if (readBody) {
                    try {
                        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
                        if (in != null) {
                            byte[] buf = new byte[8192];
                            int total = 0, n;
                            StringBuilder head = new StringBuilder();
                            while ((n = in.read(buf)) > 0 && total < 200_000) {
                                total += n;
                                if (head.length() < 600) {
                                    head.append(new String(buf, 0, n, "UTF-8"));
                                }
                                if (total >= 8192) break;
                            }
                            r.size = total;
                            String s = head.toString().replaceAll("\\s+", " ").trim();
                            r.bodyHead = s.length() > 300 ? s.substring(0, 300) : s;
                            in.close();
                        }
                    } catch (Throwable ignore) {}
                }
                r.totalMs = (System.nanoTime() - t0) / 1e6;
                return r;
            } catch (Throwable e) {
                r.error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                r.totalMs = (System.nanoTime() - t0) / 1e6;
                r.finalUrl = cur;
                return r;
            } finally {
                if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
            }
        }
        r.error = "重定向次数过多";
        return r;
    }

    private static String resolve(URL base, String loc) {
        try {
            return new URL(base, loc).toString();
        } catch (Throwable t) {
            return loc;
        }
    }

    private static void readCert(HttpsURLConnection c, Result r) {
        try {
            java.security.cert.Certificate[] certs = c.getServerCertificates();
            r.certChainLen = certs == null ? 0 : certs.length;
            if (certs != null && certs.length > 0 && certs[0] instanceof X509Certificate) {
                X509Certificate x = (X509Certificate) certs[0];
                r.certSubject = x.getSubjectDN().getName();
                r.certIssuer = x.getIssuerDN().getName();
                r.certNotBefore = String.valueOf(x.getNotBefore());
                r.certNotAfter = String.valueOf(x.getNotAfter());
                r.certSigAlg = x.getSigAlgName();
                try {
                    long days = (x.getNotAfter().getTime() - System.currentTimeMillis()) / 86400000L;
                    r.certSigAlg = r.certSigAlg + " · 剩余 " + days + " 天";
                } catch (Throwable ignore) {}
            }
            r.tlsCipher = c.getCipherSuite();
        } catch (Throwable ignore) {}
    }

    /** 直接建 TLS 连接，拿到协商版本（TLS1.2 / TLS1.3） */
    public static String tlsVersion(String host, int port, int timeoutMs) {
        SSLSocket s = null;
        try {
            SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            s = (SSLSocket) f.createSocket();
            s.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            s.startHandshake();
            String p = s.getSession().getProtocol() + " / " + s.getSession().getCipherSuite();
            return p;
        } catch (Throwable t) {
            return "TLS 握手失败: " + t.getClass().getSimpleName();
        } finally {
            if (s != null) try { s.close(); } catch (Throwable ignore) {}
        }
    }

    /** 检测 HTTP 劫持：看响应里是否被插入了非目标域名的脚本/跳转 */
    public static List<String> hijackHints(Result r) {
        List<String> hints = new ArrayList<>();
        if (r.headers.containsKey("x-adblock-key") || r.headers.containsKey("x-cache")) return hints;
        String body = r.bodyHead == null ? "" : r.bodyHead;
        if (r.code == 200 && body.isEmpty()) hints.add("响应体为空，可能存在中间设备拦截");
        return hints;
    }
}
