package tv.cleanyour.trending;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The key, the link template, the fetched list (movies.json) and the posters (posters/<kp>.jpg), all app-private. */
final class Store {
    private Store() { }

    static final long FRESH_MS = 6 * 60 * 60 * 1000L;      // refetch at most every 6 hours
    static final long RETRY_MS = 30 * 60 * 1000L;          // after a failure, wait half an hour

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("trending", Context.MODE_PRIVATE);
    }

    static String key(Context ctx) {
        return prefs(ctx).getString("key", "");
    }

    static void setKey(Context ctx, String key) {
        prefs(ctx).edit().putString("key", key == null ? "" : key.trim()).putLong("fetched", 0).putLong("attempt", 0).apply();
    }

    static String template(Context ctx) {
        return prefs(ctx).getString("link", Link.DEFAULT_TEMPLATE);
    }

    static String fallback(Context ctx) {
        return prefs(ctx).getString("fallback", Link.DEFAULT_FALLBACK);
    }

    static void setLink(Context ctx, String template, String fallback) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (template != null) e.putString("link", template.trim());
        if (fallback != null) e.putString("fallback", fallback.trim());
        e.apply();
    }

    static long fetched(Context ctx) {
        return prefs(ctx).getLong("fetched", 0);
    }

    static long attempt(Context ctx) {
        return prefs(ctx).getLong("attempt", 0);
    }

    static String error(Context ctx) {
        return prefs(ctx).getString("error", "");
    }

    static void markAttempt(Context ctx, long now) {
        prefs(ctx).edit().putLong("attempt", now).commit();
    }

    static void markFetched(Context ctx, long now) {
        prefs(ctx).edit().putLong("fetched", now).putString("error", "").commit();
    }

    static void markError(Context ctx, String error) {
        prefs(ctx).edit().putString("error", error == null ? "" : error).commit();
    }

    /** Due for a fetch: a key is set, the list is older than FRESH_MS, and no attempt in the last RETRY_MS. */
    static boolean due(Context ctx, long now) {
        if (key(ctx).isEmpty()) return false;
        return now - fetched(ctx) > FRESH_MS && now - attempt(ctx) > RETRY_MS;
    }

    static File listFile(Context ctx) {
        return new File(ctx.getFilesDir(), "movies.json");
    }

    static File posterDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "posters");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    static File poster(Context ctx, long kp) {
        return new File(posterDir(ctx), kp + ".jpg");
    }

    static String readList(Context ctx) {
        File f = listFile(ctx);
        if (!f.isFile()) return "";
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Written to a temp file and renamed, so a reader never sees half a list. */
    static void writeList(Context ctx, String json) throws IOException {
        File tmp = new File(ctx.getFilesDir(), "movies.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(listFile(ctx))) throw new IOException("rename failed");
    }
}
