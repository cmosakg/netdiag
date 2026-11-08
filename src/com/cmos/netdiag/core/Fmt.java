package com.cmos.netdiag.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 格式化小工具 */
public final class Fmt {

    private Fmt() {}

    public static String ms(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "-";
        if (v < 1) return String.format(Locale.US, "%.2f ms", v);
        if (v < 100) return String.format(Locale.US, "%.1f ms", v);
        return String.format(Locale.US, "%.0f ms", v);
    }

    public static String ms(long v) { return v + " ms"; }

    public static String speed(double bytesPerSec) {
        if (bytesPerSec <= 0 || Double.isNaN(bytesPerSec)) return "-";
        double bits = bytesPerSec * 8;
        if (bits >= 1_000_000_000d) return String.format(Locale.US, "%.2f Gbps", bits / 1e9);
        if (bits >= 1_000_000d) return String.format(Locale.US, "%.2f Mbps", bits / 1e6);
        if (bits >= 1_000d) return String.format(Locale.US, "%.1f Kbps", bits / 1e3);
        return String.format(Locale.US, "%.0f bps", bits);
    }

    public static String bytes(double b) {
        if (b < 1024) return String.format(Locale.US, "%.0f B", b);
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.2f MB", b / 1048576d);
        return String.format(Locale.US, "%.2f GB", b / 1073741824d);
    }

    public static String pct(double v) {
        if (Double.isNaN(v)) return "-";
        return String.format(Locale.US, "%.1f%%", v);
    }

    public static String time() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    public static String shortTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    public static String duration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format(Locale.US, "%.1fs", ms / 1000d);
        if (ms < 3_600_000) {
            long m = ms / 60_000;
            long s = (ms % 60_000) / 1000;
            return m + "m" + s + "s";
        }
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        return h + "h" + m + "m";
    }

    /** 信号强度 dBm -> 文字 */
    public static String rssi(int dbm) {
        if (dbm >= -50) return "极佳";
        if (dbm >= -60) return "很好";
        if (dbm >= -70) return "良好";
        if (dbm >= -80) return "一般";
        if (dbm >= -90) return "较差";
        return "很差";
    }

    public static int rssiLevel(int dbm) {
        if (dbm >= -50) return 4;
        if (dbm >= -65) return 3;
        if (dbm >= -80) return 2;
        if (dbm >= -90) return 1;
        return 0;
    }

    public static String freqBand(int mhz) {
        if (mhz <= 0) return "-";
        if (mhz < 1000) return mhz + " MHz (2.4G)";
        if (mhz < 3000) return mhz + " MHz (5G)";
        if (mhz < 6000) return mhz + " MHz (5G)";
        return mhz + " MHz (6G)";
    }

    public static int freqToChannel(int freq) {
        if (freq == 2484) return 14;
        if (freq >= 2412 && freq <= 2472) return (freq - 2407) / 5;
        if (freq >= 5000 && freq <= 5900) return (freq - 5000) / 5;
        if (freq >= 5955 && freq <= 7115) return (freq - 5955) / 5 + 1;
        return -1;
    }
}
