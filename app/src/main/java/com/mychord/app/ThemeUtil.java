package com.mychord.app;

public class ThemeUtil {
    public static final String[] IDS = {"ember", "kaca", "kacacerah", "lagun", "malam", "matcha", "amoled", "kertas", "laut", "kopi", "emas", "salji", "neon", "sakura"};
    public static final int[] STYLES = {
            R.style.Theme_MyChord_Ember, R.style.Theme_MyChord_Kaca, R.style.Theme_MyChord_KacaCerah, R.style.Theme_MyChord_Lagun, R.style.Theme_MyChord_Malam, R.style.Theme_MyChord_Matcha, R.style.Theme_MyChord_Amoled, R.style.Theme_MyChord_Kertas, R.style.Theme_MyChord_Laut, R.style.Theme_MyChord_Kopi, R.style.Theme_MyChord_Emas, R.style.Theme_MyChord_Salji, R.style.Theme_MyChord_Neon, R.style.Theme_MyChord_Sakura};
    public static final int[] NAMES = {
            R.string.theme_ember, R.string.theme_kaca, R.string.theme_kacacerah, R.string.theme_lagun, R.string.theme_malam, R.string.theme_matcha, R.string.theme_amoled, R.string.theme_kertas, R.string.theme_laut, R.string.theme_kopi, R.string.theme_emas, R.string.theme_salji, R.string.theme_neon, R.string.theme_sakura};
    /** {latar, aksen} untuk pratonton */
    public static final int[][] COLORS = {
            {0xFF1A1513, 0xFFFF6B35}, {0xFF3A2470, 0xFF9EE7FF}, {0xFFDCE5FF, 0xFF3B6CFF}, {0xFF0F1F22, 0xFF2EE6C5}, {0xFF181327, 0xFFA98BFF}, {0xFF142017, 0xFF9BE15D}, {0xFF000000, 0xFFFF3B5C}, {0xFFFFFFFF, 0xFFD9480F}, {0xFF0D1A2E, 0xFF4FA3FF}, {0xFF1F1712, 0xFFD4A373}, {0xFF17150F, 0xFFE8B931}, {0xFFFFFFFF, 0xFF2563EB}, {0xFF100E1C, 0xFF00F0FF}, {0xFF22121A, 0xFFFF8FB8}};

    public static boolean locked(String id) {
        return ("neon".equals(id) || "sakura".equals(id)) && !AddonManager.installed("themes");
    }

    public static int style() {
        String t = Prefs.theme();
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(t) && !locked(t)) return STYLES[i];
        }
        return STYLES[0];
    }
}
