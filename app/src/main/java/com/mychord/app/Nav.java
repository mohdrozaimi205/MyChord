package com.mychord.app;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/** Tekan item: playlist/album → buka senarai; lagu → main (abaikan item playlist dalam barisan). */
public class Nav {
    public static boolean isContainer(Track t) {
        return t.mediaId.startsWith("ytpl:") || t.mediaId.startsWith("sppl:") || t.mediaId.startsWith("pod:");
    }

    public static void open(Activity a, List<Track> list, int pos) {
        if (pos < 0 || pos >= list.size()) return;
        Track t = list.get(pos);
        if (t.mediaId.startsWith("pod:")) {
            a.startActivity(new Intent(a, ListActivity.class)
                    .putExtra("mode", ListActivity.MODE_POD_EPS)
                    .putExtra("id", t.mediaId.substring(4))
                    .putExtra("title", t.title));
            return;
        }
        if (isContainer(t)) {
            boolean yt = t.mediaId.startsWith("ytpl:");
            a.startActivity(new Intent(a, ListActivity.class)
                    .putExtra("mode", yt ? ListActivity.MODE_YTM_PL : ListActivity.MODE_SP_PL)
                    .putExtra("id", t.mediaId.substring(5))
                    .putExtra("title", t.title));
            return;
        }
        List<Track> play = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < list.size(); i++) {
            if (isContainer(list.get(i))) continue;
            if (i == pos) idx = play.size();
            play.add(list.get(i));
        }
        PlayerHub.playList(a, play, idx, false);
    }

    public static List<Track> playable(List<Track> list) {
        List<Track> out = new ArrayList<>();
        for (Track t : list) if (!isContainer(t)) out.add(t);
        return out;
    }
}
