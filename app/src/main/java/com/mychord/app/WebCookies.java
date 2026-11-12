package com.mychord.app;

import android.webkit.CookieManager;

public class WebCookies {
    /** Padam kuki untuk laman tertentu sahaja (log keluar satu akaun tanpa jejaskan akaun lain) */
    public static void clear(String... urls) {
        try {
            CookieManager cm = CookieManager.getInstance();
            for (String u : urls) {
                String all = cm.getCookie(u);
                if (all == null) continue;
                String host = android.net.Uri.parse(u).getHost();
                for (String part : all.split(";")) {
                    String name = part.split("=")[0].trim();
                    if (name.isEmpty()) continue;
                    cm.setCookie(u, name + "=; Max-Age=0; Path=/");
                    if (host != null) {
                        String base = host.startsWith("www.") ? host.substring(4) : host;
                        cm.setCookie(u, name + "=; Max-Age=0; Path=/; Domain=." + base);
                    }
                }
            }
            cm.flush();
        } catch (Exception ignored) { }
    }
}
