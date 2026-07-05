# QvaArbi SMS

Fork rebrandeado de [bogkonstantin/android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook) (MIT) para la automatización SMS de QvaArbi.

## Cambios respecto al upstream

- UI dark-only obsidiana `#050609`/`#0D1420` + acento emerald `#34D399` (paridad QvaArbi)
- Strings 100% en español, hints apuntando al webhook QvaArbi; sin referencias a terceros
- Launcher adaptive + legacy: rayo QvaArbi en burbuja SMS emerald (generados con pycairo)
- Icono de notificación = rayo blanco; eliminados `values-night`, `values-ru`, fastlane y assets upstream

- `applicationId`: `com.qvaarbi.sms` (instala junto a la app original sin conflicto)
- `app_name`: "QvaArbi SMS" (es/ru)
- `versionCode 1` / `versionName 1.0.0`

## Build + firma (reproducible)

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease
# firma (keystore en ~/.keys/qvaarbi-sms.keystore, pass en ~/.keys/qvaarbi-sms.keystore.pass)
$ANDROID_HOME/build-tools/36.0.0/zipalign -f 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk qvaarbi-sms-aligned.apk
$ANDROID_HOME/build-tools/36.0.0/apksigner sign \
  --ks ~/.keys/qvaarbi-sms.keystore --ks-key-alias qvaarbi \
  --ks-pass file:$HOME/.keys/qvaarbi-sms.keystore.pass \
  --out qvaarbi-sms.apk qvaarbi-sms-aligned.apk
sha256sum qvaarbi-sms.apk
```

**IMPORTANTE**: usar SIEMPRE el mismo keystore para que las actualizaciones se instalen encima sin desinstalar. NO commitear el keystore.

## Publicación

El APK firmado + su `.sha256` se suben a Cloudflare R2 (`apps/qvaarbi-sms.apk`) con el script `qvapay-arbi/backend/scripts/upload_sms_apk.py`. La página `/app/settings/sms` de QvaArbi enlaza ambos.

## Configuración recomendada en la app

- Remitentes: `PAGOxMOVIL`, `ENZONA`, `ETECSA` (una regla por remitente — solo esos SMS salen del teléfono)
- URL: la del webhook generado en QvaArbi (`/api/v1/webhooks/sms/{token}`)
- HMAC secret: el generado en QvaArbi (header `X-Signature`)
