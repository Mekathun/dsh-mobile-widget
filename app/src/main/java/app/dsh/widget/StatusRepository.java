package app.dsh.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Fetches {@code GET /widget/status} from the local DSH server and caches the
 * last good payload so the widget can keep painting real numbers while the
 * server is busy or the app was just killed.
 *
 * <p>The endpoint is loopback-only, so no network permission beyond INTERNET is
 * needed and no user credential ever reaches this app: the DSH process owns the
 * API key and the widget only receives the derived numbers.
 */
final class StatusRepository {

    static final String ENDPOINT = "http://127.0.0.1:3080/widget/status";
    private static final String PREFS = "dsh_widget";
    private static final String KEY_LAST = "last_payload";
    private static final int TIMEOUT_MS = 4000;

    /** Snapshot of one status payload. */
    static final class Snapshot {
        final boolean balanceOk;
        final String currency;
        final double total;
        final double todaySpent;
        final String title;
        final String status;
        final long updatedAt;
        final long fetchedAt;

        Snapshot(boolean balanceOk, String currency, double total, double todaySpent,
                 String title, String status, long updatedAt, long fetchedAt) {
            this.balanceOk = balanceOk;
            this.currency = currency;
            this.total = total;
            this.todaySpent = todaySpent;
            this.title = title;
            this.status = status;
            this.updatedAt = updatedAt;
            this.fetchedAt = fetchedAt;
        }

        static Snapshot parse(String raw, long fetchedAt) {
            try {
                JSONObject root = new JSONObject(raw);
                JSONObject balance = root.optJSONObject("balance");
                JSONObject session = root.optJSONObject("lastSession");
                boolean balanceOk = balance != null && balance.optBoolean("ok", false);
                return new Snapshot(
                        balanceOk,
                        balance == null ? "CNY" : balance.optString("currency", "CNY"),
                        balance == null ? 0d : balance.optDouble("total", 0d),
                        balance == null ? 0d : balance.optDouble("todaySpent", 0d),
                        session == null ? null : session.optString("title", null),
                        session == null ? "done" : session.optString("status", "done"),
                        session == null ? 0L : session.optLong("updatedAt", 0L),
                        fetchedAt);
            } catch (Exception broken) {
                return null;
            }
        }
    }

    private StatusRepository() {
    }

    /** Cached payload from the last successful fetch, or null. */
    static Snapshot cached(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LAST, null);
        if (raw == null) {
            return null;
        }
        return Snapshot.parse(raw, prefs.getLong(KEY_LAST + "_at", 0L));
    }

    /** Live fetch; caches and returns null on any failure. */
    static Snapshot fetch(Context context) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return null;
            }
            InputStream stream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            long now = System.currentTimeMillis();
            Snapshot snapshot = Snapshot.parse(body.toString(), now);
            if (snapshot == null) {
                return null;
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST, body.toString())
                    .putLong(KEY_LAST + "_at", now)
                    .apply();
            return snapshot;
        } catch (Exception failure) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
