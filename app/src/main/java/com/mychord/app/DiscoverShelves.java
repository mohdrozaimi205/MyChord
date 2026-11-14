package com.mychord.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rak penemuan (discovery shelves) — gaya YouTube Music & Spotify.
 * Semua kaedah berjalan di thread latar (IO).
 */
public class DiscoverShelves {

    // ─── YouTube Music discovery (via InnerTube browse) ─────────────

    /** Dengar Semula / Listen Again — lagu yang pernah dimainkan di akaun */
    public static List<Track> ytmListenAgain() throws Exception {
        return sectionByTitle(YtmAccount.browse("FEmusic_home"), "Listen again", "Dengar semula");
    }

    /** Kegemaran Terlupa / Forgotten Favourites */
    public static List<Track> ytmForgottenFavourites() throws Exception {
        return sectionByTitle(YtmAccount.browse("FEmusic_home"), "Forgotten favourites", "Kegemaran terlupa");
    }

    /** Penemuan Harian / Daily Discover — cadangan harian baru */
    public static List<Track> ytmDailyDiscover() throws Exception {
        List<Track> home = sectionByTitle(YtmAccount.browse("FEmusic_home"), "Discover Mix", "Mixed for you");
        if (home.isEmpty()) home = YtMusic.search("discover weekly new music 2026");
        return home;
    }

    /** Album Untuk Anda / Albums For You */
    public static List<Track> ytmAlbumsForYou() throws Exception {
        List<Track> r = sectionByTitle(YtmAccount.browse("FEmusic_home"), "Recommended albums", "Albums for you");
        if (r.isEmpty()) r = YtmAccount.parse(YtmAccount.browse("FEmusic_new_releases_albums"), true, true);
        return r;
    }

    /** Keluaran Baharu / New Releases */
    public static List<Track> ytmNewReleases() throws Exception {
        return YtmAccount.parse(YtmAccount.browse("FEmusic_new_releases"), true, true);
    }

    /** Dari Komuniti / From The Community */
    public static List<Track> ytmFromCommunity() throws Exception {
        List<Track> r = sectionByTitle(YtmAccount.browse("FEmusic_home"), "From the community", "Dari komuniti");
        if (r.isEmpty()) r = YtMusic.search("trending music community playlist 2026");
        return r;
    }

    /** Pilihan Pantas / Quick Picks */
    public static List<Track> ytmQuickPicks() throws Exception {
        List<Track> r = sectionByTitle(YtmAccount.browse("FEmusic_home"), "Quick picks", "Pilihan pantas");
        if (r.isEmpty()) r = YtMusic.search("quick picks popular songs");
        return r;
    }

    /** Serupa Dengan / Similar To — berdasarkan lagu terakhir dimainkan */
    public static List<Track> ytmSimilarTo() throws Exception {
        List<Track> history = Store.history();
        if (!history.isEmpty()) {
            Track last = history.get(0);
            String q = (last.artist != null ? last.artist : "") + " " + (last.title != null ? last.title : "");
            List<Track> r = YtMusic.search(q.trim() + " similar");
            // buang lagu yang sama
            r.removeIf(t -> t.mediaId.equals(last.mediaId));
            return r;
        }
        return YtMusic.search("recommended similar songs trending");
    }

    /** Trending Untuk Anda / Trending For You */
    public static List<Track> ytmTrending() throws Exception {
        return YtmAccount.parse(YtmAccount.browse("FEmusic_charts"), true, true);
    }

    /** Campuran Untuk Anda / Mix For You */
    public static List<Track> ytmMixForYou() throws Exception {
        List<Track> r = sectionByTitle(YtmAccount.browse("FEmusic_home"), "Mixed for you", "Campuran untuk anda");
        if (r.isEmpty()) r = YtMusic.search("my mix playlist popular");
        return r;
    }

    /** Cari Yang Segar & Lama / Find Fresh, Old Favourites */
    public static List<Track> ytmFreshOldFavs() throws Exception {
        List<Track> r = sectionByTitle(YtmAccount.browse("FEmusic_home"),
                "Fresh new music", "Old favourites", "Segar", "Kegemaran lama");
        if (r.isEmpty()) r = YtMusic.search("throwback hits classic favourites");
        return r;
    }

