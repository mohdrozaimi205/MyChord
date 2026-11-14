package com.mychord.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Layar jelajah mood dan genre: grid mozek mood di atas,
 * diikuti rak lagu mengikut genre.
 */
public class MoodsActivity extends BaseActivity {

    /** Senarai mood/genre yang disokong — emoji + label + istilah carian */
    private static final String[][] MOODS = {
        {"🎉", "Riang", "upbeat happy party music"},
        {"😢", "Sedih", "sad emotional ballads"},
        {"🔥", "Tenaga", "energetic workout pump up"},
        {"😌", "Tenang", "calm relaxing peaceful"},
        {"💕", "Romantik", "romantic love songs"},
        {"🌙", "Malam", "late night chill vibes"},
        {"☀️", "Pagi", "morning fresh acoustic"},
        {"🏋️", "Senaman", "gym workout motivation"},
        {"📚", "Fokus", "focus study concentration"},
        {"🎸", "Rock", "rock music"},
        {"🎤", "R&B", "r&b soul"},
        {"🎹", "Klasik", "classical music"},
        {"🎺", "Jazz", "jazz music"},
        {"🕌", "Nasyid", "nasyid islamic nasheed"},
        {"🏖️", "Santai", "lofi chill beats"},
        {"🎧", "EDM", "edm electronic dance music"},
        {"🇲🇾", "Melayu", "lagu melayu popular terkini"},
        {"🌏", "K-Pop", "kpop korean pop"},
        {"🎵", "Indie", "indie alternative"},
        {"🎶", "Pop", "pop hits"},
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moods);
        setupTop(getString(R.string.moods_title));

        GridLayout grid = findViewById(R.id.moodsGrid);
        LinearLayout shelves = findViewById(R.id.moodsShelves);
        LayoutInflater inf = getLayoutInflater();

        for (String[] mood : MOODS) {
            View tile = inf.inflate(R.layout.item_mood, grid, false);
            TextView emoji = tile.findViewById(R.id.moodEmoji);
            TextView label = tile.findViewById(R.id.moodLabel);
            emoji.setText(mood[0]);
            label.setText(mood[1]);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = Ui.dp(this, 80);
            int m = Ui.dp(this, 4);
            lp.setMargins(m, m, m, m);
            tile.setLayoutParams(lp);

            String query = mood[2];
            tile.setOnClickListener(v -> {
                // Buka senarai carian untuk mood ini
                Intent i = new Intent(this, ListActivity.class);
                i.putExtra("mode", "search");
                i.putExtra("query", query);
                i.putExtra("title", mood[1]);
                startActivity(i);
            });
            grid.addView(tile);

            // Tambah rak kecil untuk setiap mood
            addShelf(shelves, inf, mood[1], query);
        }
    }

    private void addShelf(LinearLayout parent, LayoutInflater inf, String title, String query) {
        View shelf = inf.inflate(R.layout.view_shelf, parent, false);
        ((TextView) shelf.findViewById(R.id.shelfTitle)).setText(title);
        ProgressBar pb = shelf.findViewById(R.id.shelfProgress);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        final TrackAdapter[] ref = new TrackAdapter[1];
        ref[0] = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { Nav.open(MoodsActivity.this, ref[0].items, pos); }
            @Override
            public void onMore(View anchor, int pos) {
                Track t = ref[0].items.get(pos);
                if (Nav.isContainer(t)) Nav.open(MoodsActivity.this, ref[0].items, pos);
                else TrackMenu.show(MoodsActivity.this, anchor, t, null);
            }
        }, true);
        rv.setAdapter(ref[0]);
        parent.addView(shelf);
        App.IO.execute(() -> {
            try {
                List<Track> r = YtMusic.search(query);
                App.MAIN.post(() -> {
                    pb.setVisibility(View.GONE);
                    ref[0].set(r.size() > 20 ? r.subList(0, 20) : r);
                    if (r.isEmpty()) shelf.setVisibility(View.GONE);
                });
            } catch (Throwable e) {
                App.MAIN.post(() -> shelf.setVisibility(View.GONE));
            }
        });
    }
}
