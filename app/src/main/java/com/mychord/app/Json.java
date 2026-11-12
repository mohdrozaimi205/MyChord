package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

/** Pembantu kecil untuk membaca JSON YouTube Music yang bersarang dalam. */
public class Json {
    public static void collect(Object node, String key, List<JSONObject> out) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = o.opt(k);
                if (k.equals(key) && v instanceof JSONObject) out.add((JSONObject) v);
                else collect(v, key, out);
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), key, out);
        }
    }

    public static Object find(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            if (o.has(key)) return o.opt(key);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                Object r = find(o.opt(it.next()), key);
                if (r != null) return r;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                Object r = find(a.opt(i), key);
                if (r != null) return r;
            }
        }
        return null;
    }

    public static Object path(Object node, String... keys) {
        Object cur = node;
        for (String k : keys) {
            if (cur instanceof JSONObject) cur = ((JSONObject) cur).opt(k);
            else if (cur instanceof JSONArray) {
                try { cur = ((JSONArray) cur).opt(Integer.parseInt(k)); } catch (Exception e) { return null; }
            } else return null;
            if (cur == null) return null;
        }
        return cur;
    }

    public static String str(Object node, String... keys) {
        Object o = path(node, keys);
        return o instanceof String ? (String) o : null;
    }

    /** Gabung teks "runs" */
    public static String runs(Object textObj) {
        if (!(textObj instanceof JSONObject)) return "";
        JSONObject t = (JSONObject) textObj;
        if (t.has("simpleText")) return t.optString("simpleText");
        JSONArray r = t.optJSONArray("runs");
        if (r == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length(); i++) sb.append(r.optJSONObject(i) != null ? r.optJSONObject(i).optString("text") : "");
        return sb.toString();
    }

    /** Teks "runs" sehingga pemisah " • " pertama (biasanya nama artis) */
    public static String firstPart(Object textObj) {
        String s = runs(textObj);
        int i = s.indexOf(" • ");
        return i > 0 ? s.substring(0, i) : s;
    }

    public static String thumb(Object node) {
        Object t = find(node, "thumbnails");
        if (t instanceof JSONArray && ((JSONArray) t).length() > 0) {
            JSONArray a = (JSONArray) t;
            String u = a.optJSONObject(a.length() - 1).optString("url");
            if (u.startsWith("//")) u = "https:" + u;
            if (u.contains("googleusercontent.com")) u = u.replaceAll("=w\\d+-h\\d+", "=w544-h544");
            return u;
        }
        return "";
    }
}