    /** Persembahan Langsung / Live Performance */
    public static List<Track> ytmLivePerformance() throws Exception {
        return YtMusic.search("live performance concert official");
    }

    // ─── Spotify discovery (via Spotify Browse / Search / Categories) ──

    /** Radio Popular / Popular Radio */
    public static List<Track> spPopularRadio() throws Exception {
        return spCategoryPlaylists("radio", "Popular Radio");
    }

    /** Artis Popular / Popular Artists */
    public static List<Track> spPopularArtists() throws Exception {
        return spSearch("top artists popular 2026", "track");
    }

    /** Keluaran Baharu Spotify / New Releases */
    public static List<Track> spNewReleases() throws Exception {
        return spFetchNewReleases();
    }

    /** Artis Baharu / New Artists */
    public static List<Track> spNewArtists() throws Exception {
        return spSearch("new artist debut 2026", "track");
    }

    /** Album & Single Popular / Popular Albums And Singles */
    public static List<Track> spPopularAlbums() throws Exception {
        return spSearch("top album single popular 2026", "track");
    }

    /** Dengar Sebelum Tonton / Listen Before You Watch */
    public static List<Track> spListenBeforeWatch() throws Exception {
        return spSearch("movie soundtrack series 2026 official", "track");
    }

    /** Coffeehouse */
    public static List<Track> spCoffeehouse() throws Exception {
        List<Track> r = spCategoryPlaylists("coffeehouse", "Coffeehouse");
        if (r.isEmpty()) r = spSearch("coffeehouse acoustic chill", "track");
        return r;
    }

    /** OST / Soundtrack */
    public static List<Track> spOstSoundtrack() throws Exception {
        return spSearch("official soundtrack OST movie drama 2026", "track");
    }

    /** Negara Lain / Other Countries */
    public static List<Track> spOtherCountries() throws Exception {
        return spSearch("top hits worldwide kpop jpop latin afrobeat", "track");
    }

    // ─── Versi tanpa akaun (guna YtMusic.search sahaja) ─────────

    public static List<Track> fallbackListenAgain() throws Exception {
        List<Track> h = Store.history();
        return h.size() > 15 ? h.subList(0, 15) : h;
    }

    public static List<Track> fallbackQuickPicks() throws Exception {
        return YtMusic.search("popular music quick picks trending");
    }

    public static List<Track> fallbackNewReleases() throws Exception {
        return YtMusic.search("new release music 2026");
    }

    public static List<Track> fallbackTrending() throws Exception {
        return YtMusic.search("trending music 2026 viral");
    }

    public static List<Track> fallbackLivePerformance() throws Exception {
        return YtMusic.search("live performance concert official");
    }

    public static List<Track> fallbackCoffeehouse() throws Exception {
        return YtMusic.search("coffeehouse acoustic cafe chill");
    }

    public static List<Track> fallbackOST() throws Exception {
        return YtMusic.search("movie soundtrack OST drama 2026");
    }

    public static List<Track> fallbackOtherCountries() throws Exception {
        return YtMusic.search("kpop jpop latin afrobeat world music hits");
    }

    // ─── Helper ─────────────────────────────────────────────────

    /** Cari rak mengikut tajuk dalam respons InnerTube browse */
    private static List<Track> sectionByTitle(JSONObject root, String... titles) {
        List<JSONObject> sections = new ArrayList<>();
        Json.collect(root, "musicCarouselShelfRenderer", sections);
        Json.collect(root, "musicShelfRenderer", sections);

        for (JSONObject section : sections) {
            Object headerObj = Json.path(section, "header");
            if (headerObj == null) headerObj = section;
            String sectionTitle = "";
            Object t = Json.find(headerObj, "title");
            if (t != null) sectionTitle = Json.runs(t);

            for (String match : titles) {
                if (sectionTitle.toLowerCase().contains(match.toLowerCase())) {
                    return YtmAccount.parse(section, true, true);
                }
            }
        }
        return new ArrayList<>();
    }

