package com.mychord.app;

import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

/** Akaun Spotify: lagu disukai & playlist (lagu dipadankan dengan YouTube Music semasa dimainkan). */
public class SpotifyAccount {
    private static String token = null;
    private static long tokenTime = 0;

    public static boolean loggedIn() { return Prefs.getB("spLogged", false); }
    public static String name() { return Prefs.getS("spName", ""); }

    public static void setToken(String t) {
        token = t;
        tokenTime = System.currentTimeMillis();
    }

    public static void logout() {
        Prefs.putB("spLogged", false);
        Prefs.putS("spName", "");
        token = null;
        WebCookies.clear("https://open.spotify.com", "https://accounts.spotify.com", "https://www.spotify.com",
                "https://spotify.com");
    }

    /** Semak token & simpan nama akaun. Panggil dari thread latar. */
    public static boolean validate(String t) {
        try {
            JSONObject me = get("https://api.spotify.com/v1/me", t);
            setToken(t);
            Prefs.putB("spLogged", true);
            String n = me.optString("display_name", me.optString("id"));
            Prefs.putS("spName", n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Dapatkan token baharu secara senyap (Web Player dalam WebView tersembunyi). */
    static synchronized String token() throws Exception {
        if (token != null && System.currentTimeMillis() - tokenTime < 45 * 60_000L) return token;
        CountDownLatch latch = new CountDownLatch(1);
        final String[] got = new String[1];
        final WebView[] holder = new WebView[1];
        App.MAIN.post(() -> {
            holder[0] = SpotifyGrabber.headless(App.get(), t -> {
                if (got[0] == null) {
                    got[0] = t;
                    latch.countDown();
                }
            });
            holder[0].loadUrl("https://open.spotify.com/");
        });
        latch.await(30, TimeUnit.SECONDS);
        App.MAIN.post(() -> {
            if (holder[0] != null) {
                holder[0].stopLoading();
                holder[0].destroy();
            }
        });
        if (got[0] == null) throw new Exception("Sesi Spotify tamat — sila log masuk semula");
        setToken(got[0]);
        return got[0];
    }

    private static JSONObject get(String url, String t) throws Exception {
        Request req = new Request.Builder().url(url).header("Authorization", "Bearer " + t).build();
        try (Response r = App.http.newCall(req).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            if (r.code() == 401) throw new YtmAccount.AuthException("401");
            if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
            return new JSONObject(s);
        }
    }

    private static JSONObject api(String url) throws Exception {
        try {
            return get(url, token());
        } catch (YtmAccount.AuthException e) {
            token = null;
            return get(url, token());
        }
    }

    private static Track toTrack(JSONObject t) {
        if (t == null || t.optString("name").isEmpty()) return null;
        if (!"track".equals(t.optString("type", "track"))) return null;
        StringBuilder artists = new StringBuilder();
        String first = "";
        JSONArray ar = t.optJSONArray("artists");
        if (ar != null) for (int i = 0; i < ar.length(); i++) {
            String n = ar.optJSONObject(i).optString("name");
            if (i == 0) first = n;
            if (artists.length() > 0) artists.append(", ");
            artists.append(n);
        }
        String art = "";
        JSONObject album = t.optJSONObject("album");
        if (album != null) {
            JSONArray imgs = album.optJSONArray("images");
            if (imgs != null && imgs.length() > 0) art = imgs.optJSONObject(0).optString("url");
        }
        String name = t.optString("name");
        Track tr = new Track("q:" + (first.isEmpty() ? "" : first + " - ") + name, name, artists.toString(), art);
        tr.durationSec = t.optLong("duration_ms") / 1000;
        tr.badge = "SPOTIFY";
        return tr;
    }

    public static List<Track> liked() throws Exception {
        List<Track> out = new ArrayList<>();
        String url = "https://api.spotify.com/v1/me/tracks?limit=50";
        while (url != null && out.size() < 1500) {
            JSONObject o = api(url);
            JSONArray items = o.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                Track t = toTrack(items.optJSONObject(i).optJSONObject("track"));
                if (t != null) out.add(t);
            }
            url = o.isNull("next") ? null : o.optString("next", null);
        }
        return out;
    }

    public static List<Track> playlists() throws Exception {
        List<Track> out = new ArrayList<>();
        String url = "https://api.spotify.com/v1/me/playlists?limit=50";
        while (url != null && out.size() < 500) {
            JSONObject o = api(url);
            JSONArray items = o.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject p = items.optJSONObject(i);
                if (p == null) continue;
                String art = "";
                JSONArray imgs = p.optJSONArray("images");
                if (imgs != null && imgs.length() > 0) art = imgs.optJSONObject(0).optString("url");
                JSONObject owner = p.optJSONObject("owner");
                JSONObject tracks = p.optJSONObject("tracks");
                String sub = (owner != null ? owner.optString("display_name") : "")
                        + (tracks != null ? " • " + tracks.optInt("total") : "");
                Track t = new Track("sppl:" + p.optString("id"), p.optString("name"), sub, art);
                t.badge = "PLAYLIST";
                out.add(t);
            }
            url = o.isNull("next") ? null : o.optString("next", null);
        }
        return out;
    }

    public static List<Track> playlist(String id) throws Exception {
        List<Track> out = new ArrayList<>();
        String url = "https://api.spotify.com/v1/playlists/" + id + "/tracks?limit=100";
        while (url != null && out.size() < 2000) {
            JSONObject o = api(url);
            JSONArray items = o.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                Track t = toTrack(items.optJSONObject(i).optJSONObject("track"));
                if (t != null) out.add(t);
            }
            url = o.isNull("next") ? null : o.optString("next", null);
        }
        return out;
    }
}
