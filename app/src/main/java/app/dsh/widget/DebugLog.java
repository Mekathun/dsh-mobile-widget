package app.dsh.widget;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Diagnostics for the "widget failed to load" class of failures: the launcher
 * swallows provider exceptions, and another app cannot read this one's logcat,
 * so the error is recorded in SharedPreferences (shown by {@link MainActivity})
 * and mirrored to a plain text file in Downloads (MediaStore, no permission).
 */
final class DebugLog {

    private static final String PREFS = "dsh_widget_debug";
    private static final String KEY_LAST = "last_error";
    private static final String FILE_NAME = "dsh-widget-debug.log";

    private DebugLog() {
    }

    /** Record a failure: tag + throwable, truncated for the widget surface. */
    static void record(Context context, String tag, Throwable error) {
        String stamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String detail;
        if (error == null) {
            detail = "unknown error";
        } else {
            StringWriter writer = new StringWriter();
            error.printStackTrace(new PrintWriter(writer));
            detail = error.getClass().getSimpleName() + ": " + error.getMessage();
            String trace = writer.toString();
            if (trace.length() > 1200) {
                trace = trace.substring(0, 1200);
            }
            detail = detail + "\n" + trace;
        }
        String entry = "[" + stamp + "] " + tag + " -> " + detail;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST, entry)
                .apply();
        appendToDownloads(context, entry);
    }

    /** Last recorded failure (full text), or null. */
    static String lastError(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null);
    }

    /** Short one-liner suitable for a RemoteViews TextView. */
    static String summary(String detail) {
        if (detail == null) {
            return "unknown error";
        }
        String first = detail.split("\n")[0];
        return first.length() > 120 ? first.substring(0, 120) + "…" : first;
    }

    /** Best-effort mirror into Downloads so the desktop side can read it. */
    private static void appendToDownloads(Context context, String entry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/dsh-widget");
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return;
            }
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write((entry + "\n\n").getBytes());
                }
            }
        } catch (Exception ignored) {
            // diagnostics must never break the widget
        }
    }
}
