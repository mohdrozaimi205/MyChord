package com.mychord.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ListActivity extends BaseActivity {
    public static final String MODE_LIKED = "liked";
    public static final String MODE_HISTORY = "history";
    public static final String MODE_LOCAL = "local";
    public static final String MODE_PLAYLIST = "playlist";
    public static final String MODE_RADIO = "radio";

    private String mode, playlistId;
    private TrackAdapter adapter;
    private ProgressBar progress;
    private TextView empty, sub;

    private String perm() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupMini();
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = MODE_LIKED;
        playlistId = getIntent().getStringExtra("id");
        progress = findViewById(R.id.listProgress);
        empty = findViewById(R.id.listEmpty);
        sub = findViewById(R.id.listSub);

        RecyclerView rv = findViewById(R.id.listRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { PlayerHub.playList(ListActivity.this, adapter.items, pos, false); }

            @Override
            public void onMore(View anchor, int pos) {
                Track t = adapter.items.get(pos);
                Runnable remove = null;
                if (MODE_PLAYLIST.equals(mode)) remove = () -> { Store.removeFromPlaylist(playlistId, pos); load(); };
                else if (MODE_LIKED.equals(mode)) remove = () -> { Store.toggleLike(t); load(); };
                TrackMenu.show(ListActivity.this, anchor, t, remove);
            }
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btnPlayAll).setOnClickListener(v -> PlayerHub.playList(this, adapter.items, 0, false));
        findViewById(R.id.btnShuffle).setOnClickListener(v -> PlayerHub.playList(this, adapter.items, 0, true));

        String title;
        switch (mode) {
            case MODE_HISTORY: title = getString(R.string.lib_history); break;
            case MODE_LOCAL: title = getString(R.string.local_title); break;
            case MODE_RADIO: title = getString(R.string.radio_title); break;
            case MODE_PLAYLIST:
                Store.Playlist p = Store.getPlaylist(playlistId);
                title = p != null ? p.name : getString(R.string.lib_playlists);
                break;
            default: title = getString(R.string.tile_liked);
        }
        setupTop(title);

        if (MODE_RADIO.equals(mode)) {
            findViewById(R.id.listButtons).setVisibility(View.GONE);
            findViewById(R.id.listSearchBox).setVisibility(View.VISIBLE);
            EditText et = findViewById(R.id.listSearch);
            et.setHint(R.string.radio_hint);
            et.setOnEditorActionListener((tv, id, ev) -> {
                if (id == EditorInfo.IME_ACTION_SEARCH || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    loadRadio(tv.getText().toString().trim());
                    return true;
                }
                return false;
            });
        }
        findViewById(R.id.btnPerm).setOnClickListener(v -> requestPermissions(new String[]{perm()}, 11));
        load();
    }

    private void showList(List<Track> list, int emptyRes) {
        progress.setVisibility(View.GONE);
        adapter.set(list);
        if (list.isEmpty()) {
            empty.setText(emptyRes);
            empty.setVisibility(View.VISIBLE);
        } else empty.setVisibility(View.GONE);
        if (!MODE_RADIO.equals(mode)) sub.setText(getString(R.string.songs_count, list.size()));
    }

    private void load() {
        switch (mode) {
            case MODE_HISTORY: showList(Store.history(), R.string.history_empty); break;
            case MODE_PLAYLIST: {
                Store.Playlist p = Store.getPlaylist(playlistId);
                showList(p != null ? p.tracks : new ArrayList<>(), R.string.list_empty);
                break;
            }
            case MODE_LOCAL: loadLocal(); break;
            case MODE_RADIO: loadRadio(""); break;
            default: showList(Store.liked(), R.string.liked_empty);
        }
    }

    private void loadLocal() {
        View permBox = findViewById(R.id.listPerm);
        if (ContextCompat.checkSelfPermission(this, perm()) != PackageManager.PERMISSION_GRANTED) {
            permBox.setVisibility(View.VISIBLE);
            return;
        }
        permBox.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        App.IO.execute(() -> {
            List<Track> list = LocalMusic.load(this);
            App.MAIN.post(() -> { if (!isFinishing()) showList(list, R.string.local_empty); });
        });
    }

    private void loadRadio(String q) {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        App.IO.execute(() -> {
            List<Track> list;
            try { list = q.isEmpty() ? RadioApi.malaysia() : RadioApi.search(q); }
            catch (Exception e) { list = null; }
            final List<Track> res = list;
            App.MAIN.post(() -> {
                if (isFinishing()) return;
                if (res == null) showList(new ArrayList<>(), R.string.error_net);
                else showList(res, R.string.no_results);
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 11) loadLocal();
    }
}
