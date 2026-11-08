package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.DiagEngine;
import com.cmos.netdiag.core.Report;

/** 报告页：完整诊断 + 导出 */
public class ReportPage extends Page {

    private LinearLayout out;
    private String last;
    private volatile boolean busy;

    public ReportPage(MainActivity act) { super(act); }

    public String title() { return "报告"; }

    public View onCreate() {
        begin("生成完整诊断报告，可保存到文件或分享");

        LinearLayout c = card();
        c.addView(Ui.text(act, "报告包含：逐项检查结果、健康评分、改进建议、全部环境信息。", 12, Ui.SUB));
        c.addView(Ui.spacer(act, 6));
        c.addView(Ui.primary(act, "生成完整报告", () -> run(false)));
        c.addView(Ui.ghost(act, "深度报告（含 MTU / 端口抽样）", () -> run(true)));

        out = new LinearLayout(act);
        out.setOrientation(LinearLayout.VERTICAL);
        body.addView(out);
        return scroll;
    }

    private void run(final boolean full) {
        if (busy) return;
        busy = true;
        out.removeAllViews();
        LinearLayout lc = Ui.card(act);
        lc.addView(Ui.text(act, "正在诊断并生成报告…", 12.5f, Ui.SUB));
        out.addView(lc);
        act.async(() -> {
            DiagEngine.Report rep = DiagEngine.run(act, full, null);
            final String text = rep.text;
            final String path = Report.save(act, text, full ? "full" : "report");
            act.ui(() -> {
                busy = false;
                last = text;
                out.removeAllViews();

                LinearLayout c = Ui.card(act);
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                TextView sc = Ui.text(act, String.valueOf(rep.score()), 30,
                        rep.score() >= 90 ? Ui.OK : rep.score() >= 60 ? Ui.WARN : Ui.FAIL);
                sc.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                row.addView(sc);
                TextView st = Ui.text(act, "  分 · " + rep.grade() + " · 用时 " + com.cmos.netdiag.core.Fmt.duration(rep.elapsed),
                        12.5f, Ui.SUB);
                row.addView(st);
                c.addView(row);
                if (path != null) {
                    c.addView(Ui.spacer(act, 6));
                    c.addView(Ui.text(act, "已保存到：\n" + path, 11, Ui.SUB));
                }
                c.addView(Ui.spacer(act, 8));
                c.addView(Ui.primary(act, "分享 / 发送报告", () -> act.share(last)));
                c.addView(Ui.ghost(act, "复制全文到剪贴板", () -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("NetDiag", last));
                    act.toast("已复制");
                }));
                out.addView(c);

                LinearLayout tc = Ui.card(act);
                tc.addView(Ui.h2(act, "报告全文"));
                tc.addView(Ui.spacer(act, 6));
                TextView tv = Ui.mono(act, text, 10, Ui.TEXT);
                tv.setTextIsSelectable(true);
                tc.addView(tv);
                out.addView(tc);
            });
        });
    }
}
