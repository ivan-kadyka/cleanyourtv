package tv.cleanyour.wallpapers;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The picture cache: up to MAX pictures in pictures/<id>.jpg (1920x1080), listed in pictures.json in the order they
 * arrived, each with when it was added and last shown. Two ways in:
 *   refresh: Bing's recent days, at most every 12 hours (30 minutes after a failure);
 *   more:    a new Spotlight batch, when the remote runs past the last picture.
 * Past MAX, the least recently shown pictures go (never ones from the batch just fetched).
 */
final class Fetcher {
    private Fetcher() {}

    static final int MAX = 30;
    static final int BATCH = 8;
    static final long FRESH_MS = 12 * 60 * 60 * 1000L;
    static final long RETRY_MS = 30 * 60 * 1000L;
    /** The stored size: what this TV draws its screen at. */
    static final int WIDTH = 1920, HEIGHT = 1080;

    private static boolean running;

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("wallpapers", Context.MODE_PRIVATE);
    }

    static boolean due(Context ctx, long now) {
        SharedPreferences p = prefs(ctx);
        return now - p.getLong("fetched", 0) > FRESH_MS && now - p.getLong("attempt", 0) > RETRY_MS;
    }

    static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "pictures");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static synchronized boolean running() {
        return running;
    }

    /** A refresh (more == false) or a new batch on its own thread unless one is running; then onDone. */
    static synchronized boolean startAsync(Context ctx, boolean more, Runnable onDone) {
        if (running) return false;
        running = true;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                if (more) more(app);
                else refresh(app);
            } finally {
                synchronized (Fetcher.class) {
                    running = false;
                }
                if (onDone != null) onDone.run();
            }
        }, "wallpapers-fetch").start();
        return true;
    }

    // ---- the list ---------------------------------------------------------------------------------

    static final class Entry {
        final String id, caption;
        final long added;
        long used;

        Entry(String id, String caption, long added, long used) {
            this.id = id;
            this.caption = caption;
            this.added = added;
            this.used = used;
        }
    }

    /** In arrival order. */
    static synchronized List<Entry> load(Context ctx) {
        List<Entry> out = new ArrayList<>();
        File f = new File(ctx.getFilesDir(), "pictures.json");
        try {
            JSONArray list = new JSONArray(f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "[]");
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                String id = o.optString("id", "");
                if (Bing.validId(id)) out.add(new Entry(id, o.optString("caption", ""), o.optLong("added", 0), o.optLong("used", 0)));
            }
        } catch (IOException | JSONException e) {
            // A damaged list starts again empty.
        }
        return out;
    }

    static synchronized void save(Context ctx, List<Entry> entries) throws IOException {
        JSONArray list = new JSONArray();
        try {
            for (Entry e : entries) {
                list.put(new JSONObject().put("id", e.id).put("caption", e.caption).put("added", e.added).put("used", e.used));
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        File tmp = new File(ctx.getFilesDir(), "pictures.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(list.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(ctx.getFilesDir(), "pictures.json"))) throw new IOException("rename failed");
    }

    /** The launcher showed this picture: it is the last one the cache gives up. */
    static synchronized void markUsed(Context ctx, String id) {
        List<Entry> entries = load(ctx);
        boolean found = false;
        for (Entry e : entries) {
            if (e.id.equals(id)) {
                e.used = System.currentTimeMillis();
                found = true;
            }
        }
        if (!found) return;
        try {
            save(ctx, entries);
        } catch (IOException ignored) {
            // only the order of eviction is lost
        }
    }

    /**
     * Drops the least recently shown (or, never shown, the oldest) pictures until MAX remain, keeping the ones in
     * protect. Pure, for the tests.
     */
    static List<Entry> evict(List<Entry> entries, Set<String> protect, int max) {
        List<Entry> out = new ArrayList<>(entries);
        while (out.size() > max) {
            Entry victim = null;
            for (Entry e : out) {
                if (protect.contains(e.id)) continue;
                long seen = Math.max(e.used, e.added);
                if (victim == null || seen < Math.max(victim.used, victim.added)) victim = e;
            }
            if (victim == null) break;
            out.remove(victim);
        }
        return out;
    }

    // ---- fetching ---------------------------------------------------------------------------------

    private static void refresh(Context ctx) {
        long now = System.currentTimeMillis();
        prefs(ctx).edit().putLong("attempt", now).commit();
        try {
            // One entry per picture whichever markets show it: their days differ by one with the time zone, so the
            // name decides and the newest day is kept. Oldest first, so the newest ends up last in arrival order.
            Map<String, Bing.Picture> byName = new LinkedHashMap<>();
            IOException last = null;
            for (String market : Bing.MARKETS) {
                for (int idx : Bing.OFFSETS) {
                    try {
                        for (Bing.Picture p : Bing.parse(new String(get(Bing.url(market, idx)), StandardCharsets.UTF_8))) {
                            String name = p.id.substring(p.id.indexOf('-') + 1);
                            Bing.Picture seen = byName.get(name);
                            if (seen == null || p.id.compareTo(seen.id) > 0) byName.put(name, p);
                        }
                    } catch (IOException e) {
                        last = e;
                    }
                }
            }
            if (byName.isEmpty()) throw last != null ? last : new IOException("no pictures in the answer");
            List<Bing.Picture> pictures = new ArrayList<>(byName.values());
            pictures.sort((a, b) -> a.id.compareTo(b.id));
            add(ctx, pictures, now);
            prefs(ctx).edit().putLong("fetched", now).putString("error", "").commit();
        } catch (IOException | JSONException e) {
            prefs(ctx).edit().putString("error", String.valueOf(e.getMessage())).commit();
        }
    }

    private static void more(Context ctx) {
        long now = System.currentTimeMillis();
        try {
            Set<String> have = new HashSet<>();
            for (Entry e : load(ctx)) have.add(e.id);
            Map<String, Bing.Picture> fresh = new LinkedHashMap<>();
            // A few requests: Spotlight answers 4 random pictures each, some of which may already be here.
            for (int i = 0; i < 4 && fresh.size() < BATCH; i++) {
                for (Bing.Picture p : Spotlight.parse(new String(get(Spotlight.url()), StandardCharsets.UTF_8))) {
                    if (!have.contains(p.id)) fresh.putIfAbsent(p.id, p);
                }
            }
            if (fresh.isEmpty()) throw new IOException("no new pictures");
            add(ctx, new ArrayList<>(fresh.values()), now);
            prefs(ctx).edit().putString("error", "").commit();
        } catch (IOException | JSONException e) {
            prefs(ctx).edit().putString("error", String.valueOf(e.getMessage())).commit();
        }
    }

    /** Downloads the pictures not yet here, appends them, evicts past MAX and deletes the files no longer listed. */
    private static void add(Context ctx, List<Bing.Picture> pictures, long now) throws IOException {
        List<Entry> entries = load(ctx);
        Set<String> have = new HashSet<>();
        for (Entry e : entries) have.add(e.id);
        Set<String> added = new HashSet<>();
        for (Bing.Picture p : pictures) {
            if (have.contains(p.id) || !store(ctx, p)) continue;
            entries.add(new Entry(p.id, p.caption, now, 0));
            have.add(p.id);
            added.add(p.id);
        }
        if (added.isEmpty() && !pictures.isEmpty() && entries.isEmpty()) throw new IOException("no picture could be downloaded");
        entries = evict(entries, added, MAX);
        save(ctx, entries);
        Set<String> keep = new HashSet<>();
        for (Entry e : entries) keep.add(e.id + ".jpg");
        File[] files = dir(ctx).listFiles();
        if (files != null) for (File f : files) if (!keep.contains(f.getName())) f.delete();
    }

    /** The picture at 1920x1080 at most (a 4K Spotlight picture is halved), JPEG quality 92. */
    private static boolean store(Context ctx, Bing.Picture p) {
        File target = new File(dir(ctx), p.id + ".jpg");
        if (target.isFile() && target.length() > 0) return true;
        try {
            byte[] raw = get(p.url);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            if (bounds.outWidth <= 0) return false;
            File tmp = new File(target.getPath() + ".tmp");
            if (bounds.outWidth <= WIDTH) {
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(raw);
                }
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                while (bounds.outWidth / (opts.inSampleSize * 2) >= WIDTH) opts.inSampleSize *= 2;
                Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
                if (b == null) return false;
                if (b.getWidth() > WIDTH) {
                    Bitmap s = Bitmap.createScaledBitmap(b, WIDTH, Math.round(b.getHeight() * WIDTH / (float) b.getWidth()), true);
                    if (s != b) b.recycle();
                    b = s;
                }
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    b.compress(Bitmap.CompressFormat.JPEG, 92, out);
                } finally {
                    b.recycle();
                }
            }
            return tmp.renameTo(target);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return false;
        }
    }

    private static byte[] get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[32768];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > 12 * 1024 * 1024) throw new IOException("answer too large");
                }
                return buf.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
