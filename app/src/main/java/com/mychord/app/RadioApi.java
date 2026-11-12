package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Addon radio: stesen dari Radio Browser (percuma, sumber terbuka). */
public class RadioApi {
    private static final String BASE = "https://de1.api.radio-browser.info/json/stations/";

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
        return fetch(HttpUrl.parse(BASE + "bycountrycodeexact/MY").newBuilder()
                .addQueryParameter("order", "clickcount")
                .addQueryParameter("reverse", "true")
                .addQueryParameter("hidebroken", "true")
                .addQueryParameter("limit", "80").build());
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
