package tv.cleanyour.wallpapers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Windows Spotlight, the lock-screen pictures of Windows: no key, a few random landscapes per request, each with its
 * place ("Aletsch Glacier, Switzerland") and photographer. Used for a new batch when the remote runs past the end.
 */
final class Spotlight {
    private Spotlight() {}

    static final int PER_REQUEST = 4;

    static String url() {
        return "https://fd.api.iris.microsoft.com/v4/api/selection?placement=88000820&bcnt=" + PER_REQUEST
                + "&country=US&locale=en-US&fmt=json";
    }

    /** id "s-<entityId>"; caption "Place (© photographer)"; the 3840x2160 picture, stored at 1920x1080. */
    static List<Bing.Picture> parse(String body) throws JSONException {
        List<Bing.Picture> out = new ArrayList<>();
        JSONArray items = new JSONObject(body).getJSONObject("batchrsp").optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject ad = new JSONObject(items.getJSONObject(i).getString("item")).optJSONObject("ad");
            if (ad == null) continue;
            String id = "s-" + ad.optString("entityId", "");
            JSONObject image = ad.optJSONObject("landscapeImage");
            String url = image == null ? "" : image.optString("asset", "");
            String place = ad.optString("iconHoverText", "").split("\r?\n")[0].trim();
            String credit = ad.optString("copyright", "").trim();
            if (!Bing.validId(id) || !url.startsWith("https://") || place.isEmpty() || !Bing.nature(place)) continue;
            out.add(new Bing.Picture(id, credit.isEmpty() ? place : place + " (" + credit + ")", url));
        }
        return out;
    }
}