    /** Spotify: cari trek */
    private static List<Track> spSearch(String query, String type) throws Exception {
        // Guna token Spotify
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url("https://api.spotify.com/v1/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                        + "&type=" + type + "&limit=30&market=MY")
                .header("Authorization", "Bearer " + spToken())
                .build();
        try (okhttp3.Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
            JSONObject o = new JSONObject(r.body().string());
            JSONObject tracks = o.optJSONObject("tracks");
            if (tracks == null) return new ArrayList<>();
            JSONArray items = tracks.optJSONArray("items");
            List<Track> out = new ArrayList<>();
            if (items != null) for (int i = 0; i < items.length(); i++) {
                Track t = spTrack(items.optJSONObject(i));
                if (t != null) out.add(t);
            }
            return out;
        }
    }

    /** Spotify: new releases */
    private static List<Track> spFetchNewReleases() throws Exception {
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url("https://api.spotify.com/v1/browse/new-releases?country=MY&limit=30")
                .header("Authorization", "Bearer " + spToken())
                .build();
        try (okhttp3.Response r = App.http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
            JSONObject o = new JSONObject(r.body().string());
            JSONObject albums = o.optJSONObject("albums");
            if (albums == null) return new ArrayList<>();
            JSONArray items = albums.optJSONArray("items");
            List<Track> out = new ArrayList<>();
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject a = items.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name");
                String art = "";
                JSONArray imgs = a.optJSONArray("images");
                if (imgs != null && imgs.length() > 0) art = imgs.optJSONObject(0).optString("url");
                StringBuilder artists = new StringBuilder();
                JSONArray ar = a.optJSONArray("artists");
                if (ar != null) for (int j = 0; j < ar.length(); j++) {
                    if (j > 0) artists.append(", ");
                    artists.append(ar.optJSONObject(j).optString("name"));
                }
                String first = (ar != null && ar.length() > 0) ? ar.optJSONObject(0).optString("name") : "";
                Track t = new Track("q:" + (first.isEmpty() ? "" : first + " - ") + name,
                        name, artists.toString(), art);
                t.badge = "SPOTIFY";
                out.add(t);
            }
            return out;
        }
    }

    /** Spotify: category playlists & lagu pertama */
    private static List<Track> spCategoryPlaylists(String categoryId, String fallbackQuery) throws Exception {
        try {
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url("https://api.spotify.com/v1/browse/categories/" + categoryId + "/playlists?country=MY&limit=10")
                    .header("Authorization", "Bearer " + spToken())
                    .build();
            try (okhttp3.Response r = App.http.newCall(req).execute()) {
                if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
                JSONObject o = new JSONObject(r.body().string());
                JSONObject playlists = o.optJSONObject("playlists");
                if (playlists == null) return spSearch(fallbackQuery + " playlist", "track");
                JSONArray items = playlists.optJSONArray("items");
                List<Track> out = new ArrayList<>();
                if (items != null) for (int i = 0; i < items.length(); i++) {
                    JSONObject p = items.optJSONObject(i);
                    if (p == null) continue;
                    String art = "";
                    JSONArray imgs = p.optJSONArray("images");
                    if (imgs != null && imgs.length() > 0) art = imgs.optJSONObject(0).optString("url");
                    Track t = new Track("sppl:" + p.optString("id"), p.optString("name"),
                            p.optString("description", ""), art);
                    t.badge = "PLAYLIST";
                    out.add(t);
                }
                return out;
            }
        } catch (Exception e) {
            return spSearch(fallbackQuery + " playlist", "track");
        }
    }

    /** Tukar JSONObject Spotify track → Track */
    private static Track spTrack(JSONObject t) {
        if (t == null || t.optString("name").isEmpty()) return null;
        StringBuilder artists = new StringBuilder();
        String first = "";
        JSONArray ar = t.optJSONArray("artists");
        if (ar != null) for (int i = 0; i < ar.length(); i++) {
            String n = ar.optJSONObject(i).optString("name");
            if (i == 0) first = n;
            if (artists.length() > 0) artists.append(", ");
            artists.append(n);
        }
        String art = "";
        JSONObject album = t.optJSONObject("album");
        if (album != null) {
            JSONArray imgs = album.optJSONArray("images");
            if (imgs != null && imgs.length() > 0) art = imgs.optJSONObject(0).optString("url");
        }
        String name = t.optString("name");
        Track tr = new Track("q:" + (first.isEmpty() ? "" : first + " - ") + name, name, artists.toString(), art);
        tr.durationSec = t.optLong("duration_ms") / 1000;
        tr.badge = "SPOTIFY";
        return tr;
    }

    /** Token Spotify */
    private static String spToken() throws Exception {
        return SpotifyAccount.token();
    }
}
