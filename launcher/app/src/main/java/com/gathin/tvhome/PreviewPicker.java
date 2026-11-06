package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * A full-screen choice list for Launcher settings with a large preview of the highlighted entry beside it: the
 * wallpaper itself, or a theme's screenshot. Moving through the list with the remote updates the preview; OK picks.
 * Previews are decoded off the main thread at about 960 px wide, one at a time.
 */
final class PreviewPicker {
    interface Pick {
        void picked(int index);
    }

    private PreviewPicker() {}

    /** previews.get(i) may be null (no picture: "None", "Built-in"), shown as an empty dark frame. */
    static void show(Activity a, CharSequence title, List<String> labels, List<File> previews, int checked, Pick pick) {
        float d = a.getResources().getDisplayMetrics().density;
        Dialog dialog = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        ExecutorService io = Executors.newSingleThreadExecutor();
        int[] shown = {-1};

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xF20D0E11);
        int pad = Math.round(48 * d);
        root.setPadding(pad, Math.round(36 * d), pad, Math.round(36 * d));

        TextView heading = new TextView(a);
        heading.setText(title);
        heading.setTextColor(0xFFFFFFFF);
        heading.setTextSize(30);
        root.addView(heading);

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyLp.topMargin = Math.round(20 * d);
        root.addView(body, bodyLp);

        ListView list = new ListView(a);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_list_item_single_choice, labels));
        list.setDivider(null);
        body.addView(list, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f));

        // The preview keeps the screen's 16:9 shape whatever the picture's.
        FrameLayout frame = new FrameLayout(a) {
            @Override
            protected void onMeasure(int w, int h) {
                int width = MeasureSpec.getSize(w);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
            }
        };
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setCornerRadius(14 * d);
        bg.setStroke(Math.round(1 * d), 0x33FFFFFF);
        frame.setBackground(bg);
        frame.setClipToOutline(true);
        ImageView image = new ImageView(a);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView caption = new TextView(a);
        caption.setTextColor(0xCCFFFFFF);
        caption.setTextSize(15);
        caption.setPadding(0, Math.round(14 * d), 0, 0);
        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_VERTICAL);
        side.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        side.addView(caption);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f);
        sideLp.leftMargin = Math.round(40 * d);
        body.addView(side, sideLp);

        AdapterView.OnItemSelectedListener selected = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                preview(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}

            private void preview(int position) {
                if (position == shown[0] || position < 0 || position >= labels.size()) return;
                shown[0] = position;
                caption.setText(labels.get(position));
                File file = position < previews.size() ? previews.get(position) : null;
                if (file == null || !file.isFile()) {
                    image.setImageDrawable(null);
                    return;
                }
                try {
                    io.execute(() -> {
                        Bitmap b = decode(file, 960);
                        image.post(() -> {
                            if (shown[0] == position && dialog.isShowing()) image.setImageBitmap(b);
                        });
                    });
                } catch (RejectedExecutionException closed) {
                    // The picker was closed.
                }
            }
        };
        list.setOnItemSelectedListener(selected);
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            pick.picked(position);
        });
        dialog.setOnDismissListener(x -> io.shutdownNow());
        dialog.setContentView(root);
        dialog.show();

        int start = Math.max(0, Math.min(checked, labels.size() - 1));
        if (checked >= 0) list.setItemChecked(checked, true);
        list.setSelection(start);
        list.requestFocus();
        selected.onItemSelected(list, null, start, start);
    }

    /** The picture at most maxWidth wide (a power-of-two sample of the file), or null. */
    static Bitmap decode(File file, int maxWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= maxWidth) opts.inSampleSize *= 2;
        try {
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
