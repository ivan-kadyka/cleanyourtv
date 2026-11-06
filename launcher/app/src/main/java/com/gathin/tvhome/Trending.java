package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.util.LruCache;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * trending_app (config.xml): a row of film posters between the top bar and the dock, from an add-on such as
 * addons/trending. Like game_day, the launcher only reads what the add-on publishes and stays offline: the rows
 * from content://<authority>/movies and each poster from the URI in its row.
 */
final class Trending {
    static final String META = "com.gathin.tvhome.movies";
    private static final String[] COLUMNS = {"kp_id", "title", "year", "rating_imdb", "rating_kp", "genres",
            "country", "poster", "link", "fallback_package", "sample"};

    static final class Movie {
        final long id;
        final String title, genres, country, poster, link, fallback;
        final int year;
        final double imdb, kp;
        final boolean sample;

        Movie(long id, String title, int year, double imdb, double kp, String genres, String country, String poster,
              String link, String fallback, boolean sample) {
            this.id = id;
            this.title = title;
            this.year = year;
            this.imdb = imdb;
            this.kp = kp;
            this.genres = genres;
            this.country = country;
            this.poster = poster;
            this.link = link;
            this.fallback = fallback;
            this.sample = sample;
        }
    }

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    /** Decoded posters, RGB_565: about 120 KB each at 1080p, so a whole list of 20 stays under 3 MB. */
    private final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private final Set<String> loading = new HashSet<>();
    private boolean watching;
    private List<Movie> movies = Collections.emptyList();

    private Trending(Context ctx, Uri uri, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.uri = uri;
        this.main = main;
        this.onChange = onChange;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /** Null when the key is empty, the package is not installed, or it names no provider of its own in {@value #META}. */
    static Trending create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim())
                    .appendPath("movies").build();
            return new Trending(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on went away; the row keeps what it had.
            }
        }
        read();
    }

    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
        posters.evictAll();
    }

    List<Movie> movies() {
        return movies;
    }

    void read() {
        try {
            io.execute(() -> {
                List<Movie> rows = new ArrayList<>();
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        String title = c.getString(1);
                        if (title == null || title.isEmpty()) continue;
                        rows.add(new Movie(c.getLong(0), title, c.getInt(2), c.getDouble(3), c.getDouble(4),
                                nz(c.getString(5)), nz(c.getString(6)), c.getString(7), nz(c.getString(8)),
                                nz(c.getString(9)), c.getInt(10) != 0));
                    }
                } catch (RuntimeException gone) {
                    return;
                }
                main.post(() -> {
                    movies = rows;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    /** The cached poster, or null after asking for it: onLoaded runs on the main thread once it is decoded. */
    Bitmap poster(String posterUri, Runnable onLoaded) {
        if (posterUri == null || posterUri.isEmpty()) return null;
        Bitmap b = posters.get(posterUri);
        if (b != null || !loading.add(posterUri)) return b;
        try {
            io.execute(() -> {
                Bitmap decoded = null;
                try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(posterUri))) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    decoded = BitmapFactory.decodeStream(in, null, opts);
                } catch (Exception e) {
                    // Missing or unreadable: the tile keeps its title placeholder.
                }
                Bitmap result = decoded;
                main.post(() -> {
                    loading.remove(posterUri);
                    if (result != null) {
                        posters.put(posterUri, result);
                        onLoaded.run();
                    }
                });
            });
        } catch (RejectedExecutionException shutDown) {
            loading.remove(posterUri);
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
