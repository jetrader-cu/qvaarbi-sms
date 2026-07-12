package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Siembra las 3 reglas de reenvío predefinidas de la banca cubana en el primer
 * arranque (spec REQ-001): PAGOxMOVIL, ENZONA y ETECSA. Cada una queda lista con
 * template/headers/reintentos por defecto y firma HMAC activada; la URL se deja
 * vacía para heredar el webhook global (REQ-002), de modo que el usuario solo pega
 * URL + secret una vez en Ajustes y todo funciona.
 *
 * <p>Se ejecuta una sola vez (flag persistente, REQ-003): si el usuario borra luego
 * las reglas NO se recrean. Tampoco duplica un remitente ya existente (REQ-004),
 * p. ej. importado por backup.
 */
public class TemplateSeeder {

    private static final String PREFERENCE = "seed";
    private static final String KEY_SEEDED = "seeded_bank_templates_v1";

    // Remitentes de la banca digital cubana (los SMS de confirmación de pago).
    static final String[] BANK_SENDERS = {"PAGOxMOVIL", "ENZONA", "ETECSA"};

    /**
     * Crea las reglas por defecto la primera vez. Idempotente: no hace nada si ya se
     * sembró, y salta los remitentes que ya existan. Marca el flag al terminar.
     */
    public static void seedIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            return;
        }

        Set<String> existing = new HashSet<>();
        for (ForwardingConfig config : ForwardingConfig.getAll(context)) {
            if (config.getSender() != null) {
                existing.add(config.getSender());
            }
        }

        for (String sender : BANK_SENDERS) {
            if (existing.contains(sender)) {
                continue; // no duplicar (REQ-004)
            }
            createRule(context, sender).save();
        }

        prefs.edit().putBoolean(KEY_SEEDED, true).commit();
    }

    // Construye una regla con los valores por defecto de la spec (§4). URL vacía →
    // hereda el webhook global; firma ON para enviar X-Signature en cuanto haya secret.
    private static ForwardingConfig createRule(Context context, String sender) {
        ForwardingConfig config = new ForwardingConfig(context);
        config.setSender(sender);
        config.setUrl(""); // hereda el webhook global (REQ-002)
        config.setTemplate(ForwardingConfig.getDefaultJsonTemplate());
        config.setHeaders(ForwardingConfig.getDefaultJsonHeaders());
        config.setRetriesNumber(ForwardingConfig.getDefaultRetriesNumber());
        config.setSignHmacSha256(true);
        config.setIsSmsEnabled(true);
        config.setChunkedMode(true);
        return config;
    }

    /** Solo para tests: fuerza que el seed pueda volver a correr. */
    static void resetFlag(Context context) {
        context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE)
                .edit().remove(KEY_SEEDED).commit();
    }

    /** Solo para tests: elimina todas las reglas sembradas/creadas. */
    static void clearAllRules(Context context) {
        ArrayList<ForwardingConfig> all = ForwardingConfig.getAll(context);
        for (ForwardingConfig config : all) {
            config.remove();
        }
    }
}
