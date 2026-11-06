package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Online wallpapers (Launcher settings): pictures from an add-on such as addons/online-wallpapers, which does the
 * downloading. The launcher stays offline: it copies the add-on's pictures into Config.dir/online/ with a captions.txt
 * (the place names), and Config uses that folder as the wallpaper collection while the setting is on.
 */
final class OnlineWallpapers {
    static final String DIR = "online";
    static final String KEY = "online_wallpapers";
    private static final String META = "com.gathin.tvhome.wallpapers";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");

    private OnlineWallpapers() {}

    static boolean enabled(SharedPreferences choices) {
        return choices.getBoolean(KEY, false);
    }

    /** The add-on's pictures URI, or null when online_wallpapers_app is empty, not installed or names no provider. */
    static Uri uri(Context ctx) {
        String pkg = ctx.getString(R.string.online_wallpapers_app).trim();
        if (pkg.isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.equals(provider.packageName)) return null;
            return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim())
                    .appendPath("pictures").build();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Brings Config.dir/online/ in line with the add-on: new pictures copied, ones it dropped deleted, captions.txt
     * rewritten. An empty answer (the add-on's first fetch still running) leaves the folder alone. True when anything
     * changed. Runs off the main thread.
     */
    static boolean sync(Context ctx, Uri uri) {
        File base = Config.dir(ctx);
        if (base == null || uri == null) return false;
        File dir = new File(base, DIR);
        // File name -> {caption, image uri}. The name starts with when the add-on got the picture, so the file-name
        // order the wallpaper player steps in is arrival order and a new batch comes after the last picture.
        Map<String, String[]> rows = new LinkedHashMap<>();
        try (Cursor c = ctx.getContentResolver().query(uri, new String[] {"id", "caption", "image", "added"}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0);
                if (id != null && ID.matcher(id).matches() && c.getString(2) != null) {
                    rows.put(String.format(java.util.Locale.ROOT, "%013d-%s", Math.max(0L, c.getLong(3)), id),
                            new String[] {c.getString(1) == null ? "" : c.getString(1), c.getString(2)});
                }
            }
        } catch (RuntimeException gone) {
            return false;
        }
        if (rows.isEmpty()) return false;
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        boolean changed = false;
        for (Map.Entry<String, String[]> e : rows.entrySet()) {
            File target = new File(dir, e.getKey() + ".jpg");
            if (target.isFile() && target.length() > 0) continue;
            File tmp = new File(dir, e.getKey() + ".jpg.tmp");
            try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(e.getValue()[1]));
                 OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) continue;
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (IOException | RuntimeException failed) {
                tmp.delete();
                continue;
            }
            if (tmp.renameTo(target)) changed = true;
        }
        File[] present = dir.listFiles();
        if (present != null) {
            for (File f : present) {
                String name = f.getName();
                if (name.equals(WallpaperCaptions.FILE_NAME)) continue;
                String id = name.endsWith(".jpg") ? name.substring(0, name.length() - 4) : null;
                if (id == null || !rows.containsKey(id)) changed |= f.delete();
            }
        }
        StringBuilder captions = new StringBuilder();
        for (Map.Entry<String, String[]> e : rows.entrySet()) {
            String caption = e.getValue()[0].replace('\t', ' ').replace('\n', ' ').trim();
            if (!caption.isEmpty()) captions.append(e.getKey()).append(".jpg\t").append(caption).append('\n');
        }
        File captionFile = new File(dir, WallpaperCaptions.FILE_NAME);
        String text = captions.toString();
        if (!text.equals(read(captionFile))) {
            try (OutputStream out = new FileOutputStream(captionFile)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                changed = true;
            } catch (IOException ignored) {
                // the pictures still show, without names
            }
        }
        return changed;
    }

    /** Asks the add-on for a new batch; it notifies the pictures URI when the batch is in. Off the main thread. */
    static void more(Context ctx, Uri uri) {
        try {
            ctx.getContentResolver().call(uri, "more", null, null);
        } catch (RuntimeException gone) {
            // the add-on went away
        }
    }

    /** Tells the add-on this picture was shown, so its cache keeps it longest. Off the main thread. */
    static void used(Context ctx, Uri uri, File picture) {
        String name = picture.getName();
        int dash = name.indexOf('-');
        if (dash < 0 || !name.endsWith(".jpg")) return;
        try {
            ctx.getContentResolver().call(uri, "used", name.substring(dash + 1, name.length() - 4), null);
        } catch (RuntimeException gone) {
            // the add-on went away
        }
    }

    private static String read(File f) {
        try {
            return f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
