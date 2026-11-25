package com.mychord.app;

public class ThemeUtil {
    public static final String[] IDS = {"tubeawak", "spothijau", "iosglass"};
    public static final int[] STYLES = {
            R.style.Theme_MyChord_TubeAwak,
            R.style.Theme_MyChord_SpotHijau,
            R.style.Theme_MyChord_IosGlass};
    public static final int[] NAMES = {
            R.string.theme_tubeawak,
            R.string.theme_spothijau,
            R.string.theme_iosglass};
    /** {latar, aksen} untuk pratonton */
    public static final int[][] COLORS = {
            {0xFF030303, 0xFFFF0000},
            {0xFF121212, 0xFF1DB954},
            {0xFFF0F4FF, 0xFF007AFF}};

    public static int style() {
        String t = Prefs.theme();
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(t)) return STYLES[i];
        }
        return STYLES[0]; // default TubeAwak
    }

    /** Check if current theme is iosglass (for frosted glass effects) */
    public static boolean isGlass() {
        return "iosglass".equals(Prefs.theme());
    }
}
