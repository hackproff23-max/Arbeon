package com.arbeon.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Histórico de buscas digitadas (até 25), da mais recente para a mais antiga. */
final class SearchStore {
    static final int MAX = 25;

    private SearchStore() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("arbeon_search", Context.MODE_PRIVATE);
    }

    static synchronized List<String> list(Context c) {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("q", "[]"));
            for (int i = 0; i < a.length() && out.size() < MAX; i++) {
                String s = a.optString(i, "");
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        sp(c).edit().putString("q", a.toString()).apply();
    }

    /** Adiciona no topo; se já existir, apenas sobe para o topo. */
    static synchronized void add(Context c, String q) {
        if (q == null) return;
        String t = q.trim();
        if (t.isEmpty()) return;
        List<String> l = list(c);
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).equalsIgnoreCase(t)) l.remove(i);
        }
        l.add(0, t);
        while (l.size() > MAX) l.remove(l.size() - 1);
        save(c, l);
    }

    static synchronized void remove(Context c, int index) {
        List<String> l = list(c);
        if (index < 0 || index >= l.size()) return;
        l.remove(index);
        save(c, l);
    }

    static synchronized void clear(Context c) {
        sp(c).edit().remove("q").apply();
    }
}
