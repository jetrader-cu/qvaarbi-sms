package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.util.Log;

/**
 * Ping de heartbeat firmado contra QvaArbi (spec REQ-011/012). A diferencia del
 * ping vacío original —que contra el endpoint firmado siempre daba 401— este envía
 * un body JSON firmado con HMAC-SHA-256 usando el secret del webhook global, el
 * mismo esquema (`X-Signature`) que las entregas de SMS (SEC-001). El backend
 * responde 200 y actualiza {@code last_seen_at} sin encolar nada.
 *
 * <p>Registra el resultado en {@link ConnectionStatus} para alimentar el banner de
 * la pantalla principal, de modo que el usuario vea "Conectado a QvaArbi" aunque no
 * haya tráfico de SMS. Reutilizado por {@link SmsReceiverService} (ping periódico) y
 * por el botón "Probar" de {@link SettingsActivity}.
 */
public class Heartbeat {

    // Body mínimo pero no vacío: firmar "" es ambiguo y el backend espera JSON.
    static final String BODY = "{\"heartbeat\":true}";

    /**
     * Deriva la URL del endpoint de heartbeat a partir de la URL del webhook:
     * {@code …/webhooks/sms/<token>} → {@code …/webhooks/sms/<token>/heartbeat}.
     * Método puro (sin Android) para poder testearlo en la JVM.
     */
    public static String heartbeatUrlFor(String webhookUrl) {
        if (webhookUrl == null) {
            return "";
        }
        String base = webhookUrl.endsWith("/")
                ? webhookUrl.substring(0, webhookUrl.length() - 1)
                : webhookUrl;
        return base + "/heartbeat";
    }

    /**
     * Envía el ping y devuelve un texto legible del resultado (para el toast de
     * "Probar"). Efecto colateral: persiste el estado de conexión.
     */
    public static String ping(Context context, String url) {
        String secret = WebhookGlobal.load(context).getSecret();
        Request request = new Request(url, BODY);
        request.setUseChunkedMode(false);
        if (secret != null && !secret.isEmpty()) {
            request.setSignatureHeader(secret, BODY);
        }

        String result = request.execute();
        int httpCode = request.getResponseCode();
        boolean ok = Request.RESULT_SUCCESS.equals(result);

        String detail = describe(ok, httpCode, secret);
        ConnectionStatus.record(context, ok, detail, ConnectionStatus.SOURCE_HEARTBEAT);
        Log.i("SmsGateway", "heartbeat: " + result + " (" + detail + ")");
        return detail;
    }

    private static String describe(boolean ok, int httpCode, String secret) {
        if (ok) {
            return "HTTP " + httpCode;
        }
        if (httpCode == 401) {
            return (secret == null || secret.isEmpty())
                    ? "HTTP 401: falta el secret del webhook"
                    : "HTTP 401: firma inválida";
        }
        if (httpCode < 0) {
            return "sin conexión";
        }
        return "HTTP " + httpCode;
    }
}
