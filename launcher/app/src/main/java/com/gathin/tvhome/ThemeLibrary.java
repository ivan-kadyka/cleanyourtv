package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Themes kept on the TV under Config.dir/library/<name>/ (config.json, an optional wallpaper.jpg and wallpapers/),
 * pushed there once through ThemeProvider. Launcher settings lists them, and applying one copies its files to where
 * tools/theme.sh puts a theme and writes a new theme_revision, so Home treats it exactly like a pushed theme.
 */
final class ThemeLibrary {
    static final String DIR = "library";
    /** Which library theme was applied, and the theme_revision it wrote; a later push from a computer differs. */
    private static final String APPLIED = "theme_applied";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    private ThemeLibrary() {}

    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Library themes with a config.json, by folder name. */
    static List<String> list(File dir) {
        List<String> out = new ArrayList<>();
        File[] folders = dir == null ? null : new File(dir, DIR).listFiles(File::isDirectory);
        if (folders == null) return out;
        Arrays.sort(folders);
        for (File f : folders) {
            if (validName(f.getName()) && new File(f, Config.FILE_NAME).isFile()) out.add(f.getName());
        }
        return out;
    }

    /** What the picker shows for a theme: its screenshot.jpg, else its wallpaper.jpg, else its first picture. */
    static File preview(File dir, String name) {
        if (dir == null) return null;
        File theme = new File(new File(dir, DIR), name);
        File shot = new File(theme, "screenshot.jpg");
        if (shot.isFile()) return shot;
        File single = new File(theme, Config.WALLPAPER_NAME);
        if (single.isFile()) return single;
        List<File> images = WallpaperRotation.images(new File(theme, ThemeFiles.WALLPAPERS));
        return images.isEmpty() ? null : images.get(0);
    }

    /** "nature-calm" reads "Nature calm". */
    static String label(String name) {
        String s = name.replace('-', ' ').replace('_', ' ');
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** The applied library theme's name; "" for none (built-in look); null when the files came from elsewhere. */
    static String current(File dir) {
        if (dir == null) return "";
        String[] applied = read(new File(dir, APPLIED)).split("\n", -1);
        String revision = read(new File(dir, "theme_revision"));
        boolean hasTheme = new File(dir, Config.FILE_NAME).isFile();
        if (applied.length == 2 && applied[1].equals(revision)) return applied[0];
        return hasTheme ? null : "";
    }

    /** Puts the named library theme in place, or removes the theme when name is empty. Runs off the main thread. */
    static void apply(File dir, String name) throws IOException {
        if (dir == null) throw new IOException("no theme directory");
        File src = name.isEmpty() ? null : new File(new File(dir, DIR), name);
        if (src != null && (!validName(name) || !new File(src, Config.FILE_NAME).isFile())) {
            throw new IOException("no theme " + name);
        }
        new File(dir, Config.FILE_NAME).delete();
        new File(dir, Config.WALLPAPER_NAME).delete();
        File wallpapers = new File(dir, ThemeFiles.WALLPAPERS);
        File[] old = wallpapers.listFiles((d, n) -> ThemeFiles.collectionImage(n)
                || WallpaperCaptions.FILE_NAME.equals(n));
        if (old != null) for (File f : old) f.delete();
        if (src != null) {
            copy(new File(src, Config.FILE_NAME), new File(dir, Config.FILE_NAME));
            File single = new File(src, Config.WALLPAPER_NAME);
            if (single.isFile()) copy(single, new File(dir, Config.WALLPAPER_NAME));
            File[] images = new File(src, ThemeFiles.WALLPAPERS).listFiles((d, n) -> ThemeFiles.collectionImage(n)
                    || WallpaperCaptions.FILE_NAME.equals(n));
            if (images != null && images.length > 0) {
                if (!wallpapers.isDirectory() && !wallpapers.mkdirs()) throw new IOException("cannot make wallpapers/");
                for (File f : images) copy(f, new File(wallpapers, f.getName()));
            }
        }
        String revision = UUID.randomUUID().toString().replace("-", "");
        write(new File(dir, "theme_revision"), revision);
        write(new File(dir, APPLIED), name + "\n" + revision);
    }

    private static void copy(File from, File to) throws IOException {
        File tmp = new File(to.getPath() + ".tmp");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(to)) throw new IOException("cannot write " + to.getName());
    }

    private static void write(File f, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File f) {
        if (!f.isFile()) return "";
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 4096)];
            int n = in.read(b);
            return n <= 0 ? "" : new String(b, 0, n, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }
}
