package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Registro persistente de cada SMS entrante que matchea una regla y su reenvío al
 * webhook de QvaArbi (spec REQ-002..005). A diferencia de {@link FailedMessage}
 * (sólo fallos, opt-in), aquí se registra TODO mensaje reenviado con su ciclo de
 * vida de estado (pending → sent | retry | failed), para dar al usuario un
 * historial agrupado por regla ("por usuario") en {@link MessageLogActivity}.
 *
 * <p>Almacenamiento en su propio archivo SharedPreferences {@code message_log},
 * separado de las reglas ({@link ForwardingConfig}) y de los fallidos
 * ({@link FailedMessage}) para que ninguno intente parsear las entradas del otro.
 * Cada entrada es un registro clave {@code timestamp_random} → JSON serializado.
 *
 * <p>Nunca persiste el secret HMAC (COM-001): sólo remitente, cuerpo, regla,
 * estado, código HTTP y timestamps. La escritura es best-effort: cualquier fallo
 * se loguea pero nunca interrumpe la recepción ni la entrega del SMS (CON-002).
 */
public class MessageLog {

    private static final String PREFERENCE = "message_log";

    // Estados de entrega (spec §4.1). Mientras haya reintentos pendientes el estado
    // es "retry"; se vuelve terminal en "sent" (2xx) o "failed" (agotó/permanente).
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_RETRY = "retry";
    public static final String STATUS_FAILED = "failed";

    private static final String KEY_LOG_KEY = "logKey";
    private static final String KEY_RULE_KEY = "ruleKey";
    private static final String KEY_RULE_NAME = "ruleName";
    private static final String KEY_SENDER = "sender";
    private static final String KEY_BODY = "body";
    private static final String KEY_STATUS = "status";
    private static final String KEY_HTTP_CODE = "httpCode";
    private static final String KEY_ATTEMPTS = "attempts";
    private static final String KEY_RECEIVED_AT = "receivedAt";
    private static final String KEY_LAST_ATTEMPT_AT = "lastAttemptAt";

    // Se poda el más antiguo pasado este umbral, igual que {@link FailedMessage},
    // para que un teléfono con mucho tráfico no crezca sin límite (REQ-005).
    static final int MAX_STORED = 500;

    /** Vista inmutable de una entrada del registro. */
    public static class Entry {
        public final String logKey;
        public final String ruleKey;
        public final String ruleName;
        public final String sender;
        public final String body;
        public final String status;
        public final int httpCode;      // -1 si aún no hubo respuesta
        public final int attempts;
        public final long receivedAt;
        public final long lastAttemptAt;

        Entry(JSONObject json) {
            this.logKey = json.optString(KEY_LOG_KEY, "");
            this.ruleKey = json.optString(KEY_RULE_KEY, "");
            this.ruleName = json.optString(KEY_RULE_NAME, "");
            this.sender = json.optString(KEY_SENDER, "");
            this.body = json.optString(KEY_BODY, "");
            this.status = json.optString(KEY_STATUS, STATUS_PENDING);
            this.httpCode = json.optInt(KEY_HTTP_CODE, -1);
            this.attempts = json.optInt(KEY_ATTEMPTS, 0);
            this.receivedAt = json.optLong(KEY_RECEIVED_AT, 0L);
            this.lastAttemptAt = json.optLong(KEY_LAST_ATTEMPT_AT, 0L);
        }
    }

    /**
     * Registra un SMS matcheado como {@code pending} y devuelve su {@code logKey},
     * que el llamante propaga por el {@link androidx.work.Data} del worker para que
     * cada intento de entrega actualice ESTA misma entrada (correlación, spec §9).
     * Devuelve null si la persistencia falla (el reenvío continúa igualmente).
     */
    public static String record(Context context, String ruleKey, String ruleName,
                                String sender, String body) {
        String logKey = generateKey();
        try {
            long now = System.currentTimeMillis();
            JSONObject json = new JSONObject();
            json.put(KEY_LOG_KEY, logKey);
            json.put(KEY_RULE_KEY, ruleKey == null ? "" : ruleKey);
            json.put(KEY_RULE_NAME, ruleName == null ? "" : ruleName);
            json.put(KEY_SENDER, sender == null ? "" : sender);
            json.put(KEY_BODY, body == null ? "" : body);
            json.put(KEY_STATUS, STATUS_PENDING);
            json.put(KEY_HTTP_CODE, -1);
            json.put(KEY_ATTEMPTS, 0);
            json.put(KEY_RECEIVED_AT, now);
            json.put(KEY_LAST_ATTEMPT_AT, 0L);

            SharedPreferences.Editor editor = getPreference(context).edit();
            editor.putString(logKey, json.toString());
            editor.commit();

            trim(context);
            return logKey;
        } catch (Exception e) {
            Log.e("MessageLog", "record failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Actualiza el resultado de un intento de entrega sobre la entrada {@code logKey}.
     * No-op silencioso si la entrada ya fue podada o el logKey es null, para que un
     * reintento tardío nunca crashee el worker.
     */
    public static void update(Context context, String logKey, String status,
                              int httpCode, int attempts) {
        if (logKey == null || logKey.isEmpty()) {
            return;
        }
        try {
            SharedPreferences pref = getPreference(context);
            String stored = pref.getString(logKey, null);
            if (stored == null) {
                return; // podada o inexistente
            }
            JSONObject json = new JSONObject(stored);
            json.put(KEY_STATUS, status);
            json.put(KEY_HTTP_CODE, httpCode);
            json.put(KEY_ATTEMPTS, attempts);
            json.put(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis());

            SharedPreferences.Editor editor = pref.edit();
            editor.putString(logKey, json.toString());
            editor.commit();
        } catch (JSONException e) {
            Log.e("MessageLog", "update failed: " + e.getMessage());
        }
    }

    /** Todas las entradas, más recientes primero (por {@code receivedAt}). */
    public static List<Entry> getAll(Context context) {
        List<Entry> entries = new ArrayList<>();
        for (Object value : getPreference(context).getAll().values()) {
            try {
                entries.add(new Entry(new JSONObject((String) value)));
            } catch (JSONException e) {
                Log.e("MessageLog", "parse failed: " + e.getMessage());
            }
        }
        Collections.sort(entries, (a, b) -> Long.compare(b.receivedAt, a.receivedAt));
        return entries;
    }

    public static int getCount(Context context) {
        return getPreference(context).getAll().size();
    }

    public static void clear(Context context) {
        SharedPreferences.Editor editor = getPreference(context).edit();
        editor.clear();
        editor.commit();
    }

    // Conserva sólo las MAX_STORED entradas más recientes. Las claves llevan prefijo
    // de timestamp → el orden natural de String es (aprox.) cronológico, más viejas
    // primero, así que se borran desde el inicio del TreeMap.
    private static void trim(Context context) {
        SharedPreferences pref = getPreference(context);
        Map<String, ?> all = pref.getAll();
        if (all.size() <= MAX_STORED) {
            return;
        }
        TreeMap<String, ?> sorted = new TreeMap<>(all);
        int toRemove = all.size() - MAX_STORED;
        SharedPreferences.Editor editor = pref.edit();
        for (String key : sorted.keySet()) {
            if (toRemove-- <= 0) {
                break;
            }
            editor.remove(key);
        }
        editor.commit();
    }

    private static SharedPreferences getPreference(Context context) {
        return context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
    }

    private static String generateKey() {
        String stamp = Long.toString(System.currentTimeMillis());
        int randomNum = new Random().nextInt((999990 - 100000) + 1) + 100000;
        return stamp + '_' + randomNum;
    }
}
