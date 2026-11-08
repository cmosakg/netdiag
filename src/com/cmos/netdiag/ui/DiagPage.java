package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cmos.netdiag.core.DiagEngine;
import com.cmos.netdiag.core.Report;

/** 一键诊断页 */
public class DiagPage extends Page {

    private LinearLayout results;
    private TextView stageView;
    private LinearLayout summaryCard;
    private boolean running;
    private DiagEngine.Report last;

    public DiagPage(MainActivity act) { super(act); }

    public String title() { return "诊断"; }

    public View onCreate() {
        begin("按 OSI 分层逐级验证，定位故障到底出在哪一层");

        LinearLayout c = card();
        c.addView(Ui.text(act, "检查链路 → 网关 → 公网出口 → DNS → 应用层，每层给出结论与建议。",
                12.5f, Ui.SUB));
        c.addView(Ui.spacer(act, 6));
        c.addView(Ui.primary(act, "开始诊断", () -> run(false)));
        c.addView(Ui.ghost(act, "深度诊断（+ MTU / 端口抽样，慢一些）", () -> run(true)));
        stageView = Ui.text(act, "", 12, Ui.ACCENT);
        stageView.setPadding(0, Ui.dp(act, 6), 0, 0);
        c.addView(stageView);

        LinearLayout rc = card("诊断明细");
        results = new LinearLayout(act);
        results.setOrientation(LinearLayout.VERTICAL);
        rc.addView(results);
        TextView hint = Ui.text(act, "还没有结果，点上面的按钮开始", 12.5f, Ui.SUB);
        results.addView(hint);
        results.setTag(hint);

        summaryCard = card();
        summaryCard.setVisibility(View.GONE);

        return scroll;
    }

    private void run(boolean full) {
        if (running) return;
        running = true;
        results.removeAllViews();
        summaryCard.setVisibility(View.GONE);
        summaryCard.removeAllViews();
        stageView.setText("准备中…");
        final long t0 = System.currentTimeMillis();
        act.async(() -> {
            DiagEngine.Report rep = DiagEngine.run(act, full, new DiagEngine.Progress() {
                public void onStage(String s) {
                    act.ui(() -> stageView.setText(s));
                }

                public void onItem(final DiagEngine.Item it) {
                    act.ui(() -> addItem(it));
                }
            });
            last = rep;
            act.ui(() -> {
                running = false;
                stageView.setText("完成，用时 " + com.cmos.netdiag.core.Fmt.duration(System.currentTimeMillis() - t0));
                showSummary(rep);
                act.refreshSubtitle();
            });
        });
    }

    private void addItem(DiagEngine.Item it) {
        if (results.getTag() != null) {
            results.removeAllViews();
            results.setTag(null);
        }
        int color = Ui.levelColor(it.level);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(act, 7), 0, Ui.dp(act, 7));

        TextView badge = Ui.text(act, it.icon(), 12, color);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badge.setBackground(Ui.round(Ui.blend(color, 0.16f), 9, act, 0));
        int sz = Ui.dp(act, 24);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(sz, sz);
        blp.rightMargin = Ui.dp(act, 10);
        blp.topMargin = Ui.dp(act, 1);
        row.addView(badge, blp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(act, it.name, 13.5f, Ui.TEXT);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView detail = Ui.text(act, it.detail, 12, color == Ui.OK ? Ui.SUB : color);
        detail.setTextIsSelectable(true);
        col.addView(name);
        col.addView(detail);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        results.addView(row);
        View div = new View(act);
        div.setBackgroundColor(Ui.STROKE);
        results.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void showSummary(DiagEngine.Report rep) {
        summaryCard.setVisibility(View.VISIBLE);
        summaryCard.removeAllViews();

        int score = rep.score();
        int color = score >= 90 ? Ui.OK : score >= 60 ? Ui.WARN : Ui.FAIL;

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView num = Ui.text(act, String.valueOf(score), 40, color);
        num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(num);
        LinearLayout right = new LinearLayout(act);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Ui.dp(act, 12), 0, 0, 0);
        TextView g = Ui.text(act, rep.grade() + " · 健康分", 13, Ui.SUB);
        TextView st = Ui.text(act, (rep.fails() == 0 ? "无严重问题" : rep.fails() + " 项严重异常")
                + (rep.warns() > 0 ? " · " + rep.warns() + " 项警告" : ""), 12, Ui.SUB);
        right.addView(g);
        right.addView(st);
        head.addView(right);
        summaryCard.addView(head);

        summaryCard.addView(Ui.divider(act));
        TextView concl = Ui.text(act, rep.conclusion, 13.5f, Ui.TEXT);
        summaryCard.addView(concl);
        summaryCard.addView(Ui.spacer(act, 8));

        if (!rep.advice.isEmpty()) {
            summaryCard.addView(Ui.label(act, "改 进 建 议"));
            summaryCard.addView(Ui.spacer(act, 6));
            int i = 1;
            for (String a : rep.advice) {
                TextView t = Ui.text(act, i++ + ". " + a, 12.5f, Ui.WARN);
                t.setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 2));
                summaryCard.addView(t);
            }
            summaryCard.addView(Ui.spacer(act, 8));
        }

        summaryCard.addView(Ui.primary(act, "查看 / 导出完整报告", () -> {
            String text = rep.text;
            String path = Report.save(act, text, "diag");
            if (path != null) act.toast("已保存：" + path);
            act.share(text);
        }));
        summaryCard.addView(Ui.ghost(act, "仅复制文本", () -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("NetDiag", rep.text));
            act.toast("诊断报告已复制到剪贴板");
        }));
    }
}
