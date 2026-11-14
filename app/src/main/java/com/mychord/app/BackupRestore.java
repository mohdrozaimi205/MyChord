package com.mychord.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Sandaran & Pulih — eksport dan import semua data MyChord ke fail JSON.
 * Termasuk: kegemaran, sejarah, senarai main, tetapan, addon.
 */
public class BackupRestore {

    /** Cipta sandaran penuh sebagai JSON. */
    public static JSONObject createBackup(Context ctx) {
        JSONObject backup = new JSONObject();
        try {
            backup.put("app", "MyChord");
            backup.put("version", getVersionName(ctx));
            backup.put("timestamp", System.currentTimeMillis());

            // Kegemaran
            JSONArray liked = new JSONArray();
            for (Track t : Store.liked()) liked.put(t.toJson());
            backup.put("liked", liked);

            // Sejarah
            JSONArray history = new JSONArray();
            for (Track t : Store.history()) history.put(t.toJson());
            backup.put("history", history);

            // Senarai main
            JSONArray playlists = new JSONArray();
            for (Store.Playlist p : Store.playlists()) {
                JSONObject po = new JSONObject();
                po.put("id", p.id);
                po.put("name", p.name);
                JSONArray tracks = new JSONArray();
                for (Track t : p.tracks) tracks.put(t.toJson());
                po.put("tracks", tracks);
                playlists.put(po);
            }
            backup.put("playlists", playlists);

            // Tetapan
            JSONObject prefs = new JSONObject();
            SharedPreferences sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
            Map<String, ?> all = sp.getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                // Langkau token akaun (keselamatan)
                String k = e.getKey();
                if (k.contains("token") || k.contains("cookie") || k.contains("session")) continue;
                Object v = e.getValue();
                if (v instanceof String) prefs.put(k, v);
                else if (v instanceof Integer) prefs.put(k, (int) v);
                else if (v instanceof Boolean) prefs.put(k, (boolean) v);
            }
            backup.put("prefs", prefs);

            // Addon tersuai
            JSONArray addons = new JSONArray();
            for (JSONObject a : CustomAddons.all()) addons.put(a);
            backup.put("addons", addons);

        } catch (Exception e) {
            android.util.Log.e("Backup", "createBackup", e);
        }
        return backup;
    }

    /** Tulis sandaran ke OutputStream (SAF). */
    public static void writeTo(Context ctx, Uri uri) throws Exception {
        JSONObject data = createBackup(ctx);
        try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new Exception("Cannot open output");
            os.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Pulihkan dari InputStream (SAF). Kembalikan ringkasan. */
    public static String restoreFrom(Context ctx, Uri uri) throws Exception {
        String json;
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("Cannot open input");
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            json = new String(bytes, StandardCharsets.UTF_8);
        }
        JSONObject data = new JSONObject(json);

        int likedCount = 0, historyCount = 0, playlistCount = 0;

        // Pulih kegemaran
        if (data.has("liked")) {
            JSONArray a = data.getJSONArray("liked");
            for (int i = a.length() - 1; i >= 0; i--) {
                Track t = Track.fromJson(a.getJSONObject(i));
                if (!Store.isLiked(t.mediaId)) Store.toggleLike(t);
                likedCount++;
            }
        }

        // Pulih sejarah
        if (data.has("history")) {
            JSONArray a = data.getJSONArray("history");
            for (int i = a.length() - 1; i >= 0; i--) {
                Store.addHistory(Track.fromJson(a.getJSONObject(i)));
                historyCount++;
            }
        }

        // Pulih senarai main
        if (data.has("playlists")) {
            JSONArray a = data.getJSONArray("playlists");
            List<Store.Playlist> existing = Store.playlists();
            java.util.Set<String> existingIds = new java.util.HashSet<>();
            for (Store.Playlist p : existing) existingIds.add(p.id);
            for (int i = 0; i < a.length(); i++) {
                JSONObject po = a.getJSONObject(i);
                if (existingIds.contains(po.optString("id"))) continue; // Langkau duplikat
                JSONArray ts = po.optJSONArray("tracks");
                java.util.List<Track> tracks = new java.util.ArrayList<>();
                if (ts != null) for (int j = 0; j < ts.length(); j++)
                    tracks.add(Track.fromJson(ts.getJSONObject(j)));
                Store.createPlaylist(po.optString("name", "Imported"), tracks);
                playlistCount++;
            }
        }

        // Pulih tetapan (kecuali token)
        if (data.has("prefs")) {
            JSONObject prefs = data.getJSONObject("prefs");
            java.util.Iterator<String> keys = prefs.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.contains("token") || k.contains("cookie") || k.contains("session")) continue;
                Object v = prefs.get(k);
                if (v instanceof String) Prefs.putS(k, (String) v);
                else if (v instanceof Integer) Prefs.putI(k, (int) v);
                else if (v instanceof Boolean) Prefs.putB(k, (boolean) v);
            }
        }

        // Pulih addon tersuai
        if (data.has("addons")) {
            JSONArray a = data.getJSONArray("addons");
            for (int i = 0; i < a.length(); i++) {
                try { CustomAddons.add(a.getJSONObject(i).toString()); } catch (Exception ignored) { }
            }
        }

        return String.format("%d kegemaran, %d sejarah, %d senarai main", likedCount, historyCount, playlistCount);
    }

    private static String getVersionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) { return "?"; }
    }
}
