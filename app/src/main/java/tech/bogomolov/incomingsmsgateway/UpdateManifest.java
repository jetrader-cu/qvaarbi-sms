package tech.bogomolov.incomingsmsgateway;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manifest de versión de la app publicado en R2 y servido por
 * {@code GET /api/v1/public/app-version} (spec §4.1). Inmutable; el parseo es puro
 * (sin Android) para poder testearlo en la JVM.
 */
public class UpdateManifest {

    public final int versionCode;
    public final String versionName;
    public final String apkUrl;
    public final String notes;
    public final String sha256;

    public UpdateManifest(int versionCode, String versionName, String apkUrl,
                          String notes, String sha256) {
        this.versionCode = versionCode;
        this.versionName = versionName == null ? "" : versionName;
        this.apkUrl = apkUrl == null ? "" : apkUrl;
        this.notes = notes == null ? "" : notes;
        this.sha256 = sha256 == null ? "" : sha256;
    }

    /**
     * Parsea el JSON del manifest. Devuelve null si falta el campo obligatorio
     * {@code versionCode} o el JSON es inválido / indica {@code available:false}.
     */
    @Nullable
    public static UpdateManifest parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("available") && !obj.optBoolean("available", true)) {
                return null;
            }
            if (!obj.has("versionCode")) {
                return null;
            }
            return new UpdateManifest(
                    obj.getInt("versionCode"),
                    obj.optString("versionName", ""),
                    obj.optString("apkUrl", ""),
                    obj.optString("notes", ""),
                    obj.optString("sha256", ""));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Hay actualización si la versión publicada es más nueva que la instalada. */
    public boolean isNewerThan(int installedVersionCode) {
        return this.versionCode > installedVersionCode;
    }
}
