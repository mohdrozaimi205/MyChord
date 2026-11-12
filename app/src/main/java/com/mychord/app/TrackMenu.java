package com.mychord.app;

import android.app.Activity;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class TrackMenu {
    public static void show(Activity a, View anchor, Track t, Runnable onRemove) {
        PopupMenu pm = new PopupMenu(a, anchor);
        boolean radio = t.mediaId.startsWith("radio:");
        pm.getMenu().add(0, 1, 0, R.string.menu_play_next);
        pm.getMenu().add(0, 2, 1, R.string.menu_add_queue);
        pm.getMenu().add(0, 3, 2, Store.isLiked(t.mediaId) ? R.string.menu_unlike : R.string.menu_like);
        if (!radio) pm.getMenu().add(0, 4, 3, R.string.menu_add_playlist);
        if (onRemove != null) pm.getMenu().add(0, 5, 4, R.string.menu_remove);
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
}
