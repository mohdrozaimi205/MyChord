package com.mychord.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

public class PlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;
    private int errorStreak = 0;
    private final android.os.Handler fader = new android.os.Handler(android.os.Looper.getMainLooper());
    private long fadeInStart = 0;
    private final Runnable fadeTick = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                float vol = 1f;
                if (AddonManager.installed("crossfade") && Prefs.getB("crossfade", true)) {
                    long fadeMs = Prefs.getI("crossfadeSec", 4) * 1000L;
                    long dur = player.getDuration(), pos = player.getCurrentPosition();
                    if (player.isPlaying() && dur > fadeMs * 2 && player.hasNextMediaItem() && dur - pos < fadeMs) {
                        vol = Math.max(0.05f, (dur - pos) / (float) fadeMs);
                    }
                    long since = System.currentTimeMillis() - fadeInStart;
                    if (since < fadeMs / 2) vol = Math.min(vol, Math.max(0.1f, since / (fadeMs / 2f)));
                }
                if (Math.abs(player.getVolume() - vol) > 0.01f) player.setVolume(vol);
            }
            fader.postDelayed(this, 120);
        }
    };
    private boolean retried = false;

    public static MediaItem buildItem(String mediaId, String title, String artist, String art, String badge) {
        Uri uri;
        String mime = null;
        if (mediaId.startsWith("yt:")) {
            uri = Uri.parse("mychord://yt/" + mediaId.substring(3));
        } else if (mediaId.startsWith("q:")) {
            uri = new Uri.Builder().scheme("mychord").authority("q")
                    .appendQueryParameter("s", mediaId.substring(2)).build();
        } else if (mediaId.startsWith("local:")) {
            uri = Uri.parse(mediaId.substring(6));
        } else if (mediaId.startsWith("url:")) {
            String u = mediaId.substring(4);
            uri = Uri.parse(u);
            if (u.contains(".m3u8")) mime = MimeTypes.APPLICATION_M3U8;
        } else if (mediaId.startsWith("radio:")) {
            String u = mediaId.substring(6);
            uri = Uri.parse(u);
            if (u.contains(".m3u8")) mime = MimeTypes.APPLICATION_M3U8;
        } else {
            uri = Uri.parse(mediaId);
        }
        Bundle ex = new Bundle();
        ex.putString("badge", badge == null ? "" : badge);
        MediaMetadata md = new MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(art == null || art.isEmpty() ? null : Uri.parse(art))
                .setExtras(ex)
                .build();
        MediaItem.Builder b = new MediaItem.Builder().setMediaId(mediaId).setUri(uri).setMediaMetadata(md);
        if (mime != null) b.setMimeType(mime);
        return b.build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(OkDownloader.UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);
        DataSource.Factory base = new DefaultDataSource.Factory(this, new ChunkedDataSource.Factory(http));
        DataSource.Factory resolving = new ResolvingDataSource.Factory(base, Resolver::resolve);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(resolving))
                .setAudioAttributes(attrs, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setLoadControl(new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        // simpan sehingga 2 minit lagu ke depan; mula main selepas 2 s, sambung selepas 4 s
                        .setBufferDurationsMs(30_000, 120_000, 2_000, 4_000)
                        .build())
                .build();

        Effects.attach(this, player.getAudioSessionId());

        player.addListener(new Player.Listener() {
            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                Effects.attach(PlaybackService.this, audioSessionId);
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
                retried = false;
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) fadeInStart = System.currentTimeMillis();
                if (item != null) Store.addHistory(Track.fromMediaItem(item));
                NowPlayingWidget.refreshAll(PlaybackService.this);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) errorStreak = 0;
                NowPlayingWidget.refreshAll(PlaybackService.this);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (!retried) {
                    // Pautan mungkin tamat tempoh — dapatkan semula
                    retried = true;
                    Resolver.clear();
                    player.prepare();
                    player.play();
                    return;
                }
                errorStreak++;
                if (errorStreak < 4 && player.hasNextMediaItem()) {
                    retried = false;
                    player.seekToNextMediaItem();
                    player.prepare();
                    player.play();
                }
            }
        });

        fader.post(fadeTick);

        Intent open = new Intent(this, PlayerActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        session = new MediaSession.Builder(this, player)
                .setSessionActivity(pi)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public ListenableFuture<List<MediaItem>> onAddMediaItems(
                            @NonNull MediaSession s, @NonNull MediaSession.ControllerInfo c,
                            @NonNull List<MediaItem> items) {
                        List<MediaItem> out = new ArrayList<>();
                        for (MediaItem it : items) {
                            MediaMetadata m = it.mediaMetadata;
                            String badge = m.extras != null ? m.extras.getString("badge", "") : "";
                            out.add(buildItem(it.mediaId,
                                    m.title != null ? m.title.toString() : "",
                                    m.artist != null ? m.artist.toString() : "",
                                    m.artworkUri != null ? m.artworkUri.toString() : "",
                                    badge));
                        }
                        return Futures.immediateFuture(out);
                    }
                })
                .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        if (player == null || !player.getPlayWhenReady() || player.getMediaItemCount() == 0) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        fader.removeCallbacksAndMessages(null);
        Effects.release();
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
