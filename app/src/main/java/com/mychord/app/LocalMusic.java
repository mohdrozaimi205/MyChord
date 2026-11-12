package com.mychord.app;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lagu dalam telefon — termasuk FLAC / WAV lossless. */
public class LocalMusic {
    public static List<Track> load(Context ctx) {
        List<Track> out = new ArrayList<>();
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DISPLAY_NAME};
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri artBase = Uri.parse("content://media/external/audio/albumart");
        try (Cursor c = ctx.getContentResolver().query(base, cols,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " ASC")) {
            if (c == null) return out;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String title = c.getString(1);
                String artist = c.getString(2);
                if (artist == null || "<unknown>".equals(artist)) artist = "";
                String mime = c.getString(4) == null ? "" : c.getString(4).toLowerCase(Locale.ROOT);
                String name = c.getString(6) == null ? "" : c.getString(6).toLowerCase(Locale.ROOT);
                Uri uri = ContentUris.withAppendedId(base, id);
                Track t = new Track("local:" + uri, title, artist,
                        ContentUris.withAppendedId(artBase, c.getLong(5)).toString());
                t.durationSec = c.getLong(3) / 1000;
                t.badge = badge(mime, name);
                out.add(t);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String badge(String mime, String name) {
        if (mime.contains("flac") || name.endsWith(".flac")) return "FLAC • LOSSLESS";
        if (mime.contains("wav") || name.endsWith(".wav")) return "WAV • LOSSLESS";
        if (name.endsWith(".alac")) return "ALAC • LOSSLESS";
        if (mime.contains("ape") || name.endsWith(".ape")) return "APE • LOSSLESS";
        if (mime.contains("mpeg") || name.endsWith(".mp3")) return "MP3";
        if (name.endsWith(".m4a") || mime.contains("mp4")) return "M4A";
        if (mime.contains("ogg") || mime.contains("opus")) return "OGG";
        return "";
    }
}
