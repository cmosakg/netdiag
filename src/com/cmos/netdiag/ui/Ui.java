package com.cmos.netdiag.ui;

import com.cmos.netdiag.MainActivity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 纯代码构建的深色卡片 UI 工具集 */
public final class Ui {

    public static final int BG = 0xFF0B0F14;
    public static final int BG2 = 0xFF10151C;
    public static final int CARD = 0xFF151A22;
    public static final int STROKE = 0xFF242C38;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int SUB = 0xFF8B98A9;
    public static final int ACCENT = 0xFF22D3EE;
    public static final int OK = 0xFF34D399;
    public static final int WARN = 0xFFFBBF24;
    public static final int FAIL = 0xFFF87171;
    public static final int INFO = 0xFF60A5FA;
    public static final int PURPLE = 0xFFA78BFA;

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(int fill, float radiusDp, Context c, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    public static TextView mono(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 11, SUB);
        t.setLetterSpacing(0.08f);
        return t;
    }

    public static TextView h1(Context c, String s) {
        TextView t = text(c, s, 19, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView h2(Context c, String s) {
        TextView t = text(c, s, 15, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 卡片容器 */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, 14, c, STROKE));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    /** 页面根容器 */
    public static ScrollView page(Context c) {
        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 14);
        inner.setPadding(p, dp(c, 6), p, dp(c, 40));
        sv.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.setTag(inner);
        return sv;
    }

    public static LinearLayout body(ScrollView sv) {
        return (LinearLayout) sv.getTag();
    }

    /** 键值行 */
    public static View kv(Context c, String k, String v) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(c, 5), 0, dp(c, 5));
        TextView kk = text(c, k, 13, SUB);
        kk.setSingleLine(false);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f);
        TextView vv = text(c, v == null ? "-" : v, 13, TEXT);
        vv.setTextIsSelectable(true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f);
        row.addView(kk, klp);
        row.addView(vv, vlp);
        return row;
    }

    /** 胶囊按钮 */
    public static TextView pill(Context c, String label, int color, Runnable onClick) {
        TextView t = text(c, label, 12.5f, color);
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        GradientDrawable d = round(0x1AFFFFFF, 11, c, 0);
        d.setColor(blend(color, 0.14f));
        t.setBackground(d);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    /** 主按钮 */
    public static TextView primary(Context c, String label, Runnable onClick) {
        TextView t = text(c, label, 14.5f, 0xFF06181D);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        t.setBackground(round(ACCENT, 12, c, 0));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 6);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    public static TextView ghost(Context c, String label, Runnable onClick) {
        TextView t = text(c, label, 14, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        t.setBackground(round(CARD, 12, c, STROKE));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 6);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    public static EditText input(Context c, String hint, String def) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(def);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(SUB);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setBackground(round(BG2, 10, c, STROKE));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 8);
        e.setLayoutParams(lp);
        return e;
    }

    /** 横向按钮条 */
    public static HorizontalScrollView hbar(Context c, View... items) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(c, 2), 0, dp(c, 2));
        for (View v : items) l.addView(v);
        h.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        h.setLayoutParams(lp);
        return h;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        lp.topMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 8);
        v.setLayoutParams(lp);
        return v;
    }

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    /** 状态徽标 */
    public static TextView badge(Context c, String text, int color) {
        TextView t = text(c, text, 11.5f, color);
        t.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        t.setBackground(round(blend(color, 0.15f), 8, c, 0));
        return t;
    }

    public static int blend(int color, float alpha) {
        int a = (int) (alpha * 255);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int levelColor(int level) {
        switch (level) {
            case 0: return OK;
            case 1: return WARN;
            case 2: return FAIL;
            default: return INFO;
        }
    }
}
