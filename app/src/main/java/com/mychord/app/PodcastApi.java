package com.mychord.app;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Podcast dari direktori Apple Podcasts (percuma, tanpa akaun). Episod dimainkan terus dari suapan RSS. */
public class PodcastApi {

    private static String get(String url) throws Exception {
        Request req = new Request.Builder().url(url).header("User-Agent", OkDownloader.UA).build();
        try (Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new Exception("HTTP " + r.code());
            return r.body().string();
        }
    }

    private static String big(String art) {
        return art == null ? "" : art.replaceAll("\\d+x\\d+bb", "600x600bb");
    }

    /** Carta podcast teratas bagi negara (my, us, gb, id...) */
    public static List<Track> top(String country, String badge) throws Exception {
        JSONObject o = new JSONObject(get("https://rss.applemarketingtools.com/api/v2/" + country + "/podcasts/top/50/podcasts.json"));
        JSONArray res = o.getJSONObject("feed").optJSONArray("results");
        List<Track> out = new ArrayList<>();
        if (res != null) for (int i = 0; i < res.length(); i++) {
            JSONObject p = res.getJSONObject(i);
            Track t = new Track("pod:" + p.optString("id"), p.optString("name"), p.optString("artistName"), big(p.optString("artworkUrl100")));
            t.badge = badge;
            out.add(t);
        }
        return out;
    }

    public static List<Track> search(String term) throws Exception {
        HttpUrl url = HttpUrl.parse("https://itunes.apple.com/search").newBuilder()
                .addQueryParameter("media", "podcast").addQueryParameter("term", term)
                .addQueryParameter("country", "MY").addQueryParameter("limit", "50").build();
        JSONArray res = new JSONObject(get(url.toString())).optJSONArray("results");
        List<Track> out = new ArrayList<>();
        if (res != null) for (int i = 0; i < res.length(); i++) {
            JSONObject p = res.getJSONObject(i);
            Track t = new Track("pod:" + p.optLong("collectionId"), p.optString("collectionName"), p.optString("artistName"),
                    big(p.optString("artworkUrl600", p.optString("artworkUrl100"))));
            t.badge = "PODCAST";
            out.add(t);
        }
        return out;
    }

    /** Episod terkini (maks 100) */
    public static List<Track> episodes(String id) throws Exception {
        JSONArray res = new JSONObject(get("https://itunes.apple.com/lookup?id=" + id)).optJSONArray("results");
        if (res == null || res.length() == 0) throw new Exception("Tiada podcast");
        JSONObject show = res.getJSONObject(0);
        String feed = show.optString("feedUrl");
        String showName = show.optString("collectionName");
        String showArt = big(show.optString("artworkUrl600", show.optString("artworkUrl100")));
        if (feed.isEmpty()) throw new Exception("Tiada suapan");

        List<Track> out = new ArrayList<>();
        XmlPullParser x = Xml.newPullParser();
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        x.setInput(new StringReader(get(feed)));
        boolean inItem = false;
        String title = "", audio = "", img = "", dur = "";
        int ev = x.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT && out.size() < 100) {
            if (ev == XmlPullParser.START_TAG) {
                String n = x.getName();
                if ("item".equals(n)) {
                    inItem = true;
                    title = ""; audio = ""; img = ""; dur = "";
                } else if (inItem) {
                    if ("title".equals(n)) title = x.nextText().trim();
                    else if ("enclosure".equals(n)) audio = x.getAttributeValue(null, "url");
                    else if ("itunes:image".equals(n)) img = x.getAttributeValue(null, "href");
                    else if ("itunes:duration".equals(n)) dur = x.nextText().trim();
                }
            } else if (ev == XmlPullParser.END_TAG && "item".equals(x.getName())) {
                inItem = false;
                if (audio != null && !audio.isEmpty()) {
                    Track t = new Track("url:" + audio, title, showName, img == null || img.isEmpty() ? showArt : img);
                    t.durationSec = parseDur(dur);
                    t.badge = "PODCAST";
                    out.add(t);
                }
            }
            ev = x.next();
        }
        return out;
    }

    private static long parseDur(String d) {
        if (d == null || d.isEmpty()) return 0;
        try {
            String[] p = d.split(":");
            long s = 0;
            for (String part : p) s = s * 60 + Long.parseLong(part.trim());
            return s;
        } catch (Exception e) {
            return 0;
        }
    }
}
