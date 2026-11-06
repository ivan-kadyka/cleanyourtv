package tv.cleanyour.trending;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Fetches Kinopoisk's popular-movies collection and the posters, one fetch at a time. Each movie in movies.json:
 * kp, imdb, title, year, ri (IMDb rating, 0 = none), rk (Kinopoisk rating), genres, country.
 */
final class Fetcher {
    private Fetcher() { }

    static final String URL_LIST =
            "https://kinopoiskapiunofficial.tech/api/v2.2/films/collections?type=TOP_POPULAR_MOVIES&page=1";
    static final int MAX = 20;
    /** Posters are stored this tall at most: the launcher shows them at about 340 px. */
    static final int POSTER_H = 360;

    private static boolean running;

    /** Runs a fetch on a background thread unless one is running; then onDone (on that thread). */
    static synchronized boolean startAsync(Context ctx, Runnable onDone) {
        if (running) return false;
        running = true;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                fetch(app);
            } finally {
                synchronized (Fetcher.class) {
                    running = false;
                }
                if (onDone != null) onDone.run();
            }
        }, "trending-fetch").start();
        return true;
    }

    static synchronized boolean running() {
        return running;
    }

    private static void fetch(Context ctx) {
        long now = System.currentTimeMillis();
        Store.markAttempt(ctx, now);
        String key = Store.key(ctx);
        if (key.isEmpty()) return;
        try {
            String body = new String(get(URL_LIST, key), StandardCharsets.UTF_8);
            JSONArray items = new JSONObject(body).optJSONArray("items");
            if (items == null) throw new IOException("no items in the answer");
            JSONArray out = new JSONArray();
            Set<String> keep = new HashSet<>();
            for (int i = 0; i < items.length() && out.length() < MAX; i++) {
                JSONObject it = items.getJSONObject(i);
                long kp = it.optLong("kinopoiskId", 0);
                if (kp <= 0) continue;
                JSONObject m = new JSONObject();
                m.put("kp", kp);
                m.put("imdb", it.optString("imdbId", ""));
                m.put("title", title(it));
                m.put("year", it.optInt("year", 0));
                m.put("ri", it.optDouble("ratingImdb", 0));
                m.put("rk", it.optDouble("ratingKinopoisk", 0));
                m.put("genres", join(it.optJSONArray("genres"), "genre", true, 3));
                m.put("country", join(it.optJSONArray("countries"), "country", false, 2));
                String poster = it.optString("posterUrlPreview", it.optString("posterUrl", ""));
                if (!poster.isEmpty() && savePoster(ctx, poster, kp)) keep.add(kp + ".jpg");
                out.put(m);
            }
            if (out.length() == 0) throw new IOException("empty list");
            Store.writeList(ctx, out.toString());
            File[] old = Store.posterDir(ctx).listFiles();
            if (old != null) for (File f : old) if (!keep.contains(f.getName())) f.delete();
            Store.markFetched(ctx, now);
        } catch (IOException | JSONException e) {
            Store.markError(ctx, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** English name when Kinopoisk has one, else the original title, else the Russian one. */
    static String title(JSONObject it) {
        for (String k : new String[] {"nameEn", "nameOriginal", "nameRu"}) {
            String v = it.optString(k, "");
            if (!v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }

    private static String join(JSONArray arr, String field, boolean genre, int max) throws JSONException {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length() && i < max; i++) {
            String ru = arr.getJSONObject(i).optString(field, "");
            if (ru.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(genre ? Translate.genre(ru) : Translate.country(ru));
        }
        return sb.toString();
    }

    private static boolean savePoster(Context ctx, String url, long kp) {
        File target = Store.poster(ctx, kp);
        if (target.isFile() && target.length() > 0) return true;     // posters don't change
        try {
            byte[] raw = get(url, null);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outHeight / (opts.inSampleSize * 2) >= POSTER_H) opts.inSampleSize *= 2;
            Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
            if (b == null) return false;
            if (b.getHeight() > POSTER_H) {
                Bitmap s = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * POSTER_H / (float) b.getHeight()), POSTER_H, true);
                if (s != b) b.recycle();
                b = s;
            }
            File tmp = new File(target.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } finally {
                b.recycle();
            }
            return tmp.renameTo(target);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static byte[] get(String url, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json, image/*");
        if (key != null) c.setRequestProperty("X-API-KEY", key);
        try {
            int code = c.getResponseCode();
            if (code == 401) throw new IOException("API key rejected (401)");
            if (code == 402) throw new IOException("daily request limit reached (402)");
            if (code == 429) throw new IOException("too many requests (429)");
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > 4 * 1024 * 1024) throw new IOException("answer too large");
                }
                return buf.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
