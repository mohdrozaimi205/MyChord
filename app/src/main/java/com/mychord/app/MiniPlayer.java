package com.mychord.app;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

public class MiniPlayer implements Player.Listener {
    private final Activity act;
    private final View root;
    private final ImageView art;
    private final TextView title, artist;
    private final ImageButton play, next;
    private final ProgressBar progress;
    private String lastArt = null;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            MediaController c = PlayerHub.get();
            if (c != null && c.getDuration() > 0) {
                progress.setProgress((int) (1000 * c.getCurrentPosition() / c.getDuration()));
            }
            App.MAIN.postDelayed(this, 700);
        }
    };

    public MiniPlayer(Activity act, View root) {
        this.act = act;
        this.root = root;
        art = root.findViewById(R.id.miniArt);
        title = root.findViewById(R.id.miniTitle);
        artist = root.findViewById(R.id.miniArtist);
        play = root.findViewById(R.id.miniPlay);
        next = root.findViewById(R.id.miniNext);
        progress = root.findViewById(R.id.miniProgress);
        root.findViewById(R.id.miniCard).setOnClickListener(v -> {
            act.startActivity(new Intent(act, PlayerActivity.class));
            act.overridePendingTransition(R.anim.slide_up, R.anim.stay);
        });
        play.setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c == null) return;
            if (c.isPlaying()) c.pause(); else { if (c.getPlaybackState() == Player.STATE_IDLE) c.prepare(); c.play(); }
        });
        next.setOnClickListener(v -> {
            MediaController c = PlayerHub.get();
            if (c != null && c.hasNextMediaItem()) c.seekToNextMediaItem();
        });
    }

    public void start() {
        PlayerHub.addListener(this);
        PlayerHub.whenReady(act, this::refresh);
        App.MAIN.post(tick);
    }

    public void stop() {
        PlayerHub.removeListener(this);
        App.MAIN.removeCallbacks(tick);
    }

    void refresh() {
        MediaController c = PlayerHub.get();
        if (c == null || c.getMediaItemCount() == 0) {
            root.setVisibility(View.GONE);
            return;
        }
        root.setVisibility(View.VISIBLE);
        MediaMetadata m = c.getMediaMetadata();
        title.setText(m.title != null ? m.title : "");
        artist.setText(m.artist != null ? m.artist : "");
        String a = m.artworkUri != null ? m.artworkUri.toString() : "";
        if (!a.equals(lastArt)) {
            lastArt = a;
            Ui.art(art, a);
        }
        play.setImageResource(c.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        refresh();
    }
}
