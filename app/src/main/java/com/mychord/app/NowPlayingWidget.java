package com.mychord.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.RemoteViews;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Widget "Sedang dimainkan" — tunjuk karya seni album, tajuk, artis dan butang kawalan
 * pada skrin utama telefon.
 */
public class NowPlayingWidget extends AppWidgetProvider {
    private static final String ACTION_PREV = "com.mychord.app.WIDGET_PREV";
    private static final String ACTION_PLAY = "com.mychord.app.WIDGET_PLAY";
    private static final String ACTION_NEXT = "com.mychord.app.WIDGET_NEXT";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, buildViews(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String act = intent.getAction();
        if (ACTION_PREV.equals(act) || ACTION_PLAY.equals(act) || ACTION_NEXT.equals(act)) {
            PlayerHub.connect(ctx);
            PlayerHub.whenReady(ctx, () -> {
                androidx.media3.session.MediaController c = PlayerHub.get();
                if (c == null) return;
                switch (act) {
                    case ACTION_PREV: c.seekToPreviousMediaItem(); break;
                    case ACTION_PLAY:
                        if (c.isPlaying()) c.pause(); else c.play();
                        break;
                    case ACTION_NEXT: c.seekToNextMediaItem(); break;
                }
                // Kemas kini widget selepas tindakan
                App.MAIN.postDelayed(() -> refreshAll(ctx), 500);
            });
        }
    }

    private RemoteViews buildViews(Context ctx) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_now_playing);

        // Buka PlayerActivity bila tekan widget
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        rv.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        // Butang kawalan
        rv.setOnClickPendingIntent(R.id.widgetPrev, makePi(ctx, ACTION_PREV, 1));
        rv.setOnClickPendingIntent(R.id.widgetPlay, makePi(ctx, ACTION_PLAY, 2));
        rv.setOnClickPendingIntent(R.id.widgetNext, makePi(ctx, ACTION_NEXT, 3));

        // Isi maklumat lagu semasa
        String title = Prefs.getS("widget_title", ctx.getString(R.string.app_name));
        String artist = Prefs.getS("widget_artist", ctx.getString(R.string.nothing_playing));
        rv.setTextViewText(R.id.widgetTitle, title);
        rv.setTextViewText(R.id.widgetArtist, artist);

        boolean playing = Prefs.getB("widget_playing", false);
        rv.setImageViewResource(R.id.widgetPlay, playing ? R.drawable.ic_pause : R.drawable.ic_play);

        return rv;
    }

    private PendingIntent makePi(Context ctx, String action, int code) {
        Intent i = new Intent(ctx, NowPlayingWidget.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Dipanggil oleh PlaybackService untuk kemas kini widget secara langsung. */
    public static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, NowPlayingWidget.class));
        if (ids.length == 0) return;

        // Simpan maklumat terkini untuk widget
        androidx.media3.session.MediaController c = PlayerHub.get();
        if (c != null && c.getMediaItemCount() > 0) {
            androidx.media3.common.MediaItem mi = c.getCurrentMediaItem();
            if (mi != null && mi.mediaMetadata.title != null) {
                Prefs.putS("widget_title", mi.mediaMetadata.title.toString());
                Prefs.putS("widget_artist", mi.mediaMetadata.artist != null ?
                        mi.mediaMetadata.artist.toString() : "");
            }
            Prefs.putB("widget_playing", c.isPlaying());
        }

        Intent update = new Intent(ctx, NowPlayingWidget.class);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        ctx.sendBroadcast(update);
    }
}
