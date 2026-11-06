package tv.cleanyour.trending;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

/** What the add-on is doing, and a Refresh button. The key is set over ADB (see ConfigProvider). */
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
            if (Store.key(this).isEmpty()) return;
            Fetcher.startAsync(this, () -> runOnUiThread(() -> {
                getContentResolver().notifyChange(MoviesProvider.MOVIES, null);
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
        StringBuilder sb = new StringBuilder();
        if (Store.key(this).isEmpty()) {
            sb.append("No Kinopoisk API key yet: Home shows five sample films.\n")
              .append("Set one from a computer:\nadb shell content call --uri content://tv.cleanyour.trending.config --method set_key --arg <key>");
        } else {
            int count = 0;
            try {
                String list = Store.readList(this);
                count = list.isEmpty() ? 0 : new JSONArray(list).length();
            } catch (JSONException ignored) {
                // shown as 0
            }
            long at = Store.fetched(this);
            sb.append("Films: ").append(count).append('\n');
            sb.append("Last update: ").append(at == 0 ? "never" : DateUtils.getRelativeTimeSpanString(at)).append('\n');
            if (Fetcher.running()) sb.append("Fetching now…\n");
            String err = Store.error(this);
            if (!err.isEmpty()) sb.append("Last error: ").append(err).append('\n');
        }
        sb.append("\nOK on a poster opens: ").append(Store.template(this));
        status.setText(sb);
    }
}
