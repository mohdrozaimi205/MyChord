package com.mychord.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;

import java.util.HashSet;
import java.util.Set;

public class Prefs {
    private static SharedPreferences sp() {
        return App.get().getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    public static String getS(String k, String def) { return sp().getString(k, def); }
    public static void putS(String k, String v) { sp().edit().putString(k, v).apply(); }
    public static int getI(String k, int def) { return sp().getInt(k, def); }
    public static void putI(String k, int v) { sp().edit().putInt(k, v).apply(); }
    public static boolean getB(String k, boolean def) { return sp().getBoolean(k, def); }
    public static void putB(String k, boolean v) { sp().edit().putBoolean(k, v).apply(); }

    public static Set<String> getSet(String k) { return new HashSet<>(sp().getStringSet(k, new HashSet<>())); }
    public static void putSet(String k, Set<String> v) { sp().edit().putStringSet(k, new HashSet<>(v)).apply(); }

    public static String theme() { return getS("theme", "ember"); }
    public static String icon() { return getS("icon", "ember"); }
    public static String lang() { return getS("lang", "ms"); }

    /** 0 = jimat, 1 = biasa, 2 = tertinggi */
    public static int qWifi() { return getI("qWifi", 2); }
    public static int qData() { return getI("qData", 1); }

    public static int currentQuality() {
        try {
            ConnectivityManager cm = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && cm.isActiveNetworkMetered()) return qData();
        } catch (Exception ignored) { }
        return qWifi();
    }
}
