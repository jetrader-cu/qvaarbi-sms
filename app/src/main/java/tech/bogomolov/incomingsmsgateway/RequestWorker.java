package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class RequestWorker extends Worker {

    public final static String DATA_URL = "URL";
    public final static String DATA_TEXT = "TEXT";
    public final static String DATA_HEADERS = "HEADERS";
    public final static String DATA_IGNORE_SSL = "IGNORE_SSL";
    public final static String DATA_MAX_RETRIES = "MAX_RETRIES";
    public final static String DATA_CHUNKED_MODE = "CHUNKED_MODE";
    public final static String DATA_SIGN_HMAC_SHA256 = "SIGN_HMAC_SHA256";
    public final static String DATA_SIGN_HMAC_SHA256_SECRET = "SIGN_HMAC_SHA256_SECRET";
    public final static String DATA_STORE_FAILED = "STORE_FAILED";
    public final static String DATA_LOCAL_MODE = "LOCAL_MODE";
    // Correlación con la entrada de MessageLog creada al recibir el SMS, para que
    // cada intento de entrega actualice ESA misma entrada (spec §9).
    public final static String DATA_LOG_KEY = "LOG_KEY";

    public RequestWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * Enqueues a delivery with the standard "wait for network + exponential
     * backoff" policy. Shared by the live SMS path ({@link SmsBroadcastReceiver})
     * and the manual retry path ({@link FailedMessage#retryAll}).
     */
    public static void enqueue(Context context, Data data) {
        // "Local network mode" (issue #83): NetworkType.CONNECTED requires a
        // *validated* internet connection, so forwarding to a LAN endpoint on a
        // Wi-Fi without upstream internet never fires. When the config opts into
        // local mode we drop the constraint (NOT_REQUIRED) so the request runs as
        // soon as it is enqueued instead of waiting for internet that never comes.
        boolean localMode = data.getBoolean(DATA_LOCAL_MODE, false);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(localMode ? NetworkType.NOT_REQUIRED : NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(RequestWorker.class)
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                                TimeUnit.MILLISECONDS
                        )
                        .setInputData(data)
                        .build();

        WorkManager.getInstance(context).enqueue(workRequest);
    }

    @NonNull
    @Override
    public Result doWork() {
        int maxRetries = getInputData().getInt(DATA_MAX_RETRIES, 10);
        boolean storeFailed = getInputData().getBoolean(DATA_STORE_FAILED, false);
        String logKey = getInputData().getString(DATA_LOG_KEY);
        // getRunAttemptCount() es 0-based; el nº de intento visible al usuario es +1.
        int attempt = getRunAttemptCount() + 1;

        if (getRunAttemptCount() > maxRetries) {
            // Reintentos agotados: cierre terminal como fallido, sin respuesta nueva.
            recordOutcome(logKey, MessageLog.STATUS_FAILED, -1, attempt,
                    "reintentos agotados");
            return fail(storeFailed);
        }

        String url = getInputData().getString(DATA_URL);
        String text = getInputData().getString(DATA_TEXT);
        String headers = getInputData().getString(DATA_HEADERS);
        boolean ignoreSsl = getInputData().getBoolean(DATA_IGNORE_SSL, false);
        boolean useChunkedMode = getInputData().getBoolean(DATA_CHUNKED_MODE, true);
        boolean signHmacSha256 = getInputData().getBoolean(DATA_SIGN_HMAC_SHA256, false);
        String signHmacSha256Secret = getInputData().getString(DATA_SIGN_HMAC_SHA256_SECRET);

        Request request = new Request(url, text);
        request.setJsonHeaders(headers);
        // A null/empty secret can't be signed with (and would throw, which makes
        // WorkManager fail the job *without* running the store-failed path). Send
        // unsigned instead: the endpoint's auth rejection stays visible in the
        // syslog via the logged response code.
        if (signHmacSha256 && signHmacSha256Secret != null && !signHmacSha256Secret.isEmpty()) {
            request.setSignatureHeader(signHmacSha256Secret, text);
        } else if (signHmacSha256) {
            Log.e("RequestWorker", "HMAC signing enabled but no secret stored; sending unsigned");
        }

        request.setIgnoreSsl(ignoreSsl);
        request.setUseChunkedMode(useChunkedMode);

        String result = request.execute();
        int httpCode = request.getResponseCode();

        if (result.equals(Request.RESULT_RETRY)) {
            // Reintentable (5xx/timeout/red): "retry" si aún quedan intentos, si no
            // será el próximo doWork el que lo marque failed vía el guard de arriba.
            boolean willRetry = getRunAttemptCount() < maxRetries;
            recordOutcome(logKey,
                    willRetry ? MessageLog.STATUS_RETRY : MessageLog.STATUS_FAILED,
                    httpCode, attempt, httpDetail(httpCode));
            return Result.retry();
        }

        if (result.equals(Request.RESULT_ERROR)) {
            recordOutcome(logKey, MessageLog.STATUS_FAILED, httpCode, attempt,
                    httpDetail(httpCode));
            return fail(storeFailed);
        }

        recordOutcome(logKey, MessageLog.STATUS_SENT, httpCode, attempt, "HTTP " + httpCode);
        return Result.success();
    }

    // Actualiza el registro de mensajes y el estado de conexión del banner (spec
    // REQ-003/010). Best-effort: nunca debe alterar el Result devuelto al WorkManager.
    private void recordOutcome(String logKey, String status, int httpCode, int attempt,
                               String detail) {
        Context context = getApplicationContext();
        try {
            MessageLog.update(context, logKey, status, httpCode, attempt);
            boolean ok = status.equals(MessageLog.STATUS_SENT);
            // Un "retry" transitorio no debe teñir el banner de rojo permanente; sólo
            // los resultados terminales (sent/failed) mueven el estado de conexión.
            if (ok || status.equals(MessageLog.STATUS_FAILED)) {
                ConnectionStatus.record(context, ok, detail, ConnectionStatus.SOURCE_DELIVERY);
            }
        } catch (Exception e) {
            Log.e("RequestWorker", "outcome record failed: " + e.getMessage());
        }
    }

    private static String httpDetail(int httpCode) {
        if (httpCode < 0) {
            return "sin respuesta";
        }
        if (httpCode == 401) {
            return "HTTP 401 firma inválida";
        }
        return "HTTP " + httpCode;
    }

    // Permanent failure: optionally persist the payload for manual retry, then
    // report failure so WorkManager stops retrying.
    private Result fail(boolean storeFailed) {
        if (storeFailed) {
            FailedMessage.save(getApplicationContext(), getInputData());
        }
        return Result.failure();
    }
}
