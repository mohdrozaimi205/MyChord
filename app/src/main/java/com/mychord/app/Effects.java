package com.mychord.app;

import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

/** Equalizer, bass, bunyi 3D & penguat suara pada sesi audio MyChord. */
public class Effects {
    public static Equalizer eq;
    public static BassBoost bass;
    public static Virtualizer virt;
    public static LoudnessEnhancer loud;
    public static int session = 0;

    // Pratetap 5 jalur (milibel): 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
    public static final String[] PRESET_KEYS = {"flat", "bass", "vocal", "rock", "pop", "treble", "night"};
    public static final int[][] PRESETS = {
            {0, 0, 0, 0, 0},
            {600, 400, 0, 0, 100},
            {-200, 0, 400, 300, 0},
            {500, 200, -200, 300, 500},
            {-100, 200, 400, 200, -100},
            {0, 0, 0, 300, 600},
            {200, 100, 0, -200, -400},
    };

    public static synchronized void attach(Context ctx, int sessionId) {
        if (sessionId == 0) return;
        release();
        session = sessionId;
        try {
            Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.getPackageName());
            i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
            ctx.sendBroadcast(i);
        } catch (Exception ignored) { }
        try {
            eq = new Equalizer(0, sessionId);
            applyEq();
        } catch (Throwable t) { eq = null; }
        try {
            bass = new BassBoost(0, sessionId);
            applyBass();
        } catch (Throwable t) { bass = null; }
        try {
            virt = new Virtualizer(0, sessionId);
            applyVirt();
        } catch (Throwable t) { virt = null; }
        try {
            loud = new LoudnessEnhancer(sessionId);
            applyLoud();
        } catch (Throwable t) { loud = null; }
    }

    public static synchronized void release() {
        try { if (eq != null) eq.release(); } catch (Throwable ignored) { }
        try { if (bass != null) bass.release(); } catch (Throwable ignored) { }
        try { if (virt != null) virt.release(); } catch (Throwable ignored) { }
        try { if (loud != null) loud.release(); } catch (Throwable ignored) { }
        eq = null; bass = null; virt = null; loud = null;
    }

    public static boolean eqOn() { return Prefs.getB("eqOn", false); }

    public static int bandLevel(int band) {
        return Prefs.getI("eqBand" + band, 0);
    }

    public static void applyEq() {
        if (eq == null) return;
        try {
            short n = eq.getNumberOfBands();
            short[] range = eq.getBandLevelRange();
            for (short b = 0; b < n; b++) {
                int lvl = Math.max(range[0], Math.min(range[1], bandLevel(b)));
                eq.setBandLevel(b, (short) lvl);
            }
            eq.setEnabled(eqOn());
        } catch (Throwable ignored) { }
    }

    /** Tetapkan pratetap ke semua jalur (diselaraskan ikut bilangan jalur peranti). */
    public static void setPreset(int p) {
        Prefs.putS("eqPreset", PRESET_KEYS[p]);
        int n = eq != null ? eq.getNumberOfBands() : 5;
        int[] src = PRESETS[p];
        for (int b = 0; b < n; b++) {
            float pos = n == 1 ? 0 : b * (src.length - 1f) / (n - 1f);
            int lo = (int) Math.floor(pos), hi = Math.min(src.length - 1, lo + 1);
            float f = pos - lo;
            int v = Math.round(src[lo] * (1 - f) + src[hi] * f);
            Prefs.putI("eqBand" + b, v);
        }
        applyEq();
    }

    public static void applyBass() {
        if (bass == null) return;
        try {
            int s = Prefs.getI("bass", 0);
            bass.setStrength((short) s);
            bass.setEnabled(s > 0);
        } catch (Throwable ignored) { }
    }

    public static void applyVirt() {
        if (virt == null) return;
        try {
            int s = Prefs.getI("virt", 0);
            virt.setStrength((short) s);
            virt.setEnabled(s > 0);
        } catch (Throwable ignored) { }
    }

    public static void applyLoud() {
        if (loud == null) return;
        try {
            int g = Prefs.getI("loud", 0);
            loud.setTargetGain(g);
            loud.setEnabled(g > 0);
        } catch (Throwable ignored) { }
    }
}
