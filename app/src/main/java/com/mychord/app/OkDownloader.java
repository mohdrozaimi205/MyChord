package com.mychord.app;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class OkDownloader extends Downloader {
    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    private final OkHttpClient client;

    public OkDownloader(OkHttpClient client) { this.client = client; }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        String method = request.httpMethod();
        String url = request.url();
        byte[] data = request.dataToSend();
        RequestBody body = null;
        if (data != null) body = RequestBody.create(data);
        else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) body = RequestBody.create(new byte[0]);

        okhttp3.Request.Builder b = new okhttp3.Request.Builder()
                .method(method, body)
                .url(url)
                .addHeader("User-Agent", UA);
        Map<String, List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                b.removeHeader(e.getKey());
                for (String v : e.getValue()) b.addHeader(e.getKey(), v);
            }
        }
        try (okhttp3.Response r = client.newCall(b.build()).execute()) {
            if (r.code() == 429) throw new ReCaptchaException("reCaptcha Challenge requested", url);
            String s = r.body() != null ? r.body().string() : null;
            return new Response(r.code(), r.message(), r.headers().toMultimap(), s,
                    r.request().url().toString());
        }
    }
}
