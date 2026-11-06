package tv.cleanyour.trending;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** What OK on a poster opens: a link template filled with the film's ids and title. */
final class Link {
    private Link() { }

    /**
     * Empty: OK opens the fallback app. HD AlfaBox's search is not exported, and its film link
     * (hd://videoboxalfa/film?...) took no parameter tried that found a film, so no link by default. Set one with
     * the set_link door; {kp} is the Kinopoisk id, {imdb} the IMDb id (tt...), {title} the URL-encoded title.
     */
    static final String DEFAULT_TEMPLATE = "";
    /** Opened instead when the link resolves to nothing. */
    static final String DEFAULT_FALLBACK = "com.hd.videoboxalfa";

    static String fill(String template, long kp, String imdb, String title) {
        if (template == null || template.isEmpty()) return "";
        if (template.contains("{kp}") && kp <= 0) return "";
        if (template.contains("{imdb}") && (imdb == null || imdb.isEmpty())) return "";
        return template
                .replace("{kp}", String.valueOf(kp))
                .replace("{imdb}", imdb == null ? "" : imdb)
                .replace("{title}", encode(title == null ? "" : title));
    }

    // URLEncoder.encode(String, Charset) is API 33; the add-on runs from API 26.
    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
}
