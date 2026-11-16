package app.dsh.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Background refresh: one request per widget update, plus a periodic pull so the
 * balance and session state stay roughly current. Android caps widget refresh at
 * 30 minutes and WorkManager's periodic minimum is 15, so this is the practical
 * floor; a tap on the widget refreshes immediately through the provider.
 */
public final class RefreshWorker extends Worker {

    private static final String ONE_SHOT = "dsh-widget-refresh-once";
    private static final String PERIODIC = "dsh-widget-refresh-periodic";

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        StatusRepository.Snapshot snapshot = StatusRepository.fetch(context);
        // Even a failed fetch should repaint: the render path falls back to the
        // cached payload and finally to the offline state.
        WidgetBase.renderAll(context, snapshot);
        return Result.success();
    }

    /** Kick a single refresh now (widget add, resize, tap on ⟳). */
    static void refreshNow(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(RefreshWorker.class).build());
    }

    /** Install the periodic pull; safe to call on every update. */
    static void schedulePeriodic(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(RefreshWorker.class, 15, TimeUnit.MINUTES).build());
    }
}
