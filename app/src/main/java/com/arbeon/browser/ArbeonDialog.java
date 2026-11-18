package com.arbeon.browser;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/** Substituto do AlertDialog.Builder no estilo azul do Arbeon (cartão arredondado e botões em pílula). */
final class ArbeonDialog {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Context c;
    private CharSequence title, message, posT, negT, neuT;
    private View view;
    private CharSequence[] items;
    private boolean choice;
    private int checked = -1;
    private DialogInterface.OnClickListener itemL, posL, negL, neuL;
    private Runnable onCancel;

    ArbeonDialog(Context c) {
        this.c = c;
    }

    ArbeonDialog setTitle(CharSequence t) {
        title = t;
        return this;
    }

    ArbeonDialog setMessage(CharSequence m) {
        message = m;
        return this;
    }

    ArbeonDialog setView(View v) {
        view = v;
        return this;
    }

    ArbeonDialog setItems(CharSequence[] i, DialogInterface.OnClickListener l) {
        items = i;
        itemL = l;
        choice = false;
        return this;
    }

    ArbeonDialog setSingleChoiceItems(CharSequence[] i, int sel, DialogInterface.OnClickListener l) {
        items = i;
        itemL = l;
        choice = true;
        checked = sel;
        return this;
    }

    ArbeonDialog setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) {
        posT = t;
        posL = l;
        return this;
    }

    ArbeonDialog setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) {
        negT = t;
        negL = l;
        return this;
    }

    ArbeonDialog setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) {
        neuT = t;
        neuL = l;
        return this;
    }

    ArbeonDialog setOnCancel(Runnable r) {
        onCancel = r;
        return this;
    }

    private int dp(int v) {
        return Fx.dp(c, v);
    }

    private static final class MaxScroll extends ScrollView {
        private final int max;

        MaxScroll(Context c, int max) {
            super(c);
            this.max = max;
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
        }
    }

    private void styleMark(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (on) {
            g.setColor(Fx.ACCENT);
            t.setText("\u2713");
            t.setTextColor(0xFFFFFFFF);
        } else {
            g.setColor(0xFFFFFFFF);
            g.setStroke(dp(2), 0xFF9DB7F5);
            t.setText("");
        }
        t.setBackground(g);
    }

    private TextView pill(CharSequence text, boolean primary) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(false);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 17 : 15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        if (primary) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Fx.GLOW, Fx.ACCENT});
            g.setCornerRadius(dp(100));
            t.setBackground(g);
            t.setTextColor(0xFFFFFFFF);
        } else {
            t.setBackground(Fx.button(c, false));
            t.setTextColor(Fx.NAVY);
        }
        Fx.glow(t, primary ? 5 : 3);
        t.setClickable(true);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int hDp, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hDp == 0 ? WRAP : dp(hDp));
        p.topMargin = dp(topDp);
        return p;
    }

    void show() {
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFE9EEFF});
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(2), 0xFFBFD0FA);
        card.setBackground(bg);

        if (title != null && title.length() > 0) {
            TextView t = new TextView(c);
            t.setText(title);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(Fx.NAVY);
            card.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(2), dp(4), dp(2), dp(8));

        if (message != null && message.length() > 0) {
            TextView m = new TextView(c);
            m.setText(message);
            m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            m.setTextColor(0xFF2B3350);
            content.addView(m, lp(MATCH, 0, 8));
        }
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            content.addView(view, lp(MATCH, 0, 12));
        }
        if (items != null) {
            final ArrayList<TextView> marks = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(8), dp(14), dp(8));
                row.setMinimumHeight(dp(54));
                row.setBackground(Fx.button(c, false));
                Fx.glow(row, 2);
                TextView lab = new TextView(c);
                lab.setText(items[i]);
                lab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                lab.setTextColor(Fx.NAVY);
                row.addView(lab, new LinearLayout.LayoutParams(0, WRAP, 1f));
                if (choice) {
                    TextView mk = new TextView(c);
                    mk.setGravity(Gravity.CENTER);
                    mk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    mk.setTypeface(Typeface.DEFAULT_BOLD);
                    styleMark(mk, i == checked);
                    marks.add(mk);
                    row.addView(mk, new LinearLayout.LayoutParams(dp(28), dp(28)));
                }
                row.setOnClickListener(v -> {
                    if (choice) {
                        checked = idx;
                        for (int k = 0; k < marks.size(); k++) styleMark(marks.get(k), k == checked);
                        if (itemL != null) itemL.onClick(d, idx);
                    } else {
                        d.dismiss();
                        if (itemL != null) itemL.onClick(d, idx);
                    }
                });
                content.addView(row, lp(MATCH, 0, 10));
            }
        }
        if (content.getChildCount() > 0) {
            MaxScroll sc = new MaxScroll(c, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.6f));
            sc.setClipChildren(false);
            sc.setClipToPadding(false);
            sc.setVerticalScrollBarEnabled(false);
            sc.addView(content);
            card.addView(sc, lp(MATCH, 0, 4));
        }

        boolean hasPos = posT != null, hasNeg = negT != null, hasNeu = neuT != null;
        if (hasPos) {
            TextView b = pill(posT, true);
            b.setOnClickListener(v -> {
                d.dismiss();
                if (posL != null) posL.onClick(d, DialogInterface.BUTTON_POSITIVE);
            });
            card.addView(b, lp(MATCH, 50, 16));
        }
        if (hasNeg || hasNeu) {
            LinearLayout two = new LinearLayout(c);
            two.setOrientation(LinearLayout.HORIZONTAL);
            two.setClipChildren(false);
            boolean both = hasNeg && hasNeu;
            if (hasNeu) {
                TextView b = pill(neuT, false);
                b.setOnClickListener(v -> {
                    d.dismiss();
                    if (neuL != null) neuL.onClick(d, DialogInterface.BUTTON_NEUTRAL);
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1f);
                if (both) p.rightMargin = dp(6);
                two.addView(b, p);
            }
            if (hasNeg) {
                TextView b = pill(negT, false);
                b.setOnClickListener(v -> {
                    d.dismiss();
                    if (negL != null) negL.onClick(d, DialogInterface.BUTTON_NEGATIVE);
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1f);
                if (both) p.leftMargin = dp(6);
                two.addView(b, p);
            }
            card.addView(two, lp(MATCH, 0, hasPos ? 12 : 16));
        }

        d.setContentView(card);
        d.setCanceledOnTouchOutside(true);
        if (onCancel != null) d.setOnCancelListener(x -> onCancel.run());
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min((int) (sw * 0.9f), dp(440)), WRAP);
        }
        d.show();
    }
}
