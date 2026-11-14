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

import java.util.Calendar;
import java.util.List;

public class HomeFragment extends Fragment {
    interface Loader { List<Track> load() throws Exception; }

    private View root;
    private LayoutInflater inflater;
    private TrackAdapter recentAdapter;
    private View recentShelf;
    private String builtFor = null;

    private static final int[] SHELF_TITLES = {R.string.shelf_malay, R.string.shelf_top, R.string.shelf_indie,
            R.string.shelf_chill, R.string.shelf_nasyid};
    private static final String[] SHELF_QUERIES = {"lagu melayu terkini popular", "top hits global 2026",
            "indie malaysia", "lofi chill santai", "nasyid terbaik"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        inflater = inf;
        root = inf.inflate(R.layout.fragment_home, c, false);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        ((TextView) root.findViewById(R.id.greet)).setText(
                h < 12 ? R.string.greet_morning : h < 19 ? R.string.greet_afternoon : R.string.greet_evening);
        root.findViewById(R.id.homeShare).setOnClickListener(v -> ShareUtil.shareApp(requireActivity()));
        root.findViewById(R.id.homeRecognize).setOnClickListener(v -> startActivity(new Intent(getContext(), RecognizeActivity.class)));
        root.findViewById(R.id.homeProfile).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).selectTab(3);
        });
        refreshHeader();
        buildTiles();
        buildShelves();
        return root;
    }

    /** Tanda keadaan semasa (akaun & addon) — bina semula rak bila berubah */
    private String state() {
        return YtmAccount.loggedIn() + "|" + SpotifyAccount.loggedIn() + "|" + CustomAddons.signature();
    }

    private void buildShelves() {
        builtFor = state();
        LinearLayout shelves = root.findViewById(R.id.shelves);
        shelves.removeAllViews();

        recentShelf = inflater.inflate(R.layout.view_shelf, shelves, false);
        ((TextView) recentShelf.findViewById(R.id.shelfTitle)).setText(R.string.shelf_recent);
        recentShelf.findViewById(R.id.shelfProgress).setVisibility(View.GONE);
        RecyclerView recentList = recentShelf.findViewById(R.id.shelfList);
        recentList.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        recentAdapter = cardAdapter();
        recentList.setAdapter(recentAdapter);
        shelves.addView(recentShelf);
        refreshRecent();

        // Lagu dari akaun (sync terus)
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.acc_for_you), YtmAccount::home, ListActivity.MODE_YTM_HOME);
            addShelf(shelves, "YouTube Music • " + getString(R.string.tile_liked), YtmAccount::liked, ListActivity.MODE_YTM_LIKED);
            addShelf(shelves, "YouTube Music • " + getString(R.string.acc_my_playlists), YtmAccount::playlists, ListActivity.MODE_YTM_LISTS);
        }
        if (SpotifyAccount.loggedIn()) {
            addShelf(shelves, "Spotify • " + getString(R.string.tile_liked), SpotifyAccount::liked, ListActivity.MODE_SP_LIKED);
            addShelf(shelves, "Spotify • " + getString(R.string.acc_my_playlists), SpotifyAccount::playlists, ListActivity.MODE_SP_LISTS);
        }
        if (!YtmAccount.loggedIn() && !SpotifyAccount.loggedIn()) addLoginCard(shelves);

        // Addon tambahan sendiri
        for (JSONObject a : CustomAddons.all()) {
            addShelf(shelves, a.optString("name"), () -> CustomAddons.tracks(a), null);
        }

        addShelf(shelves, getString(R.string.pod_shelf), () -> PodcastApi.top("my", "MALAYSIA"), ListActivity.MODE_PODCASTS);

        // ── Radio Malaysia Popular ──
        if (AddonManager.installed("radio"))
            addShelf(shelves, getString(R.string.radio_shelf), RadioApi::featured, ListActivity.MODE_RADIO);

        // ── YouTube Music Discovery ──
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_listen_again), DiscoverShelves::ytmListenAgain, null);
            addShelf(shelves, getString(R.string.dis_forgotten_fav), DiscoverShelves::ytmForgottenFavourites, null);
            addShelf(shelves, getString(R.string.dis_daily_discover), DiscoverShelves::ytmDailyDiscover, null);
            addShelf(shelves, getString(R.string.dis_albums_for_you), DiscoverShelves::ytmAlbumsForYou, null);
            addShelf(shelves, getString(R.string.dis_ytm_new_releases), DiscoverShelves::ytmNewReleases, null);
            addShelf(shelves, getString(R.string.dis_from_community), DiscoverShelves::ytmFromCommunity, null);
            addShelf(shelves, getString(R.string.dis_quick_picks), DiscoverShelves::ytmQuickPicks, null);
            addShelf(shelves, getString(R.string.dis_similar_to), DiscoverShelves::ytmSimilarTo, null);
            addShelf(shelves, getString(R.string.dis_trending), DiscoverShelves::ytmTrending, null);
            addShelf(shelves, getString(R.string.dis_mix_for_you), DiscoverShelves::ytmMixForYou, null);
            addShelf(shelves, getString(R.string.dis_fresh_old), DiscoverShelves::ytmFreshOldFavs, null);
            addShelf(shelves, getString(R.string.dis_live_perf), DiscoverShelves::ytmLivePerformance, null);
        } else {
            // Versi tanpa akaun YTM
            addShelf(shelves, getString(R.string.dis_quick_picks), DiscoverShelves::fallbackQuickPicks, null);
            addShelf(shelves, getString(R.string.dis_ytm_new_releases), DiscoverShelves::fallbackNewReleases, null);
            addShelf(shelves, getString(R.string.dis_trending), DiscoverShelves::fallbackTrending, null);
            addShelf(shelves, getString(R.string.dis_live_perf), DiscoverShelves::fallbackLivePerformance, null);
        }

        // ── Spotify Discovery ──
        if (SpotifyAccount.loggedIn()) {
            addShelf(shelves, "Spotify • " + getString(R.string.dis_popular_radio), DiscoverShelves::spPopularRadio, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_popular_artists), DiscoverShelves::spPopularArtists, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_sp_new_releases), DiscoverShelves::spNewReleases, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_new_artists), DiscoverShelves::spNewArtists, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_popular_albums), DiscoverShelves::spPopularAlbums, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_listen_before_watch), DiscoverShelves::spListenBeforeWatch, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_coffeehouse), DiscoverShelves::spCoffeehouse, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_ost), DiscoverShelves::spOstSoundtrack, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_other_countries), DiscoverShelves::spOtherCountries, null);
        } else {
            // Versi tanpa akaun Spotify
            addShelf(shelves, getString(R.string.dis_coffeehouse), DiscoverShelves::fallbackCoffeehouse, null);
            addShelf(shelves, getString(R.string.dis_ost), DiscoverShelves::fallbackOST, null);
            addShelf(shelves, getString(R.string.dis_other_countries), DiscoverShelves::fallbackOtherCountries, null);
        }

        // ── Rak carian asal ──
        for (int i = 0; i < SHELF_TITLES.length; i++) {
            String q = SHELF_QUERIES[i];
            addShelf(shelves, getString(SHELF_TITLES[i]), () -> YtMusic.search(q), null);
        }
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(requireContext(), 72));
        lp.setMargins(Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 20), Ui.dp(requireContext(), 14), 0);
        card.setLayoutParams(lp);
        card.setBackgroundResource(R.drawable.bg_surface);
        card.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).selectTab(3);
        });
        parent.addView(card);
    }

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
        View shelf = inflater.inflate(R.layout.view_shelf, parent, false);
        TextView tv = shelf.findViewById(R.id.shelfTitle);
        tv.setText(moreMode != null ? title + "  ›" : title);
        if (moreMode != null) {
            tv.setOnClickListener(v -> startActivity(new Intent(getContext(), ListActivity.class).putExtra("mode", moreMode)));
        }
        ProgressBar pb = shelf.findViewById(R.id.shelfProgress);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        TrackAdapter ad = cardAdapter();
        rv.setAdapter(ad);
        parent.addView(shelf);
        App.IO.execute(() -> {
            try {
                List<Track> r = loader.load();
                App.MAIN.post(() -> {
                    if (!isAdded()) return;
                    pb.setVisibility(View.GONE);
                    ad.set(r.size() > 30 ? r.subList(0, 30) : r);
                    if (r.isEmpty()) shelf.setVisibility(View.GONE);
                });
            } catch (Throwable e) {
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
        addTile(g, R.drawable.ic_podcast, R.string.pod_title, R.string.pod_sub, ListActivity.MODE_PODCASTS);
        addTile(g, R.drawable.ic_tune, R.string.moods_title, R.string.moods_pick, "moods");
        addTile(g, R.drawable.ic_together, R.string.tg_title,
                ListenTogether.mode() == ListenTogether.OFF ? R.string.tg_sub : R.string.tg_sub_on, "together");
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
            else if ("together".equals(mode)) TogetherDialog.show(requireActivity(), this::buildTiles);
            else if ("moods".equals(mode)) startActivity(new Intent(getContext(), MoodsActivity.class));
            else startActivity(new Intent(getContext(), ListActivity.class).putExtra("mode", mode));
        });
        g.addView(v);
    }

    private void refreshHeader() {
        if (root == null) return;
        root.findViewById(R.id.homeRecognize).setBackgroundResource(IconSwitcher.preview());
        String name = YtmAccount.loggedIn() ? YtmAccount.name() : SpotifyAccount.loggedIn() ? SpotifyAccount.name() : "";
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

    private void checkRebuild() {
        if (root == null) return;
        refreshHeader();
        if (!state().equals(builtFor)) {
            buildTiles();
            buildShelves();
        } else refreshRecent();
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
