package com.mychord.app;

import android.content.Context;

public class BuildConfigInfo {
    public static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }
}
