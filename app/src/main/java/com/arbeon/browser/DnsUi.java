package com.arbeon.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Janela "DNS de bloqueio": escolher a DNS, ligar/desligar, testar e salvar. */
final class DnsUi {
    private DnsUi() {
    }

    private static int dp(Activity a, int v) {
        return Fx.dp(a, v);
    }

    private static TextView text(Activity a, String s, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private static TextView button(Activity a, String s) {
        TextView t = text(a, s, 15, Fx.ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        t.setClickable(true);
        return t;
    }

    static void show(final Activity a, final Runnable onSaved) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 18));
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 8));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        bg.setCornerRadius(dp(a, 28));
        bg.setStroke(dp(a, 2), Fx.BORDER2);
        panel.setBackground(bg);
        Fx.glow(panel, 10);
        wrap.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(a, "DNS de bloqueio", 22, Fx.NAVY);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);
        TextView desc = text(a, "Bloqueia anúncios, pop-ups e rastreadores consultando uma DNS "
                + "(DNS sobre HTTPS) a cada site que a página usa.", 14, Fx.MUTED);
        desc.setPadding(0, dp(a, 6), 0, dp(a, 10));
        panel.addView(desc);

        // ---- ligar/desligar
        final Switch swOn = new Switch(a);
        swOn.setText("Ativar DNS de bloqueio");
        swOn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        swOn.setTextColor(Fx.NAVY);
        swOn.setChecked(AdDns.on(a));
        swOn.setPadding(0, dp(a, 8), 0, dp(a, 8));
        panel.addView(swOn);

        // ---- escolha da DNS
        String saved = AdDns.url(a);
        int sel = 0;
        boolean custom = false;
        if (!saved.isEmpty()) {
            sel = 3;
            for (int i = 0; i < 3; i++) if (AdDns.URLS[i].equals(saved)) sel = i;
            custom = sel == 3;
        }
        final RadioGroup rg = new RadioGroup(a);
        rg.setOrientation(RadioGroup.VERTICAL);
        final RadioButton[] rb = new RadioButton[4];
        for (int i = 0; i < 4; i++) {
            rb[i] = new RadioButton(a);
            rb[i].setId(View.generateViewId());
            rb[i].setText(AdDns.NAMES[i]);
            rb[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rb[i].setTextColor(Fx.NAVY);
            rb[i].setButtonTintList(ColorStateList.valueOf(Fx.ACCENT));
            rb[i].setPadding(dp(a, 6), dp(a, 8), 0, dp(a, 8));
            rg.addView(rb[i]);
        }
        rb[sel].setChecked(true);
        panel.addView(rg);

        final EditText field = new EditText(a);
        field.setHint("https://seu-dns.com/dns-query  ou  dns.exemplo.com");
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.setTextColor(Fx.NAVY);
        field.setHintTextColor(Fx.HINT);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setText(custom ? saved : "");
        GradientDrawable fb = new GradientDrawable();
        fb.setCornerRadius(dp(a, 20));
        fb.setColor(Fx.WHITE);
        fb.setStroke(dp(a, 2), Fx.BORDER);
        field.setBackground(fb);
        field.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 10));
        field.setVisibility(custom ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = dp(a, 4);
        panel.addView(field, fp);
        rg.setOnCheckedChangeListener((g, id) ->
                field.setVisibility(id == rb[3].getId() ? View.VISIBLE : View.GONE));

        // ---- redirecionamentos / pop-ups
        final Switch swGuard = new Switch(a);
        swGuard.setText("Bloquear redirecionamentos e pop-ups automáticos");
        swGuard.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        swGuard.setTextColor(Fx.NAVY);
        swGuard.setChecked(AdDns.guardRedirects(a));
        swGuard.setPadding(0, dp(a, 14), 0, dp(a, 4));
        panel.addView(swGuard);
        TextView guardInfo = text(a, "Impede que a página mande você para outro site sozinha "
                + "ou abra outros apps sem você tocar. Se algum login parar de funcionar, desligue.",
                12, Fx.MUTED);
        guardInfo.setPadding(0, 0, 0, dp(a, 8));
        panel.addView(guardInfo);

        final TextView stat = text(a, "Bloqueados nesta sessão: "
                + (AdDns.blocked.get() + AdBlock.blocked.get()), 13, Fx.MUTED);
        stat.setPadding(0, dp(a, 4), 0, 0);
        panel.addView(stat);

        // ---- botões
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView bTest = button(a, "TESTAR");
        TextView bCancel = button(a, "CANCELAR");
        TextView bSave = button(a, "SALVAR");
        row.addView(bTest);
        row.addView(bCancel);
        row.addView(bSave);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(a, 6);
        panel.addView(row, rp);

        // endereço escolhido (null = inválido)
        final java.util.concurrent.Callable<String> endpoint = () -> {
            for (int i = 0; i < 3; i++) if (rb[i].isChecked()) return AdDns.URLS[i];
            return AdDns.normalize(field.getText().toString());
        };

        bCancel.setOnClickListener(v -> d.dismiss());
        bSave.setOnClickListener(v -> {
            String ep;
            try {
                ep = endpoint.call();
            } catch (Exception e) {
                ep = null;
            }
            boolean on = swOn.isChecked();
            if (ep == null && on) {
                ArbeonUi.toast(a, "Endereço da DNS inválido");
                return;
            }
            AdDns.save(a, on, ep == null ? "" : ep, swGuard.isChecked());
            ArbeonUi.toast(a, on ? "DNS de bloqueio ativada" : "DNS de bloqueio desativada");
            d.dismiss();
            if (onSaved != null) onSaved.run();
        });
        bTest.setOnClickListener(v -> {
            String ep;
            try {
                ep = endpoint.call();
            } catch (Exception e) {
                ep = null;
            }
            if (ep == null) {
                ArbeonUi.toast(a, "Endereço da DNS inválido");
                return;
            }
            ArbeonUi.toast(a, "Testando…");
            final String fep = ep;
            new Thread(() -> {
                Boolean r = AdDns.query(fep, "doubleclick.net");
                final String msg = r == null ? "Não consegui conectar a essa DNS"
                        : r ? "Funcionando: anúncios são bloqueados"
                        : "A DNS respondeu, mas não bloqueou o domínio de teste";
                a.runOnUiThread(() -> ArbeonUi.toast(a, msg));
            }).start();
        });

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(false);
        sv.addView(wrap, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw2 = a.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (sw2 * 0.94f), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.28f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
    }
}
