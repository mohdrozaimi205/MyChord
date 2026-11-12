package com.mychord.app;

import android.net.Uri;

import androidx.media3.datasource.DataSpec;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tukar alamat dalaman mychord:// kepada pautan strim sebenar (semasa lagu dimuatkan). */
public class Resolver {
    private static class Entry {
        final String url;
        final long time;
        Entry(String url, long time) { this.url = url; this.time = time; }
    }

    private static final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private static final Map<String, String> queryToId = new ConcurrentHashMap<>();
    /** Label kualiti untuk paparan (contoh "OPUS • 160 kbps"), ikut mediaId */
    public static final Map<String, String> INFO = new ConcurrentHashMap<>();

    public static void clear() { cache.clear(); }

    public static DataSpec resolve(DataSpec spec) throws IOException {
        Uri u = spec.uri;
        if (!"mychord".equals(u.getScheme())) return spec;
        try {
            String id;
            String key;
            if ("q".equals(u.getAuthority())) {
                String s = u.getQueryParameter("s");
                key = "q:" + s;
                id = queryToId.get(s);
                if (id == null) {
                    List<Track> r = YtMusic.search(s);
                    if (r.isEmpty()) throw new IOException("Tiada padanan: " + s);
                    id = r.get(0).mediaId.substring(3);
                    queryToId.put(s, id);
                }
            } else {
                id = u.getLastPathSegment();
                key = "yt:" + id;
            }
            int q = Prefs.currentQuality();
            String ck = id + "#" + q;
            Entry e = cache.get(ck);
            long now = System.currentTimeMillis();
            if (e == null || now - e.time > 3L * 3600_000L) {
                YtMusic.Resolved r = YtMusic.resolve(id, q);
                e = new Entry(r.url, now);
                cache.put(ck, e);
                INFO.put(key, r.label);
                INFO.put("yt:" + id, r.label);
            }
            return spec.withUri(Uri.parse(e.url));
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }
}
