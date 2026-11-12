package com.mychord.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class App extends Application {
    private static App inst;
    public static final ExecutorService IO = Executors.newFixedThreadPool(4);
    public static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static OkHttpClient http;

    public static App get() { return inst; }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
        NewPipe.init(new OkDownloader(http), new Localization("ms", "MY"), new ContentCountry("MY"));
    }
}
