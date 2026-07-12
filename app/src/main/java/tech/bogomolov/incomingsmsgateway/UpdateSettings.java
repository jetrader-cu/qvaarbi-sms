package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferencia del auto-chequeo de actualizaciones (spec REQ-009). Cuando está OFF, ni
 * el chequeo en foreground ({@link MainActivity}) ni el periódico ({@link
 * UpdateCheckWorker}) consultan el manifest de versión. Default ON.
 *
 * <p>Guarda también el último {@code versionCode} avisado, para no volver a notificar
 * la misma versión en cada chequeo de background.
 */
public class UpdateSettings {

    private static final String PREFERENCE = "update_settings";
    private static final String KEY_AUTO_CHECK = "auto_check";
    private static final String KEY_LAST_NOTIFIED_CODE = "last_notified_code";

    public static boolean isAutoCheckEnabled(Context context) {
        return getPreference(context).getBoolean(KEY_AUTO_CHECK, true);
    }

    public static void setAutoCheckEnabled(Context context, boolean enabled) {
        getPreference(context).edit().putBoolean(KEY_AUTO_CHECK, enabled).commit();
    }

    public static int getLastNotifiedCode(Context context) {
        return getPreference(context).getInt(KEY_LAST_NOTIFIED_CODE, 0);
    }

    public static void setLastNotifiedCode(Context context, int code) {
        getPreference(context).edit().putInt(KEY_LAST_NOTIFIED_CODE, code).commit();
    }

    private static SharedPreferences getPreference(Context context) {
        return context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
    }
}
