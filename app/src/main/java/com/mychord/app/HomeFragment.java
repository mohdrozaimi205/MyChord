package com.mychord.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {
    interface Loader { List<Track> load() throws Exception; }

    private View root;
    private LayoutInflater inflater;
    private TrackAdapter recentAdapter;
    private View recentShelf;
    private String builtFor = null;

    /** Filter chips gaya LastWave — label + carian */
    private static final String[][] FILTER_CHIPS = {
        {"All", ""},
        {"Relax", "relaxing calm peaceful chill"},
        {"Energize", "energetic workout pump up"},
        {"Feel good", "feel good happy upbeat"},
        {"Workout", "gym workout motivation fitness"},
        {"Indie", "indie alternative malaysia"},
        {"Fokus", "focus study concentration"},
        {"Romantik", "romantic love songs"},
        {"Malam", "late night chill vibes"},
        {"Nasyid", "nasyid islamic nasheed"},
        {"K-Pop", "kpop korean pop"},
        {"EDM", "edm electronic dance"},
        {"Rock", "rock music"},
    };

    /** Genre chips */
    private static final String[][] GENRE_CHIPS = {
        {"Pop", "pop music popular"},
        {"Rock", "rock music"},
        {"Hip Hop", "hip hop rap"},
        {"Indie", "indie alternative"},
        {"R&B", "r&b soul"},
        {"Dangdut", "dangdut melayu"},
        {"Nasyid", "nasyid islamic"},
        {"Jazz", "jazz music"},
        {"Klasik", "classical music"},
        {"EDM", "edm electronic dance"},
        {"K-Pop", "kpop korean pop"},
        {"Reggae", "reggae music"},
    };

    /** Mood landscape cards — emoji + label + warna + carian */
    private static final Object[][] MOOD_CARDS = {
        {"🎉", "Riang", 0xFFFF6B35, "upbeat happy party music"},
        {"😢", "Sedih", 0xFF5B8DEF, "sad emotional ballads"},
        {"🔥", "Tenaga", 0xFFE53935, "energetic workout pump up"},
        {"😌", "Tenang", 0xFF43A047, "calm relaxing peaceful"},
        {"💕", "Romantik", 0xFFEC407A, "romantic love songs"},
        {"🌙", "Malam", 0xFF5C6BC0, "late night chill vibes"},
        {"☀️", "Pagi", 0xFFFFA726, "morning fresh acoustic"},
        {"🏋️", "Senaman", 0xFFFF7043, "gym workout motivation"},
        {"📚", "Fokus", 0xFF26A69A, "focus study concentration"},
        {"🎸", "Rock", 0xFF8D6E63, "rock music"},
        {"🎤", "R&B", 0xFFAB47BC, "r&b soul"},
        {"🎹", "Klasik", 0xFF78909C, "classical music"},
        {"🎺", "Jazz", 0xFFD4A373, "jazz music"},
        {"🕌", "Nasyid", 0xFF66BB6A, "nasyid islamic nasheed"},
        {"🎧", "EDM", 0xFF00BCD4, "edm electronic dance"},
        {"🇲🇾", "Melayu", 0xFF1565C0, "lagu melayu popular terkini"},
        {"🌏", "K-Pop", 0xFFE040FB, "kpop korean pop"},
        {"🎵", "Indie", 0xFF26C6DA, "indie alternative"},
        {"🎭", "Soundtrack", 0xFF7E57C2, "ost soundtrack movie drama"},
        {"☕", "Santai", 0xFF8D6E63, "coffeehouse acoustic cafe chill"},
    };

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
        buildFilterChips();
        buildDateDisplay();
        buildHeroCard();
        buildTiles();
        buildGenreChips();
        buildMoodLandscape();
        buildShelves();
        return root;
    }

    /** Tanda keadaan semasa (akaun & addon) — bina semula rak bila berubah */
    private String state() {
        return YtmAccount.loggedIn() + "|" + SpotifyAccount.loggedIn() + "|" + CustomAddons.signature();
    }

    // ── 1. Filter chips di atas (gaya LastWave) ──────────────────

    private void buildFilterChips() {
        LinearLayout chips = root.findViewById(R.id.moodChips);
        chips.removeAllViews();

        for (int i = 0; i < FILTER_CHIPS.length; i++) {
            String[] chip = FILTER_CHIPS[i];
            TextView tv = (TextView) inflater.inflate(R.layout.item_mood_chip, chips, false);
            tv.setText(chip[0]);
            if (i == 0) {
                // "All" chip — highlighted by default
                tv.setBackgroundResource(R.drawable.bg_accent_pill);
                tv.setTextColor(Ui.attr(requireContext(), R.attr.mcOnAccent));
            }
            String query = chip[1];
            String label = chip[0];
            if (query.isEmpty()) {
                // All — just scroll to top
                tv.setOnClickListener(v -> {
                    root.findViewById(R.id.heroCard).requestFocus();
                });
            } else {
                tv.setOnClickListener(v -> {
                    Intent intent = new Intent(getContext(), ListActivity.class);
                    intent.putExtra("mode", "search");
                    intent.putExtra("query", query);
                    intent.putExtra("title", label);
                    startActivity(intent);
                });
            }
            chips.addView(tv);
        }
    }

    // ── 2. Date display ──────────────────────────────────────────

    private void buildDateDisplay() {
        TextView dateView = root.findViewById(R.id.homeDate);
        String locale = Prefs.lang().equals("en") ? "en" : "ms";
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM", new Locale(locale));
        dateView.setText(sdf.format(new Date()).toUpperCase());
    }

    // ── 3. Made For You hero card ────────────────────────────────

    private void buildHeroCard() {
        LinearLayout hero = root.findViewById(R.id.heroCard);
        hero.removeAllViews();

        View card = inflater.inflate(R.layout.item_tile, hero, false);
        ImageView iv = card.findViewById(R.id.tileIcon);
        iv.setImageResource(R.drawable.ic_play);
        Ui.tint(iv, Ui.attr(requireContext(), R.attr.mcOnAccent));
        ((TextView) card.findViewById(R.id.tileTitle)).setText(R.string.home_hero_title);
        ((TextView) card.findViewById(R.id.tileSub)).setText(R.string.home_hero_sub);

        int height = Ui.dp(requireContext(), 100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        card.setLayoutParams(lp);
        android.graphics.drawable.GradientDrawable heroGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.attr(requireContext(), R.attr.mcAccent), Ui.attr(requireContext(), R.attr.mcSurface2)});
        heroGrad.setCornerRadius(Ui.dp(requireContext(), 18));
        card.setBackground(heroGrad);
        card.setOnClickListener(v -> {
            // Main radio / mix peribadi
            App.IO.execute(() -> {
                try {
                    List<Track> mix;
                    if (YtmAccount.loggedIn()) {
                        mix = DiscoverShelves.ytmMixForYou();
                    } else {
                        mix = DiscoverShelves.algMyTerkini();
                    }
                    if (!mix.isEmpty()) App.MAIN.post(() -> {
                        if (isAdded()) Nav.open(requireActivity(), mix, 0);
                    });
                } catch (Exception ignored) {}
            });
        });
        hero.addView(card);
    }

    // ── 5. Genre chips ───────────────────────────────────────────

    private void buildGenreChips() {
        LinearLayout chips = root.findViewById(R.id.genreChips);
        chips.removeAllViews();

        for (String[] genre : GENRE_CHIPS) {
            TextView tv = (TextView) inflater.inflate(R.layout.item_mood_chip, chips, false);
            tv.setText(genre[0]);
            String query = genre[1];
            String label = genre[0];
            tv.setOnClickListener(v -> {
                Intent i = new Intent(getContext(), ListActivity.class);
                i.putExtra("mode", "search");
                i.putExtra("query", query);
                i.putExtra("title", label);
                startActivity(i);
            });
            chips.addView(tv);
        }
    }

    // ── 6. Mood & Genre landscape cards ──────────────────────────

    private void buildMoodLandscape() {
        LinearLayout container = root.findViewById(R.id.moodLandCards);
        container.removeAllViews();

        for (Object[] mood : MOOD_CARDS) {
            String emoji = (String) mood[0];
            String label = (String) mood[1];
            int color = (int) mood[2];
            String query = (String) mood[3];

            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            int w = Ui.dp(requireContext(), 130);
            int h = Ui.dp(requireContext(), 80);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMarginEnd(Ui.dp(requireContext(), 10));
            card.setLayoutParams(lp);
            card.setPadding(Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 8),
                    Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 8));

            // Background rounded gradient
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{color, darken(color, 0.3f)});
            gd.setCornerRadius(Ui.dp(requireContext(), 14));
            card.setBackground(gd);

            // Emoji
            TextView emojiTv = new TextView(requireContext());
            emojiTv.setText(emoji);
            emojiTv.setTextSize(24);
            emojiTv.setGravity(android.view.Gravity.CENTER);
            card.addView(emojiTv);

            // Label
            TextView labelTv = new TextView(requireContext());
            labelTv.setText(label);
            labelTv.setTextSize(13);
            labelTv.setTextColor(Color.WHITE);
            labelTv.setTypeface(null, android.graphics.Typeface.BOLD);
            labelTv.setGravity(android.view.Gravity.CENTER);
            card.addView(labelTv);

            card.setOnClickListener(v -> {
                Intent i = new Intent(getContext(), ListActivity.class);
                i.putExtra("mode", "search");
                i.putExtra("query", query);
                i.putExtra("title", label);
                startActivity(i);
            });
            container.addView(card);
        }
    }

    private static int darken(int color, float factor) {
        int r = (int) (Color.red(color) * (1 - factor));
        int g = (int) (Color.green(color) * (1 - factor));
        int b = (int) (Color.blue(color) * (1 - factor));
        return Color.argb(Color.alpha(color), r, g, b);
    }

    // ── Rak utama (shelves) — susunan gaya LastWave ────────────

    private void buildShelves() {
        builtFor = state();
        LinearLayout shelves = root.findViewById(R.id.shelves);
        shelves.removeAllViews();

        // ━━━━ Baru Dimainkan ━━━━
        recentShelf = inflater.inflate(R.layout.view_shelf, shelves, false);
        ((TextView) recentShelf.findViewById(R.id.shelfTitle)).setText(R.string.shelf_recent);
        recentShelf.findViewById(R.id.shelfProgress).setVisibility(View.GONE);
        RecyclerView recentList = recentShelf.findViewById(R.id.shelfList);
        recentList.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        recentAdapter = cardAdapter();
        recentList.setAdapter(recentAdapter);
        shelves.addView(recentShelf);
        refreshRecent();

        // ━━━━ Recent Artists — artis terkini (rounded besar) ━━━━
        buildRecentArtists(shelves);

        // ━━━━ Artist Spotlight — artis terkenal (rounded besar) ━━━━
        buildArtistSpotlight(shelves);

        // ━━━━ Artist For You — bubble artis Melayu terkenal ━━━━
        buildArtistForYou(shelves);

        // ━━━━ 1. Discover New Music (horizontal large cards + badge DISCOVER) ━━━━
        addShelf(shelves, getString(R.string.dis_discover_new), DiscoverShelves::discoverNewMusic, null);

        // ━━━━ 2. Quick Picks — VERTICAL list (lagu melayu) ━━━━
        if (YtmAccount.loggedIn()) {
            addVerticalShelf(shelves, getString(R.string.dis_quick_picks), DiscoverShelves::ytmQuickPicks);
        } else {
            addVerticalShelf(shelves, getString(R.string.dis_quick_picks), DiscoverShelves::fallbackQuickPicks);
        }

        // ━━━━ 3. Because You Listen ━━━━
        addShelf(shelves, getString(R.string.dis_because_you_listen), DiscoverShelves::becauseYouListen, null);

        // ━━━━ 4. Sambung Dengar / Jump Back In ━━━━
        addShelf(shelves, getString(R.string.dis_jump_back), DiscoverShelves::jumpBackIn, null);

        // ━━━━ 5. Lagu dari akaun ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.acc_for_you), YtmAccount::home, ListActivity.MODE_YTM_HOME);
            addShelf(shelves, "YouTube Music • " + getString(R.string.tile_liked), YtmAccount::liked, ListActivity.MODE_YTM_LIKED);
        }
        if (SpotifyAccount.loggedIn()) {
            addShelf(shelves, "Spotify • " + getString(R.string.tile_liked), SpotifyAccount::liked, ListActivity.MODE_SP_LIKED);
        }
        if (!YtmAccount.loggedIn() && !SpotifyAccount.loggedIn()) addLoginCard(shelves);

        // Addon sendiri
        for (JSONObject a : CustomAddons.all()) {
            addShelf(shelves, a.optString("name"), () -> CustomAddons.tracks(a), null);
        }

        // ━━━━ 80% MALAYSIA — Lagu Melayu Popular & Terkini ━━━━
        addShelf(shelves, getString(R.string.alg_my_popular), DiscoverShelves::algMyPopular, null);
        addShelf(shelves, getString(R.string.alg_my_terkini), DiscoverShelves::algMyTerkini, null);

        // ━━━━ Lagu Melayu Baru (Daily Discover) ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_daily_discover), DiscoverShelves::ytmDailyDiscover, null);
        }

        // ━━━━ Lagu Artis Melayu Terhebat (Albums For You) ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_albums_for_you), DiscoverShelves::ytmAlbumsForYou, null);
        }

        // ━━━━ 80% MALAYSIA — 90an & Klasik ━━━━
        addShelf(shelves, getString(R.string.alg_my_90an), DiscoverShelves::algMy90an, null);
        addShelf(shelves, getString(R.string.alg_my_nostalgia), DiscoverShelves::algMyNostalgia, null);

        // ━━━━ DISCOVERY: Listen Again ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_listen_again), DiscoverShelves::ytmListenAgain, null);
        } else {
            addShelf(shelves, getString(R.string.dis_listen_again), DiscoverShelves::fallbackListenAgain, null);
        }

        // ━━━━ 80% MALAYSIA — Nasyid, Indie, Rock, Dangdut ━━━━
        addShelf(shelves, getString(R.string.alg_my_nasyid), DiscoverShelves::algMyNasyid, null);
        addShelf(shelves, getString(R.string.alg_my_indie), DiscoverShelves::algMyIndie, null);

        // ━━━━ Forgotten Favourites ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_forgotten_fav), DiscoverShelves::ytmForgottenFavourites, null);
        }

        // ━━━━ 10% ENGLISH — Popular & Classics ━━━━
        addShelf(shelves, getString(R.string.alg_en_popular), DiscoverShelves::algEnPopular, null);
        addShelf(shelves, getString(R.string.alg_en_90s), DiscoverShelves::algEn90s, null);

        // ━━━━ Artist Spotlight ━━━━
        addShelf(shelves, getString(R.string.dis_artist_spotlight), DiscoverShelves::artistSpotlight, null);

        // ━━━━ 80% MALAYSIA — Rock Kapak & Dangdut ━━━━
        addShelf(shelves, getString(R.string.alg_my_rock), DiscoverShelves::algMyRock, null);
        addShelf(shelves, getString(R.string.alg_my_dangdut), DiscoverShelves::algMyDangdut, null);

        // ━━━━ Keluaran Baharu (70% MY 20% EN) ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_ytm_new_releases), DiscoverShelves::ytmNewReleases, null);
        } else {
            addShelf(shelves, getString(R.string.dis_ytm_new_releases), DiscoverShelves::fallbackNewReleases, null);
        }

        // ━━━━ Dari Komuniti (GPS location) ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_from_community), DiscoverShelves::ytmFromCommunity, null);
        }

        // ━━━━ 5% SOUNDTRACK ━━━━
        addShelf(shelves, getString(R.string.alg_ost_my), DiscoverShelves::algOstMy, null);
        addShelf(shelves, getString(R.string.alg_ost_kr), DiscoverShelves::algOstKr, null);
        addShelf(shelves, getString(R.string.alg_ost_jp), DiscoverShelves::algOstJp, null);
        addShelf(shelves, getString(R.string.alg_ost_hw), DiscoverShelves::algOstHw, null);

        // ━━━━ 5% RILEKS ━━━━
        addShelf(shelves, getString(R.string.alg_chill_coffee), DiscoverShelves::algChillCoffee, null);
        addShelf(shelves, getString(R.string.alg_chill_lofi), DiscoverShelves::algChillLofi, null);

        // ━━━━ Podcast & Radio ━━━━
        addShelf(shelves, getString(R.string.pod_shelf), () -> PodcastApi.top("my", "MALAYSIA"), ListActivity.MODE_PODCASTS);
        if (AddonManager.installed("radio"))
            addShelf(shelves, getString(R.string.radio_shelf), RadioApi::featured, ListActivity.MODE_RADIO);

        // ━━━━ YTM Discovery tambahan ━━━━
        if (YtmAccount.loggedIn()) {
            addShelf(shelves, getString(R.string.dis_trending), DiscoverShelves::ytmTrending, null);
            addShelf(shelves, getString(R.string.dis_mix_for_you), DiscoverShelves::ytmMixForYou, null);
            addShelf(shelves, getString(R.string.dis_similar_to), DiscoverShelves::ytmSimilarTo, null);
            addShelf(shelves, getString(R.string.dis_fresh_old), DiscoverShelves::ytmFreshOldFavs, null);
            addShelf(shelves, getString(R.string.dis_live_perf), DiscoverShelves::ytmLivePerformance, null);
        } else {
            addShelf(shelves, getString(R.string.dis_trending), DiscoverShelves::fallbackTrending, null);
            addShelf(shelves, getString(R.string.dis_live_perf), DiscoverShelves::fallbackLivePerformance, null);
        }

        // ━━━━ Spotify Discovery tambahan ━━━━
        if (SpotifyAccount.loggedIn()) {
            addShelf(shelves, "Spotify • " + getString(R.string.dis_popular_radio), DiscoverShelves::spPopularRadio, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_sp_new_releases), DiscoverShelves::spNewReleases, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_coffeehouse), DiscoverShelves::spCoffeehouse, null);
            addShelf(shelves, "Spotify • " + getString(R.string.dis_ost), DiscoverShelves::spOstSoundtrack, null);
        } else {
            addShelf(shelves, getString(R.string.dis_coffeehouse), DiscoverShelves::fallbackCoffeehouse, null);
            addShelf(shelves, getString(R.string.dis_ost), DiscoverShelves::fallbackOST, null);
        }
    }

    // ── Recent Artists — rounded large cards (max 10) ─────────
    private void buildRecentArtists(LinearLayout parent) {
        List<Store.RecentArtist> artists = Store.recentArtists();
        if (artists.isEmpty()) return;

        View shelf = inflater.inflate(R.layout.view_shelf, parent, false);
        ((TextView) shelf.findViewById(R.id.shelfTitle)).setText("Artis Terkini");
        shelf.findViewById(R.id.shelfProgress).setVisibility(View.GONE);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));

        LinearLayout cards = new LinearLayout(requireContext());
        cards.setOrientation(LinearLayout.HORIZONTAL);

        for (Store.RecentArtist ra : artists) {
            View card = buildArtistCard(ra.name, ra.art);
            cards.addView(card);
        }

        // Ganti RecyclerView dengan ScrollView berisi kad
        rv.setVisibility(View.GONE);
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.addView(cards);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsv.setLayoutParams(hsvLp);
        ((ViewGroup) rv.getParent()).addView(hsv);

        parent.addView(shelf);
    }

    // ── Artist Spotlight — artis terkenal Melayu (rounded besar) ─
    private static final String[][] SPOTLIGHT_ARTISTS = {
        {"Yuna", "yuna malaysian singer"},
        {"Hael Husaini", "hael husaini"},
        {"Dato Sri Siti Nurhaliza", "siti nurhaliza"},
        {"Faizal Tahir", "faizal tahir"},
        {"Sheila Majid", "sheila majid"},
        {"Dayang Nurfaizah", "dayang nurfaizah"},
        {"Alif Satar", "alif satar"},
        {"Nabila Razali", "nabila razali"},
        {"Aina Abdul", "aina abdul"},
        {"Khai Bahar", "khai bahar"},
    };

    private void buildArtistSpotlight(LinearLayout parent) {
        View shelf = inflater.inflate(R.layout.view_shelf, parent, false);
        ((TextView) shelf.findViewById(R.id.shelfTitle)).setText("Artist Spotlight");
        shelf.findViewById(R.id.shelfProgress).setVisibility(View.GONE);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        rv.setVisibility(View.GONE);

        LinearLayout cards = new LinearLayout(requireContext());
        cards.setOrientation(LinearLayout.HORIZONTAL);

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.addView(cards);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsv.setLayoutParams(hsvLp);
        ((ViewGroup) rv.getParent()).addView(hsv);

        parent.addView(shelf);

        // Muat gambar artis dari carian
        App.IO.execute(() -> {
            for (String[] sa : SPOTLIGHT_ARTISTS) {
                try {
                    List<Track> res = YtMusic.search(sa[1]);
                    if (res.isEmpty()) continue;
                    final String name = sa[0];
                    final String art = res.get(0).art;
                    App.MAIN.post(() -> {
                        if (!isAdded()) return;
                        View card = buildArtistCard(name, art);
                        cards.addView(card);
                    });
                } catch (Exception ignored) { }
            }
        });
    }

    // ── Artist For You — bubble artis Melayu terkenal masa kini ──
    private static final String[][] ARTIST_FOR_YOU = {
        {"Yuna"}, {"Hael Husaini"}, {"Dato Sri Siti Nurhaliza"},
        {"Faizal Tahir"}, {"Alif Satar"}, {"Nabila Razali"},
        {"Aina Abdul"}, {"Khai Bahar"}, {"Dayang Nurfaizah"},
        {"Ismail Izzani"}, {"Naim Daniel"}, {"Syamel"},
        {"Hafiz Suip"}, {"Ara Johari"}, {"Siti Nordiana"},
        {"Ayda Jebat"}, {"Neelofa"}, {"Ernie Zakri"},
        {"Tajul"}, {"Wany Hasrita"},
    };

    private void buildArtistForYou(LinearLayout parent) {
        // Tajuk
        TextView titleTv = new TextView(requireContext());
        titleTv.setText("Artis Untuk Anda");
        titleTv.setTextSize(18);
        titleTv.setTextColor(Ui.attr(requireContext(), R.attr.mcText));
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(Ui.dp(requireContext(), 16), Ui.dp(requireContext(), 20), 0, Ui.dp(requireContext(), 8));
        titleTv.setLayoutParams(titleLp);
        parent.addView(titleTv);

        // Sub-tajuk
        TextView subTv = new TextView(requireContext());
        subTv.setText("Artis Melayu Terkenal Masa Kini");
        subTv.setTextSize(13);
        subTv.setTextColor(Ui.attr(requireContext(), R.attr.mcTextDim));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(Ui.dp(requireContext(), 16), 0, 0, Ui.dp(requireContext(), 10));
        subTv.setLayoutParams(subLp);
        parent.addView(subTv);

        // Bubble chips — FlowLayout (wrap content)
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setPadding(Ui.dp(requireContext(), 16), 0, Ui.dp(requireContext(), 16), 0);
        hsv.setClipToPadding(false);

        // Wrap layout untuk bubbles — gunakan GridLayout
        // Guna LinearLayout sahaja (scroll horizontal)
        LinearLayout bubbles = new LinearLayout(requireContext());
        bubbles.setOrientation(LinearLayout.HORIZONTAL);

        for (String[] a : ARTIST_FOR_YOU) {
            TextView chip = new TextView(requireContext());
            chip.setText(a[0]);
            chip.setTextSize(14);
            chip.setTextColor(Ui.attr(requireContext(), R.attr.mcText));
            int px8 = Ui.dp(requireContext(), 8);
            int px16 = Ui.dp(requireContext(), 16);
            chip.setPadding(px16, px8, px16, px8);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(Ui.dp(requireContext(), 20));
            bg.setColor(Ui.attr(requireContext(), R.attr.mcSurface));
            bg.setStroke(Ui.dp(requireContext(), 1), Ui.attr(requireContext(), R.attr.mcAccent) & 0x40FFFFFF | 0x40000000);
            chip.setBackground(bg);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMarginEnd(Ui.dp(requireContext(), 8));
            chip.setLayoutParams(clp);

            String artistName = a[0];
            chip.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(getContext(), ArtistActivity.class);
                i.putExtra(ArtistActivity.EXTRA_ARTIST, artistName);
                startActivity(i);
            });

            bubbles.addView(chip);
        }

        hsv.addView(bubbles);
        parent.addView(hsv);
    }

    /** Kad artis rounded besar — digunakan oleh Recent Artists & Artist Spotlight */
    private View buildArtistCard(String name, String artUrl) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        int size = Ui.dp(requireContext(), 110);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(requireContext(), 12));
        card.setLayoutParams(lp);
        card.setPadding(0, Ui.dp(requireContext(), 8), 0, Ui.dp(requireContext(), 8));

        // Gambar bulat
        ImageView img = new ImageView(requireContext());
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(size, size);
        img.setLayoutParams(imgLp);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // Rounded corners
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            img.setClipToOutline(true);
            img.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(requireContext(), 22));
                }
            });
        }

        if (artUrl != null && !artUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this).load(artUrl).centerCrop().into(img);
        } else {
            img.setBackgroundColor(Ui.attr(requireContext(), R.attr.mcSurface));
        }
        card.addView(img);

        // Nama
        TextView nameTv = new TextView(requireContext());
        nameTv.setText(name);
        nameTv.setTextSize(13);
        nameTv.setTextColor(Ui.attr(requireContext(), R.attr.mcText));
        nameTv.setGravity(android.view.Gravity.CENTER);
        nameTv.setMaxLines(2);
        nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(size, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = Ui.dp(requireContext(), 6);
        nameTv.setLayoutParams(nameLp);
        card.addView(nameTv);

        card.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(getContext(), ArtistActivity.class);
            i.putExtra(ArtistActivity.EXTRA_ARTIST, name);
            i.putExtra(ArtistActivity.EXTRA_ART, artUrl);
            startActivity(i);
        });

        return card;
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
        tv.setText(moreMode != null ? title + "  >" : title);
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
                List<Track> raw = loader.load();
                // Tapis Hindi/India
                final List<Track> r = DiscoverShelves.filterHindi(raw);
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

    /** Rak VERTICAL — senarai menegak (thumbnail + tajuk + artis) gaya Quick Picks LastWave */
    private void addVerticalShelf(LinearLayout parent, String title, Loader loader) {
        View shelf = inflater.inflate(R.layout.view_shelf, parent, false);
        ((TextView) shelf.findViewById(R.id.shelfTitle)).setText(title);
        ProgressBar pb = shelf.findViewById(R.id.shelfProgress);
        RecyclerView rv = shelf.findViewById(R.id.shelfList);
        // VERTICAL layout — bukan horizontal
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
                    // Quick Picks — papar 10 sahaja dalam bentuk vertikal
                    ad.set(r.size() > 10 ? r.subList(0, 10) : r);
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
            buildFilterChips();
            buildDateDisplay();
            buildHeroCard();
            buildTiles();
            buildGenreChips();
            buildMoodLandscape();
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
