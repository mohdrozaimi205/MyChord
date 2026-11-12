package com.mychord.app;

import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends BaseActivity implements Player.Listener {
    private ImageView bg, art;
    private TextView title, artistTv, pos, dur, badge;
    private ImageButton playBtn, likeBtn, shuffleBtn, repeatBtn;
    private Slider seek;
    private boolean dragging = false;
    private String lastId = null;
    private ScrollView lyricsScroll;
    private LinearLayout lyricsBox;
    private boolean lyricsOn = false;
    private Lrclib.Result lyrics;
    private final List<TextView> lyricViews = new ArrayList<>();
    private int lyricIndex = -1;
    private TextView sleepChipText;

    // Leret ke bawah untuk tutup
    private View sheet;
    private float downX, downY;
    private boolean swipeAllowed, swiping, closing;
    private VelocityTracker vt;
    private int slop;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            App.MAIN.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        sheet = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        slop = ViewConfiguration.get(this).getScaledTouchSlop();
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        bg = findViewById(R.id.pBg);
        art = findViewById(R.id.pArt);
        title = findViewById(R.id.pTitle);
        artistTv = findViewById(R.id.pArtist);
        pos = findViewById(R.id.pPos);
        dur = findViewById(R.id.pDur);
        badge = findViewById(R.id.pBadge);
        playBtn = findViewById(R.id.pPlay);
        likeBtn = findViewById(R.id.pLike);
        shuffleBtn = findViewById(R.id.pShuffle);
        repeatBtn = findViewById(R.id.pRepeat);
        seek = findViewById(R.id.pSeek);
        lyricsScroll = findViewById(R.id.pLyricsScroll);
        lyricsBox = findViewById(R.id.pLyrics);
        Ui.tint(playBtn, Ui.attr(this, R.attr.mcOnAccent));
        title.setSelected(true);

        if (Build.VERSION.SDK_INT >= 31) {
            bg.setRenderEffect(RenderEffect.createBlurEffect(80f, 80f, Shader.TileMode.CLAMP));
        }

        // Saiz seni kulit segi empat sama
        View holder = (View) art.getParent();
        holder.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int size = Math.min(r - l, b - t) - Ui.dp(this, 24);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) art.getLayoutParams();
            if (size > 0 && lp.width != size) {
                lp.width = size;
                lp.height = size;
                lp.gravity = Gravity.CENTER;
                art.post(() -> art.setLayoutParams(lp));
            }
        });

        findViewById(R.id.pClose).setOnClickListener(v -> finish());
        findViewById(R.id.pQueue).setOnClickListener(v -> showQueue());
        playBtn.setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c == null) return;
            if (c.isPlaying()) c.pause();
            else {
                if (c.getPlaybackState() == Player.STATE_IDLE) c.prepare();
                c.play();
            }
        });
        findViewById(R.id.pNext).setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c != null && c.hasNextMediaItem()) c.seekToNextMediaItem();
        });
        findViewById(R.id.pPrev).setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c != null) c.seekToPrevious();
        });
        shuffleBtn.setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c != null) c.setShuffleModeEnabled(!c.getShuffleModeEnabled());
        });
        repeatBtn.setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c == null) return;
            int m = c.getRepeatMode();
            c.setRepeatMode(m == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                    : m == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        });
        likeBtn.setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c == null || c.getCurrentMediaItem() == null) return;
            Track cur = Track.fromMediaItem(c.getCurrentMediaItem());
            YtmAccount.syncLike(cur, Store.toggleLike(cur));
            refreshLike();
        });
        seek.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) { dragging = true; }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                dragging = false;
                MediaController c = PlayerHub.get();
                if (c != null && c.getDuration() > 0) c.seekTo((long) (slider.getValue() / 1000f * c.getDuration()));
            }
        });
        seek.addOnChangeListener((slider, value, fromUser) -> {
            MediaController c = PlayerHub.get();
            if (fromUser && c != null && c.getDuration() > 0) pos.setText(Ui.time((long) (value / 1000f * c.getDuration())));
        });
        buildChips();
    }

    private void buildChips() {
        LinearLayout chips = findViewById(R.id.pChips);
        chips.removeAllViews();
        if (AddonManager.installed("lyrics")) addChip(chips, R.drawable.ic_lyrics, getString(R.string.lyrics), v -> toggleLyrics());
        addChip(chips, R.drawable.ic_eq, getString(R.string.effects), v -> startActivity(new Intent(this, EffectsActivity.class)));
        if (AddonManager.installed("sleep")) {
            View c = addChip(chips, R.drawable.ic_timer, getString(R.string.sleep), v -> sleepDialog());
            sleepChipText = c.findViewById(R.id.chipText);
            updateSleepChip();
        }
        addChip(chips, R.drawable.ic_hq, getString(R.string.q_chip), v -> qualityDialog());
        View tg = addChip(chips, R.drawable.ic_together, ListenTogether.mode() == ListenTogether.OFF
                ? getString(R.string.tg_title) : getString(R.string.tg_chip_on, ListenTogether.code()), v -> TogetherDialog.show(this, this::buildChips));
        addChip(chips, R.drawable.ic_share, getString(R.string.share_song), v -> shareSong());
        addChip(chips, R.drawable.ic_queue, getString(R.string.queue), v -> showQueue());
    }

    private void qualityDialog() {
        boolean metered = false;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            metered = cm != null && cm.isActiveNetworkMetered();
        } catch (Exception ignored) { }
        final String key = metered ? "qData" : "qWifi";
        String[] opts = {getString(R.string.q_low) + "  •  ~50–70 kbps", getString(R.string.q_normal) + "  •  ~128 kbps",
                getString(R.string.q_high) + "  •  ~160–256 kbps"};
        int cur = Prefs.currentQuality();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.q_chip) + " (" + getString(metered ? R.string.q_data : R.string.q_wifi) + ")")
                .setSingleChoiceItems(opts, cur, (d, w) -> {
                    d.dismiss();
                    if (w == cur) return;
                    Prefs.putI(key, w);
                    Resolver.clear();
                    reloadCurrent();
                    Toast.makeText(this, getString(R.string.q_switched, opts[w].split("  •")[0]), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** Muat semula lagu semasa pada kedudukan yang sama dengan kualiti baharu */
    private void reloadCurrent() {
        MediaController c = PlayerHub.get();
        if (c == null || c.getCurrentMediaItem() == null) return;
        MediaItem it = c.getCurrentMediaItem();
        if (!it.mediaId.startsWith("yt:") && !it.mediaId.startsWith("q:")) return;
        int idx = c.getCurrentMediaItemIndex();
        long pos = c.getCurrentPosition();
        boolean play = c.getPlayWhenReady();
        Resolver.INFO.remove(it.mediaId);
        c.removeMediaItem(idx);
        c.addMediaItem(idx, Track.fromMediaItem(it).toMediaItem());
        c.seekTo(idx, pos);
        c.prepare();
        if (play) c.play();
        badge.setVisibility(View.INVISIBLE);
    }

    private void shareSong() {
        MediaController c = PlayerHub.get();
        if (c == null || c.getCurrentMediaItem() == null) return;
        Track t = Track.fromMediaItem(c.getCurrentMediaItem());
        String link;
        if (t.mediaId.startsWith("yt:")) link = "https://music.youtube.com/watch?v=" + t.mediaId.substring(3);
        else link = "https://music.youtube.com/search?q=" + android.net.Uri.encode(t.title + " " + t.artist);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "🎵 " + t.title + (t.artist.isEmpty() ? "" : " — " + t.artist) + "\n" + link);
        startActivity(Intent.createChooser(i, getString(R.string.share_song)));
    }

    private View addChip(LinearLayout parent, int icon, String text, View.OnClickListener l) {
        View c = LayoutInflater.from(this).inflate(R.layout.view_pchip, parent, false);
        ((ImageView) c.findViewById(R.id.chipIcon)).setImageResource(icon);
        ((TextView) c.findViewById(R.id.chipText)).setText(text);
        c.setOnClickListener(l);
        parent.addView(c);
        return c;
    }

    private void updateSleepChip() {
        if (sleepChipText == null) return;
        if (PlayerHub.sleepAt > System.currentTimeMillis()) {
            long left = (PlayerHub.sleepAt - System.currentTimeMillis()) / 60000 + 1;
            sleepChipText.setText(getString(R.string.minutes, (int) left));
        } else sleepChipText.setText(R.string.sleep);
    }

    private void sleepDialog() {
        int[] mins = {15, 30, 45, 60, 90};
        String[] labels = new String[mins.length + 1];
        for (int i = 0; i < mins.length; i++) labels[i] = getString(R.string.minutes, mins[i]);
        labels[mins.length] = getString(R.string.sleep_off);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sleep)
                .setItems(labels, (d, w) -> {
                    if (w < mins.length) {
                        PlayerHub.setSleep(mins[w]);
                        Toast.makeText(this, getString(R.string.sleep_set, mins[w]), Toast.LENGTH_SHORT).show();
                    } else {
                        PlayerHub.setSleep(0);
                        Toast.makeText(this, R.string.sleep_cancelled, Toast.LENGTH_SHORT).show();
                    }
                    updateSleepChip();
                }).show();
    }

    private void showQueue() {
        MediaController c = PlayerHub.get();
        if (c == null || c.getMediaItemCount() == 0) return;
        int n = c.getMediaItemCount();
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            MediaItem it = c.getMediaItemAt(i);
            MediaMetadata m = it.mediaMetadata;
            String t = (m.title != null ? m.title : "") + (m.artist != null && m.artist.length() > 0 ? " — " + m.artist : "");
            names[i] = (i == c.getCurrentMediaItemIndex() ? "▶  " : "     ") + t;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.queue)
                .setItems(names, (d, w) -> {
                    c.seekTo(w, 0);
                    c.play();
                })
                .setNegativeButton(R.string.ok, null).show();
    }

    // ---------- Lirik ----------
    private void toggleLyrics() {
        lyricsOn = !lyricsOn;
        lyricsScroll.setVisibility(lyricsOn ? View.VISIBLE : View.GONE);
        art.setVisibility(lyricsOn ? View.INVISIBLE : View.VISIBLE);
        if (lyricsOn) loadLyrics();
    }

    private void loadLyrics() {
        MediaController c = PlayerHub.get();
        if (c == null) return;
        MediaMetadata m = c.getMediaMetadata();
        String t = m.title != null ? m.title.toString() : "";
        String a = m.artist != null ? m.artist.toString() : "";
        final String forId = lastId;
        lyrics = null;
        showLyricMessage(getString(R.string.lyrics_loading));
        App.IO.execute(() -> {
            Lrclib.Result r;
            try { r = Lrclib.fetch(t, a); } catch (Exception e) { r = null; }
            final Lrclib.Result res = r;
            App.MAIN.post(() -> {
                if (isFinishing() || (forId != null && !forId.equals(lastId))) return;
                lyrics = res;
                renderLyrics();
            });
        });
    }

    private void showLyricMessage(String msg) {
        lyricsBox.removeAllViews();
        lyricViews.clear();
        TextView tv = lyricLine(msg);
        tv.setAlpha(0.7f);
        lyricsBox.addView(tv);
    }

    private TextView lyricLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(22);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        int p = Ui.dp(this, 8);
        tv.setPadding(0, p, 0, p);
        return tv;
    }

    private void renderLyrics() {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            showLyricMessage(getString(R.string.lyrics_none));
            return;
        }
        lyricsBox.removeAllViews();
        lyricViews.clear();
        lyricIndex = -1;
        for (Lrclib.Line l : lyrics.lines) {
            TextView tv = lyricLine(l.text);
            tv.setAlpha(lyrics.synced ? 0.35f : 0.9f);
            if (lyrics.synced) {
                tv.setOnClickListener(v -> {
                    MediaController c = PlayerHub.get();
                    if (c != null) c.seekTo(l.ms);
                });
            }
            lyricViews.add(tv);
            lyricsBox.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void syncLyrics(long posMs) {
        if (!lyricsOn || lyrics == null || !lyrics.synced || lyricViews.isEmpty()) return;
        int idx = -1;
        for (int i = 0; i < lyrics.lines.size(); i++) {
            if (lyrics.lines.get(i).ms <= posMs + 250) idx = i; else break;
        }
        if (idx == lyricIndex) return;
        if (lyricIndex >= 0 && lyricIndex < lyricViews.size()) lyricViews.get(lyricIndex).setAlpha(0.35f);
        lyricIndex = idx;
        if (idx >= 0) {
            TextView tv = lyricViews.get(idx);
            tv.setAlpha(1f);
            lyricsScroll.smoothScrollTo(0, Math.max(0, tv.getTop() - lyricsScroll.getHeight() / 3));
        }
    }

    // ---------- Kemas kini paparan ----------
    private void refresh() {
        MediaController c = PlayerHub.get();
        if (c == null) return;
        MediaItem item = c.getCurrentMediaItem();
        if (item == null) {
            title.setText(R.string.nothing_playing);
            artistTv.setText("");
            return;
        }
        MediaMetadata m = c.getMediaMetadata();
        title.setText(m.title != null ? m.title : "");
        artistTv.setText(m.artist != null ? m.artist : "");
        if (!item.mediaId.equals(lastId)) {
            lastId = item.mediaId;
            String a = m.artworkUri != null ? m.artworkUri.toString() : "";
            Ui.art(art, a);
            if (!a.isEmpty()) Glide.with(this).load(a).centerCrop().into(bg);
            else bg.setImageDrawable(null);
            if (lyricsOn) loadLyrics();
        }
        playBtn.setImageResource(c.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        shuffleBtn.setAlpha(c.getShuffleModeEnabled() ? 1f : 0.45f);
        int rm = c.getRepeatMode();
        repeatBtn.setImageResource(rm == Player.REPEAT_MODE_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        repeatBtn.setAlpha(rm == Player.REPEAT_MODE_OFF ? 0.45f : 1f);
        refreshLike();
        refreshBadge(item);
    }

    private void refreshBadge(MediaItem item) {
        String b = Resolver.INFO.get(item.mediaId);
        if (b == null || b.isEmpty()) {
            Bundle ex = item.mediaMetadata.extras;
            b = ex != null ? ex.getString("badge", "") : "";
        }
        if (b.isEmpty() && item.mediaId.startsWith("radio:")) b = "LIVE";
        if (item.mediaId.startsWith("local:") && !Resolver.INFO.containsKey(item.mediaId)) {
            String id = item.mediaId;
            Resolver.INFO.put(id, b);
            App.IO.execute(() -> {
                String q = localQuality(id.substring(6));
                if (q != null) {
                    Resolver.INFO.put(id, q);
                    App.MAIN.post(() -> { if (!isFinishing()) refreshBadgeFor(id); });
                }
            });
        }
        badge.setVisibility(b.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        badge.setText(b);
    }

    private void refreshBadgeFor(String id) {
        MediaController c = PlayerHub.get();
        if (c != null && c.getCurrentMediaItem() != null && c.getCurrentMediaItem().mediaId.equals(id))
            refreshBadge(c.getCurrentMediaItem());
    }

    /** Kualiti sebenar fail dalam telefon, contoh "FLAC • 24-bit / 96 kHz • HI-RES" */
    private String localQuality(String uri) {
        android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
        try {
            r.setDataSource(this, android.net.Uri.parse(uri));
            String mime = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            String fmt = mime == null ? "AUDIO" : mime.replace("audio/", "").replace("x-", "").toUpperCase(java.util.Locale.ROOT);
            int bits = 0, rate = 0;
            if (Build.VERSION.SDK_INT >= 31) {
                String b = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE);
                String sr = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
                if (b != null) bits = Integer.parseInt(b);
                if (sr != null) rate = Integer.parseInt(sr);
            }
            String br = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE);
            boolean lossless = fmt.contains("FLAC") || fmt.contains("WAV") || fmt.contains("ALAC") || fmt.contains("APE");
            StringBuilder sb = new StringBuilder(fmt);
            if (bits > 0 && rate > 0) {
                sb.append(" • ").append(bits).append("-bit / ").append(rate % 1000 == 0 ? (rate / 1000) + "" : String.format(java.util.Locale.US, "%.1f", rate / 1000f)).append(" kHz");
            } else if (br != null && !lossless) {
                sb.append(" • ").append(Integer.parseInt(br) / 1000).append(" kbps");
            }
            if (lossless) sb.append(bits > 16 || rate > 48000 ? " • HI-RES" : " • LOSSLESS");
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    private void refreshLike() {
        MediaController c = PlayerHub.get();
        boolean liked = c != null && c.getCurrentMediaItem() != null && Store.isLiked(c.getCurrentMediaItem().mediaId);
        likeBtn.setImageResource(liked ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        Ui.tint(likeBtn, liked ? Ui.attr(this, R.attr.mcAccent) : 0xFFFFFFFF);
    }

    private void updateProgress() {
        MediaController c = PlayerHub.get();
        if (c == null) return;
        long d = c.getDuration();
        long p = c.getCurrentPosition();
        if (d > 0) {
            dur.setText(Ui.time(d));
            if (!dragging) {
                seek.setValue(Math.max(0, Math.min(1000, 1000f * p / d)));
                pos.setText(Ui.time(p));
            }
        } else {
            dur.setText(c.getCurrentMediaItem() != null && c.getCurrentMediaItem().mediaId.startsWith("radio:") ? "LIVE" : "--:--");
            pos.setText(Ui.time(p));
        }
        syncLyrics(p);
        if (c.getCurrentMediaItem() != null && badge.getVisibility() != View.VISIBLE) refreshBadge(c.getCurrentMediaItem());
        updateSleepChip();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        PlayerHub.addListener(this);
        PlayerHub.whenReady(this, this::refresh);
        App.MAIN.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildChips();
    }

    @Override
    protected void onStop() {
        PlayerHub.removeListener(this);
        App.MAIN.removeCallbacks(tick);
        super.onStop();
    }

    private boolean inside(View v, MotionEvent e) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float x = e.getRawX(), y = e.getRawY();
        return x >= loc[0] && x <= loc[0] + v.getWidth() && y >= loc[1] && y <= loc[1] + v.getHeight();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (closing) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                swiping = false;
                // Jangan ganggu bar masa lagu, atau lirik yang sedang ditatal
                swipeAllowed = !inside(seek, e) && !(lyricsOn && inside(lyricsScroll, e) && lyricsScroll.getScrollY() > 0);
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                break;
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
                if (!swiping && swipeAllowed && dy > slop * 2 && dy > Math.abs(dx) * 1.4f) {
                    swiping = true;
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                if (swiping) {
                    float t = Math.max(0, dy);
                    sheet.setTranslationY(t);
                    float r = 1f - Math.min(1f, t / (float) sheet.getHeight());
                    sheet.setAlpha(0.4f + 0.6f * r);
                    float sc = 1f - 0.06f * (1f - r);
                    sheet.setScaleX(sc);
                    sheet.setScaleY(sc);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (swiping) {
                    swiping = false;
                    float dy = e.getRawY() - downY;
                    float vy = 0;
                    if (vt != null) {
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        vy = vt.getYVelocity();
                    }
                    if (dy > sheet.getHeight() * 0.22f || vy > 1400) {
                        closing = true;
                        sheet.animate().translationY(sheet.getHeight()).alpha(0.3f).setDuration(180)
                                .withEndAction(() -> {
                                    super.finish();
                                    overridePendingTransition(0, 0);
                                }).start();
                    } else {
                        sheet.animate().translationY(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
                    }
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public void finish() {
        super.finish();
        if (!closing) overridePendingTransition(R.anim.stay, R.anim.slide_down);
    }
}
