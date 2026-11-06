package tv.cleanyour.trending;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * content://tv.cleanyour.trending.movies/movies: one row per film, in Kinopoisk's popularity order.
 * content://tv.cleanyour.trending.movies/poster/<kp>: the poster JPEG, read-only.
 * A query starts a fetch in the background when the list is due (Store.due), then notifies the movies URI.
 * With no key set it answers with a few sample rows and no posters.
 */
public final class MoviesProvider extends ContentProvider {
    static final String AUTHORITY = "tv.cleanyour.trending.movies";
    static final Uri MOVIES = Uri.parse("content://" + AUTHORITY + "/movies");
    static final String[] COLUMNS = {"kp_id", "title", "year", "rating_imdb", "rating_kp", "genres", "country",
            "poster", "link", "fallback_package", "sample"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        List<String> path = uri.getPathSegments();
        if (path.size() != 1 || !"movies".equals(path.get(0))) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        if (Store.due(ctx, System.currentTimeMillis())) {
            Fetcher.startAsync(ctx, () -> ctx.getContentResolver().notifyChange(MOVIES, null));
        }
        MatrixCursor c = new MatrixCursor(COLUMNS);
        String template = Store.template(ctx);
        String fallback = Store.fallback(ctx);
        if (Store.key(ctx).isEmpty()) {
            sample(c, template, fallback);
        } else {
            try {
                JSONArray list = new JSONArray(Store.readList(ctx).isEmpty() ? "[]" : Store.readList(ctx));
                for (int i = 0; i < list.length(); i++) {
                    JSONObject m = list.getJSONObject(i);
                    long kp = m.optLong("kp", 0);
                    String title = m.optString("title", "");
                    File poster = Store.poster(ctx, kp);
                    c.addRow(new Object[] {kp, title, m.optInt("year", 0), m.optDouble("ri", 0), m.optDouble("rk", 0),
                            m.optString("genres", ""), m.optString("country", ""),
                            poster.isFile() ? "content://" + AUTHORITY + "/poster/" + kp : null,
                            Link.fill(template, kp, m.optString("imdb", ""), title), fallback, 0});
                }
            } catch (JSONException e) {
                // A damaged list reads as empty until the next fetch replaces it.
            }
        }
        c.setNotificationUri(ctx.getContentResolver(), MOVIES);
        return c;
    }

    /** Shown until a key is set, so the carousel can be seen working; ratings are approximate, marked sample=1. */
    private static void sample(MatrixCursor c, String template, String fallback) {
        Object[][] films = {
            {326L, "The Shawshank Redemption", 1994, 9.3, 9.1, "Drama", "USA"},
            {301L, "The Matrix", 1999, 8.7, 8.5, "Sci-Fi, Action", "USA"},
            {447301L, "Inception", 2010, 8.8, 8.7, "Sci-Fi, Action, Thriller", "USA, UK"},
            {258687L, "Interstellar", 2014, 8.7, 8.6, "Sci-Fi, Drama, Adventure", "USA, UK"},
            {448L, "Forrest Gump", 1994, 8.8, 8.9, "Drama, Comedy, Romance", "USA"},
        };
        for (Object[] f : films) {
            long kp = (Long) f[0];
            c.addRow(new Object[] {kp, f[1], f[2], f[3], f[4], f[5], f[6], null,
                    Link.fill(template, kp, "", (String) f[1]), fallback, 1});
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> path = uri.getPathSegments();
        if (!"r".equals(mode) || path.size() != 2 || !"poster".equals(path.get(0))) throw new FileNotFoundException();
        long kp;
        try {
            kp = Long.parseLong(path.get(1));
        } catch (NumberFormatException e) {
            throw new FileNotFoundException();
        }
        Context ctx = getContext();
        if (ctx == null || kp <= 0) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(Store.poster(ctx, kp), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        return uri.getPathSegments().size() == 2 ? "image/jpeg" : null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
