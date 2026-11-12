package com.mychord.app;

import android.os.Bundle;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import org.json.JSONObject;

public class Track {
    /** yt:ID, q:carian, local:content://..., radio:http... */
    public String mediaId;
    public String title = "";
    public String artist = "";
    public String art = "";
    public long durationSec;
    public String badge = "";

    public Track() { }

    public Track(String mediaId, String title, String artist, String art) {
        this.mediaId = mediaId;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.art = art == null ? "" : art;
    }

    public MediaItem toMediaItem() {
        return PlaybackService.buildItem(mediaId, title, artist, art, badge);
    }

    public static Track fromMediaItem(MediaItem item) {
        Track t = new Track();
        t.mediaId = item.mediaId;
        MediaMetadata m = item.mediaMetadata;
        t.title = m.title != null ? m.title.toString() : "";
        t.artist = m.artist != null ? m.artist.toString() : "";
        t.art = m.artworkUri != null ? m.artworkUri.toString() : "";
        Bundle ex = m.extras;
        if (ex != null) t.badge = ex.getString("badge", "");
        return t;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", mediaId);
            o.put("t", title);
            o.put("a", artist);
            o.put("img", art);
            o.put("d", durationSec);
            o.put("b", badge);
        } catch (Exception ignored) { }
        return o;
    }

    public static Track fromJson(JSONObject o) {
        Track t = new Track(o.optString("id"), o.optString("t"), o.optString("a"), o.optString("img"));
        t.durationSec = o.optLong("d");
        t.badge = o.optString("b");
        return t;
    }
}
