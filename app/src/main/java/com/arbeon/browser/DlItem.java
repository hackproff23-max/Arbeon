package com.arbeon.browser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLongArray;

/** Um download: dados, estado e progresso por pedaço (chunk). */
class DlItem {
    static final int QUEUED = 0, RUNNING = 1, PAUSED = 2, DONE = 3, FAILED = 4;

    String id, fileName, mime, ua, referer, savedUri;
    volatile String url;
    volatile long total = -1;
    volatile boolean ranges;
    long added;
    volatile int state = PAUSED;
    volatile String error = "";
    volatile boolean stop, finalizing, force, removeAfter, restartAfter;
    volatile long[] cStart, cEnd;
    volatile AtomicLongArray cDone;
    volatile double speed;
    long lastBytes, lastTime;
    final ArrayList<HttpURLConnection> conns = new ArrayList<>();

    long done() {
        AtomicLongArray d = cDone;
        if (d == null) return 0;
        long s = 0;
        for (int i = 0; i < d.length(); i++) s += d.get(i);
        return s;
    }

    JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("url", url);
            o.put("name", fileName);
            o.put("mime", mime);
            o.put("ua", ua);
            o.put("ref", referer);
            o.put("uri", savedUri);
            o.put("total", total);
            o.put("ranges", ranges);
            o.put("added", added);
            o.put("state", state);
            o.put("error", error);
            long[] cs = cStart, ce = cEnd;
            AtomicLongArray cd = cDone;
            if (cs != null && ce != null && cd != null && cd.length() == cs.length) {
                JSONArray a = new JSONArray(), b = new JSONArray(), c = new JSONArray();
                for (int i = 0; i < cs.length; i++) {
                    a.put(cs[i]);
                    b.put(ce[i]);
                    c.put(cd.get(i));
                }
                o.put("cs", a);
                o.put("ce", b);
                o.put("cd", c);
            }
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    static DlItem fromJson(JSONObject o) {
        DlItem it = new DlItem();
        it.id = o.optString("id");
        it.url = o.optString("url");
        it.fileName = o.optString("name");
        it.mime = o.optString("mime", null);
        it.ua = o.optString("ua", null);
        it.referer = o.optString("ref", null);
        it.savedUri = o.optString("uri", null);
        it.total = o.optLong("total", -1);
        it.ranges = o.optBoolean("ranges");
        it.added = o.optLong("added");
        it.state = o.optInt("state", PAUSED);
        it.error = o.optString("error", "");
        JSONArray a = o.optJSONArray("cs"), b = o.optJSONArray("ce"), c = o.optJSONArray("cd");
        if (a != null && b != null && c != null && a.length() == b.length() && a.length() == c.length()) {
            int n = a.length();
            it.cStart = new long[n];
            it.cEnd = new long[n];
            it.cDone = new AtomicLongArray(n);
            for (int i = 0; i < n; i++) {
                it.cStart[i] = a.optLong(i);
                it.cEnd[i] = b.optLong(i);
                it.cDone.set(i, c.optLong(i));
            }
        }
        if (it.state == RUNNING || it.state == QUEUED) {
            it.state = PAUSED;
            it.error = "Interrompido. Toque em ▶ para continuar de onde parou.";
        }
        return it;
    }
}
