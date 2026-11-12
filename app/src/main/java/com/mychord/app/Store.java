package com.mychord.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Kegemaran, sejarah dan senarai main (disimpan dalam telefon). */
public class Store {
    private static SharedPreferences sp() {
        return App.get().getSharedPreferences("store", Context.MODE_PRIVATE);
    }

    private static List<Track> readList(String key) {
        List<Track> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp().getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Track.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    private static void writeList(String key, List<Track> list) {
        JSONArray a = new JSONArray();
        for (Track t : list) a.put(t.toJson());
        sp().edit().putString(key, a.toString()).apply();
    }

    // ---- Kegemaran ----
    public static List<Track> liked() { return readList("liked"); }

    public static boolean isLiked(String id) {
        for (Track t : liked()) if (t.mediaId.equals(id)) return true;
        return false;
    }

    public static boolean toggleLike(Track t) {
        List<Track> l = liked();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).mediaId.equals(t.mediaId)) {
                l.remove(i);
                writeList("liked", l);
                return false;
            }
        }
        l.add(0, t);
        writeList("liked", l);
        return true;
    }

    // ---- Sejarah ----
    public static List<Track> history() { return readList("history"); }

    public static void addHistory(Track t) {
        if (t == null || t.mediaId == null) return;
        List<Track> l = history();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).mediaId.equals(t.mediaId)) { l.remove(i); break; }
        }
        l.add(0, t);
        while (l.size() > 60) l.remove(l.size() - 1);
        writeList("history", l);
    }

    // ---- Senarai main ----
    public static class Playlist {
        public String id;
        public String name;
        public List<Track> tracks = new ArrayList<>();
    }

    public static List<Playlist> playlists() {
        List<Playlist> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp().getString("playlists", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Playlist p = new Playlist();
                p.id = o.optString("id");
                p.name = o.optString("name");
                JSONArray ts = o.optJSONArray("tracks");
                if (ts != null) for (int j = 0; j < ts.length(); j++) p.tracks.add(Track.fromJson(ts.getJSONObject(j)));
                out.add(p);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void savePlaylists(List<Playlist> list) {
        JSONArray a = new JSONArray();
        try {
            for (Playlist p : list) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                JSONArray ts = new JSONArray();
                for (Track t : p.tracks) ts.put(t.toJson());
                o.put("tracks", ts);
                a.put(o);
            }
        } catch (Exception ignored) { }
        sp().edit().putString("playlists", a.toString()).apply();
    }

    public static Playlist getPlaylist(String id) {
        for (Playlist p : playlists()) if (p.id.equals(id)) return p;
        return null;
    }

    public static Playlist createPlaylist(String name, List<Track> tracks) {
        List<Playlist> l = playlists();
        Playlist p = new Playlist();
        p.id = "pl" + System.currentTimeMillis();
        p.name = name;
        if (tracks != null) p.tracks.addAll(tracks);
        l.add(0, p);
        savePlaylists(l);
        return p;
    }

    public static void deletePlaylist(String id) {
        List<Playlist> l = playlists();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) { l.remove(i); break; }
        savePlaylists(l);
    }

    public static void addToPlaylist(String id, Track t) {
        List<Playlist> l = playlists();
        for (Playlist p : l) if (p.id.equals(id)) p.tracks.add(t);
        savePlaylists(l);
    }

    public static void removeFromPlaylist(String id, int index) {
        List<Playlist> l = playlists();
        for (Playlist p : l) if (p.id.equals(id) && index >= 0 && index < p.tracks.size()) p.tracks.remove(index);
        savePlaylists(l);
    }
}
