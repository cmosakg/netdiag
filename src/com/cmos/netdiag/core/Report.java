package com.cmos.netdiag.core;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** 文本诊断报告生成 / 保存 */
public final class Report {

    private Report() {}

    public static String build(Context ctx, DiagEngine.Report rep, boolean full) {
        StringBuilder s = new StringBuilder();
        s.append("====================================\n");
        s.append("      NetDiag 网络诊断报告\n");
        s.append("====================================\n");
        s.append("时间: ").append(Fmt.time()).append('\n');
        s.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (Android ").append(Build.VERSION.RELEASE).append(")\n");
        String pub = NetInfo.cachedPublicIp();
        if (pub != null) s.append("公网 IP: ").append(pub).append('\n');
        s.append("耗时: ").append(Fmt.duration(rep.elapsed)).append('\n');
        s.append('\n');

        s.append("---------- 检查项 ----------\n");
        for (DiagEngine.Item i : rep.items) {
            s.append('[').append(i.icon()).append("] ").append(i.name).append('\n');
            s.append("    ").append(i.detail).append('\n');
        }
        s.append('\n');

        s.append("---------- 结论 ----------\n");
        s.append(rep.conclusion).append('\n');
        s.append("健康评分: ").append(rep.score()).append("/100 (").append(rep.grade()).append(")\n");
        s.append('\n');

        if (!rep.advice.isEmpty()) {
            s.append("---------- 建议 ----------\n");
            int n = 1;
            for (String a : rep.advice) {
                s.append(n++).append(". ").append(a).append('\n');
            }
            s.append('\n');
        }

        s.append("---------- 环境信息 ----------\n");
        for (NetInfo.Section sec : NetInfo.collect(ctx)) {
            s.append('[').append(sec.title).append("]\n");
            for (java.util.Map.Entry<String, String> e : sec.kv.entrySet()) {
                s.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
        }
        s.append('\n');
        s.append("---------- 由 NetDiag 生成 ----------\n");
        return s.toString();
    }

    /** 保存到应用外部目录，返回文件路径 */
    public static String save(Context ctx, String content, String prefix) {
        try {
            File dir = ctx.getExternalFilesDir("reports");
            if (dir == null) dir = new File(ctx.getFilesDir(), "reports");
            if (!dir.exists()) dir.mkdirs();
            String name = prefix + "_" + System.currentTimeMillis() + ".txt";
            File f = new File(dir, name);
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(content.getBytes("UTF-8"));
            fo.close();
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String kvBlock(String title, java.util.Map<String, String> kv) {
        StringBuilder s = new StringBuilder("[" + title + "]\n");
        for (java.util.Map.Entry<String, String> e : kv.entrySet()) {
            s.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return s.toString();
    }

    public static String join(List<String> lines) {
        StringBuilder s = new StringBuilder();
        for (String l : lines) s.append(l).append('\n');
        return s.toString();
    }
}
