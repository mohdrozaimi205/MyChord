package com.mychord.app;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Muat turun strim YouTube sekeping-sekeping (1 MB setiap permintaan) supaya YouTube tidak
 * memperlahankan muat turun — punca utama lagu "stop-stop". Sumber lain dibaca seperti biasa.
 */
public class ChunkedDataSource implements DataSource {
    private static final long CHUNK = 1024L * 1024L;

    public static class Factory implements DataSource.Factory {
        private final HttpDataSource.Factory http;
        public Factory(HttpDataSource.Factory http) { this.http = http; }
        @NonNull
        @Override
        public DataSource createDataSource() { return new ChunkedDataSource(http.createDataSource()); }
    }

    private final HttpDataSource upstream;
    private DataSpec base;
    private boolean chunked;
    private long pos, end;

    public ChunkedDataSource(HttpDataSource upstream) { this.upstream = upstream; }

    @Override
    public void addTransferListener(@NonNull TransferListener l) { upstream.addTransferListener(l); }

    private static boolean isYoutube(Uri u) {
        String h = u.getHost();
        return h != null && h.endsWith("googlevideo.com");
    }

    @Override
    public long open(@NonNull DataSpec spec) throws IOException {
        base = spec;
        chunked = isYoutube(spec.uri);
        if (!chunked) return upstream.open(spec);
        pos = spec.position;
        end = spec.length == C.LENGTH_UNSET ? C.LENGTH_UNSET : spec.position + spec.length;
        openChunk();
        if (end == C.LENGTH_UNSET) {
            long total = totalFromHeaders();
            if (total > 0) end = total;
        }
        return end == C.LENGTH_UNSET ? C.LENGTH_UNSET : end - spec.position;
    }

    private void openChunk() throws IOException {
        long len = CHUNK;
        if (end != C.LENGTH_UNSET) len = Math.min(len, end - pos);
        upstream.open(base.buildUpon().setPosition(pos).setLength(len).build());
    }

    private long totalFromHeaders() {
        Map<String, List<String>> h = upstream.getResponseHeaders();
        for (Map.Entry<String, List<String>> e : h.entrySet()) {
            if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Content-Range") || e.getValue().isEmpty()) continue;
            String v = e.getValue().get(0);
            int slash = v.lastIndexOf('/');
            if (slash > 0) {
                try { return Long.parseLong(v.substring(slash + 1).trim()); } catch (Exception ignored) { }
            }
        }
        return -1;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (!chunked) return upstream.read(buffer, offset, length);
        if (length == 0) return 0;
        if (end != C.LENGTH_UNSET && pos >= end) return C.RESULT_END_OF_INPUT;
        int n = upstream.read(buffer, offset, length);
        if (n == C.RESULT_END_OF_INPUT) {
            upstream.close();
            if (end == C.LENGTH_UNSET || pos >= end) return C.RESULT_END_OF_INPUT;
            openChunk();
            n = upstream.read(buffer, offset, length);
            if (n == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT;
        }
        pos += n;
        return n;
    }

    @Nullable
    @Override
    public Uri getUri() { return upstream.getUri(); }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getUri() == null ? Collections.emptyMap() : upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException { upstream.close(); }
}
