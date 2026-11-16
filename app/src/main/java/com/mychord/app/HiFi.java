package com.mychord.app;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;

import java.util.List;
import java.util.Locale;

/**
 * MyChord Hi-Fi Engine.
 * Semua maklumat diambil daripada stream sebenar yang dimainkan oleh ExoPlayer —
 * bukan label. Audio YouTube kekal lossy (Opus/AAC); lossless sebenar hanya untuk FLAC/WAV/ALAC.
 */
public class HiFi {
    // Format masuk (sumber)
    public static volatile String inMime = null;
    public static volatile int inBitrate = 0, inRate = 0, inChannels = 0, inBits = 0;
    // Format keluar (ke AudioTrack)
    public static volatile int outEncoding = 0, outRate = 0;
    public static volatile boolean outOffload = false;

    /** Mod Hi-Fi tulen: tiada EQ/kesan, laluan 32-bit float, kualiti tertinggi */
    public static boolean pureOn() { return Prefs.getB("hifiPure", false); }

    public static boolean bitPerfectOn() { return Prefs.getB("hifiBitPerfect", false); }

    public static AnalyticsListener listener() {
        return new AnalyticsListener() {
            @Override
            public void onAudioInputFormatChanged(@NonNull EventTime t, @NonNull Format f,
                                                  @Nullable DecoderReuseEvaluation e) {
                int oldRate = inRate;
                inMime = f.sampleMimeType;
                inBitrate = f.bitrate != Format.NO_VALUE ? f.bitrate
                        : (f.averageBitrate != Format.NO_VALUE ? f.averageBitrate : f.peakBitrate);
                inRate = f.sampleRate;
                inChannels = f.channelCount;
                inBits = bitsOf(f.pcmEncoding);
                if (inBits == 0 && MimeTypes.AUDIO_FLAC.equals(inMime) && f.initializationData.size() > 0) {
                    inBits = flacBits(f.initializationData.get(0));
                }
                // Bit-perfect: ikut kadar sampel lagu baharu
                if (bitPerfectOn() && inRate > 0 && inRate != oldRate) {
                    try { applyBitPerfect(App.get()); } catch (Throwable ignored) { }
                }
            }

            @Override
            public void onAudioTrackInitialized(@NonNull EventTime t, @NonNull AudioSink.AudioTrackConfig c) {
                outEncoding = c.encoding;
                outRate = c.sampleRate;
                outOffload = c.offload;
            }
        };
    }

    /** Baca bit depth daripada blok STREAMINFO FLAC (initializationData) */
    static int flacBits(byte[] d) {
        try {
            // Mungkin bermula dengan "fLaC" + header blok 4 bait; STREAMINFO: bit 100..104 = bps-1
            int off = 0;
            if (d.length >= 4 && d[0] == 'f' && d[1] == 'L' && d[2] == 'a' && d[3] == 'C') off = 8;
            if (d.length < off + 14) return 0;
            int b12 = d[off + 12] & 0xFF, b13 = d[off + 13] & 0xFF;
            int bps = (((b12 & 0x01) << 4) | (b13 >> 4)) + 1;
            return (bps >= 4 && bps <= 32) ? bps : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void reset() {
        inMime = null;
        inBitrate = inRate = inChannels = inBits = 0;
    }

    static int bitsOf(int enc) {
        switch (enc) {
            case C.ENCODING_PCM_8BIT: return 8;
            case C.ENCODING_PCM_16BIT: case C.ENCODING_PCM_16BIT_BIG_ENDIAN: return 16;
            case C.ENCODING_PCM_24BIT: case C.ENCODING_PCM_24BIT_BIG_ENDIAN: return 24;
            case C.ENCODING_PCM_32BIT: case C.ENCODING_PCM_32BIT_BIG_ENDIAN: case C.ENCODING_PCM_FLOAT: return 32;
            default: return 0;
        }
    }

    public static String codecName(String mime) {
        if (mime == null) return "—";
        switch (mime) {
            case MimeTypes.AUDIO_OPUS: return "OPUS";
            case MimeTypes.AUDIO_AAC: return "AAC";
            case MimeTypes.AUDIO_MPEG: return "MP3";
            case MimeTypes.AUDIO_FLAC: return "FLAC";
            case MimeTypes.AUDIO_ALAC: return "ALAC";
            case MimeTypes.AUDIO_RAW: return "PCM (WAV)";
            case MimeTypes.AUDIO_VORBIS: return "VORBIS";
            case MimeTypes.AUDIO_AC3: return "Dolby AC-3";
            case MimeTypes.AUDIO_E_AC3: case MimeTypes.AUDIO_E_AC3_JOC: return "Dolby E-AC-3";
            case MimeTypes.AUDIO_AC4: return "Dolby AC-4";
            default: return mime.replace("audio/", "").toUpperCase(Locale.ROOT);
        }
    }

    public static boolean lossless(String mime) {
        return MimeTypes.AUDIO_FLAC.equals(mime) || MimeTypes.AUDIO_ALAC.equals(mime)
                || MimeTypes.AUDIO_RAW.equals(mime) || (mime != null && mime.contains("wav"));
    }

    static String khz(int rate) {
        if (rate <= 0) return "—";
        return rate % 1000 == 0 ? (rate / 1000) + " kHz" : String.format(Locale.US, "%.1f kHz", rate / 1000f);
    }

    static String outEnc(int enc) {
        switch (enc) {
            case C.ENCODING_PCM_FLOAT: return "32-bit float";
            case C.ENCODING_PCM_16BIT: return "16-bit PCM";
            case C.ENCODING_PCM_24BIT: return "24-bit PCM";
            case C.ENCODING_PCM_32BIT: return "32-bit PCM";
            case 0: return "—";
            default: return "Passthrough";
        }
    }

    /** Peranti output semasa: USB DAC / Bluetooth / earphone / pembesar suara */
    public static String outputDevice(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo[] ds = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            String bt = null, wired = null, usb = null;
            for (AudioDeviceInfo d : ds) {
                switch (d.getType()) {
                    case AudioDeviceInfo.TYPE_USB_DEVICE:
                    case AudioDeviceInfo.TYPE_USB_HEADSET:
                        usb = "USB DAC • " + d.getProductName(); break;
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                        bt = "Bluetooth • " + d.getProductName(); break;
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                        wired = ctx.getString(R.string.hf_wired); break;
                }
            }
            if (usb != null) return usb;
            if (bt != null) return bt;
            if (wired != null) return wired;
            return ctx.getString(R.string.hf_speaker);
        } catch (Exception e) {
            return "—";
        }
    }

    @Nullable
    static AudioDeviceInfo usbDevice(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int ty = d.getType();
            if (ty == AudioDeviceInfo.TYPE_USB_DEVICE || ty == AudioDeviceInfo.TYPE_USB_HEADSET) return d;
        }
        return null;
    }

