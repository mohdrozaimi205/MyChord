package com.mychord.app;

import android.content.ComponentName;
import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Penghubung antara skrin app dan servis pemain muzik. */
public class PlayerHub {
    private static MediaController controller;
    private static ListenableFuture<MediaController> future;
    private static final List<Runnable> pending = new ArrayList<>();
    private static final List<Player.Listener> listeners = new ArrayList<>();
    private static Runnable sleepTask;
    public static long sleepAt = 0;

    public static void connect(Context ctx) {
        if (future != null) return;
        Context app = ctx.getApplicationContext();
        SessionToken token = new SessionToken(app, new ComponentName(app, PlaybackService.class));
        future = new MediaController.Builder(app, token).buildAsync();
        future.addListener(() -> {
            try {
                controller = future.get();
                for (Player.Listener l : listeners) controller.addListener(l);
                List<Runnable> run = new ArrayList<>(pending);
                pending.clear();
                for (Runnable r : run) r.run();
            } catch (Exception e) {
                future = null;
            }
        }, ContextCompat.getMainExecutor(app));
    }

    public static MediaController get() { return controller; }

    public static void whenReady(Context ctx, Runnable r) {
        if (controller != null) r.run();
        else {
            pending.add(r);
            connect(ctx);
        }
    }

    public static void addListener(Player.Listener l) {
        listeners.add(l);
        if (controller != null) controller.addListener(l);
    }

    public static void removeListener(Player.Listener l) {
        listeners.remove(l);
        if (controller != null) controller.removeListener(l);
    }

    public static void playList(Context ctx, List<Track> list, int index, boolean shuffle) {
        if (list == null || list.isEmpty()) return;
        List<MediaItem> items = new ArrayList<>();
        List<Track> src = new ArrayList<>(list);
        if (shuffle) {
            Collections.shuffle(src);
            index = 0;
        }
        for (Track t : src) items.add(t.toMediaItem());
        final int start = Math.max(0, Math.min(index, items.size() - 1));
        whenReady(ctx, () -> {
            controller.setShuffleModeEnabled(false);
            controller.setMediaItems(items, start, 0);
            controller.prepare();
            controller.play();
        });
    }

    public static void playNext(Context ctx, Track t) {
        whenReady(ctx, () -> {
            if (controller.getMediaItemCount() == 0) {
                playList(ctx, Collections.singletonList(t), 0, false);
            } else {
                controller.addMediaItem(controller.getCurrentMediaItemIndex() + 1, t.toMediaItem());
            }
        });
    }

    public static void addToQueue(Context ctx, Track t) {
        whenReady(ctx, () -> {
            if (controller.getMediaItemCount() == 0) {
                playList(ctx, Collections.singletonList(t), 0, false);
            } else {
                controller.addMediaItem(t.toMediaItem());
            }
        });
    }

    public static void setSleep(int minutes) {
        if (sleepTask != null) App.MAIN.removeCallbacks(sleepTask);
        sleepTask = null;
        sleepAt = 0;
        if (minutes <= 0) return;
        sleepTask = () -> {
            if (controller != null) controller.pause();
            sleepAt = 0;
        };
        sleepAt = System.currentTimeMillis() + minutes * 60_000L;
        App.MAIN.postDelayed(sleepTask, minutes * 60_000L);
    }
}
