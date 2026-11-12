package com.mychord.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;

/** Ambil token akses dari Spotify Web Player (selepas pengguna log masuk dalam WebView). */
public class SpotifyGrabber extends WebViewClient {
    public interface Callback { void onToken(String token); }

    private final Callback cb;

    public SpotifyGrabber(Callback cb) { this.cb = cb; }

    @SuppressLint("SetJavaScriptEnabled")
    public static void setup(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
    }

    public static WebView headless(Context ctx, Callback cb) {
        WebView w = new WebView(ctx);
        setup(w);
        w.setWebViewClient(new SpotifyGrabber(cb));
        return w;
    }

    private void deliver(String t) {
        if (t == null || t.isEmpty()) return;
        App.MAIN.post(() -> cb.onToken(t));
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
        String url = req.getUrl().toString();
        Map<String, String> h = req.getRequestHeaders();
        if (h != null) {
            for (Map.Entry<String, String> e : h.entrySet()) {
                if ("authorization".equalsIgnoreCase(e.getKey()) && e.getValue() != null
                        && e.getValue().startsWith("Bearer ") && url.contains("spotify.com")
                        && !url.contains("accounts.spotify.com")) {
                    deliver(e.getValue().substring(7));
                }
            }
        }
        if (url.contains("open.spotify.com/api/token") || url.contains("open.spotify.com/get_access_token")) {
            try {
                Request.Builder b = new Request.Builder().url(url);
                if (h != null) for (Map.Entry<String, String> e : h.entrySet()) b.header(e.getKey(), e.getValue());
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) b.header("Cookie", ck);
                try (Response r = App.http.newCall(b.build()).execute()) {
                    String body = r.body() != null ? r.body().string() : "";
                    try {
                        JSONObject o = new JSONObject(body);
                        if (!o.optBoolean("isAnonymous", false)) deliver(o.optString("accessToken"));
                    } catch (Exception ignored) { }
                    Map<String, String> rh = new HashMap<>();
                    rh.put("Access-Control-Allow-Origin", "https://open.spotify.com");
                    rh.put("Access-Control-Allow-Credentials", "true");
                    return new WebResourceResponse("application/json", "utf-8", r.code(),
                            r.message().isEmpty() ? "OK" : r.message(), rh,
                            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                }
            } catch (Exception ignored) { }
        }
        return null;
    }
}
