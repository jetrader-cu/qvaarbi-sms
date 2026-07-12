package tech.bogomolov.incomingsmsgateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Chequeo periódico de actualizaciones en segundo plano (spec REQ-008). Si el toggle
 * está OFF no hace nada. Cuando hay una versión nueva que aún no se notificó, publica
 * una notificación con un botón que abre el APK en el navegador (REQ-010, sin permisos
 * de instalación). El intervalo mínimo de WorkManager es 15 min; usamos 24 h.
 */
public class UpdateCheckWorker extends Worker {

    private static final String UNIQUE_WORK = "qvaarbi_update_check";
    private static final String CHANNEL_ID = "SmsUpdates";
    private static final int NOTIFICATION_ID = 4210;

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Programa (o mantiene) el chequeo diario. Idempotente vía KEEP. */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(UpdateCheckWorker.class, 24, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!UpdateSettings.isAutoCheckEnabled(context)) {
            return Result.success();
        }

        UpdateManifest manifest = UpdateChecker.fetch(context);
        if (!UpdateChecker.hasUpdate(manifest)) {
            return Result.success();
        }

        // No repetir la notificación de la misma versión en cada ciclo diario.
        if (manifest.versionCode <= UpdateSettings.getLastNotifiedCode(context)) {
            return Result.success();
        }

        notifyUpdate(context, manifest);
        UpdateSettings.setLastNotifiedCode(context, manifest.versionCode);
        return Result.success();
    }

    private void notifyUpdate(Context context, UpdateManifest manifest) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        // Toca la notificación → abre el APK en el navegador para descargar/instalar.
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(manifest.apkUrl));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(context, 0, intent, flags);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_f)
                .setContentTitle(context.getString(R.string.update_available_title))
                .setContentText(context.getString(
                        R.string.update_available_message_short, manifest.versionName))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();

        manager.notify(NOTIFICATION_ID, notification);
    }
}
