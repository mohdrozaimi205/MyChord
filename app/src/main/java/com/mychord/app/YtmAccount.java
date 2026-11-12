package com.mychord.app;

import android.webkit.CookieManager;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Akaun YouTube Music: playlist, lagu disukai & cadangan (melalui InnerTube). */
public class YtmAccount {
    static final String ORIGIN = "https://music.youtube.com";
    static final String VER = "1.20250310.01.00";

    public static class AuthException extends Exception {
        AuthException(String m) { super(m); }
    }

    public static boolean loggedIn() { return !cookie().isEmpty(); }
    public static String cookie() { return Prefs.getS("ytmCookie", ""); }
    public static String name() { return Prefs.getS("ytmName", ""); }

    public static void save(String cookie) { Prefs.putS("ytmCookie", cookie); }

    public static void logout() {
        Prefs.putS("ytmCookie", "");
        Prefs.putS("ytmName", "");
        WebCookies.clear("https://music.youtube.com", "https://www.youtube.com", "https://youtube.com",
                "https://accounts.google.com", "https://www.google.com", "https://google.com");
    }

    private static String cookieValue(String name) {
        for (String part : cookie().split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return null;
    }

    private static String auth() throws Exception {
        String sapisid = cookieValue("SAPISID");
        if (sapisid == null) sapisid = cookieValue("__Secure-3PAPISID");
        if (sapisid == null) throw new AuthException("Tiada SAPISID");
        long ts = System.currentTimeMillis() / 1000;
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] d = md.digest((ts + " " + sapisid + " " + ORIGIN).getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
        return "SAPISIDHASH " + ts + "_" + sb;
    }

    private static JSONObject context() throws Exception {
        JSONObject client = new JSONObject();
        client.put("clientName", "WEB_REMIX");
        client.put("clientVersion", VER);
        client.put("hl", "en".equals(Prefs.lang()) ? "en" : "ms");
        client.put("gl", "MY");
        JSONObject ctx = new JSONObject();
        ctx.put("client", client);
        return ctx;
    }

    public static JSONObject post(String endpoint, JSONObject body) throws Exception {
        body.put("context", context());
        Request req = new Request.Builder()
                .url(ORIGIN + "/youtubei/v1/" + endpoint + "?prettyPrint=false")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .header("Cookie", cookie())
                .header("Authorization", auth())
                .header("Origin", ORIGIN)
                .header("X-Origin", ORIGIN)
                .header("Referer", ORIGIN + "/")
                .header("X-Goog-AuthUser", "0")
                .header("X-Youtube-Client-Name", "67")
                .header("X-Youtube-Client-Version", VER)
                .header("User-Agent", OkDownloader.UA)
                .build();
        try (Response r = App.http.newCall(req).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            if (r.code() == 401 || r.code() == 403) throw new AuthException("HTTP " + r.code());
            if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
            return new JSONObject(s);
        }
    }

    public static JSONObject browse(String browseId) throws Exception {
        JSONObject b = new JSONObject();
        b.put("browseId", browseId);
        return post("browse", b);
    }

    /** Senarai lagu & playlist dari halaman browse */
    public static List<Track> parse(JSONObject root, boolean wantSongs, boolean wantLists) {
        List<Track> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<JSONObject> rows = new ArrayList<>();
        Json.collect(root, "musicResponsiveListItemRenderer", rows);
        for (JSONObject o : rows) {
            String vid = Json.str(o, "playlistItemData", "videoId");
            if (vid == null) {
                Object w = Json.find(Json.path(o, "overlay"), "videoId");
                if (w instanceof String) vid = (String) w;
            }
            String title = Json.runs(Json.path(o, "flexColumns", "0", "musicResponsiveListItemFlexColumnRenderer", "text"));
            String sub = Json.firstPart(Json.path(o, "flexColumns", "1", "musicResponsiveListItemFlexColumnRenderer", "text"));
            if (vid != null) {
                if (wantSongs && seen.add("yt:" + vid)) out.add(new Track("yt:" + vid, title, sub, Json.thumb(Json.path(o, "thumbnail"))));
            } else if (wantLists) {
                String bid = Json.str(o, "navigationEndpoint", "browseEndpoint", "browseId");
                if (bid != null && (bid.startsWith("VL") || bid.startsWith("MPRE")) && seen.add("ytpl:" + bid)) {
                    Track t = new Track("ytpl:" + bid, title, Json.runs(Json.path(o, "flexColumns", "1", "musicResponsiveListItemFlexColumnRenderer", "text")), Json.thumb(Json.path(o, "thumbnail")));
                    t.badge = "PLAYLIST";
                    out.add(t);
                }
            }
        }
        List<JSONObject> cards = new ArrayList<>();
        Json.collect(root, "musicTwoRowItemRenderer", cards);
        for (JSONObject o : cards) {
            String title = Json.runs(o.opt("title"));
            String sub = Json.runs(o.opt("subtitle"));
            String vid = Json.str(o, "navigationEndpoint", "watchEndpoint", "videoId");
            String bid = Json.str(o, "navigationEndpoint", "browseEndpoint", "browseId");
            String art = Json.thumb(Json.path(o, "thumbnailRenderer"));
            if (vid != null) {
                if (wantSongs && seen.add("yt:" + vid)) {
                    int i = sub.indexOf(" • ");
                    out.add(new Track("yt:" + vid, title, i > 0 ? sub.substring(i + 3) : sub, art));
                }
            } else if (wantLists && bid != null && (bid.startsWith("VL") || bid.startsWith("MPRE")) && seen.add("ytpl:" + bid)) {
                Track t = new Track("ytpl:" + bid, title, sub, art);
                t.badge = bid.startsWith("MPRE") ? "ALBUM" : "PLAYLIST";
                out.add(t);
            }
        }
        return out;
    }

    public static List<Track> liked() throws Exception { return parse(browse("VLLM"), true, false); }

    public static List<Track> playlists() throws Exception {
        List<Track> l = parse(browse("FEmusic_liked_playlists"), false, true);
        // buang "Lagu disukai" (sudah ada menu sendiri)
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).mediaId.equals("ytpl:VLLM")) l.remove(i);
        return l;
    }

    public static List<Track> home() throws Exception { return parse(browse("FEmusic_home"), true, true); }

    public static List<Track> playlist(String browseId) throws Exception { return parse(browse(browseId), true, false); }

    public static String fetchName() {
        try {
            JSONObject r = post("account/account_menu", new JSONObject());
            Object n = Json.find(r, "accountName");
            String name = Json.runs(n);
            if (!name.isEmpty()) Prefs.putS("ytmName", name);
            return name;
        } catch (Exception e) {
            return "";
        }
    }

    /** Segerak butang ♥ dengan akaun YouTube Music */
    public static void syncLike(Track t, boolean liked) {
        if (!loggedIn() || t == null || t.mediaId == null || !t.mediaId.startsWith("yt:")) return;
        String id = t.mediaId.substring(3);
        App.IO.execute(() -> {
            try {
                JSONObject b = new JSONObject();
                JSONObject target = new JSONObject();
                target.put("videoId", id);
                b.put("target", target);
                post(liked ? "like/like" : "like/removelike", b);
            } catch (Exception ignored) { }
        });
    }
}
