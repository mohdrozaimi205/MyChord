package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Addon tambahan sendiri. Boleh tambah dengan tampal:
 *  - pautan fail addon .json (format MyChord)
 *  - pautan playlist YouTube / YouTube Music
 *  - pautan playlist/album Spotify
 *  - pautan strim radio (mp3/aac/m3u8)
 */
public class CustomAddons {

    public static List<JSONObject> all() {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.getS("customAddons", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (Exception ignored) { }
        return out;
    }

    public static String signature() {
        StringBuilder sb = new StringBuilder();
        for (JSONObject o : all()) sb.append(o.optString("id")).append(',');
        return sb.toString();
    }

    private static void save(List<JSONObject> list) {
        JSONArray a = new JSONArray();
        for (JSONObject o : list) a.put(o);
        Prefs.putS("customAddons", a.toString());
    }

    private static void put(JSONObject addon) {
        List<JSONObject> l = all();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).optString("id").equals(addon.optString("id"))) l.remove(i);
        l.add(addon);
        save(l);
    }

    public static void remove(String id) {
        List<JSONObject> l = all();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).optString("id").equals(id)) l.remove(i);
        save(l);
    }

    private static JSONObject make(String name, String desc, String type, JSONArray items, String source) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", "c" + Math.abs((source == null ? name : source).hashCode()));
        o.put("name", name);
        o.put("desc", desc);
        o.put("type", type);
        o.put("items", items);
        o.put("source", source);
        return o;
    }

    private static JSONObject item(String title, String artist, String art, String key, String value) throws Exception {
        JSONObject i = new JSONObject();
        i.put("title", title);
        i.put("artist", artist);
        i.put("art", art);
        i.put(key, value);
        return i;
    }

    /** Tambah addon dari teks yang ditampal. Pulangkan nama addon. Panggil dari thread latar. */
    public static String add(String input) throws Exception {
        String s = input.trim();
        if (s.isEmpty()) throw new Exception("kosong");

        // Playlist YouTube / YouTube Music
        Matcher m = Pattern.compile("[?&]list=([A-Za-z0-9_-]+)").matcher(s);
        if (s.contains("youtube.com") || s.contains("youtu.be")) {
            if (!m.find()) throw new Exception("Bukan pautan playlist");
            String url = "https://www.youtube.com/playlist?list=" + m.group(1);
            PlaylistInfo info = PlaylistInfo.getInfo(ServiceList.YouTube, url);
            JSONArray items = new JSONArray();
            for (InfoItem it : info.getRelatedItems()) {
                if (!(it instanceof StreamInfoItem)) continue;
                StreamInfoItem si = (StreamInfoItem) it;
                String id = YtMusic.idFromUrl(si.getUrl());
                if (id != null) items.put(item(si.getName(), si.getUploaderName(), YtMusic.bestThumb(si.getThumbnails()), "youtube", id));
            }
            JSONObject a = make(info.getName(), "YouTube • " + items.length(), "playlist", items, url);
            put(a);
            return info.getName();
        }

        // Spotify
        if (SpotifyImport.looksLikeLink(s)) {
            SpotifyImport.Result r = SpotifyImport.fromLink(s);
            JSONArray items = new JSONArray();
            for (Track t : r.tracks) items.put(item(t.title, t.artist, t.art, "query", t.mediaId.substring(2)));
            JSONObject a = make(r.name, "Spotify • " + items.length(), "playlist", items, s);
            put(a);
            return r.name;
        }

        if (!s.startsWith("http")) throw new Exception("Bukan pautan");

        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.(mp3|aac|m3u8|ogg|opus|pls|m3u)(\\?.*)?$")) return addRadio(s, null);

        Request req = new Request.Builder().url(s).header("User-Agent", "MyChord/1.0").build();
        try (Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new Exception("HTTP " + r.code());
            String ct = r.header("Content-Type", "");
            if (ct != null && (ct.startsWith("audio/") || ct.contains("mpegurl"))) {
                r.close();
                return addRadio(s, null);
            }
            String body = r.body().string();
            JSONObject root = new JSONObject(body.trim());
            if (root.has("addons")) {
                // Katalog: pasang addon terbina (lirik, radio...) & addon berkandungan
                JSONArray arr = root.getJSONArray("addons");
                String first = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ad = arr.getJSONObject(i);
                    if (ad.has("items")) {
                        String n = addManifest(ad, s + "#" + i);
                        if (first == null) first = n;
                    } else if (ad.has("id")) {
                        AddonManager.setInstalled(ad.getString("id"), true);
                        if (first == null) first = ad.optString("name", ad.getString("id"));
                    }
                }
                if (first == null) throw new Exception("Katalog kosong");
                return first;
            }
            return addManifest(root, s);
        }
    }

    private static String addRadio(String url, String name) throws Exception {
        String n = name != null ? name : android.net.Uri.parse(url).getHost();
        JSONArray items = new JSONArray();
        items.put(item(n, "Radio", "", "stream", url));
        put(make("Radio: " + n, "Radio • LIVE", "radio", items, url));
        return n;
    }

    private static String addManifest(JSONObject ad, String source) throws Exception {
        String name = ad.optString("name", "Addon");
        JSONArray items = ad.optJSONArray("items");
        if (items == null) throw new Exception("Tiada kandungan");
        JSONObject a = make(name, ad.optString("desc", ad.optString("type", "playlist") + " • " + items.length()),
                ad.optString("type", "playlist"), items, source);
        put(a);
        return name;
    }

    public static List<Track> tracks(JSONObject addon) {
        List<Track> out = new ArrayList<>();
        JSONArray items = addon.optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            String title = it.optString("title", it.optString("name"));
            String artist = it.optString("artist");
            String art = it.optString("art", it.optString("image"));
            Track t = null;
            String yt = it.optString("youtube", "");
            String url = it.optString("url", "");
            if (!yt.isEmpty()) {
                String id = yt.contains("v=") ? YtMusic.idFromUrl(yt) : yt.replaceAll(".*youtu\\.be/", "");
                t = new Track("yt:" + id, title, artist, art);
            } else if (url.contains("youtube.com/watch") || url.contains("youtu.be/")) {
                String id = url.contains("v=") ? YtMusic.idFromUrl(url) : url.replaceAll(".*youtu\\.be/", "");
                t = new Track("yt:" + id, title, artist, art);
            } else if (!it.optString("query", "").isEmpty()) {
                t = new Track("q:" + it.optString("query"), title.isEmpty() ? it.optString("query") : title, artist, art);
            } else if (!it.optString("stream", "").isEmpty() || !url.isEmpty()) {
                String st = it.optString("stream", url);
                t = new Track("radio:" + st, title, artist, art);
                t.badge = "LIVE";
            } else if (!title.isEmpty()) {
                t = new Track("q:" + (artist.isEmpty() ? "" : artist + " - ") + title, title, artist, art);
            }
            if (t != null) out.add(t);
        }
        return out;
    }
}
