package tv.cleanyour.wallpapers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bing's image-of-the-day archive (the feed behind the Bing Wallpaper app and Windows): no key, the last ~15 days per
 * market, each picture with its place and photographer in "copyright".
 */
final class Bing {
    private Bing() {}

    static final String[] MARKETS = {"en-US", "en-GB", "en-CA", "en-AU", "en-IN"};
    /** idx 0 gives the latest 8 days, idx 7 the 8 before them; Bing keeps no older ones. */
    static final int[] OFFSETS = {0, 7};

    /** Interiors and buildings rather than nature; such days are skipped. */
    private static final Pattern NOT_NATURE = Pattern.compile(
            "\\b(library|cathedral|church|chapel|mosque|temple|museum|clock|station|hall|palace|interior|market|"
                    + "halloween|christmas market|stadium|bridge at night|skyline|cabinet of reading|reading room|idol|pandal|replica|cave art|aqueduct)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("OHR\\.([A-Za-z0-9]+)_");

    static String url(String market, int idx) {
        return "https://www.bing.com/HPImageArchive.aspx?format=js&n=8&idx=" + idx + "&mkt=" + market;
    }

    static final class Picture {
        final String id, caption, url;

        Picture(String id, String caption, String url) {
            this.id = id;
            this.caption = caption;
            this.url = url;
        }
    }

    /**
     * The pictures in one answer. id is "<date>-<Name>" (sorts by day, the same picture from two markets has one id),
     * the caption is Bing's copyright line, and the URL is the 1920x1080 rendition this TV draws its screen at.
     */
    static List<Picture> parse(String body) throws JSONException {
        List<Picture> out = new ArrayList<>();
        JSONArray images = new JSONObject(body).optJSONArray("images");
        if (images == null) return out;
        for (int i = 0; i < images.length(); i++) {
            JSONObject im = images.getJSONObject(i);
            String base = im.optString("urlbase", "");
            String date = im.optString("startdate", "");
            String caption = im.optString("copyright", "").trim();
            Matcher m = NAME.matcher(base);
            if (!m.find() || !date.matches("\\d{8}") || caption.isEmpty()) continue;
            if (!nature(caption)) continue;
            out.add(new Picture(date + "-" + m.group(1), caption,
                    "https://www.bing.com" + base + "_1920x1080.jpg"));
        }
        return out;
    }

    /** False for interiors and buildings (a short word list), which the cache skips. */
    static boolean nature(String caption) {
        return !NOT_NATURE.matcher(caption).find();
    }

    /** Safe as a file name, as the launcher checks: letters, digits, '-' and '_'. */
    static boolean validId(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).matches("[a-z0-9_-]{1,80}");
    }
}
