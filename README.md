# QvaArbi SMS

App Android oficial de [QvaArbi](https://qvaarbi.arbiexpress.com) para la automatización SMS: reenvía los SMS de confirmación de la banca digital cubana (remitentes `PAGOxMOVIL`, `ENZONA`, `ETECSA`) al webhook firmado de tu cuenta QvaArbi, donde se detectan pagos y recepciones de tus trades.

- Tema oscuro obsidiana + acento emerald (sistema de diseño QvaArbi)
- Firma HMAC-SHA-256 (`X-Signature`) — el backend rechaza todo lo no firmado
- Filtro por remitente: solo los SMS bancarios salen del teléfono
- Reintentos con backoff, almacenamiento de fallidos, heartbeat opcional

## Instalación (usuarios)

Descarga el APK desde tu panel QvaArbi → **Ajustes → SMS bancarios** (incluye SHA-256 para verificar). Google Play no admite apps con permiso de SMS → instalación directa; el aviso de Play Protect es un falso positivo esperado.

## Configuración

1. En QvaArbi (**Ajustes → SMS bancarios**) genera el webhook y copia URL + secret.
2. En la app crea una regla por remitente: `PAGOxMOVIL`, `ENZONA`, `ETECSA`.
3. Pega la URL del webhook y el secret HMAC en cada regla.
4. Prueba con el botón "Probar" y verifica el evento en el historial del panel.

## Build y publicación (desarrollo)

Ver [BRANDING.md](BRANDING.md) — build manual con Gradle, firma con `apksigner` (keystore único reutilizable) y subida a R2 con `upload_sms_apk.py`.

## Licencia

MIT (ver [LICENSE.txt](LICENSE.txt)). Basada en el proyecto MIT `android_income_sms_gateway_webhook`.
