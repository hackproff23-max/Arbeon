package com.arbeon.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Histórico de navegação (até 500 páginas): título, endereço e hora. */
final class HistoryStore {
    private static final int MAX = 500;

    private HistoryStore() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("arbeon_hist", Context.MODE_PRIVATE);
    }

    static synchronized JSONArray list(Context c) {
        try {
            return new JSONArray(sp(c).getString("h", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void add(Context c, String title, String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) return;
        try {
            JSONArray old = list(c);
            JSONArray n = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("t", title == null || title.isEmpty() ? url : title);
            o.put("u", url);
            o.put("ts", System.currentTimeMillis());
            n.put(o);
            for (int i = 0; i < old.length() && n.length() < MAX; i++) {
                JSONObject x = old.optJSONObject(i);
                if (x == null) continue;
                if (i == 0 && url.equals(x.optString("u"))) continue; // mesma página: só atualiza
                n.put(x);
            }
            sp(c).edit().putString("h", n.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized void remove(Context c, int index) {
        try {
            JSONArray old = list(c);
            JSONArray n = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                if (i != index) n.put(old.get(i));
            }
            sp(c).edit().putString("h", n.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized void clear(Context c) {
        sp(c).edit().remove("h").apply();
    }
}
