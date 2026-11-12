package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Import senarai main / album / lagu Spotify (awam) dari halaman "embed".
 * Setiap lagu disimpan sebagai carian "q:Artis - Lagu" dan dipadankan dengan
 * YouTube Music semasa dimainkan.
 */
public class SpotifyImport {
    public static class Result {
        public String name = "Spotify";
        public List<Track> tracks = new ArrayList<>();
    }

    private static final Pattern LINK = Pattern.compile(
            "spotify\\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?(playlist|album|track)/([A-Za-z0-9]+)");
    private static final Pattern URI = Pattern.compile("spotify:(playlist|album|track):([A-Za-z0-9]+)");

    public static boolean looksLikeLink(String s) {
        return s != null && (LINK.matcher(s).find() || URI.matcher(s).find());
    }

    public static Result fromLink(String link) throws Exception {
        Matcher m = LINK.matcher(link);
        if (!m.find()) {
            m = URI.matcher(link);
            if (!m.find()) throw new Exception("Pautan tidak sah");
        }
        String type = m.group(1), id = m.group(2);
        Request req = new Request.Builder()
                .url("https://open.spotify.com/embed/" + type + "/" + id)
                .header("User-Agent", OkDownloader.UA)
                .header("Accept-Language", "en")
                .build();
        String html;
        try (Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new Exception("HTTP " + r.code());
            html = r.body().string();
        }
        Matcher nd = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
        if (!nd.find()) throw new Exception("Format halaman berubah");
        JSONObject root = new JSONObject(nd.group(1));
        JSONObject entity = findEntity(root);
        if (entity == null) throw new Exception("Tiada data");

        Result res = new Result();
        res.name = entity.optString("name", entity.optString("title", "Spotify"));
        String cover = coverOf(entity);
        JSONArray list = entity.optJSONArray("trackList");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject t = list.getJSONObject(i);
                String title = t.optString("title");
                String artist = t.optString("subtitle").replace(" ", " ");
                if (title.isEmpty()) continue;
                res.tracks.add(make(title, artist, "album".equals(type) ? cover : ""));
            }
        } else {
            String artist = "";
            JSONArray artists = entity.optJSONArray("artists");
            if (artists != null && artists.length() > 0) artist = artists.getJSONObject(0).optString("name");
            res.tracks.add(make(res.name, artist, cover));
        }
        return res;
    }

    private static Track make(String title, String artist, String art) {
        String first = artist.split(",")[0].trim();
        Track t = new Track("q:" + (first.isEmpty() ? "" : first + " - ") + title, title, artist, art);
        t.badge = "SPOTIFY";
        return t;
    }

    private static String coverOf(JSONObject e) {
        try {
            JSONObject c = e.optJSONObject("coverArt");
            if (c != null) {
                JSONArray s = c.optJSONArray("sources");
                if (s != null && s.length() > 0) return s.getJSONObject(s.length() - 1).optString("url");
            }
            JSONArray imgs = e.optJSONArray("images");
            if (imgs != null && imgs.length() > 0) return imgs.getJSONObject(0).optString("url");
        } catch (Exception ignored) { }
        return "";
    }

    /** Cari objek "entity" (atau mana-mana objek yang ada "trackList") secara rekursif. */
    private static JSONObject findEntity(Object node) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONObject e = o.optJSONObject("entity");
            if (e != null) return e;
            if (o.has("trackList")) return o;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                JSONObject r = findEntity(o.opt(it.next()));
                if (r != null) return r;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                JSONObject r = findEntity(a.opt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Import senarai teks: satu baris "Artis - Lagu" */
    public static List<Track> fromText(String text) {
        List<Track> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String l = line.trim().replaceFirst("^\\d+[.)]\\s*", "");
            if (l.isEmpty()) continue;
            String artist = "", title = l;
            int dash = l.indexOf(" - ");
            if (dash > 0) {
                artist = l.substring(0, dash).trim();
                title = l.substring(dash + 3).trim();
            }
            Track t = new Track("q:" + l, title, artist, "");
            t.badge = "IMPORT";
            out.add(t);
        }
        return out;
    }
}
