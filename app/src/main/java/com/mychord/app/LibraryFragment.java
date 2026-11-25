package com.mychord.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class LibraryFragment extends Fragment {
    private LinearLayout playlistsBox;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.fragment_library, c, false);
        LinearLayout rows = v.findViewById(R.id.libRows);
        rows.addView(row(inf, rows, R.drawable.ic_heart_fill, getString(R.string.tile_liked), null,
                x -> open(ListActivity.MODE_LIKED, null)));
        rows.addView(row(inf, rows, R.drawable.ic_history, getString(R.string.lib_history), null,
                x -> open(ListActivity.MODE_HISTORY, null)));
        rows.addView(row(inf, rows, R.drawable.ic_phone, getString(R.string.tile_local), getString(R.string.tile_local_sub),
                x -> open(ListActivity.MODE_LOCAL, null)));
        playlistsBox = v.findViewById(R.id.libPlaylists);
        v.findViewById(R.id.libAdd).setOnClickListener(x -> newPlaylist());
        ImageView add = v.findViewById(R.id.libAdd);
        Ui.tint(add, Ui.attr(requireContext(), R.attr.mcOnAccent));
        return v;
    }

    private View row(LayoutInflater inf, ViewGroup parent, int icon, String title, String sub, View.OnClickListener l) {
        View r = inf.inflate(R.layout.item_row, parent, false);
        ImageView iv = r.findViewById(R.id.rowIcon);
        iv.setImageResource(icon);
        Ui.tint(iv, Ui.attr(requireContext(), R.attr.mcAccent));
        ((TextView) r.findViewById(R.id.rowTitle)).setText(title);
        TextView st = r.findViewById(R.id.rowSub);
        if (sub != null) {
            st.setText(sub);
            st.setVisibility(View.VISIBLE);
        }
        r.setOnClickListener(l);
        return r;
    }

    private void open(String mode, String id) {
        Intent i = new Intent(getContext(), ListActivity.class).putExtra("mode", mode);
        if (id != null) i.putExtra("id", id);
        startActivity(i);
    }

    private void newPlaylist() {
        EditText et = new EditText(requireContext());
        et.setHint(R.string.playlist_name);
        int p = Ui.dp(requireContext(), 20);
        LinearLayout box = new LinearLayout(requireContext());
        box.setPadding(p, p / 2, p, 0);
        box.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.new_playlist)
                .setView(box)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) {
                        Store.createPlaylist(n, null);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refreshAccounts() {
        View v = getView();
        if (v == null) return;
        LinearLayout box = v.findViewById(R.id.libAccounts);
        box.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(getContext());
        if (YtmAccount.loggedIn()) {
            String n = YtmAccount.name();
            box.addView(row(inf, box, R.drawable.ic_remote, getString(R.string.acc_for_you), "YouTube Music" + (n.isEmpty() ? "" : " • " + n),
                    x -> open(ListActivity.MODE_YTM_HOME, null)));
            box.addView(row(inf, box, R.drawable.ic_heart_fill, getString(R.string.tile_liked), "YouTube Music",
                    x -> open(ListActivity.MODE_YTM_LIKED, null)));
            box.addView(row(inf, box, R.drawable.ic_queue, getString(R.string.acc_my_playlists), "YouTube Music",
                    x -> open(ListActivity.MODE_YTM_LISTS, null)));
        } else {
            box.addView(row(inf, box, R.drawable.ic_remote, getString(R.string.login_yt), getString(R.string.acc_login_sub),
                    x -> startActivity(new Intent(getContext(), LoginActivity.class).putExtra("service", LoginActivity.YT))));
        }
        // Spotify login dibuang — hanya YouTube Music
    }

    private void refresh() {
        refreshAccounts();
        if (playlistsBox == null) return;
        playlistsBox.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(getContext());
        List<Store.Playlist> pls = Store.playlists();
        for (Store.Playlist pl : pls) {
            View r = row(inf, playlistsBox, R.drawable.ic_queue, pl.name,
                    getString(R.string.songs_count, pl.tracks.size()), x -> open(ListActivity.MODE_PLAYLIST, pl.id));
            r.setOnLongClickListener(x -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(pl.name)
                        .setMessage(R.string.delete_playlist_q)
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            Store.deletePlaylist(pl.id);
                            refresh();
                        })
                        .setNegativeButton(R.string.cancel, null).show();
                return true;
            });
            playlistsBox.addView(r);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refresh();
    }
}
