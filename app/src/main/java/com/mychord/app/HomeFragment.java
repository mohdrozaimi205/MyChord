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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Home screen — gaya YouTube Music.
 * Top bar: Logo, Share, Search, Account.
 * Speed Dial 2×2 artis, 10 rak YTM, akaun, addon, podcast & radio.
 */
public class HomeFragment extends Fragment {
    interface Loader { List<Track> load() throws Exception; }

    private View root;
    private LayoutInflater inflater;
    private TrackAdapter recentAdapter;
    private View recentShelf;
    private String builtFor = null;

    /** Filter chips — label + carian */
    private static final String[][] FILTER_CHIPS = {
        {"All", ""},
        {"Relax", "relaxing calm peaceful chill"},
        {"Energize", "energetic workout pump up"},
        {"Feel Good", "feel good happy upbeat"},
        {"Workout", "gym workout motivation fitness"},
        {"Fokus", "focus study concentration"},
        {"Romantik", "romantic love songs"},
        {"K-Pop", "kpop korean pop"},
        {"Indie", "indie alternative malaysia"},
        {"Rock", "rock music"},
        {"Nasyid", "nasyid islamic nasheed"},
    };

    /** Artis lalai untuk Speed Dial jika tiada sejarah */
    private static final String[][] DEFAULT_SPEED_DIAL = {
        {"Dato Sri Siti Nurhaliza", "siti nurhaliza"},
        {"Yuna", "yuna malaysian singer"},
        {"Hael Husaini", "hael husaini"},
        {"Faizal Tahir", "faizal tahir"},
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        inflater = inf;
        root = inf.inflate(R.layout.fragment_home, c, false);

        // Top bar — tint ikon mengikut tema
        ImageView logoIcon = root.findViewById(R.id.logoIcon);
        Ui.tint(logoIcon, Ui.attr(requireContext(), R.attr.mcAccent));

        root.findViewById(R.id.homeShare).setOnClickListener(v -> ShareUtil.shareApp(requireActivity()));
        root.findViewById(R.id.homeSearch).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).selectTab(1);
        });
        root.findViewById(R.id.homeProfile).setOnClickListener(v -> showAccountMenu());

        refreshHeader();
        buildFilterChips();
        buildSpeedDial();
        buildShelves();
        return root;
    }

    /** Tanda keadaan semasa — bina semula apabila berubah */
    private String state() {
        return YtmAccount.loggedIn() + "|" + SpotifyAccount.loggedIn() + "|" + CustomAddons.signature();
    }

    // ── Account menu popup (gaya YTM) ────────────────────────────

    private void showAccountMenu() {
        boolean ytm = YtmAccount.loggedIn();
        boolean sp = SpotifyAccount.loggedIn();

        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (ytm) {
            String n = YtmAccount.name();
            items.add("YouTube Music • " + (n != null && !n.isEmpty() ? n : "✓"));
            actions.add(() -> {});
        }
        if (sp) {
            String n = SpotifyAccount.name();
            items.add("Spotify • " + (n != null && !n.isEmpty() ? n : "✓"));
            actions.add(() -> {});
        }

        items.add(getString(R.string.tile_liked));
        actions.add(() -> startActivity(new Intent(getContext(), ListActivity.class)
                .putExtra("mode", ListActivity.MODE_LIKED)));

        items.add(getString(R.string.lib_history));
        actions.add(() -> startActivity(new Intent(getContext(), ListActivity.class)
                .putExtra("mode", ListActivity.MODE_HISTORY)));

        if (!ytm && !sp) {
            items.add(getString(R.string.home_login_title));
            actions.add(() -> {
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).selectTab(3);
            });
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setItems(items.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    // ── Filter chips ─────────────────────────────────────────────

    private void buildFilterChips() {
        LinearLayout chips = root.findViewById(R.id.chipRow);
        chips.removeAllViews();

        for (int i = 0; i < FILTER_CHIPS.length; i++) {
            String[] chip = FILTER_CHIPS[i];
            TextView tv = (TextView) inflater.inflate(R.layout.item_mood_chip, chips, false);
            tv.setText(chip[0]);
            if (i == 0) {
                tv.setBackgroundResource(R.drawable.bg_accent_pill);
                tv.setTextColor(Ui.attr(requireContext(), R.attr.mcOnAccent));
            }
            String query = chip[1];
            String label = chip[0];
            if (query.isEmpty()) {
                tv.setOnClickListener(v -> root.scrollTo(0, 0));
            } else {
                tv.setOnClickListener(v -> startActivity(new Intent(getContext(), ListActivity.class)
                        .putExtra("mode", "search").putExtra("query", query).putExtra("title", label)));
            }
            chips.addView(tv);
        }
    }

    // ── Speed Dial: 2×2 artist grid ─────────────────────────────

    private void buildSpeedDial() {
        GridLayout grid = root.findViewById(R.id.speedDial);
        grid.removeAllViews();

        List<Store.RecentArtist> recent = Store.recentArtists();
        List<String[]> artists = new ArrayList<>(); // {name, artUrl, searchQuery}

        for (Store.RecentArtist ra : recent) {
            if (artists.size() >= 4) break;
            artists.add(new String[]{ra.name, ra.art, null});
        }

        int di = 0;
        while (artists.size() < 4 && di < DEFAULT_SPEED_DIAL.length) {
            String defName = DEFAULT_SPEED_DIAL[di][0];
            boolean dup = false;
            for (String[] a : artists) if (a[0].equals(defName)) { dup = true; break; }
            if (!dup) artists.add(new String[]{defName, null, DEFAULT_SPEED_DIAL[di][1]});
            di++;
        }

        for (String[] a : artists) {
            addSpeedDialItem(grid, a[0], a[1], a[2]);
        }
    }

    private void addSpeedDialItem(GridLayout grid, String name, String artUrl, String searchQuery) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        item.setBackgroundResource(R.drawable.bg_surface);

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = Ui.dp(requireContext(), 56);
        int m = Ui.dp(requireContext(), 4);
        lp.setMargins(m, m, m, m);
        item.setLayoutParams(lp);
        item.setPadding(Ui.dp(requireContext(), 4), 0, Ui.dp(requireContext(), 10), 0);

        // Round artist image
        ImageView img = new ImageView(requireContext());
        int imgSize = Ui.dp(requireContext(), 48);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(imgSize, imgSize);
        img.setLayoutParams(imgLp);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setClipToOutline(true);
        img.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });

        if (artUrl != null && !artUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this).load(artUrl).centerCrop().into(img);
        } else {
            img.setBackgroundColor(Ui.attr(requireContext(), R.attr.mcSurface2));
            // Muat gambar async jika ada query carian
            if (searchQuery != null) {
                App.IO.execute(() -> {
                    try {
                        List<Track> r = YtMusic.search(searchQuery);
                        if (!r.isEmpty() && r.get(0).art != null) {
                            String art = r.get(0).art;
                            App.MAIN.post(() -> {
                                if (isAdded()) com.bumptech.glide.Glide.with(HomeFragment.this)
                                        .load(art).centerCrop().into(img);
                            });
                        }
                    } catch (Exception ignored) {}
                });
            }
        }
        item.addView(img);

        // Artist name
        TextView tv = new TextView(requireContext());
        tv.setText(name);
        tv.setTextSize(13);
        tv.setTextColor(Ui.attr(requireContext(), R.attr.mcText));
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvLp.setMarginStart(Ui.dp(requireContext(), 10));
        tv.setLayoutParams(tvLp);
        item.addView(tv);

        item.setOnClickListener(v -> {
            Intent i = new Intent(getContext(), ArtistActivity.class);
            i.putExtra(ArtistActivity.EXTRA_ARTIST, name);
            if (artUrl != null) i.putExtra(ArtistActivity.EXTRA_ART, artUrl);
            startActivity(i);
        });

        grid.addView(item);
    }

    // ── Main shelves — 10 rak gaya YouTube Music ─────────────────

    private void buildShelves() {
        builtFor = state();
        LinearLayout shelves = root.findViewById(R.id.shelves);
        shelves.removeAllViews();

        // ━━━━ Baru Dimainkan / Recently Played ━━━━
        recentShelf = inflater.inflate(R.layout.view_shelf, shelves, false);
        ((TextView) recentShelf.findViewById(R.id.shelfTitle)).setText(R.string.shelf_recent);
        recentShelf.findViewById(R.id.shelfProgress).setVisibility(View.GONE);
        RecyclerView recentList = recentShelf.findViewById(R.id.shelfList);
        recentList.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        recentAdapter = cardAdapter();
        recentList.setAdapter(recentAdapter);
        shelves.addView(recentShelf);
        refreshRecent();

        // ━━━━ 1. Forgotten Favourites / Kegemaran Terlupa ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_forgotten_fav), DiscoverShelves::ytmForgottenFavourites, null);
        } else {
            addShelf(shelves, getString(R.string.dis_forgotten_fav), DiscoverShelves::fallbackListenAgain, null);
        }

        // ━━━━ 2. Quick Picks / Pilihan Pantas — VERTICAL (4 baris + 3-dot) ━━━━
        if (YtmAccount.loggedIn()) {
            addVerticalShelf(shelves, getString(R.string.dis_quick_picks), DiscoverShelves::ytmQuickPicks);
        } else {
            addVerticalShelf(shelves, getString(R.string.dis_quick_picks), DiscoverShelves::fallbackQuickPicks);
        }

        // ━━━━ 3. Cover & Remixed ━━━━
        addShelf(shelves, "Cover & Remixed",
                () -> {
                    if (YtmAccount.loggedIn()) {
                        List<Track> r = DiscoverShelves.ytmFreshOldFavs();
                        if (!r.isEmpty()) return r;
                    }
                    return DiscoverShelves.filterHindi(
                            YtMusic.search("cover lagu melayu popular remix acoustic 2026"));
                }, null);

        // ━━━━ 4. Trending Songs / Lagu Trending ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_trending), DiscoverShelves::ytmTrending, null);
        } else {
            addShelf(shelves, getString(R.string.dis_trending), DiscoverShelves::fallbackTrending, null);
        }

        // ━━━━ 5. Take It Easy / Santai ━━━━
        addShelf(shelves, "Take It Easy", DiscoverShelves::algChillCoffee, null);

        // ━━━━ 6. Long Listening / Dengaran Panjang ━━━━
        addShelf(shelves, "Long Listening", DiscoverShelves::algMyNostalgia, null);

        // ━━━━ 7. From The Community / Dari Komuniti ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_from_community), DiscoverShelves::ytmFromCommunity, null);
        } else {
            addShelf(shelves, getString(R.string.dis_from_community), DiscoverShelves::algMyPopular, null);
        }

        // ━━━━ 8. Similar To / Serupa Dengan (50 lagu) ━━━━
        addShelf(shelves, getString(R.string.dis_similar_to), DiscoverShelves::ytmSimilarTo, null, 50);

        // ━━━━ 9. New Releases / Keluaran Baharu ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_ytm_new_releases), DiscoverShelves::ytmNewReleases, null);
        } else {
            addShelf(shelves, getString(R.string.dis_ytm_new_releases), DiscoverShelves::fallbackNewReleases, null);
        }

        // ━━━━ Lagu Melayu Popular ━━━━
        addShelf(shelves, getString(R.string.alg_my_popular), DiscoverShelves::algMyPopular, null);
        addShelf(shelves, getString(R.string.alg_my_terkini), DiscoverShelves::algMyTerkini, null);

        // ━━━━ Akaun ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.acc_for_you), YtmAccount::home, ListActivity.MODE_YTM_HOME);
            addShelf(shelves, "YouTube Music • " + getString(R.string.tile_liked),
                    YtmAccount::liked, ListActivity.MODE_YTM_LIKED);
        }
        if (SpotifyAccount.loggedIn()) {
            addShelf(shelves, "Spotify • " + getString(R.string.tile_liked),
                    SpotifyAccount::liked, ListActivity.MODE_SP_LIKED);
        }
        if (!YtmAccount.loggedIn() && !SpotifyAccount.loggedIn()) addLoginCard(shelves);

        // Addon sendiri
        for (JSONObject a : CustomAddons.all()) {
            addShelf(shelves, a.optString("name"), () -> CustomAddons.tracks(a), null);
        }

        // ━━━━ Podcast & Radio ━━━━
        addShelf(shelves, getString(R.string.pod_shelf),
                () -> PodcastApi.top("my", "MALAYSIA"), ListActivity.MODE_PODCASTS);
        if (AddonManager.installed("radio"))
            addShelf(shelves, getString(R.string.radio_shelf), RadioApi::featured, ListActivity.MODE_RADIO);

        // ━━━━ Spotify Discovery ━━━━
        if (SpotifyAccount.loggedIn()) {
            addShelf(shelves, "Spotify • " + getString(R.string.dis_sp_new_releases),
                    DiscoverShelves::spNewReleases, null);
        }
    }

    // ── Shelf helpers ────────────────────────────────────────────

    private TrackAdapter cardAdapter() {
        final TrackAdapter[] ref = new TrackAdapter[1];
        ref[0] = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { Nav.open(requireActivity(), ref[0].items, pos); }

            @Override
            public void onMore(View anchor, int pos) {
                Track t = ref[0].items.get(pos);
                if (Nav.isContainer(t)) Nav.open(requireActivity(), ref[0].items, pos);
                else TrackMenu.show(requireActivity(), anchor, t, null);
            }
        }, true);
        return ref[0];
    }

    private void addShelf(LinearLayout parent, String title, Loader loader, String moreMode) {
        addShelf(parent, title, loader, moreMode, 30);
    }

    private void addShelf(LinearLayout parent, String title, Loader loader, String moreMode, int maxItems) {
        View shelf = inflater.inflate(R.layout.view_shelf, parent, false);
        TextView tv = shelf.findViewById(R.id.shelfTitle);
        tv.setText(moreMode != null ? title + "  >" : title);
        if (moreMode != null) {
            tv.setOnClickListener(v -> startActivity(
                    new Intent(getContext(), ListActivity.class).putExtra("mode", moreMode)));
        }
        ProgressBar pb = shelf.findViewById(R.id.shelfProgress);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        TrackAdapter ad = cardAdapter();
        rv.setAdapter(ad);
        parent.addView(shelf);
        App.IO.execute(() -> {
            try {
                List<Track> raw = loader.load();
                final List<Track> r = DiscoverShelves.filterHindi(raw);
                App.MAIN.post(() -> {
                    if (!isAdded()) return;
                    pb.setVisibility(View.GONE);
                    ad.set(r.size() > maxItems ? r.subList(0, maxItems) : r);
                    if (r.isEmpty()) shelf.setVisibility(View.GONE);
                });
            } catch (Throwable e) {
                App.MAIN.post(() -> { if (isAdded()) shelf.setVisibility(View.GONE); });
            }
        });
    }

    /** Rak VERTICAL — senarai menegak (thumbnail + tajuk + artis) gaya Quick Picks YTM */
    private void addVerticalShelf(LinearLayout parent, String title, Loader loader) {
        View shelf = inflater.inflate(R.layout.view_shelf, parent, false);
        ((TextView) shelf.findViewById(R.id.shelfTitle)).setText(title);
        ProgressBar pb = shelf.findViewById(R.id.shelfProgress);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        rv.setNestedScrollingEnabled(false);
        TrackAdapter ad = cardAdapter();
        ad.setVerticalMode(true);
        rv.setAdapter(ad);
        parent.addView(shelf);
        App.IO.execute(() -> {
            try {
                List<Track> raw = loader.load();
                final List<Track> r = DiscoverShelves.filterHindi(raw);
                App.MAIN.post(() -> {
                    if (!isAdded()) return;
                    pb.setVisibility(View.GONE);
                    // Quick Picks — 4 baris sahaja
                    ad.set(r.size() > 4 ? r.subList(0, 4) : r);
                    if (r.isEmpty()) shelf.setVisibility(View.GONE);
                });
            } catch (Throwable e) {
                App.MAIN.post(() -> { if (isAdded()) shelf.setVisibility(View.GONE); });
            }
        });
    }

    private void addLoginCard(LinearLayout parent) {
        View card = inflater.inflate(R.layout.item_row, parent, false);
        ImageView iv = card.findViewById(R.id.rowIcon);
        iv.setImageResource(R.drawable.ic_remote);
        Ui.tint(iv, Ui.attr(requireContext(), R.attr.mcAccent));
        ((TextView) card.findViewById(R.id.rowTitle)).setText(R.string.home_login_title);
        TextView sub = card.findViewById(R.id.rowSub);
        sub.setVisibility(View.VISIBLE);
        sub.setText(R.string.home_login_sub);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(requireContext(), 72));
        lp.setMargins(Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 20),
                Ui.dp(requireContext(), 14), 0);
        card.setLayoutParams(lp);
        card.setBackgroundResource(R.drawable.bg_surface);
        card.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).selectTab(3);
        });
        parent.addView(card);
    }

    // ── Header ───────────────────────────────────────────────────

    private void refreshHeader() {
        if (root == null) return;
        String name = YtmAccount.loggedIn() ? YtmAccount.name()
                : SpotifyAccount.loggedIn() ? SpotifyAccount.name() : "";
        TextView letter = root.findViewById(R.id.homeProfileLetter);
        ImageView icon = root.findViewById(R.id.homeProfileIcon);
        Ui.tint(icon, Ui.attr(requireContext(), R.attr.mcOnAccent));
        if (name != null && !name.isEmpty()) {
            letter.setText(name.substring(0, 1).toUpperCase());
            letter.setVisibility(View.VISIBLE);
            icon.setVisibility(View.GONE);
        } else {
            letter.setVisibility(View.GONE);
            icon.setVisibility(View.VISIBLE);
        }
    }

    /** Dipanggil dari MainActivity selepas addon sync dsb. */
    public void buildTiles() {
        checkRebuild();
    }

    // ── Lifecycle & rebuild ──────────────────────────────────────

    private void checkRebuild() {
        if (root == null) return;
        refreshHeader();
        if (!state().equals(builtFor)) {
            buildFilterChips();
            buildSpeedDial();
            buildShelves();
        } else {
            refreshRecent();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkRebuild();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) checkRebuild();
    }

    private void refreshRecent() {
        if (recentAdapter == null) return;
        List<Track> h = Store.history();
        recentAdapter.set(h.size() > 15 ? h.subList(0, 15) : h);
        recentShelf.setVisibility(h.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
