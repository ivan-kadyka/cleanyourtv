package tv.cleanyour.trending;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * The ADB door (the manifest requires WRITE_SECURE_SETTINGS, which only the shell holds):
 *   adb shell content call --uri content://tv.cleanyour.trending.config --method set_key --arg <key>
 *   adb shell content call --uri content://tv.cleanyour.trending.config --method set_link --arg 'app://film?id={kp}' [--extra fallback:s:com.hd.videoboxalfa]
 *   adb shell content call --uri content://tv.cleanyour.trending.config --method set_link     (no link: OK opens the fallback app)
 *   adb shell content call --uri content://tv.cleanyour.trending.config --method refresh
 *   adb shell content call --uri content://tv.cleanyour.trending.config --method status
 */
public final class ConfigProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        Bundle out = new Bundle();
        if (ctx == null) return out;
        Runnable notify = () -> ctx.getContentResolver().notifyChange(MoviesProvider.MOVIES, null);
        switch (method) {
            case "set_key":
                Store.setKey(ctx, arg);
                ctx.getContentResolver().notifyChange(MoviesProvider.MOVIES, null);
                if (!Store.key(ctx).isEmpty()) Fetcher.startAsync(ctx, notify);
                break;
            case "set_link":
                // No --arg (the shell cannot pass an empty one): back to no link, so OK opens the fallback app.
                Store.setLink(ctx, arg == null ? "" : arg, extras == null ? null : extras.getString("fallback"));
                ctx.getContentResolver().notifyChange(MoviesProvider.MOVIES, null);
                break;
            case "refresh":
                out.putBoolean("started", Fetcher.startAsync(ctx, notify));
                break;
            case "status":
                break;
            default:
                out.putString("error", "unknown method " + method);
                return out;
        }
        out.putBoolean("key_set", !Store.key(ctx).isEmpty());
        out.putLong("fetched", Store.fetched(ctx));
        out.putString("last_error", Store.error(ctx));
        out.putString("link", Store.template(ctx));
        out.putString("fallback", Store.fallback(ctx));
        out.putBoolean("fetching", Fetcher.running());
        return out;
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override public String getType(Uri uri) {
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
