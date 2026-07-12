---
title: QvaArbi SMS — Versionado, auto-actualización y pantalla Ayuda
version: 1.0
date_created: 2026-07-08
owner: QvaArbi
tags: [architecture, app, android, release, web, ota]
---

# Introduction

Versionado de cada build de la app **QvaArbi SMS** y detección de nuevas versiones en
app y web. Al subir un APK a Cloudflare R2 se publica un **manifest de versión** que se
vuelve la fuente única de verdad de "hay actualización". La app lo consulta
automáticamente (con toggle on/off), la web muestra la última versión + descarga, y la
app gana una pantalla **Ayuda** con versión actual + contacto/redes de QvaArbi.

## 1. Purpose & Scope

**Propósito**: que usuarios y web sepan cuándo hay una versión nueva del gateway SMS y
puedan descargarla, sin publicar en Play Store (permiso SMS ⇒ instalación directa).

**Alcance**:
- Script de release `upload_sms_apk.py` → publica manifest JSON en R2.
- Backend `GET /api/v1/public/app-version` → sirve el manifest (público, sin auth).
- Web `/app/settings/sms` → muestra versión + aviso "nueva versión" + descarga.
- App Android → chequeo automático de updates (toggle), aviso + botón "Actualizar"
  (abre la descarga en navegador), y pantalla **Ayuda** (versión + contacto + redes).

**Fuera de alcance**: instalación in-app del APK (DownloadManager/FileProvider) — el
botón abre la descarga en el navegador (decisión de producto). Auto-update silencioso.

## 2. Definitions

- **Manifest de versión**: objeto JSON en R2 (`apps/qvaarbi-sms-version.json`) con la
  metadata de la última release.
- **versionCode**: entero monotónico de Android (`build.gradle`) — criterio de comparación.
- **versionName**: string legible ("1.2.0").
- **aapt2**: herramienta de Android build-tools que extrae metadata del APK.
- **OTA**: over-the-air; aquí = descarga manual asistida, no push automático.

## 3. Requirements, Constraints & Guidelines

### Release / manifest
- **REQ-001**: `upload_sms_apk.py` DEBE, además del APK y su `.sha256`, publicar
  `apps/qvaarbi-sms-version.json` con `versionCode`, `versionName`, `sha256`, `apkUrl`,
  `notes`, `publishedAt` (ISO-8601).
- **REQ-002**: `versionCode`/`versionName` se extraen automáticamente del APK vía
  `aapt2 dump badging`; si `aapt2` no está disponible, se toman de flags CLI
  (`--version-code`, `--version-name`). `notes` siempre por flag (`--notes`), opcional.
- **CON-001**: El manifest tiene `Cache-Control` corto (≤600s), igual que el APK, porque
  se reemplaza in-place en cada release.
- **GUD-001**: Cada release DEBE subir el `versionCode` en `app/build.gradle` (build nuevo
  = versionCode+1), o la app no detectará el cambio.

### Backend
- **REQ-003**: `GET /api/v1/public/app-version` (sin auth) DEVUELVE el manifest. Lee el
  objeto de R2 (`get_object_bytes`) y lo devuelve como JSON; 200 con el manifest o 404
  `{"available": false}` si aún no se ha publicado ninguno.
- **REQ-004**: `sms_automation._public()` (endpoint autenticado del panel) DEBE incluir
  `latest_version` (versionName) y `latest_version_code` leídos del manifest, para que la
  web muestre la versión sin una segunda llamada.

### Web
- **REQ-005**: `/app/settings/sms` DEBE mostrar la `versionName` de la última release junto
  al botón de descarga.
- **REQ-006**: (Opcional realzado) Si el manifest trae `notes`, mostrarlas como "Novedades".

### App Android — auto-update
- **REQ-007**: La app DEBE consultar `GET /api/v1/public/app-version` y, si
  `manifest.versionCode > BuildConfig.VERSION_CODE`, considerar que hay actualización.
- **REQ-008**: El chequeo automático DEBE ejecutarse al abrir la app (foreground) y de
  forma periódica en segundo plano (WorkManager, intervalo ≥ mínimo permitido, ~24h).
- **REQ-009**: DEBE existir un toggle en Ajustes ("Buscar actualizaciones automáticamente",
  default ON). OFF ⇒ no se consulta el manifest ni en foreground ni en background.
- **REQ-010**: Con update disponible, la app DEBE mostrar un aviso (banner/diálogo) con
  `versionName` + `notes` y un botón "Actualizar" que abra `apkUrl` en el navegador
  (`ACTION_VIEW`). Sin permisos nuevos.
- **CON-002**: El chequeo NO debe bloquear el arranque ni la recepción de SMS; corre en
  hilo/Worker aparte y falla en silencio ante red/JSON inválido.

### App Android — Ayuda
- **REQ-011**: Nueva pantalla **Ayuda** (item de menú) que muestre: `versionName`
  (+`versionCode`), estado de actualización, y accesos a QvaArbi: bot de Telegram y canal
  con **botones directos** (abren `https://t.me/<bot>` y `https://t.me/<canal>`), más
  email/WhatsApp de soporte.
- **CON-003**: Los datos de contacto/redes son constantes de la app (espejo de
  `frontend/lib/content/help.ts`); no requieren red.

### Seguridad
- **SEC-001**: El manifest es público y sólo metadata; NO incluye secrets. La integridad
  del APK se verifica por el `.sha256` publicado (el usuario puede cotejarlo), no por el
  manifest.
- **SEC-002**: El endpoint de versión es de sólo lectura y no expone datos de usuarios.

## 4. Interfaces & Data Contracts

