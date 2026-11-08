package com.cmos.netdiag.core;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 手写 DNS 客户端（UDP / TCP），支持 A/AAAA/CNAME/MX/TXT/NS/PTR/SOA/ANY。
 * 自己拼报文的好处：可以任意指定 DNS 服务器，用于多服务器解析对比与劫持检测。
 */
public final class Dns {

    public static final class Answer {
        public String name = "";
        public String type = "";
        public String value = "";
        public int ttl;
        public String toString() { return value; }
    }

    public static final class Result {
        public String server = "";
        public String domain = "";
        public String qtype = "A";
        public final List<Answer> answers = new ArrayList<>();
        public int rcode = 0;
        public double ms = Double.NaN;
        public String error;
        public int raLen;
        public boolean ok() { return error == null && rcode == 0 && !answers.isEmpty(); }

        public String rcodeText() {
            switch (rcode) {
                case 0: return "NOERROR";
                case 1: return "FORMERR";
                case 2: return "SERVFAIL";
                case 3: return "NXDOMAIN(不存在)";
                case 4: return "NOTIMP";
                case 5: return "REFUSED(拒绝)";
                default: return "RCODE" + rcode;
            }
        }

        public String firstIp() {
            for (Answer a : answers) {
                if ("A".equals(a.type) || "AAAA".equals(a.type)) return a.value;
            }
            return null;
        }
    }

    private Dns() {}

    public static int typeCode(String t) {
        if (t == null) return 1;
        switch (t.toUpperCase(Locale.US)) {
            case "A": return 1;
            case "NS": return 2;
            case "CNAME": return 5;
            case "SOA": return 6;
            case "PTR": return 12;
            case "MX": return 15;
            case "TXT": return 16;
            case "AAAA": return 28;
            case "SRV": return 33;
            case "ANY": return 255;
            default: return 1;
        }
    }

    public static String typeName(int c) {
        switch (c) {
            case 1: return "A";
            case 2: return "NS";
            case 5: return "CNAME";
            case 6: return "SOA";
            case 12: return "PTR";
            case 15: return "MX";
            case 16: return "TXT";
            case 28: return "AAAA";
            case 33: return "SRV";
            default: return "T" + c;
        }
    }

    public static Result query(String server, String domain, String qtype) {
        return query(server, domain, qtype, 2500);
    }

    public static Result query(String server, String domain, String qtype, int timeoutMs) {
        Result r = new Result();
        r.server = server;
        r.domain = domain;
        r.qtype = qtype;
        long t0 = System.nanoTime();
        try {
            byte[] req = buildQuery(domain, typeCode(qtype));
            InetAddress addr = InetAddress.getByName(server);
            DatagramSocket so = new DatagramSocket();
            so.setSoTimeout(timeoutMs);
            so.send(new DatagramPacket(req, req.length, addr, 53));
            byte[] buf = new byte[4096];
            DatagramPacket rp = new DatagramPacket(buf, buf.length);
            so.receive(rp);
            so.close();
            r.ms = (System.nanoTime() - t0) / 1e6;
            r.raLen = rp.getLength();
            parse(r, rp.getData(), rp.getLength());
        } catch (java.net.SocketTimeoutException e) {
            r.error = "超时";
            r.ms = (System.nanoTime() - t0) / 1e6;
        } catch (Throwable t) {
            r.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            r.ms = (System.nanoTime() - t0) / 1e6;
        }
        return r;
    }

    /** TCP 模式查询：用于检测 UDP 53 被劫持而 TCP 正常的情况 */
    public static Result queryTcp(String server, String domain, String qtype, int timeoutMs) {
        Result r = new Result();
        r.server = server;
        r.domain = domain;
        r.qtype = qtype;
        long t0 = System.nanoTime();
        Socket s = null;
        try {
            byte[] req = buildQuery(domain, typeCode(qtype));
            s = new Socket();
            s.connect(new InetSocketAddress(server, 53), timeoutMs);
            s.setSoTimeout(timeoutMs);
            OutputStream os = s.getOutputStream();
            os.write(new byte[]{(byte) (req.length >> 8), (byte) req.length});
            os.write(req);
            os.flush();
            DataInputStream in = new DataInputStream(s.getInputStream());
            int len = in.readUnsignedShort();
            byte[] buf = new byte[len];
            in.readFully(buf);
            r.ms = (System.nanoTime() - t0) / 1e6;
            r.raLen = len;
            parse(r, buf, len);
        } catch (Throwable t) {
            r.error = t.getClass().getSimpleName();
            r.ms = (System.nanoTime() - t0) / 1e6;
        } finally {
            if (s != null) try { s.close(); } catch (Throwable ignore) {}
        }
        return r;
    }

