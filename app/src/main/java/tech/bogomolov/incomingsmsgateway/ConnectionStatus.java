package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Último resultado conocido de conectividad con QvaArbi (spec REQ-009..011), que
 * alimenta el banner de estado de {@link MainActivity}. Se actualiza desde dos
 * fuentes (REQ-010): el heartbeat periódico ({@link SmsReceiverService}) y cada
 * entrega de SMS ({@link RequestWorker}). El banner muestra el evento más reciente
 * de cualquiera de las dos, así que un teléfono sin tráfico SMS igual confirma
 * "conectado" gracias al heartbeat.
 *
 * <p>Estado derivado: {@code OK} (último evento 2xx), {@code ERROR} (último fallo,
 * con motivo) o {@code UNKNOWN} (aún sin actividad).
 */
public class ConnectionStatus {

    private static final String PREFERENCE = "connection_status";
    private static final String KEY_OK = "last_ok";        // boolean del último evento
    private static final String KEY_AT = "last_at";        // epoch millis
    private static final String KEY_DETAIL = "last_detail"; // motivo/código legible
    private static final String KEY_SOURCE = "last_source"; // "heartbeat" | "delivery"

    public static final String STATE_OK = "ok";
    public static final String STATE_ERROR = "error";
    public static final String STATE_UNKNOWN = "unknown";

    public static final String SOURCE_HEARTBEAT = "heartbeat";
    public static final String SOURCE_DELIVERY = "delivery";

    public final String state;
    public final long at;        // 0 si nunca
    public final String detail;
    public final String source;

    private ConnectionStatus(String state, long at, String detail, String source) {
        this.state = state;
        this.at = at;
        this.detail = detail == null ? "" : detail;
        this.source = source == null ? "" : source;
    }

    public static ConnectionStatus load(Context context) {
        SharedPreferences pref = getPreference(context);
        long at = pref.getLong(KEY_AT, 0L);
        if (at == 0L) {
            return new ConnectionStatus(STATE_UNKNOWN, 0L, "", "");
        }
        boolean ok = pref.getBoolean(KEY_OK, false);
        return new ConnectionStatus(
                ok ? STATE_OK : STATE_ERROR,
                at,
                pref.getString(KEY_DETAIL, ""),
                pref.getString(KEY_SOURCE, ""));
    }

    /** Persiste un evento de conectividad. {@code detail} es un motivo legible
     *  (p. ej. "HTTP 200", "HTTP 401 firma inválida", "sin conexión"). */
    public static void record(Context context, boolean ok, String detail, String source) {
        SharedPreferences.Editor editor = getPreference(context).edit();
        editor.putBoolean(KEY_OK, ok);
        editor.putLong(KEY_AT, System.currentTimeMillis());
        editor.putString(KEY_DETAIL, detail == null ? "" : detail);
        editor.putString(KEY_SOURCE, source == null ? "" : source);
        editor.commit();
    }

    private static SharedPreferences getPreference(Context context) {
        return context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
    }
}
