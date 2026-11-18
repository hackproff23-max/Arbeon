package com.arbeon.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tela cheia de Favoritos no estilo azul do Arbeon: busca, ordenar, pastas e estado vazio ilustrado. */
final class BookmarksUi {

    interface Host {
        void open(String url);

        void openNewTab(String url);

        void changed();
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int ALERT = android.R.style.Theme_Material_Light_Dialog_Alert;

    private static final class Bm {
        String title = "";
        String url = "";
        String folder = "";
        int idx;
    }

    private final Activity a;
    private final SharedPreferences prefs;
    private final Host host;

    private Dialog dialog;
    private TextView title;
    private EditText search;
    private LinearLayout list;
    private ScrollView scroll;
    private LinearLayout empty;
    private TextView emptyTitle, emptySub;

    private String folder = null;
    private int sort = 0; // 0 mais recentes, 1 mais antigos, 2 A-Z

    private BookmarksUi(Activity a, SharedPreferences prefs, Host host) {
        this.a = a;
        this.prefs = prefs;
        this.host = host;
    }

    static void show(Activity a, SharedPreferences prefs, Host host) {
        new BookmarksUi(a, prefs, host).open();
    }

    private int dp(int v) {
        return Fx.dp(a, v);
    }

    // ------------------------------------------------------------------ dados

    private JSONArray rawBms() {
        try {
            return new JSONArray(prefs.getString("bookmarks", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveRaw(JSONArray arr) {
        prefs.edit().putString("bookmarks", arr.toString()).apply();
    }

    private List<Bm> loadBms() {
        List<Bm> out = new ArrayList<>();
        JSONArray arr = rawBms();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Bm b = new Bm();
            b.title = o.optString("title", "");
            b.url = o.optString("url", "");
            b.folder = o.optString("folder", "");
            b.idx = i;
            out.add(b);
        }
        return out;
    }

    private List<String> loadFolders() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("bm_folders", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveFolders(List<String> f) {
        JSONArray arr = new JSONArray();
        for (String s : f) arr.put(s);
        prefs.edit().putString("bm_folders", arr.toString()).apply();
    }

    private static String nameOf(Bm b) {
        if (!TextUtils.isEmpty(b.title)) return b.title;
        String h = null;
        try {
            h = Uri.parse(b.url).getHost();
        } catch (Exception ignored) {
        }
        return TextUtils.isEmpty(h) ? b.url : h;
    }

    // ------------------------------------------------------------------ janela

    private void open() {
        dialog = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar) {
            @Override
            public void onBackPressed() {
                if (folder != null) {
                    folder = null;
                    render();
                } else {
                    super.onBackPressed();
                }
            }
        };

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(8), dp(16), 0);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.SURFACE, Fx.CARD2}));

        // ---- cabeçalho
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);

