package tv.cleanyour.wallpapers;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;


/** How many pictures there are and when they were fetched, with a Refresh button. Turned on in Launcher settings. */
public final class StatusActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        int pad = Math.round(48 * d);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(0xCCFFFFFF);
        status.setTextSize(17);
        status.setLineSpacing(6 * d, 1f);
        status.setPadding(0, Math.round(20 * d), 0, Math.round(24 * d));
        root.addView(status);

        Button refresh = new Button(this);
        refresh.setText("Refresh now");
        refresh.setAllCaps(false);
        refresh.setTextColor(Color.BLACK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(24 * d);
        refresh.setBackground(bg);
        refresh.setPadding(Math.round(28 * d), 0, Math.round(28 * d), 0);
        refresh.setOnClickListener(v -> {
            Fetcher.startAsync(this, false, () -> runOnUiThread(() -> {
                getContentResolver().notifyChange(PicturesProvider.PICTURES, null);
                show();
            }));
            show();
        });
        root.addView(refresh);
        setContentView(root);
        refresh.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    private void show() {
        SharedPreferences p = Fetcher.prefs(this);
        int count = Fetcher.load(this).size();
        long at = p.getLong("fetched", 0);
        StringBuilder sb = new StringBuilder();
        sb.append("Pictures: ").append(count).append(" of ").append(Fetcher.MAX)
          .append(" (Bing image of the day, and Windows Spotlight batches)\n");
        sb.append("Last update: ").append(at == 0 ? "never" : DateUtils.getRelativeTimeSpanString(at)).append('\n');
        if (Fetcher.running()) sb.append("Fetching now…\n");
        String err = p.getString("error", "");
        if (!err.isEmpty()) sb.append("Last error: ").append(err).append('\n');
        sb.append("\nTurn them on in Launcher settings > Online wallpapers.\nOn Home, ⏪ and ⏩ on the remote step through them;\n⏩ past the last one fetches a new batch.");
        status.setText(sb);
    }
}
