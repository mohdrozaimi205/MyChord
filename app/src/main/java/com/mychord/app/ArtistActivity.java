package com.mychord.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Halaman artis — poster besar di atas, senarai lagu di bawah.
 * Dibuka dari Now Playing (klik nama artis) atau dari Artist Spotlight / Recent Artists.
 */
public class ArtistActivity extends BaseActivity {
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ART = "art";

    private String artistName;
    private TrackAdapter adapter;
    private final List<Track> tracks = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        artistName = getIntent().getStringExtra(EXTRA_ARTIST);
        String artUrl = getIntent().getStringExtra(EXTRA_ART);

        if (artistName == null || artistName.isEmpty()) { finish(); return; }

        // ── Header ──
        TextView nameTv = findViewById(R.id.artistName);
        nameTv.setText(artistName);

        ImageView poster = findViewById(R.id.artistPoster);
        if (artUrl != null && !artUrl.isEmpty()) {
            Glide.with(this).load(artUrl).centerCrop().into(poster);
        }

        findViewById(R.id.artistClose).setOnClickListener(v -> finish());

        // ── RecyclerView ──
        RecyclerView rv = findViewById(R.id.artistRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        final TrackAdapter[] ref = new TrackAdapter[1];
        ref[0] = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { Nav.open(ArtistActivity.this, ref[0].items, pos); }
            @Override
            public void onMore(View anchor, int pos) {
                TrackMenu.show(ArtistActivity.this, anchor, ref[0].items.get(pos), null);
            }
        }, false);
        ref[0].setVerticalMode(true);
        adapter = ref[0];
        rv.setAdapter(adapter);

        // ── Mini player ──
        setupMini();

        // ── Butang Play All & Shuffle ──
        findViewById(R.id.artistPlayAll).setOnClickListener(v -> {
            if (!tracks.isEmpty()) PlayerHub.playList(this, tracks, 0, false);
        });
        findViewById(R.id.artistShuffle).setOnClickListener(v -> {
            if (!tracks.isEmpty()) PlayerHub.playList(this, tracks, 0, true);
        });

        // ── Cari lagu artis ──
        loadArtistSongs();

        // ── Simpan recent artist ──
        Store.addRecentArtist(artistName, artUrl != null ? artUrl : "");
    }

    private void loadArtistSongs() {
        ProgressBar pb = findViewById(R.id.artistProgress);
        pb.setVisibility(View.VISIBLE);
        App.IO.execute(() -> {
            try {
                List<Track> result = YtMusic.search(artistName);
                // Tapis — hanya lagu artis ini
                List<Track> filtered = new ArrayList<>();
                String low = artistName.toLowerCase();
                for (Track t : result) {
                    if (t.artist != null && t.artist.toLowerCase().contains(low)) {
                        filtered.add(t);
                    }
                }
                // Jika tapis terlalu ketat, guna semua
                final List<Track> display = filtered.size() >= 3 ? filtered : result;
                // Tapis Hindi
                final List<Track> clean = DiscoverShelves.filterHindi(display);
                App.MAIN.post(() -> {
                    pb.setVisibility(View.GONE);
                    tracks.clear();
                    tracks.addAll(clean);
                    adapter.set(clean);
                    TextView countTv = findViewById(R.id.artistSongCount);
                    countTv.setText(clean.size() + " lagu");
                });
            } catch (Exception e) {
                App.MAIN.post(() -> {
                    pb.setVisibility(View.GONE);
                    TextView countTv = findViewById(R.id.artistSongCount);
                    countTv.setText("Tiada lagu ditemui");
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupMini();
    }
}