        title = new TextView(a);
        title.setText("Favoritos");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Fx.NAVY);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setOnClickListener(v -> {
            if (folder != null) {
                folder = null;
                render();
            }
        });
        head.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));

        IconView bSort = new IconView(a, IconView.SORT, Fx.ACCENT, 22);
        bSort.setBackground(Fx.button(a, true));
        Fx.glow(bSort, 4);
        bSort.setOnClickListener(v -> sortMenu());
        head.addView(bSort, headLp());

        IconView bFolder = new IconView(a, IconView.FOLDER_PLUS, Fx.ACCENT, 22);
        bFolder.setBackground(Fx.button(a, true));
        Fx.glow(bFolder, 4);
        bFolder.setOnClickListener(v -> newFolder());
        head.addView(bFolder, headLp());

        Glyph bClose = new Glyph(a, Glyph.CLOSE, Fx.ACCENT, 20);
        bClose.setBackground(Fx.button(a, true));
        Fx.glow(bClose, 4);
        bClose.setOnClickListener(v -> dialog.dismiss());
        head.addView(bClose, headLp());
        root.addView(head, new LinearLayout.LayoutParams(MATCH, dp(58)));

        // ---- busca
        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), 0, dp(14), 0);
        final GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.WHITE, Fx.FIELD2});
        pbg.setCornerRadius(dp(30));
        pbg.setStroke(dp(2), Fx.BORDER);
        pill.setBackground(pbg);
        Fx.glow(pill, 3);
        Glyph lupa = new Glyph(a, Glyph.SEARCH, Fx.ACCENT, 20);
        lupa.setClickable(false);
        pill.addView(lupa, new LinearLayout.LayoutParams(dp(24), dp(24)));
        search = new EditText(a);
        search.setHint("Pesquise nos seus favoritos");
        search.setHintTextColor(Fx.MUTED);
        search.setTextColor(Fx.NAVY);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(null);
        search.setPadding(dp(12), 0, 0, 0);
        search.setOnFocusChangeListener((v, f) -> pbg.setStroke(dp(2), f ? Fx.GLOW : Fx.BORDER));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        pill.addView(search, new LinearLayout.LayoutParams(0, MATCH, 1f));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, dp(54));
        plp.topMargin = dp(6);
        plp.leftMargin = dp(2);
        plp.rightMargin = dp(2);
        root.addView(pill, plp);

        // ---- conteúdo: lista + estado vazio
        FrameLayout body = new FrameLayout(a);
        body.setClipChildren(false);

        scroll = new ScrollView(a);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setPadding(dp(4), dp(16), dp(4), dp(20));
        scroll.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        body.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        empty = new LinearLayout(a);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(dp(8), 0, dp(8), dp(40));
        empty.addView(new EmptyArt(a), new LinearLayout.LayoutParams(dp(240), dp(210)));
        emptyTitle = new TextView(a);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        emptyTitle.setTextColor(Fx.NAVY);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setPadding(dp(30), dp(14), dp(30), dp(14));
        emptyTitle.setBackground(Fx.button(a, false));
        Fx.glow(emptyTitle, 3);
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(WRAP, WRAP);
        etp.topMargin = dp(18);
        empty.addView(emptyTitle, etp);
        emptySub = new TextView(a);
        emptySub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptySub.setTextColor(Fx.TEXT2);
        emptySub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams esp = new LinearLayout.LayoutParams(WRAP, WRAP);
        esp.topMargin = dp(14);
        empty.addView(emptySub, esp);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(MATCH, MATCH);
        elp.gravity = Gravity.CENTER;
        empty.setGravity(Gravity.CENTER);
        body.addView(empty, elp);

        root.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> host.changed());

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Fx.SURFACE));
            w.setLayout(MATCH, MATCH);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (Build.VERSION.SDK_INT >= 23) {
                w.setStatusBarColor(Fx.SURFACE);
                View dec = w.getDecorView();
                dec.setSystemUiVisibility(dec.getSystemUiVisibility()
                        | (Fx.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
            }
        }
        render();
        dialog.show();
    }

    private LinearLayout.LayoutParams headLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(46), dp(46));
        p.leftMargin = dp(10);
        return p;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.bottomMargin = dp(12);
        return p;
    }

    // ------------------------------------------------------------------ lista

    private void render() {
        if (list == null) return;
        String q = search.getText().toString().trim().toLowerCase();
        list.removeAllViews();
        title.setText(folder == null ? "Favoritos" : "\u2039 " + folder);

        List<Bm> all = loadBms();
        int shown = 0;

        if (folder == null && q.isEmpty()) {
            for (String f : loadFolders()) {
                int n = 0;
                for (Bm b : all) if (f.equals(b.folder)) n++;
                list.addView(folderRow(f, n), rowLp());
                shown++;
            }
        }

        List<Bm> items = new ArrayList<>();
        for (Bm b : all) {
            if (!q.isEmpty()) {
                if (!(nameOf(b).toLowerCase().contains(q) || b.url.toLowerCase().contains(q))) continue;
            } else if (folder == null ? !b.folder.isEmpty() : !folder.equals(b.folder)) {
                continue;
            }
            items.add(b);
        }
        final int mode = sort;
        Collections.sort(items, (x, y) -> {
            if (mode == 0) return y.idx - x.idx;
            if (mode == 1) return x.idx - y.idx;
            return nameOf(x).compareToIgnoreCase(nameOf(y));
        });
        for (Bm b : items) {
            list.addView(bmRow(b), rowLp());
            shown++;
        }

        boolean none = shown == 0;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        scroll.setVisibility(none ? View.GONE : View.VISIBLE);
        if (none) {
            if (!q.isEmpty()) {
                emptyTitle.setText("Nada encontrado");
                emptySub.setText("Tente outro termo de pesquisa.");
            } else if (folder != null) {
                emptyTitle.setText("Pasta vazia");
                emptySub.setText("Segure um favorito e escolha\n\"Mover para pasta\".");
            } else {
                emptyTitle.setText("Nenhum favorito ainda");
                emptySub.setText("Adicione p\u00e1ginas aos seus favoritos\npara acess\u00e1-las rapidamente.");
            }
        }
    }

    private LinearLayout pillRow(View leading, String t, String sub) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(10), dp(8), dp(16), dp(8));
        r.setMinimumHeight(dp(68));
        r.setBackground(Fx.button(a, false));
        Fx.glow(r, 3);
        r.addView(leading, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, dp(8), 0);
        TextView tt = new TextView(a);
        tt.setText(t);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tt.setTypeface(Typeface.DEFAULT_BOLD);
        tt.setTextColor(Fx.NAVY);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(tt, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView ss = new TextView(a);
        ss.setText(sub);
        ss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ss.setTextColor(Fx.MUTED);
        ss.setSingleLine(true);
        ss.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(ss, new LinearLayout.LayoutParams(MATCH, WRAP));
        r.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return r;
    }

    private View bmRow(final Bm b) {
        String name = nameOf(b);
        TextView circle = new TextView(a);
        circle.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        circle.setGravity(Gravity.CENTER);
        circle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        circle.setTextColor(Fx.ACCENT);
        circle.setBackground(Fx.button(a, true));
        LinearLayout r = pillRow(circle, name, b.url);
        r.setOnClickListener(v -> {
            dialog.dismiss();
            host.open(b.url);
        });
        r.setOnLongClickListener(v -> {
            bmMenu(b);
            return true;
        });
        return r;
    }

    private View folderRow(final String f, int count) {
        IconView ic = new IconView(a, IconView.FOLDER, Fx.ACCENT, 22);
        ic.setBackground(Fx.button(a, true));
        LinearLayout r = pillRow(ic, f, count == 1 ? "1 favorito" : count + " favoritos");
        TextView seta = new TextView(a);
        seta.setText("\u203a");
        seta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        seta.setTextColor(Fx.ACCENT);
        r.addView(seta, new LinearLayout.LayoutParams(WRAP, WRAP));
        r.setOnClickListener(v -> {
            folder = f;
            search.setText("");
            render();
        });
        r.setOnLongClickListener(v -> {
            new ArbeonDialog(a)
                    .setTitle(f)
                    .setItems(new String[]{"Excluir pasta (os favoritos voltam para a lista)"}, (d, w) -> deleteFolder(f))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return true;
        });
        return r;
    }

    // ------------------------------------------------------------------ ações

    private void bmMenu(final Bm b) {
        new ArbeonDialog(a)
                .setTitle(nameOf(b))
                .setItems(new String[]{"Abrir em nova guia", "Mover para pasta", "Remover"}, (d, w) -> {
                    if (w == 0) {
                        dialog.dismiss();
                        host.openNewTab(b.url);
                    } else if (w == 1) {
                        moveMenu(b);
                    } else {
                        JSONArray arr = rawBms();
                        arr.remove(b.idx);
                        saveRaw(arr);
                        render();
                        toast("Favorito removido");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void moveMenu(final Bm b) {
        final List<String> folders = loadFolders();
        if (folders.isEmpty()) {
            toast("Crie uma pasta primeiro (bot\u00e3o de pasta no topo)");
            return;
        }
        String[] names = new String[folders.size() + 1];
        names[0] = "Sem pasta";
        for (int i = 0; i < folders.size(); i++) names[i + 1] = folders.get(i);
        new ArbeonDialog(a)
                .setTitle("Mover para")
                .setItems(names, (d, w) -> {
                    JSONArray arr = rawBms();
                    JSONObject o = arr.optJSONObject(b.idx);
                    if (o == null) return;
                    try {
                        if (w == 0) o.remove("folder");
                        else o.put("folder", folders.get(w - 1));
                    } catch (Exception ignored) {
                    }
                    saveRaw(arr);
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteFolder(String f) {
        JSONArray arr = rawBms();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && f.equals(o.optString("folder", ""))) o.remove("folder");
        }
        saveRaw(arr);
        List<String> fs = loadFolders();
        fs.remove(f);
        saveFolders(fs);
        render();
    }

    private void sortMenu() {
        new ArbeonDialog(a)
                .setTitle("Ordenar por")
                .setSingleChoiceItems(new String[]{"Mais recentes", "Mais antigos", "A\u2013Z"}, sort, (d, w) -> {
                    sort = w;
                    d.dismiss();
                    render();
                })
                .show();
    }

    private void newFolder() {
        final EditText in = new EditText(a);
        ArbeonUi.styleField(in);
        in.setHint("Nome da pasta");
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        FrameLayout box = new FrameLayout(a);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(in, new FrameLayout.LayoutParams(MATCH, WRAP));
        new ArbeonDialog(a)
                .setTitle("Nova pasta")
                .setView(box)
                .setPositiveButton("Criar", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty()) return;
                    List<String> fs = loadFolders();
                    if (fs.contains(n)) {
                        toast("J\u00e1 existe uma pasta com esse nome");
                        return;
                    }
                    fs.add(n);
                    saveFolders(fs);
                    folder = null;
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toast(String s) {
        ArbeonUi.toast(a, s);
    }

    // ------------------------------------------------------------------ ícones e ilustração

    /** Ícones extras desenhados no código: ordenar, nova pasta e pasta. */
    private static final class IconView extends View {
        static final int SORT = 0, FOLDER_PLUS = 1, FOLDER = 2;
        private final int type;
        private final int color;
        private final float iconDp;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        IconView(Context c, int type, int color, int iconDp) {
            super(c);
            this.type = type;
            this.color = color;
            this.iconDp = iconDp;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float size = iconDp * getResources().getDisplayMetrics().density;
            float u = size / 24f;
            c.save();
            c.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            p.setColor(color);
            p.setStrokeWidth(2.6f * u);
            if (type == SORT) {
                c.drawLine(4 * u, 7 * u, 20 * u, 7 * u, p);
                c.drawLine(7 * u, 12 * u, 17 * u, 12 * u, p);
                c.drawLine(10.5f * u, 17 * u, 13.5f * u, 17 * u, p);
            } else {
                p.setStrokeWidth(2.2f * u);
                path.reset();
                path.moveTo(3 * u, 6 * u);
                path.lineTo(9 * u, 6 * u);
                path.lineTo(11.2f * u, 8.6f * u);
                path.lineTo(21 * u, 8.6f * u);
                path.lineTo(21 * u, 19 * u);
                path.lineTo(3 * u, 19 * u);
                path.close();
                c.drawPath(path, p);
                if (type == FOLDER_PLUS) {
                    c.drawLine(12 * u, 11.6f * u, 12 * u, 16.4f * u, p);
                    c.drawLine(9.6f * u, 14 * u, 14.4f * u, 14 * u, p);
                }
            }
            c.restore();
        }
    }

    /** Pasta com estrela brilhante, usada quando não há favoritos. */
    private static final class EmptyArt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        EmptyArt(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        private void star(float cx, float cy, float rad) {
            path.reset();
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 5;
                float rr = (i % 2 == 0) ? rad : rad * 0.46f;
                float x = cx + rr * (float) Math.cos(ang);
                float y = cy + rr * (float) Math.sin(ang);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float d = getResources().getDisplayMetrics().density;

            // brilho azul no chão
            p.reset();
            p.setAntiAlias(true);
            p.setColor(0xAA2D6BFF);
            p.setMaskFilter(new BlurMaskFilter(14 * d, BlurMaskFilter.Blur.NORMAL));
            r.set(w * 0.18f, h * 0.80f, w * 0.82f, h * 0.94f);
            c.drawOval(r, p);
            p.setMaskFilter(null);

            // aba de trás da pasta
            p.setColor(0xFF2F6BFF);
            r.set(w * 0.22f, h * 0.50f, w * 0.78f, h * 0.72f);
            c.drawRoundRect(r, 14 * d, 14 * d, p);

            // corpo da pasta
            p.setShader(new LinearGradient(0, h * 0.55f, 0, h * 0.88f, 0xFFFFFFFF, 0xFFD2DEFF,
                    Shader.TileMode.CLAMP));
            r.set(w * 0.12f, h * 0.56f, w * 0.88f, h * 0.88f);
            c.drawRoundRect(r, 20 * d, 20 * d, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(Fx.WHITE);
            c.drawRoundRect(r, 20 * d, 20 * d, p);

            // estrelinha na pasta
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(3 * d);
            p.setColor(0xFF1F5FE0);
            star(w * 0.5f, h * 0.73f, 15 * d);
            c.drawPath(path, p);

            // brilho + estrela grande
            star(w * 0.5f, h * 0.35f, 46 * d);
            p.setColor(0xCCFFFFFF);
            p.setMaskFilter(new BlurMaskFilter(12 * d, BlurMaskFilter.Blur.NORMAL));
            c.drawPath(path, p);
            p.setMaskFilter(null);
            p.setShader(new LinearGradient(0, h * 0.15f, 0, h * 0.55f, 0xFF3A82FF, 0xFF0B3FC8,
                    Shader.TileMode.CLAMP));
            p.setStrokeWidth(8 * d);
            c.drawPath(path, p);
            p.setShader(null);

            // faíscas
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(5 * d);
            p.setColor(0xFF2F6BFF);
            c.drawLine(w * 0.15f, h * 0.29f, w * 0.24f, h * 0.36f, p);
            c.drawLine(w * 0.08f, h * 0.43f, w * 0.20f, h * 0.47f, p);
            c.drawLine(w * 0.85f, h * 0.29f, w * 0.76f, h * 0.36f, p);
            c.drawLine(w * 0.92f, h * 0.43f, w * 0.80f, h * 0.47f, p);
        }
    }
}