    private static byte[] buildQuery(String domain, int qtype) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int id = (int) (System.currentTimeMillis() & 0xFFFF);
        out.write(id >> 8);
        out.write(id & 0xFF);
        out.write(0x01); // RD=1
        out.write(0x00);
        out.write(0x00); out.write(0x01); // QDCOUNT=1
        out.write(0); out.write(0);
        out.write(0); out.write(0);
        out.write(0); out.write(0);
        String d = domain.endsWith(".") ? domain.substring(0, domain.length() - 1) : domain;
        if (!d.isEmpty()) {
            for (String part : d.split("\\.")) {
                byte[] pb = part.getBytes("UTF-8");
                if (pb.length > 63) pb = java.util.Arrays.copyOf(pb, 63);
                out.write(pb.length);
                out.write(pb, 0, pb.length);
            }
        }
        out.write(0);
        out.write(qtype >> 8); out.write(qtype & 0xFF);
        out.write(0x00); out.write(0x01); // IN
        return out.toByteArray();
    }

    private static void parse(Result r, byte[] b, int len) {
        try {
            int[] pos = {0};
            readU16(b, pos); // id
            int flags = readU16(b, pos);
            r.rcode = flags & 0x000F;
            int qd = readU16(b, pos);
            int an = readU16(b, pos);
            readU16(b, pos);
            readU16(b, pos);
            for (int i = 0; i < qd; i++) {
                skipName(b, pos);
                pos[0] += 4;
            }
            for (int i = 0; i < an; i++) {
                Answer a = new Answer();
                a.name = readName(b, pos);
                int type = readU16(b, pos);
                readU16(b, pos); // class
                a.ttl = (int) readU32(b, pos);
                int rdlen = readU16(b, pos);
                int rdStart = pos[0];
                a.type = typeName(type);
                a.value = readRdata(b, type, pos, rdStart + rdlen, len);
                pos[0] = rdStart + rdlen;
                if (a.value != null && !a.value.isEmpty()) r.answers.add(a);
            }
        } catch (Throwable t) {
            if (r.error == null) r.error = "报文解析异常";
        }
    }

    private static String readRdata(byte[] b, int type, int[] pos, int end, int len) {
        try {
            switch (type) {
                case 1:
                    if (pos[0] + 4 <= end) {
                        String ip = (b[pos[0]] & 0xFF) + "." + (b[pos[0] + 1] & 0xFF) + "."
                                + (b[pos[0] + 2] & 0xFF) + "." + (b[pos[0] + 3] & 0xFF);
                        pos[0] += 4;
                        return ip;
                    }
                    break;
                case 28: {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 8; i++) {
                        int v = ((b[pos[0] + i * 2] & 0xFF) << 8) | (b[pos[0] + i * 2 + 1] & 0xFF);
                        sb.append(Integer.toHexString(v));
                        if (i < 7) sb.append(':');
                    }
                    pos[0] += 16;
                    return sb.toString();
                }
                case 2:
                case 5:
                case 12:
                    return readName(b, pos);
                case 15: {
                    int pref = readU16(b, pos);
                    String mx = readName(b, pos);
                    return pref + " " + mx;
                }
                case 16: {
                    StringBuilder sb = new StringBuilder();
                    int p = pos[0];
                    while (p < end) {
                        int l = b[p] & 0xFF;
                        p++;
                        if (l == 0) break;
                        sb.append(new String(b, p, Math.min(l, end - p), "UTF-8"));
                        p += l;
                    }
                    pos[0] = end;
                    return sb.toString();
                }
                case 6: {
                    String mname = readName(b, pos);
                    String rname = readName(b, pos);
                    pos[0] = end;
                    return mname + " " + rname;
                }
                default:
                    pos[0] = end;
                    return "[" + (end - pos[0] > 0 ? (end - pos[0]) : 0) + " bytes]";
            }
        } catch (Throwable ignore) {
        }
        pos[0] = end;
        return null;
    }

    private static String readName(byte[] b, int[] pos) {
        StringBuilder sb = new StringBuilder();
        int p = pos[0];
        int guard = 0;
        boolean jumped = false;
        int afterJump = -1;
        while (p < b.length && guard++ < 128) {
            int l = b[p] & 0xFF;
            if ((l & 0xC0) == 0xC0) {
                int off = ((l & 0x3F) << 8) | (b[p + 1] & 0xFF);
                if (!jumped) afterJump = p + 2;
                jumped = true;
                p = off;
                continue;
            }
            if (l == 0) {
                p++;
                break;
            }
            p++;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(b, p, Math.min(l, b.length - p), java.nio.charset.StandardCharsets.UTF_8));
            p += l;
        }
        pos[0] = jumped && afterJump > 0 ? afterJump : p;
        return sb.toString();
    }

    private static void skipName(byte[] b, int[] pos) {
        int p = pos[0];
        while (p < b.length) {
            int l = b[p] & 0xFF;
            if ((l & 0xC0) == 0xC0) { p += 2; break; }
            if (l == 0) { p++; break; }
            p += l + 1;
        }
        pos[0] = p;
    }

    private static int readU16(byte[] b, int[] pos) {
        int v = ((b[pos[0]] & 0xFF) << 8) | (b[pos[0] + 1] & 0xFF);
        pos[0] += 2;
        return v;
    }

    private static long readU32(byte[] b, int[] pos) {
        long v = ((long) (b[pos[0]] & 0xFF) << 24) | ((b[pos[0] + 1] & 0xFF) << 16)
                | ((b[pos[0] + 2] & 0xFF) << 8) | (b[pos[0] + 3] & 0xFF);
        pos[0] += 4;
        return v;
    }

    /** 系统解析器（走 Android 自己的 DNS 链路，含缓存） */
    public static Result systemResolve(String domain, int timeoutMs) {
        Result r = new Result();
        r.server = "系统解析器";
        r.domain = domain;
        r.qtype = "A";
        long t0 = System.nanoTime();
        try {
            InetAddress[] addrs = InetAddress.getAllByName(domain);
            r.ms = (System.nanoTime() - t0) / 1e6;
            for (InetAddress a : addrs) {
                Answer an = new Answer();
                an.name = domain;
                an.type = a.getHostAddress().contains(":") ? "AAAA" : "A";
                an.value = a.getHostAddress();
                r.answers.add(an);
            }
        } catch (Throwable t) {
            r.error = "解析失败";
            r.ms = (System.nanoTime() - t0) / 1e6;
        }
        return r;
    }
}
