package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Addon radio: stesen dari Radio Browser (percuma, sumber terbuka) + stesen MY popular. */
public class RadioApi {
    private static final String BASE = "https://de1.api.radio-browser.info/json/stations/";
    private static final String STW  = "https://playerservices.streamtheworld.com/api/livestream-redirect/";

    // ── Stesen Malaysia popular (hardcoded, sentiasa tersedia) ──

    private static final String[][] MY_STATIONS = {
        // {nama, keterangan, url stream, logo url}
        {"ERA FM", "Muzik Hit Terkini", STW + "ERA_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s17365/images/logog.png"},
        {"Suria FM", "Segalanya Hiburan", STW + "SURIA_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s98941/images/logog.png"},
        {"Kedah FM", "Stesen Kedah", STW + "KEDAH_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s192730/images/logog.png"},
        {"Mutiara FM", "Stesen Pulau Pinang", STW + "MUTIARA_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s192731/images/logog.png"},
        {"Hot FM", "Panas Sentiasa Panas", STW + "HOT_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s17363/images/logog.png"},
        {"Fly FM", "Malaysian No.1 Hit Station", STW + "FLY_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s81413/images/logog.png"},
        {"Hitz FM", "Malaysia's #1 Hit Music", STW + "HITZ_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s17358/images/logog.png"},
        {"Mix FM", "Today's Best Mix", STW + "MIX_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s17362/images/logog.png"},
        {"Nasional FM", "Siaran Nasional RTM", STW + "NASIONAL_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s17366/images/logog.png"},
        {"Ai FM", "RTM Chinese Service", STW + "AI_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s17357/images/logog.png"},
        {"TraXX FM", "Music of Our Lives", STW + "TRAXX_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s17356/images/logog.png"},
        {"Asyik FM", "RTM Tamil Service", STW + "ASYIK_FMAAC.aac",
         "https://cdn-profiles.tunein.com/s35879/images/logog.png"},
    };

    /** Senarai stesen MY popular (sentiasa ada walaupun Radio Browser down). */
    public static List<Track> featured() {
        List<Track> out = new ArrayList<>();
        for (String[] s : MY_STATIONS) {
            Track t = new Track("radio:" + s[2], s[0], s[1], s[3]);
            t.badge = "LIVE";
            out.add(t);
        }
        return out;
    }

    private static List<Track> fetch(HttpUrl url) throws Exception {
        Request req = new Request.Builder().url(url).header("User-Agent", "MyChord/1.0").build();
        List<Track> out = new ArrayList<>();
        try (Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) return out;
            JSONArray a = new JSONArray(r.body().string());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String u = o.optString("url_resolved", o.optString("url"));
                if (u.isEmpty()) continue;
                String tags = o.optString("tags", "");
                if (tags.length() > 40) tags = tags.substring(0, 40);
                Track t = new Track("radio:" + u, o.optString("name").trim(),
                        tags.isEmpty() ? o.optString("country") : tags, o.optString("favicon"));
                int br = o.optInt("bitrate", 0);
                t.badge = (o.optString("codec", "LIVE") + (br > 0 ? " " + br : "")).toUpperCase();
                out.add(t);
            }
        }
        return out;
    }

    public static List<Track> malaysia() throws Exception {
        // Mula dengan stesen popular, kemudian tambah dari Radio Browser
        List<Track> out = featured();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Track t : out) names.add(t.title.toLowerCase());
        try {
            List<Track> rb = fetch(HttpUrl.parse(BASE + "bycountrycodeexact/MY").newBuilder()
                    .addQueryParameter("order", "clickcount")
                    .addQueryParameter("reverse", "true")
                    .addQueryParameter("hidebroken", "true")
                    .addQueryParameter("limit", "80").build());
            // Tambah stesen Radio Browser yang belum ada
            for (Track t : rb) {
                if (!names.contains(t.title.toLowerCase().trim())) {
                    out.add(t);
                    names.add(t.title.toLowerCase().trim());
                }
            }
        } catch (Exception ignored) { /* Radio Browser down — featured cukup */ }
        return out;
    }

    public static List<Track> search(String name) throws Exception {
        return fetch(HttpUrl.parse(BASE + "search").newBuilder()
                .addQueryParameter("name", name)
                .addQueryParameter("order", "clickcount")
                .addQueryParameter("reverse", "true")
                .addQueryParameter("hidebroken", "true")
                .addQueryParameter("limit", "60").build());
    }
}