### 4.1 Manifest `apps/qvaarbi-sms-version.json`
```json
{
  "versionCode": 3,
  "versionName": "1.2.0",
  "sha256": "ab12…ef",
  "apkUrl": "https://<r2-public>/apps/qvaarbi-sms.apk",
  "notes": "Registro de mensajes, webhook global, heartbeat firmado.",
  "publishedAt": "2026-07-08T14:00:00Z"
}
```

### 4.2 `GET /api/v1/public/app-version`
| Caso | Status | Body |
|---|---|---|
| Publicado | 200 | manifest §4.1 + `"available": true` |
| Sin releases | 404 | `{"available": false}` |

### 4.3 App: comparación
```
hayUpdate = manifest.versionCode > BuildConfig.VERSION_CODE
```

## 5. Acceptance Criteria

- **AC-001**: Given un APK, When se corre `upload_sms_apk.py`, Then se publican APK,
  `.sha256` y `qvaarbi-sms-version.json` con `versionCode`/`versionName` reales del APK.
- **AC-002**: Given un manifest publicado, When `GET /api/v1/public/app-version`, Then 200
  con la metadata; sin manifest, Then 404 `{"available": false}`.
- **AC-003**: Given la web `/app/settings/sms`, When carga la config, Then muestra la
  `versionName` de la última release y el botón de descarga.
- **AC-004**: Given `manifest.versionCode > VERSION_CODE` y toggle ON, When la app abre,
  Then muestra aviso de actualización con botón que abre `apkUrl`.
- **AC-005**: Given toggle OFF, When la app abre, Then NO consulta el manifest.
- **AC-006**: Given la pantalla Ayuda, Then muestra la versión actual y botones que abren
  el bot y el canal de Telegram.
- **AC-007**: Given red caída o JSON inválido, When corre el chequeo, Then no crashea y no
  muestra aviso.

## 6. Test Automation Strategy

- **Backend (pytest-asyncio)**: `test_app_version.py` — 200 con manifest mockeado
  (`r2_storage.get_object_bytes` → bytes JSON), 404 sin manifest, JSON inválido → 404.
- **App JVM (`app/src/test`)**: `UpdateManifestTest` (parseo + comparación versionCode),
  extracción de versión en el script cubierta manualmente.
- **App instrumented**: `UpdatePrefsTest` (toggle persistente en SharedPreferences,
  patrón `HeartbeatSettingsTest`).
- **Web (Vitest)**: `sms-automation-card.test.tsx` ampliado — renderiza `latest_version`.
- **Script**: prueba manual `upload_sms_apk.py --version-code 3 --version-name 1.2.0`.

## 7. Rationale & Context

- Play Store no admite apps con permiso SMS ⇒ distribución directa por APK ⇒ hace falta un
  canal propio de "hay nueva versión". El manifest en R2 evita tocar la DB y reutiliza la
  infra de subida ya existente (`upload_sms_apk.py`, `r2_storage.public_url`).
- Extraer la versión del APK con `aapt2` elimina la desincronía entre lo subido y lo
  anunciado (el operador podría teclear mal un número).
- Abrir la descarga en navegador evita `REQUEST_INSTALL_PACKAGES`/FileProvider (permisos
  sensibles + más superficie) y es compatible con el flujo Play-Protect que el usuario ya
  conoce al instalar el APK la primera vez.

## 8. Dependencies & External Integrations

### External Systems
- **EXT-001**: Cloudflare R2 — hosting del APK, `.sha256` y manifest.
- **EXT-002**: Telegram — bot (`qvaarbi_bot`) y canal (`@qvaarbi`) enlazados desde Ayuda.

### Infrastructure Dependencies
- **INF-001**: `aapt2` de Android build-tools (release host) — extracción de versión.
- **INF-002**: WorkManager (ya en la app) — chequeo periódico en background.

### Data Dependencies
- **DAT-001**: `apps/qvaarbi-sms-version.json` en R2 — formato §4.1.

### Technology Platform Dependencies
- **PLT-001**: Android `minSdk 21`; `BuildConfig.VERSION_CODE`/`VERSION_NAME`.
- **PLT-002**: FastAPI (Python 3.12); Next.js 16 (frontend).

## 9. Examples & Edge Cases

```bash
# Release: versión auto desde el APK, notas por flag
.venv/bin/python scripts/upload_sms_apk.py qvaarbi-sms.apk \
  --notes "Registro de mensajes + webhook global + heartbeat"
# aapt2 no disponible → fallback explícito
.venv/bin/python scripts/upload_sms_apk.py qvaarbi-sms.apk \
  --version-code 3 --version-name 1.2.0 --notes "…"
```

Edge cases:
- Manifest ausente (primera vez) → endpoint 404 `available:false`; app y web no anuncian nada.
- `versionCode` igual o menor → sin aviso (app al día).
- Toggle OFF → cero llamadas de red por updates.
- `aapt2` presente pero el APK sin badging legible → error claro; usar flags.

## 10. Validation Criteria

- AC-001..007 verdes.
- `pytest tests/integration/api/test_app_version.py` pasa.
- `./gradlew testDebugUnitTest` pasa (UpdateManifest parse/compare).
- Web `vitest run` de la card SMS pasa con `latest_version`.
- Sin permisos nuevos en el `AndroidManifest`.

## 11. Related Specifications / Further Reading

- `spec-architecture-qvaarbi-sms-logging-heartbeat.md` (feature previa)
- `backend/scripts/upload_sms_apk.py`, `backend/app/services/r2_storage.py`
- `backend/app/api/v1/public.py`, `backend/app/api/v1/sms_automation.py`
- `frontend/lib/content/help.ts` (fuente de bot/canal/soporte)
