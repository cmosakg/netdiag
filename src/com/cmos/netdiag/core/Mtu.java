package com.cmos.netdiag.core;

import java.util.ArrayList;
import java.util.List;

/** MTU 探测：二分法找路径最大不分片载荷 */
public final class Mtu {

    public static final class Result {
        public int maxPayload;
        public int mtu;
        public String target;
        public final List<String> log = new ArrayList<>();
        public String error;

        public String text() {
            if (error != null) return "探测失败：" + error;
            return "路径 MTU ≈ " + mtu + " 字节（最大载荷 " + maxPayload + " 字节）";
        }
    }

    private Mtu() {}

    public static Result probe(String target, int interfaceMtu) {
        Result r = new Result();
        r.target = target;
        String ip = Ping.resolve(target);
        if (ip == null) {
            r.error = "域名解析失败";
            return r;
        }
        int lo = 1200, hi = Math.max(1400, interfaceMtu - 28); // 1472 = 1500-28
        // 先测上限
        if (Trace.pingSize(ip, hi, 0, 2)) {
            r.maxPayload = hi;
            r.mtu = hi + 28;
            r.log.add("直接以 " + hi + " 字节载荷成功，链路支持标准 MTU");
            return r;
        }
        r.log.add(hi + " 字节载荷失败（过大或分片被丢弃），开始二分");
        while (lo < hi - 1) {
            int mid = (lo + hi) / 2;
            boolean ok = Trace.pingSize(ip, mid, 0, 2);
            r.log.add("测试 " + mid + " 字节 -> " + (ok ? "通过" : "失败"));
            if (ok) lo = mid;
            else hi = mid;
        }
        r.maxPayload = lo;
        r.mtu = lo + 28;
        return r;
    }
}
