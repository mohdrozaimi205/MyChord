package com.mychord.app;

import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Equalizer, bass, bunyi 3D, penguat suara + addon kualiti (AutoEq, kelantangan seimbang). */
public class Effects {
    public static Equalizer eq;
    public static BassBoost bass;
    public static Virtualizer virt;
    public static LoudnessEnhancer loud;
    public static DynamicsProcessing dp;
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
    // Addon "Pek Pratetap EQ"
    public static final String[] PACK_KEYS = {"dangdut", "rockkapak", "nasyid", "clearvocal", "carbass", "acoustic", "electronic", "classical"};
    public static final int[][] PACK = {
            {400, 300, 200, 300, 100},
            {500, 100, -300, 400, 600},
            {-100, 100, 400, 300, 100},
            {-300, -100, 500, 400, 100},
            {900, 600, 0, -100, 100},
            {200, 100, 100, 200, 300},
            {600, 300, -100, 200, 500},
            {300, 100, -100, 200, 300},
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
        try { eq = new Equalizer(0, sessionId); } catch (Throwable t) { eq = null; }
        try { bass = new BassBoost(0, sessionId); applyBass(); } catch (Throwable t) { bass = null; }
        try { virt = new Virtualizer(0, sessionId); applyVirt(); } catch (Throwable t) { virt = null; }
        try { loud = new LoudnessEnhancer(sessionId); applyLoud(); } catch (Throwable t) { loud = null; }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                        true, 10, true, 1, false, 0, true).build();
                dp = new DynamicsProcessing(0, sessionId, cfg);
            } catch (Throwable t) { dp = null; }
        }
        applyQualityAddons();
    }

    public static synchronized void release() {
        try { if (eq != null) eq.release(); } catch (Throwable ignored) { }
        try { if (bass != null) bass.release(); } catch (Throwable ignored) { }
        try { if (virt != null) virt.release(); } catch (Throwable ignored) { }
        try { if (loud != null) loud.release(); } catch (Throwable ignored) { }
        try { if (dp != null) dp.release(); } catch (Throwable ignored) { }
        eq = null; bass = null; virt = null; loud = null; dp = null;
    }

    public static boolean eqOn() { return Prefs.getB("eqOn", false); }

    public static int bandLevel(int band) { return Prefs.getI("eqBand" + band, 0); }

    // ---------------- AutoEq (data: github.com/jaakkopasanen/AutoEq, MIT) ----------------
    public static class Profile {
        public String name, src;
        public float pre;
        public float[] f, g;
    }

    private static List<Profile> profiles;

    public static synchronized List<Profile> profiles() {
        if (profiles != null) return profiles;
        profiles = new ArrayList<>();
        try (InputStream in = App.get().getAssets().open("autoeq.json")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONArray a = new JSONArray(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Profile p = new Profile();
                p.name = o.getString("name");
                p.src = o.optString("src");
                p.pre = (float) o.optDouble("pre", 0);
                JSONArray f = o.getJSONArray("f"), g = o.getJSONArray("g");
                p.f = new float[f.length()];
                p.g = new float[g.length()];
                for (int k = 0; k < f.length(); k++) {
                    p.f[k] = (float) f.getDouble(k);
                    p.g[k] = (float) g.getDouble(k);
                }
                profiles.add(p);
            }
        } catch (Exception ignored) { }
        return profiles;
    }

    public static Profile currentProfile() {
        if (!AddonManager.installed("autoeq")) return null;
        String n = Prefs.getS("autoeqModel", "");
        if (n.isEmpty()) return null;
        for (Profile p : profiles()) if (p.name.equals(n)) return p;
        return null;
    }

    /** Gain AutoEq (dB) pada frekuensi tertentu — interpolasi log antara jalur */
    static float autoeqGainAt(Profile p, float hz) {
        if (p == null || p.f.length == 0) return 0;
        if (hz <= p.f[0]) return p.g[0];
        for (int i = 1; i < p.f.length; i++) {
            if (hz <= p.f[i]) {
                double t = (Math.log(hz) - Math.log(p.f[i - 1])) / (Math.log(p.f[i]) - Math.log(p.f[i - 1]));
                return (float) (p.g[i - 1] * (1 - t) + p.g[i] * t);
            }
        }
        return p.g[p.g.length - 1];
    }

    public static boolean normalizeOn() { return AddonManager.installed("normalize") && Prefs.getB("normalize", true); }

    public static void applyEq() {
        if (eq == null) return;
        try {
            // Tanpa DynamicsProcessing (Android 8), AutoEq digabung terus ke Equalizer
            Profile p = (dp == null) ? currentProfile() : null;
            short n = eq.getNumberOfBands();
            short[] range = eq.getBandLevelRange();
            for (short b = 0; b < n; b++) {
                int lvl = eqOn() ? bandLevel(b) : 0;
                if (p != null) lvl += Math.round(autoeqGainAt(p, eq.getCenterFreq(b) / 1000f) * 100);
                lvl = Math.max(range[0], Math.min(range[1], lvl));
                eq.setBandLevel(b, (short) lvl);
            }
            eq.setEnabled(eqOn() || p != null);
        } catch (Throwable ignored) { }
    }

    /** AutoEq (10 jalur) & kelantangan seimbang melalui DynamicsProcessing (Android 9+) */
    public static void applyQualityAddons() {
        applyEq();
        if (dp == null || Build.VERSION.SDK_INT < 28) return;
        try {
            Profile p = currentProfile();
            DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, 10);
            float[] centers = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
            for (int i = 0; i < 10; i++) {
                float cutoff = i < 9 ? (float) Math.sqrt(centers[i] * centers[i + 1]) : 20000f;
                float gain = p != null ? autoeqGainAt(p, centers[i]) : 0f;
                pre.setBand(i, new DynamicsProcessing.EqBand(true, cutoff, gain));
            }
            dp.setPreEqAllChannelsTo(pre);
            dp.setInputGainAllChannelsTo(p != null ? Math.min(0f, p.pre) : 0f);

            boolean norm = normalizeOn();
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, true, 1);
            mbc.setBand(0, new DynamicsProcessing.MbcBand(true, 20000f,
                    norm ? 5f : 1f, norm ? 120f : 50f, norm ? 3f : 1f, norm ? -26f : 0f, norm ? 8f : 0f,
                    -90f, 1f, 0f, norm ? 9f : 0f));
            dp.setMbcAllChannelsTo(mbc);
            dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, norm || p != null, 0,
                    1f, 60f, 10f, -1f, 0f));
            dp.setEnabled(p != null || norm);
        } catch (Throwable ignored) { }
    }

    /** Tetapkan pratetap ke semua jalur (diselaraskan ikut bilangan jalur peranti). */
    public static void setPreset(String key, int[] src) {
        Prefs.putS("eqPreset", key);
        int n = eq != null ? eq.getNumberOfBands() : 5;
        for (int b = 0; b < n; b++) {
            float pos = n == 1 ? 0 : b * (src.length - 1f) / (n - 1f);
            int lo = (int) Math.floor(pos), hi = Math.min(src.length - 1, lo + 1);
            float f = pos - lo;
            int v = Math.round(src[lo] * (1 - f) + src[hi] * f);
            Prefs.putI("eqBand" + b, v);
        }
        applyEq();
    }

    public static void setPreset(int p) { setPreset(PRESET_KEYS[p], PRESETS[p]); }

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
