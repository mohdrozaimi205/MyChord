package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Addon lirik: lirik segerak dari LRCLIB (percuma). */
public class Lrclib {
    public static class Line {
        public long ms;
        public String text;
        Line(long ms, String text) { this.ms = ms; this.text = text; }
    }

    public static class Result {
        public boolean synced;
        public List<Line> lines = new ArrayList<>();
    }

    public static String clean(String title) {
        if (title == null) return "";
        String t = title.replaceAll("\\(.*?\\)|\\[.*?]", " ");
        t = t.replaceAll("(?i)\\b(official|music video|lyric video|lyrics|audio|mv|hd|4k)\\b", " ");
        t = t.replaceAll("(?i)\\s(ft\\.?|feat\\.?)\\s.*$", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static JSONArray query(HttpUrl url) throws Exception {
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "MyChord/1.0 (https://github.com/mohdrozaimi205/MyChord)").build();
        try (Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) return new JSONArray();
            return new JSONArray(r.body().string());
        }
    }

    public static Result fetch(String title, String artist) throws Exception {
        String t = clean(title);
        String a = artist == null ? "" : artist.replace(" - Topic", "").trim();
        HttpUrl url = HttpUrl.parse("https://lrclib.net/api/search").newBuilder()
                .addQueryParameter("track_name", t)
                .addQueryParameter("artist_name", a).build();
        JSONArray arr = query(url);
        if (arr.length() == 0) {
            arr = query(HttpUrl.parse("https://lrclib.net/api/search").newBuilder()
                    .addQueryParameter("q", t + " " + a).build());
        }
        Result res = new Result();
        JSONObject pick = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (!o.optString("syncedLyrics", "").isEmpty() && !"null".equals(o.optString("syncedLyrics"))) { pick = o; break; }
            if (pick == null && !o.optString("plainLyrics", "").isEmpty()) pick = o;
        }
        if (pick == null) return res;
        String synced = pick.optString("syncedLyrics", "");
        if (!synced.isEmpty() && !"null".equals(synced)) {
            res.synced = true;
            Pattern p = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)");
            for (String line : synced.split("\n")) {
                Matcher m = p.matcher(line.trim());
                if (!m.matches()) continue;
                long ms = Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000;
                if (m.group(3) != null) {
                    String f = m.group(3);
                    ms += f.length() == 2 ? Long.parseLong(f) * 10 : Long.parseLong(f.substring(0, Math.min(3, f.length())));
                }
                res.lines.add(new Line(ms, m.group(4).isEmpty() ? "♪" : m.group(4)));
            }
        } else {
            for (String line : pick.optString("plainLyrics", "").split("\n")) res.lines.add(new Line(-1, line));
        }
        return res;
    }
}
