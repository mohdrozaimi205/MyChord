package com.mychord.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;

public class HomeFragment extends Fragment {
    private View root;
    private RecyclerView recentList;
    private TrackAdapter recentAdapter;
    private View recentShelf;

    private static final int[] SHELF_TITLES = {R.string.shelf_malay, R.string.shelf_top, R.string.shelf_indie,
            R.string.shelf_chill, R.string.shelf_nasyid};
    private static final String[] SHELF_QUERIES = {"lagu melayu terkini popular", "top hits global 2026",
            "indie malaysia", "lofi chill santai", "nasyid terbaik"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        root = inf.inflate(R.layout.fragment_home, c, false);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        ((TextView) root.findViewById(R.id.greet)).setText(
                h < 12 ? R.string.greet_morning : h < 19 ? R.string.greet_afternoon : R.string.greet_evening);
        buildTiles();

        LinearLayout shelves = root.findViewById(R.id.shelves);
        // Baru dimainkan
        recentShelf = inf.inflate(R.layout.view_shelf, shelves, false);
        ((TextView) recentShelf.findViewById(R.id.shelfTitle)).setText(R.string.shelf_recent);
        recentShelf.findViewById(R.id.shelfProgress).setVisibility(View.GONE);
        recentList = recentShelf.findViewById(R.id.shelfList);
        recentList.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        recentAdapter = cardAdapter();
        recentList.setAdapter(recentAdapter);
        shelves.addView(recentShelf);

        if (YtmAccount.loggedIn()) addAccountShelf(inf, shelves);
        for (int i = 0; i < SHELF_TITLES.length; i++) addShelf(inf, shelves, SHELF_TITLES[i], SHELF_QUERIES[i]);
        return root;
    }

    private TrackAdapter cardAdapter() {
        final TrackAdapter[] ref = new TrackAdapter[1];
        ref[0] = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { Nav.open(requireActivity(), ref[0].items, pos); }

            @Override
            public void onMore(View anchor, int pos) { TrackMenu.show(requireActivity(), anchor, ref[0].items.get(pos), null); }
        }, true);
        return ref[0];
    }

    private void addAccountShelf(LayoutInflater inf, LinearLayout parent) {
        View shelf = inf.inflate(R.layout.view_shelf, parent, false);
        ((TextView) shelf.findViewById(R.id.shelfTitle)).setText(R.string.acc_for_you);
        ProgressBar pb = shelf.findViewById(R.id.shelfProgress);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        TrackAdapter ad = cardAdapter();
        rv.setAdapter(ad);
        parent.addView(shelf);
        App.IO.execute(() -> {
            try {
                List<Track> r = YtmAccount.home();
                App.MAIN.post(() -> {
                    if (!isAdded()) return;
                    pb.setVisibility(View.GONE);
                    ad.set(r.size() > 24 ? r.subList(0, 24) : r);
                    if (r.isEmpty()) shelf.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                App.MAIN.post(() -> { if (isAdded()) shelf.setVisibility(View.GONE); });
            }
        });
    }

    private void addShelf(LayoutInflater inf, LinearLayout parent, int title, String query) {
        View shelf = inf.inflate(R.layout.view_shelf, parent, false);
        ((TextView) shelf.findViewById(R.id.shelfTitle)).setText(title);
        ProgressBar pb = shelf.findViewById(R.id.shelfProgress);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        TrackAdapter ad = cardAdapter();
        rv.setAdapter(ad);
        parent.addView(shelf);
        App.IO.execute(() -> {
            try {
                List<Track> r = YtMusic.search(query);
                App.MAIN.post(() -> {
                    if (!isAdded()) return;
                    pb.setVisibility(View.GONE);
                    ad.set(r.size() > 15 ? r.subList(0, 15) : r);
                    if (r.isEmpty()) shelf.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                App.MAIN.post(() -> { if (isAdded()) shelf.setVisibility(View.GONE); });
            }
        });
    }

    public void buildTiles() {
        if (root == null || getContext() == null) return;
        GridLayout g = root.findViewById(R.id.tiles);
        g.removeAllViews();
        addTile(g, R.drawable.ic_heart_fill, R.string.tile_liked, R.string.tile_liked_sub, ListActivity.MODE_LIKED);
        addTile(g, R.drawable.ic_phone, R.string.tile_local, R.string.tile_local_sub, ListActivity.MODE_LOCAL);
        addTile(g, R.drawable.ic_link, R.string.tile_spotify, R.string.tile_spotify_sub, "spotify");
        if (AddonManager.installed("radio"))
            addTile(g, R.drawable.ic_radio, R.string.tile_radio, R.string.tile_radio_sub, ListActivity.MODE_RADIO);
        else
            addTile(g, R.drawable.ic_history, R.string.lib_history, R.string.shelf_recent, ListActivity.MODE_HISTORY);
    }

    private void addTile(GridLayout g, int icon, int title, int sub, String mode) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.item_tile, g, false);
        ImageView iv = v.findViewById(R.id.tileIcon);
        iv.setImageResource(icon);
        Ui.tint(iv, Ui.attr(requireContext(), R.attr.mcOnAccent));
        ((TextView) v.findViewById(R.id.tileTitle)).setText(title);
        ((TextView) v.findViewById(R.id.tileSub)).setText(sub);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = Ui.dp(requireContext(), 72);
        int m = Ui.dp(requireContext(), 6);
        lp.setMargins(m, m, m, m);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> {
            if ("spotify".equals(mode)) startActivity(new Intent(getContext(), SpotifyActivity.class));
            else startActivity(new Intent(getContext(), ListActivity.class).putExtra("mode", mode));
        });
        g.addView(v);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRecent();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refreshRecent();
    }

    private void refreshRecent() {
        if (recentAdapter == null) return;
        List<Track> h = Store.history();
        recentAdapter.set(h.size() > 15 ? h.subList(0, 15) : h);
        recentShelf.setVisibility(h.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
