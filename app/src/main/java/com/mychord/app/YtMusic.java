package com.mychord.app;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Carian & strim YouTube Music (melalui NewPipe Extractor). */
public class YtMusic {

    public static class Resolved {
        public String url;
        public String label;
    }

    public static List<Track> search(String query) throws Exception {
        StreamingService yt = ServiceList.YouTube;
        SearchQueryHandler h = yt.getSearchQHFactory().fromQuery(query,
                Collections.singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "");
        SearchInfo info = SearchInfo.getInfo(yt, h);
        List<Track> out = new ArrayList<>();
        for (InfoItem it : info.getRelatedItems()) {
            if (!(it instanceof StreamInfoItem)) continue;
            StreamInfoItem s = (StreamInfoItem) it;
            String id = idFromUrl(s.getUrl());
            if (id == null) continue;
            Track t = new Track("yt:" + id, s.getName(), s.getUploaderName(), bestThumb(s.getThumbnails()));
            t.durationSec = s.getDuration();
            out.add(t);
        }
        return out;
    }

    public static String idFromUrl(String url) {
        if (url == null) return null;
        int i = url.indexOf("v=");
        if (i < 0) return null;
        String id = url.substring(i + 2);
        int amp = id.indexOf('&');
        if (amp >= 0) id = id.substring(0, amp);
        return id;
    }

    public static String bestThumb(List<Image> imgs) {
        if (imgs == null || imgs.isEmpty()) return "";
        Image best = imgs.get(0);
        for (Image im : imgs) if (im.getHeight() > best.getHeight()) best = im;
        String u = best.getUrl();
        // Gambar YouTube Music kecil (60px) — minta versi besar
        if (u != null && u.contains("googleusercontent.com")) {
            u = u.replaceAll("=w\\d+-h\\d+", "=w544-h544");
        }
        return u == null ? "" : u;
    }

    /** quality: 0 = jimat, 1 = biasa, 2 = tertinggi, 3 = lossless (maks bitrate + Hi-Fi) */
    public static Resolved resolve(String videoId, int quality) throws Exception {
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=" + videoId);
        List<AudioStream> ok = new ArrayList<>();
        for (AudioStream a : info.getAudioStreams()) {
            if (!a.isUrl() || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            AudioTrackType type = a.getAudioTrackType();
            if (type != null && type != AudioTrackType.ORIGINAL) continue;
            ok.add(a);
        }
        if (ok.isEmpty()) {
            for (AudioStream a : info.getAudioStreams()) if (a.isUrl()) ok.add(a);
        }
        if (ok.isEmpty()) throw new Exception("Tiada strim audio");

        AudioStream pick = ok.get(0);
        for (AudioStream a : ok) {
            int b = kbps(a), pb = kbps(pick);
            if (quality >= 2 && b > pb) pick = a;  // quality 2 & 3: pilih bitrate tertinggi
            else if (quality <= 0 && b < pb) pick = a;
            else if (quality == 1 && Math.abs(b - 128) < Math.abs(pb - 128)) pick = a;
        }
        Resolved r = new Resolved();
        r.url = pick.getContent();
        String fmt = pick.getFormat() != null ? pick.getFormat().getName() : pick.getCodec();
        int k = kbps(pick);
        r.label = (fmt == null ? "AUDIO" : fmt.toUpperCase()) + (k > 0 ? " • " + k + " kbps" : "");
        return r;
    }

    private static int kbps(AudioStream a) {
        int b = a.getAverageBitrate();
        if (b <= 0) b = a.getBitrate() / 1000;
        return Math.max(b, 0);
    }
}
