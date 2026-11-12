package com.mychord.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public class IconSwitcher {
    public static final String[] IDS = {"ember", "lagun", "malam", "kertas"};
    public static final String[] ALIASES = {".IconEmber", ".IconLagun", ".IconMalam", ".IconKertas"};
    public static final int[] PREVIEWS = {R.drawable.icon_preview_ember, R.drawable.icon_preview_lagun,
            R.drawable.icon_preview_malam, R.drawable.icon_preview_kertas};
    public static final int[] NAMES = {R.string.icon_ember, R.string.icon_lagun, R.string.icon_malam, R.string.icon_kertas};

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
