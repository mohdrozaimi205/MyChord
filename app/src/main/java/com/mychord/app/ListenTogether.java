package com.mychord.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Dengar bersama: hos menghantar lagu & kedudukan semasa melalui ntfy.sh (perkhidmatan
 * pesanan percuma). Tetamu yang masuk kod sesi akan main lagu yang sama, serentak.
 */
public class ListenTogether {
    public static final int OFF = 0, HOST = 1, GUEST = 2;
    private static int mode = OFF;
    private static String code = null;
    private static Player.Listener hostListener;
    private static Runnable heartbeat;
    private static Thread guestThread;
    private static okhttp3.Call guestCall;
    private static long lastSent = 0;

    public static int mode() { return mode; }
    public static String code() { return code; }

    private static String topic() { return "https://ntfy.sh/mychord-" + code; }

    public static String newCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    // ---------------- HOS ----------------
    public static void host(Context ctx) {
        stop();
        code = newCode();
        mode = HOST;
        hostListener = new Player.Listener() {
            @Override
            public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                    send();
                }
            }
        };
        PlayerHub.addListener(hostListener);
        heartbeat = new Runnable() {
            @Override
            public void run() {
                if (mode != HOST) return;
                if (System.currentTimeMillis() - lastSent > 9000) send();
                App.MAIN.postDelayed(this, 10000);
            }
        };
        App.MAIN.postDelayed(heartbeat, 1000);
        PlayerHub.whenReady(ctx, ListenTogether::send);
    }

    private static void send() {
        MediaController c = PlayerHub.get();
        if (mode != HOST || c == null) return;
        MediaItem it = c.getCurrentMediaItem();
        if (it == null) return;
        try {
            Track t = Track.fromMediaItem(it);
            JSONObject o = t.toJson();
            o.put("pos", c.getCurrentPosition());
            o.put("playing", c.isPlaying() || (c.getPlayWhenReady() && c.getPlaybackState() == Player.STATE_BUFFERING));
            o.put("ts", System.currentTimeMillis());
            lastSent = System.currentTimeMillis();
            String body = o.toString();
            String url = topic();
            App.IO.execute(() -> {
                try {
                    Request req = new Request.Builder().url(url)
                            .post(RequestBody.create(body, MediaType.get("text/plain")))
                            .header("Cache", "yes").build();
                    App.http.newCall(req).execute().close();
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    // ---------------- TETAMU ----------------
    public static void join(Context ctx, String joinCode) {
        stop();
        code = joinCode.trim().toUpperCase();
        mode = GUEST;
        Context app = ctx.getApplicationContext();
        OkHttpClient stream = App.http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build();
        guestThread = new Thread(() -> {
            String myCode = code;
            while (mode == GUEST && myCode.equals(code)) {
                try {
                    Request req = new Request.Builder().url(topic() + "/json?since=2m").build();
                    guestCall = stream.newCall(req);
                    try (Response r = guestCall.execute()) {
                        if (r.body() == null) throw new Exception("tiada");
                        BufferedReader br = new BufferedReader(new InputStreamReader(r.body().byteStream()));
                        String line;
                        while (mode == GUEST && (line = br.readLine()) != null) {
                            JSONObject ev = new JSONObject(line);
                            if (!"message".equals(ev.optString("event"))) continue;
                            JSONObject st = new JSONObject(ev.optString("message"));
                            App.MAIN.post(() -> apply(app, st));
                        }
                    }
                } catch (Exception e) {
                    if (mode != GUEST) return;
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { return; }
                }
            }
        }, "dengar-bersama");
        guestThread.start();
    }

    private static void apply(Context ctx, JSONObject st) {
        if (mode != GUEST) return;
        Track t = Track.fromJson(st);
        long elapsed = Math.max(0, System.currentTimeMillis() - st.optLong("ts"));
        boolean playing = st.optBoolean("playing");
        long target = st.optLong("pos") + (playing ? elapsed : 0);
        PlayerHub.whenReady(ctx, () -> {
            MediaController c = PlayerHub.get();
            MediaItem cur = c.getCurrentMediaItem();
            if (cur == null || !cur.mediaId.equals(t.mediaId)) {
                c.setMediaItems(Collections.singletonList(t.toMediaItem()), 0, target);
                c.prepare();
                if (playing) c.play(); else c.pause();
                return;
            }
            if (Math.abs(c.getCurrentPosition() - target) > 2500) c.seekTo(target);
            if (playing && !c.isPlaying()) c.play();
            if (!playing && c.isPlaying()) c.pause();
        });
    }

    public static void stop() {
        if (hostListener != null) PlayerHub.removeListener(hostListener);
        hostListener = null;
        if (heartbeat != null) App.MAIN.removeCallbacks(heartbeat);
        heartbeat = null;
        mode = OFF;
        if (guestCall != null) guestCall.cancel();
        guestCall = null;
        guestThread = null;
        code = null;
    }
}
