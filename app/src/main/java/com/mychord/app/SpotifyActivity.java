package com.mychord.app;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

public class SpotifyActivity extends BaseActivity {
    private static final String PKG = "com.spotify.music";
    private MediaController spotify;
    private TextView spTitle, spArtist, result;
    private ImageView spArt;
    private ImageButton spPlay;
    private View progress;

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) { App.MAIN.post(() -> showNow()); }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) { App.MAIN.post(() -> showNow()); }

        @Override
        public void onSessionDestroyed() {
            spotify = null;
            App.MAIN.post(() -> showNow());
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spotify);
        setupTop(getString(R.string.sp_title));
        spTitle = findViewById(R.id.spTitle);
        spArtist = findViewById(R.id.spArtist);
        spArt = findViewById(R.id.spArt);
        spPlay = findViewById(R.id.spPlay);
        result = findViewById(R.id.spResult);
        progress = findViewById(R.id.spProgress);
        Ui.tint(spPlay, Ui.attr(this, R.attr.mcOnAccent));

        findViewById(R.id.spOpen).setOnClickListener(v -> openSpotify(null));
        findViewById(R.id.spGrant).setOnClickListener(v -> {
            Toast.makeText(this, R.string.sp_grant_note, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
            }
        });
        spPlay.setOnClickListener(v -> {
            if (spotify != null) {
                PlaybackState st = spotify.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) spotify.getTransportControls().pause();
                else spotify.getTransportControls().play();
            } else mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        });
        findViewById(R.id.spNext).setOnClickListener(v -> {
            if (spotify != null) spotify.getTransportControls().skipToNext();
            else mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        });
        findViewById(R.id.spPrev).setOnClickListener(v -> {
            if (spotify != null) spotify.getTransportControls().skipToPrevious();
            else mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        });

        EditText link = findViewById(R.id.spLink);
        findViewById(R.id.spImport).setOnClickListener(v -> importLink(link.getText().toString().trim()));
        EditText text = findViewById(R.id.spText);
        findViewById(R.id.spTextImport).setOnClickListener(v -> {
            List<Track> tracks = SpotifyImport.fromText(text.getText().toString());
            if (tracks.isEmpty()) return;
            Store.Playlist p = Store.createPlaylist(getString(R.string.sp_text_name), tracks);
            done(p.name, tracks.size(), p.id);
            text.setText("");
        });

        handleShare(getIntent(), link);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent, findViewById(R.id.spLink));
    }

    /** Kongsi pautan dari app Spotify terus ke MyChord */
    private void handleShare(Intent i, EditText link) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String s = i.getStringExtra(Intent.EXTRA_TEXT);
            if (s != null && SpotifyImport.looksLikeLink(s)) {
                link.setText(s);
                importLink(s);
            }
        }
    }

    private void importLink(String s) {
        if (!SpotifyImport.looksLikeLink(s)) {
            result.setText(R.string.sp_import_fail);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        result.setText(R.string.sp_importing);
        App.IO.execute(() -> {
            try {
                SpotifyImport.Result r = SpotifyImport.fromLink(s);
                if (r.tracks.isEmpty()) throw new Exception("kosong");
                Store.Playlist p = Store.createPlaylist(r.name, r.tracks);
                App.MAIN.post(() -> done(p.name, r.tracks.size(), p.id));
            } catch (Exception e) {
                App.MAIN.post(() -> {
                    progress.setVisibility(View.GONE);
                    result.setText(R.string.sp_import_fail);
                });
            }
        });
    }

    private void done(String name, int count, String id) {
        progress.setVisibility(View.GONE);
        result.setText(getString(R.string.sp_imported, name, count));
        startActivity(new Intent(this, ListActivity.class)
                .putExtra("mode", ListActivity.MODE_PLAYLIST).putExtra("id", id));
    }

    private boolean listenerOn() {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
    }

    private void connectSpotify() {
        if (spotify != null) spotify.unregisterCallback(cb);
        spotify = null;
        if (!listenerOn()) return;
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(this, SpotifyListener.class));
            for (MediaController c : list) {
                if (PKG.equals(c.getPackageName())) {
                    spotify = c;
                    spotify.registerCallback(cb);
                    break;
                }
            }
        } catch (Exception ignored) { }
    }

    private void showNow() {
        findViewById(R.id.spGrant).setVisibility(listenerOn() ? View.GONE : View.VISIBLE);
        if (getPackageManager().getLaunchIntentForPackage(PKG) == null) {
            spTitle.setText(R.string.sp_not_installed);
            spArtist.setText("");
            return;
        }
        if (spotify == null || spotify.getMetadata() == null) {
            spTitle.setText(R.string.sp_idle);
            spArtist.setText("");
            spArt.setImageResource(R.drawable.art_placeholder);
            spPlay.setImageResource(R.drawable.ic_play);
            return;
        }
        MediaMetadata m = spotify.getMetadata();
        spTitle.setText(m.getString(MediaMetadata.METADATA_KEY_TITLE));
        spArtist.setText(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b != null) spArt.setImageBitmap(b); else spArt.setImageResource(R.drawable.art_placeholder);
        PlaybackState st = spotify.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        spPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void mediaKey(int code) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    private void openSpotify(String uri) {
        try {
            Intent i = uri != null ? new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    : getPackageManager().getLaunchIntentForPackage(PKG);
            if (i == null) {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PKG));
            }
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.sp_not_installed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectSpotify();
        showNow();
    }

    @Override
    protected void onPause() {
        if (spotify != null) spotify.unregisterCallback(cb);
        spotify = null;
        super.onPause();
    }
}