    static android.media.AudioAttributes musicAttrs() {
        return new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    /**
     * Bit-perfect ke USB DAC (Android 14+). Pulangkan mesej keputusan untuk dipaparkan.
     * Dipanggil semula setiap kali lagu bertukar supaya kadar sampel ikut lagu.
     */
    public static String applyBitPerfect(Context ctx) {
        if (Build.VERSION.SDK_INT < 34) return ctx.getString(R.string.hf_bp_old);
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo usb = usbDevice(ctx);
            if (!bitPerfectOn()) {
                if (usb != null) am.clearPreferredMixerAttributes(musicAttrs(), usb);
                return ctx.getString(R.string.hf_bp_off);
            }
            if (usb == null) return ctx.getString(R.string.hf_bp_nousb);
            List<android.media.AudioMixerAttributes> list = am.getSupportedMixerAttributes(usb);
            int want = inRate > 0 ? inRate : 48000;
            android.media.AudioMixerAttributes best = null;
            int bestScore = -1;
            for (android.media.AudioMixerAttributes m : list) {
                if (m.getMixerBehavior() != android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) continue;
                AudioFormat f = m.getFormat();
                int score = (f.getSampleRate() == want ? 100 : 0);
                int enc = f.getEncoding();
                if (enc == AudioFormat.ENCODING_PCM_FLOAT) score += 30;
                else if (enc == AudioFormat.ENCODING_PCM_24BIT_PACKED || enc == AudioFormat.ENCODING_PCM_32BIT) score += 20;
                else if (enc == AudioFormat.ENCODING_PCM_16BIT) score += 10;
                if (f.getChannelCount() == 2) score += 5;
                if (score > bestScore) { bestScore = score; best = m; }
            }
            if (best == null) return ctx.getString(R.string.hf_bp_unsupported);
            boolean ok = am.setPreferredMixerAttributes(musicAttrs(), usb, best);
            return ok ? ctx.getString(R.string.hf_bp_on, khz(best.getFormat().getSampleRate()))
                    : ctx.getString(R.string.hf_bp_unsupported);
        } catch (Throwable t) {
            return ctx.getString(R.string.hf_bp_unsupported);
        }
    }

    /** Ringkasan teks untuk panel Hi-Fi */
    public static String report(Context ctx, String mediaId) {
        StringBuilder sb = new StringBuilder();
        String src;
        if (mediaId == null) src = "—";
        else if (mediaId.startsWith("yt:") || mediaId.startsWith("q:")) src = "YouTube Music";
        else if (mediaId.startsWith("local:")) src = ctx.getString(R.string.hf_src_local);
        else if (mediaId.startsWith("radio:")) src = "Radio";
        else src = ctx.getString(R.string.hf_src_link);
        boolean ll = lossless(inMime);
        line(sb, ctx.getString(R.string.hf_source), src);
        line(sb, "Codec", codecName(inMime));
        line(sb, ctx.getString(R.string.hf_quality), inMime == null ? "—"
                : (ll ? ((inBits > 16 || inRate > 48000) ? "HI-RES LOSSLESS" : "LOSSLESS") : "LOSSY"));
        if (inBitrate > 0) line(sb, "Bitrate", (inBitrate / 1000) + " kbps");
        line(sb, ctx.getString(R.string.hf_rate), khz(inRate));
        if (inBits > 0) line(sb, ctx.getString(R.string.hf_bits), inBits + "-bit");
        if (inChannels > 0) line(sb, ctx.getString(R.string.hf_channels), inChannels == 1 ? "Mono" : inChannels == 2 ? "Stereo" : inChannels + " ch");
        sb.append('\n');
        line(sb, ctx.getString(R.string.hf_output), outEnc(outEncoding) + (outRate > 0 ? " • " + khz(outRate) : "") + (outOffload ? " • offload" : ""));
        line(sb, ctx.getString(R.string.hf_device), outputDevice(ctx));
        line(sb, ctx.getString(R.string.hf_effects), pureOn() ? ctx.getString(R.string.hf_bypass) : ctx.getString(R.string.hf_active));
        if (inMime != null && !ll && (src.equals("YouTube Music"))) {
            sb.append('\n').append(ctx.getString(R.string.hf_yt_note));
        }
        return sb.toString().trim();
    }

    private static void line(StringBuilder sb, String k, String v) {
        sb.append(k).append(":  ").append(v).append('\n');
    }
}
