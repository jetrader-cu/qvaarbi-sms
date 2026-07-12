package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Consulta el manifest de versión en {@code GET /api/v1/public/app-version} y decide
 * si hay actualización (spec REQ-007). Ejecuta un GET simple con HttpURLConnection en
 * el hilo del llamante (background), sin nuevas dependencias, y falla en silencio
 * (null) ante red/JSON inválido (CON-002).
 */
public class UpdateChecker {

    private static final String VERSION_PATH = "/api/v1/public/app-version";
    private static final int TIMEOUT_MS = 10_000;

    /**
     * Descarga y parsea el manifest. Devuelve null si no hay red, el endpoint falla,
     * el JSON es inválido, o aún no se ha publicado ninguna release (404).
     */
    @Nullable
    public static UpdateManifest fetch(Context context) {
        String url = versionUrl(context);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                // 404 = aún no hay releases publicadas; cualquier otro = fallo transitorio.
                return null;
            }
            String body = readStream(connection.getInputStream());
            return UpdateManifest.parse(body);
        } catch (Exception e) {
            Log.i("SmsGateway", "update check failed: " + e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** True si hay una versión más nueva que la instalada. */
    public static boolean hasUpdate(@Nullable UpdateManifest manifest) {
        return manifest != null && manifest.isNewerThan(BuildConfig.VERSION_CODE);
    }

    // Deriva la URL del endpoint de versión: reutiliza el host del webhook global si
    // está configurado (mismo backend), si no cae al api_base_url por defecto. Así un
    // despliegue self-hosted apunta al mismo API sin recompilar.
    static String versionUrl(Context context) {
        String base = context.getString(R.string.api_base_url);
        String webhook = WebhookGlobal.load(context).getUrl();
        int idx = webhook.indexOf("/api/v1/");
        if (idx > 0) {
            base = webhook.substring(0, idx);
        }
        return base + VERSION_PATH;
    }

    private static String readStream(InputStream input) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }
}
