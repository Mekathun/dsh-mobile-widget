package app.dsh.widget;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Minimal diagnostics surface. It exists for two reasons: MIUI treats an app
 * that was never launched as a restricted background app (its widget provider
 * then fails to load), and the launcher gives no way to read provider errors.
 * Opening this screen starts the process and shows the last recorded failure.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView info = findViewById(R.id.diagInfo);
        TextView error = findViewById(R.id.diagError);
        Button refresh = findViewById(R.id.diagRefresh);

        info.setText("端点：\n" + StatusRepository.ENDPOINT
                + "\n\n最近一次缓存：\n" + describeCache());

        String last = DebugLog.lastError(this);
        error.setText(last == null ? "暂无错误记录 ✓" : last);

        refresh.setOnClickListener((View v) -> {
            refresh.setEnabled(false);
            error.setText("请求中…");
            new Thread(() -> {
                StatusRepository.Snapshot snapshot = StatusRepository.fetch(this);
                String message = snapshot == null
                        ? "请求失败（DSH 未运行或 /widget/status 尚未生效）\n\n" + nullToText(DebugLog.lastError(this))
                        : "成功：余额 " + snapshot.currency + " " + String.format(java.util.Locale.getDefault(), "%.2f", snapshot.total)
                        + " / 今日 " + String.format(java.util.Locale.getDefault(), "%.2f", snapshot.todaySpent)
                        + "\n状态：" + snapshot.status
                        + "\n会话：" + nullToText(snapshot.title);
                runOnUiThread(() -> {
                    error.setText(message);
                    info.setText("端点：\n" + StatusRepository.ENDPOINT + "\n\n最近一次缓存：\n" + describeCache());
                    refresh.setEnabled(true);
                });
            }).start();
        });
    }

    /** Render the cached payload summary (or "empty"). */
    private String describeCache() {
        StatusRepository.Snapshot cached = StatusRepository.cached(this);
        if (cached == null) {
            return "（无）";
        }
        return cached.currency + " " + String.format(java.util.Locale.getDefault(), "%.2f", cached.total)
                + " / 状态 " + cached.status
                + " / 会话 " + nullToText(cached.title);
    }

    private static String nullToText(String value) {
        return value == null || value.isEmpty() ? "（空）" : value;
    }
}
