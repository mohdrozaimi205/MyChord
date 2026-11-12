package com.mychord.app;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

/** Log masuk YouTube Music / Spotify melalui halaman rasmi dalam WebView. */
public class LoginActivity extends BaseActivity {
    public static final String YT = "yt";
    public static final String SP = "sp";
    private WebView web;
    private boolean done = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        String service = getIntent().getStringExtra("service");
        boolean yt = !SP.equals(service);
        setupTop(getString(yt ? R.string.login_yt : R.string.login_sp));
        ((TextView) findViewById(R.id.loginNote)).setText(yt ? R.string.login_yt_note : R.string.login_sp_note);
        ProgressBar pb = findViewById(R.id.loginProgress);
        web = findViewById(R.id.loginWeb);
        SpotifyGrabber.setup(web);

        if (yt) {
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView v, String url, Bitmap f) { pb.setVisibility(View.VISIBLE); }

                @Override
                public void onPageFinished(WebView v, String url) {
                    pb.setVisibility(View.GONE);
                    if (done || url == null || !url.startsWith("https://music.youtube.com")) return;
                    String c = CookieManager.getInstance().getCookie("https://music.youtube.com");
                    if (c != null && (c.contains("SAPISID=") || c.contains("__Secure-3PAPISID="))) {
                        done = true;
                        CookieManager.getInstance().flush();
                        YtmAccount.save(c);
                        App.IO.execute(() -> {
                            String n = YtmAccount.fetchName();
                            App.MAIN.post(() -> finishOk(n));
                        });
                    }
                }
            });
            web.loadUrl("https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true"
                    + "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue"
                    + "%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F");
        } else {
            SpotifyGrabber grabber = new SpotifyGrabber(token -> {
                if (done) return;
                App.IO.execute(() -> {
                    if (!done && SpotifyAccount.validate(token)) {
                        done = true;
                        CookieManager.getInstance().flush();
                        App.MAIN.post(() -> finishOk(SpotifyAccount.name()));
                    }
                });
            });
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView v, String url, Bitmap f) { pb.setVisibility(View.VISIBLE); }

                @Override
                public void onPageFinished(WebView v, String url) { pb.setVisibility(View.GONE); }

                @Nullable
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    return grabber.shouldInterceptRequest(view, request);
                }
            });
            web.loadUrl("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F");
        }
    }

    private void finishOk(String name) {
        if (isFinishing()) return;
        Toast.makeText(this, getString(R.string.login_ok, name == null || name.isEmpty() ? "✓" : name),
                Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
