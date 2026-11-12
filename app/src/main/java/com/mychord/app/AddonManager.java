package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Sistem addon: katalog dimuat dari GitHub (atau salinan dalam app).
 * Addon baharu dalam katalog dipasang automatik bila "Auto-pasang" dihidupkan.
 */
public class AddonManager {
    public static final String CATALOG_URL =
            "https://raw.githubusercontent.com/mohdrozaimi205/MyChord/main/addons/catalog.json";

    public static class Addon {
        public String id, name, nameEn, desc, descEn, version;
        public boolean auto;
        public boolean custom;

        public String title() { return isEn() ? nameEn : name; }
        public String about() { return isEn() ? descEn : desc; }
    }

    static boolean isEn() { return "en".equals(Prefs.lang()); }

    public static boolean installed(String id) { return Prefs.getSet("addons").contains(id); }

    public static void setInstalled(String id, boolean on) {
        Set<String> s = Prefs.getSet("addons");
        if (on) s.add(id); else s.remove(id);
        Prefs.putSet("addons", s);
    }

    public static boolean autoInstall() { return Prefs.getB("addonAuto", true); }

    private static List<Addon> parse(String json) throws Exception {
        List<Addon> out = new ArrayList<>();
        JSONArray a = new JSONObject(json).getJSONArray("addons");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Addon ad = new Addon();
            ad.id = o.getString("id");
            ad.name = o.optString("name");
            ad.nameEn = o.optString("name_en", ad.name);
            ad.desc = o.optString("desc");
            ad.descEn = o.optString("desc_en", ad.desc);
            ad.version = o.optString("version", "1");
            ad.auto = o.optBoolean("auto", true);
            out.add(ad);
        }
        return out;
    }

    private static List<Addon> bundled() {
        try (InputStream in = App.get().getAssets().open("addons.json")) {
            byte[] b = new byte[in.available()];
            int r = in.read(b);
            return parse(new String(b, 0, Math.max(r, 0), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Katalog terkini (cuba dalam talian dahulu). Panggil dari thread latar. */
    public static List<Addon> catalog() {
        List<Addon> list = bundled();
        try {
            Request req = new Request.Builder().url(CATALOG_URL).build();
            try (Response r = App.http.newCall(req).execute()) {
                if (r.isSuccessful() && r.body() != null) {
                    List<Addon> remote = parse(r.body().string());
                    // gabung: addon jauh menggantikan yang sama id
                    for (Addon ra : remote) {
                        boolean found = false;
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).id.equals(ra.id)) { list.set(i, ra); found = true; }
                        }
                        if (!found) list.add(ra);
                    }
                }
            }
        } catch (Exception ignored) { }
        Prefs.putS("catalogCache", String.valueOf(list.size()));
        return list;
    }

    /** Pasang automatik addon yang belum pernah dilihat. Pulangkan nama addon yang baru dipasang. */
    public static List<String> sync() {
        List<String> added = new ArrayList<>();
        Set<String> known = Prefs.getSet("addonsKnown");
        for (Addon a : catalog()) {
            if (known.contains(a.id)) continue;
            known.add(a.id);
            if (a.auto && autoInstall()) {
                setInstalled(a.id, true);
                added.add(a.title());
            }
        }
        Prefs.putSet("addonsKnown", known);
        return added;
    }

    public static String letter(Addon a) {
        String t = a.title();
        return t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
