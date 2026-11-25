package com.mychord.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kenal lagu melalui dua mod:
 * 1) Dengar Muzik — Shazam fingerprint (muzik sekeliling)
 * 2) Nyanyi/Humming — rakam nyanyian/humming, cuba Shazam dulu,
 *    lalu Speech-to-Text → cari lirik di YouTube Music
 */
public class RecognizeActivity extends BaseActivity {
    private static final int RATE = 16000;
    private static final int[] ASK_AT_SHAZAM = {4, 7, 10, 13};
    private static final int[] ASK_AT_HUM = {5, 10, 15, 20};

    private View pulse, resultCard, humResultsHeader;
    private TextView status, rTitle, rArtist, noteText;
    private ImageView rArt;
    private volatile boolean listening = false;
    private Thread worker;
    private Track found;
    private TrackAdapter histAdapter, humAdapter;
    private RecyclerView humList;

    private boolean humMode = false;
    private SpeechRecognizer speechRecognizer;
    private String lastSpeechText = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        setupTop(getString(R.string.rec_title));
        pulse = findViewById(R.id.recPulse);
        status = findViewById(R.id.recStatus);
        noteText = findViewById(R.id.recNote);
        resultCard = findViewById(R.id.recResult);
        rTitle = findViewById(R.id.recSongTitle);
        rArtist = findViewById(R.id.recSongArtist);
        rArt = findViewById(R.id.recSongArt);
        humResultsHeader = findViewById(R.id.recHumResults);
        humList = findViewById(R.id.recHumList);

        // Mode toggle
        Chip modeShazam = findViewById(R.id.modeShazam);
        Chip modeHum = findViewById(R.id.modeHum);
        modeShazam.setOnClickListener(v -> switchMode(false));
        modeHum.setOnClickListener(v -> switchMode(true));

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

        // Senarai sejarah
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

        // Senarai hasil humming
        humList.setLayoutManager(new LinearLayoutManager(this));
        humAdapter = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { Nav.open(RecognizeActivity.this, humAdapter.items, pos); }

            @Override
            public void onMore(View anchor, int pos) { TrackMenu.show(RecognizeActivity.this, anchor, humAdapter.items.get(pos), null); }
        });
        humList.setAdapter(humAdapter);

        setupMini();
        start();
    }

    private void switchMode(boolean hum) {
        if (listening) stop();
        humMode = hum;
        resultCard.setVisibility(View.GONE);
        humResultsHeader.setVisibility(View.GONE);
        humList.setVisibility(View.GONE);
        noteText.setText(hum ? R.string.rec_note_hum : R.string.rec_note);
        status.setText(R.string.rec_tap);
    }

    private void start() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
            return;
        }
        resultCard.setVisibility(View.GONE);
        humResultsHeader.setVisibility(View.GONE);
        humList.setVisibility(View.GONE);
        listening = true;
        status.setText(humMode ? R.string.rec_listening_hum : R.string.rec_listening);
        lastSpeechText = "";

        if (humMode) {
            // Mulakan Speech-to-Text serentak dengan rakaman Shazam
            startSpeechRecognizer();
        }

        worker = new Thread(this::listen, "recognize");
        worker.start();
    }

    private void stop() {
        listening = false;
        status.setText(R.string.rec_tap);
        pulse.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        stopSpeechRecognizer();
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
        int[] askAt = humMode ? ASK_AT_HUM : ASK_AT_SHAZAM;
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
                if (asked < askAt.length && secs >= askAt[asked]) {
                    asked++;
                    try { song = Shazam.lookup(sig.encode(), sig.durationMs()); } catch (Exception ignored) { }
                    if (song != null) break;
                    if (asked == askAt.length) break;
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
            stopSpeechRecognizer();
            if (s != null) {
                show(s);
            } else if (wasListening && humMode) {
                // Shazam tak jumpa — cuba cari via teks nyanyian
                trySearchByVoice();
            } else if (wasListening) {
                status.setText(R.string.rec_not_found);
            }
        });
    }

    // ── Speech-to-Text (Android bawaan) untuk tangkap lirik nyanyian ──

    private void startSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) {
                // Restart jika masih mendengar (timeout dsb)
                if (listening && humMode) {
                    try { restartSpeech(); } catch (Exception ignored) { }
                }
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    if (text.length() > lastSpeechText.length()) lastSpeechText = text;
                }
                if (listening && humMode) {
                    try { restartSpeech(); } catch (Exception ignored) { }
                }
            }
            @Override
            public void onPartialResults(Bundle partial) {
                ArrayList<String> matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    if (text.length() > lastSpeechText.length()) lastSpeechText = text;
                }
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        startSpeechIntent();
    }

    private void startSpeechIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);
        try { speechRecognizer.startListening(intent); } catch (Exception ignored) { }
    }

    private void restartSpeech() {
        if (speechRecognizer != null && listening && humMode) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) { }
            startSpeechIntent();
        }
    }

    private void stopSpeechRecognizer() {
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) { }
            try { speechRecognizer.cancel(); } catch (Exception ignored) { }
            try { speechRecognizer.destroy(); } catch (Exception ignored) { }
            speechRecognizer = null;
        }
    }

    private void trySearchByVoice() {
        String query = lastSpeechText.trim();
        if (query.isEmpty()) {
            status.setText(R.string.rec_hum_no_text);
            return;
        }
        status.setText(getString(R.string.rec_hum_searching, query));

        App.IO.execute(() -> {
            try {
                List<Track> results = YtMusic.search(query);
                results = DiscoverShelves.filterHindi(results);
                List<Track> top = results.size() > 10 ? results.subList(0, 10) : results;
                ui(() -> {
                    if (top.isEmpty()) {
                        status.setText(R.string.rec_not_found);
                        return;
                    }
                    status.setText(R.string.rec_hum_found);
                    humResultsHeader.setVisibility(View.VISIBLE);
                    humList.setVisibility(View.VISIBLE);
                    // Beri ruang untuk senarai
                    humList.getLayoutParams().height = 0;
                    ((android.widget.LinearLayout.LayoutParams) humList.getLayoutParams()).weight = 1;
                    humList.requestLayout();
                    humAdapter.set(top);
                    // Simpan lagu pertama ke sejarah
                    if (!top.isEmpty()) {
                        Track first = top.get(0);
                        first.badge = "NYANYI";
                        remember(first);
                        histAdapter.set(history());
                    }
                });
            } catch (Exception e) {
                ui(() -> status.setText(R.string.rec_not_found));
            }
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
        stopSpeechRecognizer();
        super.onDestroy();
    }
}
