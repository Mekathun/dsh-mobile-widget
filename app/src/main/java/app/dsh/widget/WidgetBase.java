package app.dsh.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared widget behaviour for every preset size.
 *
 * <p>Layout choice is a pure function of the widget's current min size, so a
 * widget the user resizes behaves like the matching preset. Every layout carries
 * the full view-id set, which is why the render path can set all texts without
 * risking RemoteViews' missing-id failure.
 *
 * <p>States use one colour language: working = blue, awaiting = amber,
 * done = green, server unreachable = grey.
 */
public abstract class WidgetBase extends AppWidgetProvider {

    /** Action used by the in-widget refresh affordance. */
    public static final String ACTION_REFRESH = "app.dsh.widget.ACTION_REFRESH";
    /** Package of the DSH host app whose main page the logo opens. */
    private static final String DSH_PACKAGE = "com.dshmobile.shell";

    /** Every provider class, in ascending size order. */
    private static final Class<?>[] WIDGETS = {
            Widget2x1.class, Widget3x1.class, Widget4x1.class,
            Widget2x2.class, Widget3x2.class, Widget4x2.class,
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        StatusRepository.Snapshot cached = StatusRepository.cached(context);
        for (int id : appWidgetIds) {
            apply(context, manager, id, cached);
        }
        RefreshWorker.schedulePeriodic(context);
        RefreshWorker.refreshNow(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        apply(context, manager, appWidgetId, StatusRepository.cached(context));
        RefreshWorker.refreshNow(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            RefreshWorker.refreshNow(context);
        }
    }

    @Override
    public void onEnabled(Context context) {
        RefreshWorker.schedulePeriodic(context);
        RefreshWorker.refreshNow(context);
    }

    /** Repaint every widget instance of every size with the given snapshot. */
    static void renderAll(Context context, StatusRepository.Snapshot snapshot) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        StatusRepository.Snapshot effective = snapshot != null ? snapshot : StatusRepository.cached(context);
        for (Class<?> widget : WIDGETS) {
            int[] ids;
            try {
                ids = manager.getAppWidgetIds(new ComponentName(context, widget));
            } catch (RuntimeException ignored) {
                continue;
            }
            for (int id : ids) {
                apply(context, manager, id, effective);
            }
        }
    }

    /** Pick the layout for a widget's current cell footprint. */
    static int layoutFor(int widthDp, int heightDp) {
        int columns = Math.round((widthDp + 30f) / 70f);
        if (columns < 2) {
            columns = 2;
        }
        if (columns > 4) {
            columns = 4;
        }
        boolean twoRows = ((heightDp + 30f) / 70f) >= 1.8f;
        if (!twoRows) {
            if (columns >= 4) {
                return R.layout.widget_4x1;
            }
            return columns == 3 ? R.layout.widget_3x1 : R.layout.widget_2x1;
        }
        if (columns >= 4) {
            return R.layout.widget_4x2;
        }
        return columns == 3 ? R.layout.widget_3x2 : R.layout.widget_2x2;
    }

    /** Build and push one widget instance. */
    private static void apply(Context context, AppWidgetManager manager, int widgetId,
                              StatusRepository.Snapshot snapshot) {
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int widthDp = options == null ? 110 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        int heightDp = options == null ? 40 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40);

        RemoteViews views = new RemoteViews(context.getPackageName(), layoutFor(widthDp, heightDp));

        boolean offline = snapshot == null;
        String status = offline ? "offline" : snapshot.status;
        if (status == null) {
            status = "done";
        }

        int dotRes;
        int colorRes;
        int labelRes;
        if ("working".equals(status)) {
            dotRes = R.drawable.dot_working;
            colorRes = R.color.status_working;
            labelRes = R.string.status_working;
        } else if ("awaiting".equals(status)) {
            dotRes = R.drawable.dot_awaiting;
            colorRes = R.color.status_awaiting;
            labelRes = R.string.status_awaiting;
        } else if ("done".equals(status)) {
            dotRes = R.drawable.dot_done;
            colorRes = R.color.status_done;
            labelRes = R.string.status_done;
        } else {
            dotRes = R.drawable.dot_offline;
            colorRes = R.color.status_offline;
            labelRes = R.string.status_offline;
        }

        views.setImageViewResource(R.id.statusDot, dotRes);
        views.setTextViewText(R.id.statusText, context.getString(labelRes));
        views.setTextColor(R.id.statusText, context.getColor(colorRes));

        String title = offline || snapshot.title == null || snapshot.title.isEmpty()
                ? context.getString(R.string.session_unknown)
                : snapshot.title;
        views.setTextViewText(R.id.sessionTitle, title);

        String currency = offline ? "¥" : currencySymbol(snapshot.currency);
        String balanceText = offline || !snapshot.balanceOk
                ? context.getString(R.string.balance_unknown)
                : currency + formatAmount(snapshot.total);
        String todayText = offline || !snapshot.balanceOk
                ? ""
                : context.getString(R.string.today_prefix) + " " + currency + formatAmount(snapshot.todaySpent);

        views.setTextViewText(R.id.balanceText, balanceText);
        views.setTextViewText(R.id.balanceValue, balanceText);
        views.setTextViewText(R.id.todayText, todayText);
        views.setTextViewText(R.id.todayValue, offline || !snapshot.balanceOk ? "--" : currency + formatAmount(snapshot.todaySpent));
        views.setTextViewText(R.id.updatedAt, offline
                ? context.getString(R.string.status_offline)
                : "更新于 " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(snapshot.fetchedAt)));

        // Logo (and the whole widget) opens the DSH main page.
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(DSH_PACKAGE);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent open = PendingIntent.getActivity(context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetRoot, open);
            views.setOnClickPendingIntent(R.id.logo, open);
        }

        // ⟳ and the balance digits refresh without opening the app.
        Intent refresh = new Intent(context, Widget2x1.class).setAction(ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, 1, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.refresh, refreshPending);
        views.setOnClickPendingIntent(R.id.balanceText, refreshPending);
        views.setOnClickPendingIntent(R.id.balanceValue, refreshPending);

        manager.updateAppWidget(widgetId, views);
    }

    private static String currencySymbol(String currency) {
        if (currency == null) {
            return "¥";
        }
        if ("USD".equalsIgnoreCase(currency)) {
            return "$";
        }
        return "¥";
    }

    private static String formatAmount(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }
}
