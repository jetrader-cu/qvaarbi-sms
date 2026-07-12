package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Configuración de webhook GLOBAL de QvaArbi (spec REQ-006..008): un único par
 * URL + secret HMAC que se aplica a todas las reglas bancarias, en vez de pegarlo
 * en cada regla (PAGOxMOVIL / ENZONA / ETECSA). El panel QvaArbi entrega un
 * webhook por cuenta, así que el usuario lo pega una sola vez en Ajustes.
 *
 * <p>Resolución de efectividad ({@link #resolveUrl}/{@link #resolveSecret}): una
 * regla que defina su propia URL/secret los conserva (override); las reglas con
 * campo vacío heredan el global. Así la migración es sin pérdida: las reglas
 * existentes siguen funcionando y las nuevas pueden dejar la URL vacía.
 *
 * <p>Almacén propio {@code webhook_global}, separado de reglas y del registro.
 */
public class WebhookGlobal {

    private static final String PREFERENCE = "webhook_global";
    private static final String KEY_URL = "url";
    private static final String KEY_SECRET = "secret";

    private final String url;
    private final String secret;

    public WebhookGlobal(String url, String secret) {
        this.url = url == null ? "" : url.trim();
        this.secret = secret == null ? "" : secret.trim();
    }

    public String getUrl() {
        return this.url;
    }

    public String getSecret() {
        return this.secret;
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(this.url);
    }

    public static WebhookGlobal load(Context context) {
        SharedPreferences pref = getPreference(context);
        return new WebhookGlobal(pref.getString(KEY_URL, ""), pref.getString(KEY_SECRET, ""));
    }

    public void save(Context context) {
        SharedPreferences.Editor editor = getPreference(context).edit();
        editor.putString(KEY_URL, this.url);
        editor.putString(KEY_SECRET, this.secret);
        editor.commit();
    }

    /** URL efectiva para una regla: la propia si la tiene, si no la global. */
    public static String resolveUrl(Context context, String ruleUrl) {
        if (!TextUtils.isEmpty(ruleUrl)) {
            return ruleUrl;
        }
        return load(context).getUrl();
    }

    /** Secret efectivo para una regla: el propio si lo tiene, si no el global. */
    public static String resolveSecret(Context context, String ruleSecret) {
        if (!TextUtils.isEmpty(ruleSecret)) {
            return ruleSecret;
        }
        return load(context).getSecret();
    }

    private static SharedPreferences getPreference(Context context) {
        return context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
    }
}
