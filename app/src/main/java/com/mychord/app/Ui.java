package com.mychord.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.Locale;

public class Ui {
    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int attr(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    public static void tint(ImageView v, int color) {
        v.setImageTintList(ColorStateList.valueOf(color));
    }

    public static void art(ImageView v, String url) {
        if (url == null || url.isEmpty()) {
            Glide.with(v).clear(v);
            v.setImageResource(R.drawable.art_placeholder);
            return;
        }
        Glide.with(v).load(url).placeholder(R.drawable.art_placeholder)
                .error(R.drawable.art_placeholder).centerCrop().into(v);
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%d:%02d", m, sec);
    }
}
