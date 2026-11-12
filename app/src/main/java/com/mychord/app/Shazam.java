package com.mychord.app;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Hantar cap jari (bukan rakaman) ke Shazam dan dapatkan nama lagu. Berasaskan Opentune (GPL-3.0). */
public class Shazam {
    public static class Song {
        public String title = "", artist = "", album = "", cover = "";
    }

    private static final String BASE = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag";

    public static Song lookup(byte[] sig, long durationMs) throws Exception {
        String uri = "data:audio/vnd.shazam.sig;base64," + Base64.encodeToString(sig, Base64.NO_WRAP);
        JSONObject body = new JSONObject();
        body.put("timezone", TimeZone.getDefault().getID());
        body.put("signature", new JSONObject().put("uri", uri).put("samplems", durationMs));
        body.put("timestamp", System.currentTimeMillis());
        body.put("context", new JSONObject());
        body.put("geolocation", new JSONObject());
        String url = BASE + "/" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + "/"
                + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)
                + "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1&hidelb=true&video=v3";
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UQ1A.240205.004)")
                .header("Content-Language", "en_US")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();
        try (Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new Exception("Shazam " + r.code());
            JSONObject root = new JSONObject(r.body().string());
            JSONObject track = root.optJSONObject("track");
            if (track == null || track.optString("title").isEmpty()) return null;
            Song s = new Song();
            s.title = track.optString("title");
            s.artist = track.optString("subtitle");
            JSONObject images = track.optJSONObject("images");
            if (images != null) s.cover = images.optString("coverarthq", images.optString("coverart", ""));
            JSONArray sections = track.optJSONArray("sections");
            if (sections != null) {
                for (int i = 0; i < sections.length(); i++) {
                    JSONArray meta = sections.optJSONObject(i) != null ? sections.optJSONObject(i).optJSONArray("metadata") : null;
                    if (meta == null) continue;
                    for (int k = 0; k < meta.length(); k++) {
                        JSONObject m = meta.optJSONObject(k);
                        if (m != null && "Album".equalsIgnoreCase(m.optString("title"))) s.album = m.optString("text");
                    }
                }
            }
            return s;
        }
    }
}
