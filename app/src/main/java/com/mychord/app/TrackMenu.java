package com.mychord.app;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.media3.session.MediaController;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 3-dot menu gaya YouTube Music.
 * Item: main seterusnya, tambah barisan, suka, simpan ke playlist,
 *       kongsi, mulakan mix, pin ke speed dial, tak berminat.
 */
public class TrackMenu {
    public static void show(Activity a, View anchor, Track t, Runnable onRemove) {
        PopupMenu pm = new PopupMenu(a, anchor);
        boolean radio = t.mediaId.startsWith("radio:");
        int order = 0;
        pm.getMenu().add(0, 1, order++, R.string.menu_play_next);
        pm.getMenu().add(0, 2, order++, R.string.menu_add_queue);
        pm.getMenu().add(0, 3, order++, Store.isLiked(t.mediaId) ? R.string.menu_unlike : R.string.menu_like);
        if (!radio) pm.getMenu().add(0, 4, order++, R.string.menu_add_playlist);
        if (!radio) pm.getMenu().add(0, 6, order++, R.string.menu_share);
        if (!radio) pm.getMenu().add(0, 7, order++, R.string.menu_start_mix);
        if (!radio) pm.getMenu().add(0, 8, order++, R.string.menu_pin_speed_dial);
        if (!radio) pm.getMenu().add(0, 9, order++, R.string.menu_not_interested);
        if (onRemove != null) pm.getMenu().add(0, 5, order, R.string.menu_remove);
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    PlayerHub.playNext(a, t);
                    Toast.makeText(a, R.string.added_queue, Toast.LENGTH_SHORT).show();
                    return true;
                case 2:
                    PlayerHub.addToQueue(a, t);
                    Toast.makeText(a, R.string.added_queue, Toast.LENGTH_SHORT).show();
                    return true;
                case 3:
                    YtmAccount.syncLike(t, Store.toggleLike(t));
                    return true;
                case 4:
                    pickPlaylist(a, t);
                    return true;
                case 5:
                    onRemove.run();
                    return true;
                case 6:
                    shareSong(a, t);
                    return true;
                case 7:
                    startMix(a, t);
                    return true;
                case 8:
                    pinToSpeedDial(a, t);
                    return true;
                case 9:
                    // Tak berminat — just dismiss (no backend to report)
                    Toast.makeText(a, R.string.menu_not_interested_done, Toast.LENGTH_SHORT).show();
                    return true;
            }
            return false;
        });
        pm.show();
    }

    static void pickPlaylist(Activity a, Track t) {
        List<Store.Playlist> pls = Store.playlists();
        if (pls.isEmpty()) {
            Toast.makeText(a, R.string.no_playlists, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[pls.size()];
        for (int i = 0; i < pls.size(); i++) names[i] = pls.get(i).name;
        AlertDialog d = new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.menu_add_playlist)
                .setItems(names, (dialog, which) -> {
                    Store.addToPlaylist(pls.get(which).id, t);
                    Toast.makeText(a, a.getString(R.string.added_playlist, names[which]), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        d.show();
    }

    /** Kongsi lagu — link YouTube Music atau carian */
    private static void shareSong(Activity a, Track t) {
        String link;
        if (t.mediaId.startsWith("yt:")) {
            link = "https://music.youtube.com/watch?v=" + t.mediaId.substring(3);
        } else {
            link = "https://music.youtube.com/search?q=" + android.net.Uri.encode(t.title + " " + t.artist);
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "🎵 " + t.title
                + (t.artist != null && !t.artist.isEmpty() ? " — " + t.artist : "") + "\n" + link);
        a.startActivity(Intent.createChooser(i, a.getString(R.string.share_song)));
    }

    /** Mulakan mix berdasarkan lagu ini — cari lagu serupa */
    private static void startMix(Activity a, Track t) {
        Toast.makeText(a, "Mix ♫", Toast.LENGTH_SHORT).show();
        App.IO.execute(() -> {
            try {
                List<Track> similar = YtMusic.search(t.title + " " + t.artist);
                List<Track> filtered = DiscoverShelves.filterHindi(similar);
                if (filtered.size() > 30) filtered = filtered.subList(0, 30);
                if (!filtered.isEmpty()) {
                    final List<Track> f = new ArrayList<>(filtered);
                    App.MAIN.post(() -> {
                        if (!a.isFinishing()) PlayerHub.playList(a, f, 0, false);
                    });
                }
            } catch (Exception e) {
                App.MAIN.post(() -> {
                    if (!a.isFinishing())
                        Toast.makeText(a, "Mix tidak tersedia", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /** Pin artis ke Speed Dial — simpan ke recent artists */
    private static void pinToSpeedDial(Activity a, Track t) {
        if (t.artist != null && !t.artist.isEmpty()) {
            String artistName = t.artist.contains(",") ? t.artist.split(",")[0].trim() : t.artist;
            Store.addRecentArtist(artistName, t.art);
            Toast.makeText(a, a.getString(R.string.menu_pinned_speed_dial, artistName), Toast.LENGTH_SHORT).show();
        }
    }
}
