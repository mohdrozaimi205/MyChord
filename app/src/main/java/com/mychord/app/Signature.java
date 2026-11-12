package com.mychord.app;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Cap jari audio format Shazam (16 kHz mono). Diterjemah ke Java dari Opentune
 * (https://github.com/justshivv/Opentune, GPL-3.0) yang berasaskan SongRec (GPL-3.0).
 */
public class Signature {
    private static final int WINDOW = 2048, BINS = 1025, STEP = 128, RING = 256;
    private static final int[] FORMER = {-1, -3, -6};
    private static final int[] NEIGHBOURS = {-10, -7, -4, -3, 1, 2, 5, 8};
    private static final int[] OTHER_FRAMES = {-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249};
    private static final double[] HANN = new double[WINDOW];

    static {
        for (int i = 0; i < WINDOW; i++) HANN[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * (i + 1) / 2049);
    }

    private final short[] samples = new short[WINDOW];
    private int samplesAt = 0;
    private final float[][] ffts = new float[RING][BINS];
    private int fftAt = 0;
    private final float[][] spread = new float[RING][BINS];
    private int spreadAt = 0;
    private int spreadWritten = 0;
    private int sampleCount = 0;
    private final TreeMap<Integer, List<int[]>> peaks = new TreeMap<>(); // {pass, magnitude, bin}
    private final double[] re = new double[WINDOW];
    private final double[] im = new double[WINDOW];
    private final short[] pending = new short[STEP];
    private int pendingCount = 0;

    public void add(short[] pcm, int count) {
        for (int i = 0; i < count; i++) {
            pending[pendingCount++] = pcm[i];
            if (pendingCount == STEP) {
                for (int k = 0; k < STEP; k++) samples[(samplesAt + k) % WINDOW] = pending[k];
                samplesAt = (samplesAt + STEP) % WINDOW;
                sampleCount += STEP;
                pendingCount = 0;
                fft();
                spreadPeaks();
                if (spreadWritten >= 46) findPeaks();
            }
        }
    }

    public long durationMs() { return sampleCount * 1000L / 16000; }

    private void fft() {
        for (int k = 0; k < WINDOW; k++) {
            re[k] = samples[(samplesAt + k) % WINDOW] * HANN[k];
            im[k] = 0;
        }
        transform(re, im);
        float[] out = ffts[fftAt];
        for (int k = 0; k < BINS; k++) {
            out[k] = Math.max((float) ((re[k] * re[k] + im[k] * im[k]) / (1 << 17)), 1e-10f);
        }
        fftAt = (fftAt + 1) % RING;
    }

    private void spreadPeaks() {
        float[] origin = ffts[(fftAt - 1 + RING) % RING];
        float[] s = spread[spreadAt];
        System.arraycopy(origin, 0, s, 0, BINS);
        for (int p = 0; p < BINS; p++) {
            if (p < BINS - 2) s[p] = Math.max(s[p], Math.max(s[p + 1], s[p + 2]));
            float top = s[p];
            for (int back : FORMER) {
                float[] former = spread[(spreadAt + back + RING) % RING];
                top = Math.max(former[p], top);
                former[p] = top;
            }
        }
        spreadAt = (spreadAt + 1) % RING;
        spreadWritten++;
    }

    private void findPeaks() {
        float[] f46 = ffts[(fftAt - 46 + RING) % RING];
        float[] s49 = spread[(spreadAt - 49 + RING) % RING];
        for (int bin = 10; bin < 1015; bin++) {
            float v = f46[bin];
            if (v < 1f / 64 || v < s49[bin - 1]) continue;
            float near = 0f;
            for (int o : NEIGHBOURS) near = Math.max(s49[bin + o], near);
            if (v <= near) continue;
            float around = near;
            for (int o : OTHER_FRAMES) around = Math.max(spread[((spreadAt + o) % RING + RING) % RING][bin - 1], around);
            if (v <= around) continue;

            int pass = spreadWritten - 46;
            float magnitude = (float) (Math.log(Math.max(1f / 64, v)) * 1477.3f + 6144);
            float before = (float) (Math.log(Math.max(1f / 64, f46[bin - 1])) * 1477.3f + 6144);
            float after = (float) (Math.log(Math.max(1f / 64, f46[bin + 1])) * 1477.3f + 6144);
            float variation1 = magnitude * 2 - before - after;
            if (variation1 <= 0f) continue;
            float variation2 = (after - before) * 32 / variation1;
            float corrected = bin * 64 + variation2;
            float hz = corrected * (16000f / 2 / 1024 / 64);
            int band;
            if (hz > 250 && hz < 520) band = 0;
            else if (hz > 520 && hz < 1450) band = 1;
            else if (hz > 1450 && hz < 3500) band = 2;
            else if (hz > 3500 && hz <= 5500) band = 3;
            else continue;
            List<int[]> l = peaks.get(band);
            if (l == null) { l = new ArrayList<>(); peaks.put(band, l); }
            l.add(new int[]{pass, (int) magnitude, (int) corrected});
        }
    }

    private static void le32(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF); o.write((v >>> 16) & 0xFF); o.write((v >>> 24) & 0xFF);
    }

    private static void le16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF);
    }

    public byte[] encode() {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        for (Map.Entry<Integer, List<int[]>> e : peaks.entrySet()) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            int pass = 0;
            for (int[] p : e.getValue()) {
                if (p[0] - pass >= 255) {
                    body.write(0xFF);
                    le32(body, p[0]);
                    pass = p[0];
                }
                body.write(p[0] - pass);
                le16(body, p[1]);
                le16(body, p[2]);
                pass = p[0];
            }
            byte[] bytes = body.toByteArray();
            le32(contents, 0x60030040 + e.getKey());
            le32(contents, bytes.length);
            contents.write(bytes, 0, bytes.length);
            for (int i = 0; i < (4 - bytes.length % 4) % 4; i++) contents.write(0);
        }
        byte[] c = contents.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        le32(out, 0xCAFE2580);
        le32(out, 0);
        le32(out, c.length + 8);
        le32(out, 0x94119C00);
        for (int i = 0; i < 3; i++) le32(out, 0);
        le32(out, 3 << 27);
        for (int i = 0; i < 2; i++) le32(out, 0);
        le32(out, (int) (sampleCount + 16000 * 0.24));
        le32(out, (15 << 19) + 0x40000);
        le32(out, 0x40000000);
        le32(out, c.length + 8);
        out.write(c, 0, c.length);
        byte[] all = out.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(all, 8, all.length - 8);
        int v = (int) crc.getValue();
        for (int k = 0; k < 4; k++) all[4 + k] = (byte) (v >>> (8 * k));
        return all;
    }

    private static void transform(double[] re, double[] im) {
        int n = re.length;
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) { j ^= bit; bit >>= 1; }
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wr = Math.cos(angle), wi = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double ar = re[b] * cr - im[b] * ci;
                    double ai = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - ar;
                    im[b] = im[a] - ai;
                    re[a] += ar;
                    im[a] += ai;
                    double ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }
}
