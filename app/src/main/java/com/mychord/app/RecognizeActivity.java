package com.mychord.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Kenal lagu yang sedang dimainkan di sekeliling (macam Shazam). */
public class RecognizeActivity extends BaseActivity {
    private static final int RATE = 16000;
    private static final int[] ASK_AT = {4, 7, 10, 13};

    private View pulse, resultCard;
    private TextView status, rTitle, rArtist;
    private ImageView rArt;
    private volatile boolean listening = false;
    private Thread worker;
    private Track found;
    private TrackAdapter histAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        setupTop(getString(R.string.rec_title));
        pulse = findViewById(R.id.recPulse);
        status = findViewById(R.id.recStatus);
        resultCard = findViewById(R.id.recResult);
        rTitle = findViewById(R.id.recSongTitle);
        rArtist = findViewById(R.id.recSongArtist);
        rArt = findViewById(R.id.recSongArt);

        findViewById(R.id.recButton).setBackgroundResource(IconSwitcher.preview());
        findViewById(R.id.recButton).setOnClickListener(v -> {
            if (listening) stop(); else start();
        });
        findViewById(R.id.recPlay).setOnClickListener(v -> {
            if (found != null) PlayerHub.playList(this, Collections.singletonList(found), 0, false);
        });
        findViewById(R.id.recLike).setOnClickListener(v -> {
            if (found == null) return;
            if (!Store.isLiked(found.mediaId)) Store.toggleLike(found);
            Toast.makeText(this, R.string.rec_saved, Toast.LENGTH_SHORT).show();
        });

        RecyclerView rv = findViewById(R.id.recHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        histAdapter = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { Nav.open(RecognizeActivity.this, histAdapter.items, pos); }

            @Override
            public void onMore(View anchor, int pos) { TrackMenu.show(RecognizeActivity.this, anchor, histAdapter.items.get(pos), null); }
        });
        rv.setAdapter(histAdapter);
        histAdapter.set(history());
        setupMini();
        start();
    }

    private void start() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
            return;
        }
        // Jeda MyChord supaya mikrofon tak dengar lagu sendiri dengan kuat (pengguna boleh main dari telefon lain)
        resultCard.setVisibility(View.GONE);
        listening = true;
        status.setText(R.string.rec_listening);
        worker = new Thread(this::listen, "recognize");
        worker.start();
    }

    private void stop() {
        listening = false;
        status.setText(R.string.rec_tap);
        pulse.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    @SuppressLint("MissingPermission")
    private void listen() {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
        } catch (Exception e) {
            ui(() -> { status.setText(R.string.rec_mic_fail); listening = false; });
            return;
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            ui(() -> { status.setText(R.string.rec_mic_fail); listening = false; });
            return;
        }
        Signature sig = new Signature();
        short[] buf = new short[RATE / 10];
        int asked = 0;
        Shazam.Song song = null;
        try {
            rec.startRecording();
            while (listening) {
                int n = rec.read(buf, 0, buf.length);
                if (n < 0) throw new Exception("mic " + n);
                if (n == 0) continue;
                sig.add(buf, n);
                double sum = 0;
                for (int i = 0; i < n; i++) sum += buf[i] * (double) buf[i];
                float level = (float) Math.min(1, Math.sqrt(sum / n) / 6000.0);
                ui(() -> {
                    float sc = 1f + level * 0.5f;
                    pulse.setScaleX(sc);
                    pulse.setScaleY(sc);
                });
                float secs = sig.durationMs() / 1000f;
                if (asked < ASK_AT.length && secs >= ASK_AT[asked]) {
                    asked++;
                    try { song = Shazam.lookup(sig.encode(), sig.durationMs()); } catch (Exception ignored) { }
                    if (song != null) break;
                    if (asked == ASK_AT.length) break;
                }
            }
        } catch (Exception e) {
            ui(() -> status.setText(R.string.rec_mic_fail));
        } finally {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
        }
        final Shazam.Song s = song;
        final boolean wasListening = listening;
        ui(() -> {
            listening = false;
            pulse.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
            if (s != null) show(s);
            else if (wasListening) status.setText(R.string.rec_not_found);
        });
    }

    private void show(Shazam.Song s) {
        status.setText(R.string.rec_found);
        found = new Track("q:" + s.artist + " - " + s.title, s.title, s.artist, s.cover);
        found.badge = "SHAZAM";
        rTitle.setText(s.title);
        rArtist.setText(s.artist + (s.album.isEmpty() ? "" : " • " + s.album));
        Ui.art(rArt, s.cover);
        resultCard.setVisibility(View.VISIBLE);
        remember(found);
        histAdapter.set(history());
        // Terus main versi YouTube Music bila tekan "Dengar"
    }

    private void ui(Runnable r) { App.MAIN.post(() -> { if (!isFinishing()) r.run(); }); }

    private static List<Track> history() {
        List<Track> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.getS("recHistory", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Track.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    private static void remember(Track t) {
        List<Track> l = history();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).mediaId.equals(t.mediaId)) l.remove(i);
        l.add(0, t);
        while (l.size() > 30) l.remove(l.size() - 1);
        JSONArray a = new JSONArray();
        for (Track x : l) a.put(x.toJson());
        Prefs.putS("recHistory", a.toString());
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 21) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) start();
            else status.setText(R.string.rec_need_mic);
        }
    }

    @Override
    protected void onDestroy() {
        listening = false;
        super.onDestroy();
    }
}
