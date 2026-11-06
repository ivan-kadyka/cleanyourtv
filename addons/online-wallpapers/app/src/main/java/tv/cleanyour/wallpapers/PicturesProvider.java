package tv.cleanyour.wallpapers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * content://tv.cleanyour.wallpapers.pictures/pictures: id, caption and image URI per picture, in arrival order.
 * content://tv.cleanyour.wallpapers.pictures/image/<id>: the JPEG, read-only.
 * A query starts a fetch in the background when the list is due, then notifies the pictures URI.
 */
public final class PicturesProvider extends ContentProvider {
    static final String AUTHORITY = "tv.cleanyour.wallpapers.pictures";
    static final Uri PICTURES = Uri.parse("content://" + AUTHORITY + "/pictures");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        List<String> path = uri.getPathSegments();
        Context ctx = getContext();
        if (ctx == null || path.size() != 1 || !"pictures".equals(path.get(0))) return null;
        if (Fetcher.due(ctx, System.currentTimeMillis())) {
            Fetcher.startAsync(ctx, false, () -> ctx.getContentResolver().notifyChange(PICTURES, null));
        }
        MatrixCursor c = new MatrixCursor(new String[] {"id", "caption", "image", "added"});
        for (Fetcher.Entry p : Fetcher.load(ctx)) {
            if (!new File(Fetcher.dir(ctx), p.id + ".jpg").isFile()) continue;
            c.addRow(new Object[] {p.id, p.caption, "content://" + AUTHORITY + "/image/" + p.id, p.added});
        }
        c.setNotificationUri(ctx.getContentResolver(), PICTURES);
        return c;
    }

    /**
     * "more": fetch a new batch (the remote ran past the last picture); the pictures URI is notified when it is in.
     * "used" with the picture's id: it was just shown, so it stays longest in the cache.
     */
    @Override
    public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        Context ctx = getContext();
        android.os.Bundle out = new android.os.Bundle();
        if (ctx == null) return out;
        if ("more".equals(method)) {
            out.putBoolean("started", Fetcher.startAsync(ctx, true,
                    () -> ctx.getContentResolver().notifyChange(PICTURES, null)));
        } else if ("used".equals(method) && Bing.validId(arg)) {
            Fetcher.markUsed(ctx, arg);
        }
        return out;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> path = uri.getPathSegments();
        Context ctx = getContext();
        if (ctx == null || !"r".equals(mode) || path.size() != 2 || !"image".equals(path.get(0))
                || !Bing.validId(path.get(1))) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(Fetcher.dir(ctx), path.get(1) + ".jpg"), ParcelFileDescriptor.MODE_READ_ONLY);
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
