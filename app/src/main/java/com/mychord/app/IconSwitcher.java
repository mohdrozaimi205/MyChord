package com.mychord.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public class IconSwitcher {
    public static final String[] IDS = {"ember", "lagun", "malam", "kertas", "neon", "emas", "matcha", "sakura"};
    public static final String[] ALIASES = {".IconEmber", ".IconLagun", ".IconMalam", ".IconKertas", ".IconNeon", ".IconEmas", ".IconMatcha", ".IconSakura"};
    public static final int[] PREVIEWS = {R.drawable.icon_preview_ember, R.drawable.icon_preview_lagun,
            R.drawable.icon_preview_malam, R.drawable.icon_preview_kertas,
            R.drawable.icon_preview_neon, R.drawable.icon_preview_emas, R.drawable.icon_preview_matcha, R.drawable.icon_preview_sakura};
    public static final int[] NAMES = {R.string.icon_ember, R.string.icon_lagun, R.string.icon_malam, R.string.icon_kertas,
            R.string.icon_neon, R.string.icon_emas, R.string.icon_matcha, R.string.icon_sakura};

    public static int preview() {
        String cur = Prefs.icon();
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(cur)) return PREVIEWS[i];
        return PREVIEWS[0];
    }

    public static void apply(Context ctx, String id) {
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();
        // Hidupkan ikon baharu dahulu, kemudian matikan yang lain
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + ALIASES[i]),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }
        }
        for (int i = 0; i < IDS.length; i++) {
            if (!IDS[i].equals(id)) {
                pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + ALIASES[i]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        Prefs.putS("icon", id);
    }
}
