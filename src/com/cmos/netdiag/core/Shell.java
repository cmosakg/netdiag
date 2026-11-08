package com.cmos.netdiag.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 轻量 shell 执行器。Android 上很多诊断能力要靠系统二进制（ping / ip / getprop ...），
 * 这里统一封装，带超时与 root 降级。
 */
public final class Shell {

    /** 是否尝试使用 root 执行（Magisk / su 可用时） */
    public static volatile boolean ROOT = true;
    private static volatile Boolean hasRoot = null;

    public static final class R {
        public int code;
        public String out = "";
        public String err = "";
        public long ms;
        public boolean ok() { return code == 0 && !out.isEmpty(); }
        public String all() { return out.isEmpty() ? err : out; }
        public String toString() { return "code=" + code + " ms=" + ms; }
    }

    private Shell() {}

    public static R run(String cmd) { return run(cmd, 8000); }

    public static R run(String cmd, int timeoutMs) {
        return exec(new String[]{"/system/bin/sh", "-c", cmd}, timeoutMs);
    }

    /** 优先普通身份执行，确实需要提权时才走 su（避免频繁触发 root 授权提示） */
    public static R root(String cmd, int timeoutMs) {
        R normal = run(cmd, timeoutMs);
        String n = normal.out.trim();
        boolean denied = n.toLowerCase().contains("permission denied")
                || n.contains("Operation not permitted")
                || n.toLowerCase().contains("not permitted");
        if (!n.isEmpty() && !denied) return normal;
        if (!ROOT || !hasRoot()) return normal;
        R r = exec(new String[]{"/system/bin/sh", "-c", "su -c " + q(cmd)}, timeoutMs);
        if (r.code != 0 && r.out.trim().isEmpty()) return normal;
        return r;
    }

    public static boolean hasRoot() {
        if (hasRoot != null) return hasRoot;
        synchronized (Shell.class) {
            if (hasRoot != null) return hasRoot;
            try {
                R r = exec(new String[]{"/system/bin/sh", "-c", "su -c id"}, 4000);
                hasRoot = r.code == 0 && r.out.contains("uid=0");
            } catch (Throwable t) {
                hasRoot = false;
            }
            return hasRoot;
        }
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static R exec(String[] argv, int timeoutMs) {
        R r = new R();
        long t0 = System.currentTimeMillis();
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectErrorStream(false);
            p = pb.start();
            final Process fp = p;
            final ByteArrayOutputStream ob = new ByteArrayOutputStream();
            final ByteArrayOutputStream eb = new ByteArrayOutputStream();
            Thread to = pump(fp.getInputStream(), ob);
            Thread te = pump(fp.getErrorStream(), eb);
            boolean done = p.waitFor(Math.max(300, timeoutMs), TimeUnit.MILLISECONDS);
            if (!done) {
                try { p.destroyForcibly(); } catch (Throwable ignore) {}
            }
            to.join(400);
            te.join(400);
            r.code = done ? p.exitValue() : -9;
            r.out = ob.toString("UTF-8");
            r.err = eb.toString("UTF-8");
        } catch (Throwable t) {
            r.code = -1;
            r.err = String.valueOf(t.getMessage());
        } finally {
            if (p != null) try { p.destroy(); } catch (Throwable ignore) {}
            r.ms = System.currentTimeMillis() - t0;
        }
        return r;
    }

    private static Thread pump(final InputStream in, final ByteArrayOutputStream sink) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                byte[] buf = new byte[4096];
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (sink.size() < 512 * 1024) sink.write(buf, 0, n);
                    }
                } catch (Throwable ignore) {
                } finally {
                    try { in.close(); } catch (Throwable ignore) {}
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 系统里是否存在某个可执行文件 */
    public static boolean hasBin(String name) {
        R r = run("which " + name + " 2>/dev/null || command -v " + name + " 2>/dev/null", 3000);
        String s = r.out.trim();
        return !s.isEmpty() && !s.contains("not found");
    }

    /** 把一行行输出拆成 List，过滤空行 */
    public static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\n")) {
            String v = line.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }
}
