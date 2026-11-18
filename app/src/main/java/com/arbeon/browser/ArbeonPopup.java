package com.arbeon.browser;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;

/** Menu suspenso no estilo azul do Arbeon, com a mesma forma de uso do PopupMenu. */
final class ArbeonPopup {

    interface Listener {
        boolean onMenuItemClick(Item item);
    }

    static final class Item {
        final int id, order;
        final CharSequence title;

        Item(int id, int order, CharSequence title) {
            this.id = id;
            this.order = order;
            this.title = title;
        }

        int getItemId() {
            return id;
        }
    }

    static final class Items {
        final ArrayList<Item> list = new ArrayList<>();

        void add(int group, int id, int order, CharSequence title) {
            list.add(new Item(id, order, title));
        }
    }

    private final Context c;
    private final View anchor;
    private final Items items = new Items();
    private Listener listener;

    ArbeonPopup(Context c, View anchor) {
        this.c = c;
        this.anchor = anchor;
    }

    Items getMenu() {
        return items;
    }

    void setOnMenuItemClickListener(Listener l) {
        listener = l;
    }

    private int dp(int v) {
        return Fx.dp(c, v);
    }

    void show() {
        ArrayList<Item> sorted = new ArrayList<>(items.list);
        Collections.sort(sorted, (x, y) -> x.order - y.order);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));

        final PopupWindow pw = new PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFEEF2FF});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(2), 0xFFB9CCF7);
        pw.setBackgroundDrawable(bg);
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(12));

        for (final Item it : sorted) {
            TextView t = new TextView(c);
            t.setText(it.title);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            t.setTextColor(Fx.NAVY);
            t.setTypeface(Typeface.DEFAULT);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMinWidth(dp(190));
            t.setPadding(dp(16), dp(12), dp(16), dp(12));
            GradientDrawable pressed = new GradientDrawable();
            pressed.setCornerRadius(dp(18));
            pressed.setColor(0xFFDDE5FF);
            StateListDrawable sl = new StateListDrawable();
            sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sl.addState(new int[]{}, new ColorDrawable(0x00000000));
            t.setBackground(sl);
            t.setOnClickListener(v -> {
                pw.dismiss();
                if (listener != null) listener.onMenuItemClick(it);
            });
            box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        box.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int xoff = anchor.getWidth() - box.getMeasuredWidth();
        pw.showAsDropDown(anchor, xoff, -anchor.getHeight() / 2);
    }
}
