---
title: QvaArbi SMS — Registro de mensajes, webhook global y estado de conexión
version: 1.0
date_created: 2026-07-07
last_updated: 2026-07-07
owner: QvaArbi
tags: [architecture, app, android, sms, observability]
---

# Introduction

Especificación de tres mejoras acopladas en la app Android **QvaArbi SMS**
(`tech.bogomolov.incomingsmsgateway`, fork de `android_income_sms_gateway_webhook`)
y el endpoint que la respalda en el backend `qvaarbi-api`:

1. **Corregir el crash** al abrir "Registro" (menú de la barra) — hoy revienta.
2. **Registro persistente por mensaje** (recibido → reenviado) con estado y timestamps,
   agrupado por regla ("por usuario"), reemplazando el volcado de `logcat` actual.
3. **Configuración de webhook global** (URL + secret HMAC en Ajustes, un solo par)
   en vez de pegarlo en cada regla, con el secret hoy oculto bajo "Avanzado".
4. **Estado de conexión visible**: banner en la pantalla principal que indique si el
   teléfono está conectando correctamente a QvaArbi (último heartbeat/entrega OK/fallo),
   apoyado en un **endpoint heartbeat firmado** en el backend (hoy inexistente).

## 1. Purpose & Scope

**Propósito**: dar al usuario final observabilidad y confianza sobre el gateway SMS
(¿llegan los SMS?, ¿se reenvían?, ¿estoy conectado a QvaArbi?) y eliminar dos fallos:
el crash de "Registro" y un heartbeat que nunca puede dar OK contra el endpoint firmado.

**Alcance**:
- App Android (Java, `minSdk 21`, `compileSdk 33`, WorkManager 2.7.1, SharedPreferences).
- Endpoint backend `POST /api/v1/webhooks/sms/{token}/heartbeat` (FastAPI) + persistencia
  de `last_seen_at` en `TenantBankSmsConfig`.

**Fuera de alcance**: cambios en el parser/matcher de SMS del backend (`sms_parser.py`,
`sms_trade_matcher.py`), USSD, y el panel web de QvaArbi (salvo, opcionalmente, mostrar
`last_seen_at`; no es requisito de esta spec).

**Audiencia**: desarrolladores Android y backend de QvaArbi.

## 2. Definitions

- **Regla / ForwardingConfig**: entrada de reenvío (remitente + destino) guardada en
  SharedPreferences `key_phones_preference`.
- **Webhook**: `POST` HTTPS a `…/api/v1/webhooks/sms/{token}` con body JSON del SMS.
- **Secret HMAC**: clave para firmar el body con HMAC-SHA256 → header `X-Signature` (hex).
- **Heartbeat**: ping periódico para monitoreo dead-man's-switch de que la app sigue viva.
- **Entrega (delivery)**: intento de POST del SMS al webhook, gestionado por `RequestWorker`.
- **MessageLog**: nuevo store local de eventos de mensaje (recibido/reenviado + estado).
- **Estado de entrega**: `pending | sent | failed | retry`.
- **Token**: identificador opaco en la URL del webhook que resuelve la config del usuario.

## 3. Requirements, Constraints & Guidelines

### Crash "Registro"
- **REQ-001**: Abrir "Registro" NUNCA debe crashear, incluso sin permiso `RECEIVE_SMS`
  concedido (estado en el que `MainActivity.context` sigue null).
- **BUG-001**: Causa raíz — `onOptionsItemSelected` usa `new AlertDialog.Builder(context)`
  con el campo `context`, asignado sólo en `showList()`, que no corre hasta conceder SMS.

### Registro de mensajes
- **REQ-002**: Cada SMS entrante que matchee una regla habilitada DEBE registrar un evento
  con: remitente, cuerpo, `ruleKey`, `ruleName` (sender de la regla), estado inicial
  `pending`, `receivedAt`, `attempts=0`.
- **REQ-003**: Cada resultado de entrega DEBE actualizar el evento: estado
  (`sent|failed|retry`), `httpCode`, `attempts`, `lastAttemptAt`.
- **REQ-004**: El Registro DEBE listar los eventos agrupados por regla, ordenados por
  fecha desc, con detalle por evento (cuerpo completo, historial de intentos, código HTTP).
- **REQ-005**: El store DEBE tener cota superior (máx. entradas) con poda del más antiguo,
  igual que `FailedMessage` (`MAX_STORED`), para no crecer sin límite.
- **CON-001**: Sin base de datos nueva — usar SharedPreferences (patrón existente) para no
  añadir Room/deps ni migraciones. Un archivo de preferencias `message_log` dedicado.
- **CON-002**: El registro NO debe bloquear la recepción del SMS ni la entrega; escritura
  best-effort con try/catch que sólo loguea en error (patrón `FailedMessage`).

### Webhook global
- **REQ-006**: Ajustes DEBE ofrecer una sección "Webhook QvaArbi" con URL + secret (un par).
- **REQ-007**: Al recibir un SMS, si la regla no define URL/secret propios, DEBE usarse la
  config global (URL + firma HMAC habilitada + secret). Override por-regla sigue siendo posible.
- **REQ-008**: Migración sin pérdida — reglas existentes conservan su URL/secret; el global
  sólo rellena las que estén vacías.
- **GUD-001**: Botón "Probar" en la sección webhook que firme y llame al heartbeat (REQ-012).

### Estado de conexión + heartbeat
- **REQ-009**: La pantalla principal DEBE mostrar un banner de estado:
  verde "Conectado a QvaArbi" (+ hora del último OK) / rojo con el error del último fallo /
  neutro "Sin actividad aún".
- **REQ-010**: El estado se deriva del último resultado de heartbeat Y de la última entrega.
- **REQ-011**: El heartbeat DEBE firmarse con el secret HMAC (hoy va sin firma → 401).
- **REQ-012 (backend)**: Nuevo `POST /api/v1/webhooks/sms/{token}/heartbeat` que valide la
  misma firma HMAC del body, actualice `TenantBankSmsConfig.last_seen_at` y responda `200`
  sin encolar nada. Sin firma válida → `401`.
- **SEC-001**: El heartbeat firma el mismo esquema que el webhook (`X-Signature` = HMAC-SHA256
  hex sobre el raw body). No introducir un esquema de auth distinto.
- **CON-003**: `last_seen_at` es columna nueva → migración Alembic autogenerada; NO SQL ad-hoc.

### Generales
- **PAT-001**: Cambios pequeños y focalizados (el repo es "stable, minimal"); nada de refactors.
- **PAT-002**: Java, no Kotlin. Sin nuevas dependencias Gradle salvo estrictamente necesarias
  (ninguna prevista).
- **GUD-002**: Textos de UI en español (el fork QvaArbi ya lo hace); strings en `strings.xml`.

## 4. Interfaces & Data Contracts

### 4.1 MessageLog (SharedPreferences `message_log`)

Cada entrada: clave `timestamp_random`, valor JSON:

```json
{
  "ruleKey": "1720000000000_123456",
  "ruleName": "PAGOxMOVIL",
  "sender": "PAGOxMOVIL",
  "body": "Ha recibido una transferencia de 1000.00 CUP...",
  "status": "sent",
  "httpCode": 200,
  "attempts": 1,
  "receivedAt": 1720000000000,
  "lastAttemptAt": 1720000000450
}
```

Estados: `pending` (encolado) → `sent` (2xx) | `retry` (reintentable, p.ej. 5xx/timeout) |
`failed` (agotó reintentos o error permanente).

### 4.2 Webhook global (SharedPreferences `webhook_global`)

```json
{ "url": "https://qvaarbi-api.arbiexpress.com/api/v1/webhooks/sms/<token>",
  "secret": "<hmac-secret>" }
```

### 4.3 Endpoint heartbeat (backend)

| Método | Ruta | Headers | Body | 200 | 401 |
|---|---|---|---|---|---|
| POST | `/api/v1/webhooks/sms/{token}/heartbeat` | `X-Signature: <hmac_hex>` | `{}` (o `{"heartbeat":true}`) | `{"ok":true}` + set `last_seen_at=now()` | firma inválida / token desconocido / sin secret |

- Firma: `HMAC_SHA256(secret, raw_body)` en hex, comparado timing-safe (`hmac.compare_digest`).
- Reutiliza `TenantBankSmsConfig` + `decrypt_secret` (idéntico a `sms_webhook`).
- No encola en `queue:sms_inbound`.

## 5. Acceptance Criteria

- **AC-001**: Given permiso SMS NO concedido, When abro "Registro", Then se muestra la pantalla
  (o un vacío informativo) sin crash.
- **AC-002**: Given un SMS de `PAGOxMOVIL` que matchea una regla, When se recibe, Then aparece
  un evento `pending` en el Registro bajo el grupo "PAGOxMOVIL".
- **AC-003**: Given ese evento, When el webhook responde 200, Then su estado pasa a `sent` con
  `httpCode=200` y `attempts>=1`.
- **AC-004**: Given webhook 500 con reintentos, When se agotan, Then estado `failed` con el
  último `httpCode`.
- **AC-005**: Given webhook global configurado y una regla sin URL, When llega el SMS, Then se
  reenvía a la URL global firmado con el secret global.
- **AC-006**: Given secret HMAC configurado, When pulso "Probar", Then el heartbeat firma y el
  backend responde 200 y el banner pasa a verde con la hora.
- **AC-007**: Given `POST /webhooks/sms/{token}/heartbeat` con firma válida, Then 200 y
  `last_seen_at` actualizado; con firma inválida, Then 401.
- **AC-008**: El store `message_log` nunca excede `MAX_STORED`; el más antiguo se poda.

## 6. Test Automation Strategy

- **Android — JVM unit** (`app/src/test/`): serialización/poda de `MessageLog`
  (patrón `ForwardingConfigPrepareMessageTest`, sin APIs Android → o instrumentado si toca
  SharedPreferences), lógica de derivación de estado del banner, y resolución
  webhook global vs per-regla.
- **Android — instrumented** (`app/src/androidTest/`): `MessageLogTest` (SharedPreferences,
  patrón `FailedMessageTest`/`HeartbeatSettingsTest`), Espresso para la pantalla Registro y la
  sección webhook de Ajustes (sin disparar `startForegroundService`).
- **Backend — pytest-asyncio** (`backend/tests/integration/api/test_sms_webhook.py` +
  unit): heartbeat 200 con firma válida (y `last_seen_at` seteado), 401 sin/mal firma,
  404 token desconocido.
- **CI**: GitHub Actions ya corre ambos jobs Android; backend con `.venv` host.
- **Cobertura**: caso feliz + edges (sin permiso, sin secret, poda, firma inválida).

## 7. Rationale & Context

- El crash es un NPE trivial pero bloquea justo la función que el usuario más necesita para
  diagnosticar (screenshot: la app abre en estado "sin permiso" y "Registro" revienta).
- El `logcat` actual como "Registro" no es un historial de negocio: no persiste, no agrupa,
  y `Runtime.exec` con `| grep` no funciona (exec no interpreta pipes de shell).
- El webhook per-regla obliga a pegar URL+secret 3 veces (PAGOxMOVIL/ENZONA/ETECSA) y el
  secret está escondido bajo "Avanzado" → el usuario cree que "no hay dónde ponerlo".
- El heartbeat contra el endpoint firmado siempre da 401 (ping sin firma) → nunca prueba
  "conexión OK". Firmarlo + endpoint dedicado cierra el lazo y permite el banner verde.

## 8. Dependencies & External Integrations

### External Systems
- **EXT-001**: Backend `qvaarbi-api` — endpoint webhook SMS + nuevo heartbeat.

### Infrastructure Dependencies
- **INF-001**: WorkManager 2.7.1 (ya presente) para la entrega con reintentos.
- **INF-002**: SharedPreferences (Android) — sin nuevas libs.

### Data Dependencies
- **DAT-001**: `TenantBankSmsConfig` (Postgres) — se le añade `last_seen_at TIMESTAMPTZ NULL`.

### Technology Platform Dependencies
- **PLT-001**: Android `minSdk 21`, Java 8+, `compileSdk 33`.
- **PLT-002**: FastAPI (Python 3.12) + SQLAlchemy async + Alembic.

### Compliance Dependencies
- **COM-001**: El secret y la URL son sensibles (ya se advierte en export backup) — el
  MessageLog NO debe persistir el secret, sólo URL/estado/código.

## 9. Examples & Edge Cases

```java
// Estado de entrega → estado de log
// Request.RESULT_SUCCESS  -> "sent"   (httpCode 2xx)
// Request.RESULT_RETRY    -> "retry"  (mientras queden intentos); "failed" al agotarse
// Request.RESULT_ERROR    -> "failed" (permanente: URL malformada, etc.)
```

Edge cases:
- Sin permiso SMS → Registro abre vacío (no crash).
- Regla sin URL y sin webhook global → no se reenvía; se registra `failed` con motivo.
- Secret vacío con firma habilitada → se envía sin firma (patrón actual `RequestWorker`) →
  backend responde 401 → log `failed` httpCode 401 → banner rojo "firma inválida".
- Reintentos WorkManager: cada `retry` actualiza `attempts`/`lastAttemptAt` del MISMO evento
  (correlación por un `logKey` propagado en el `Data` del worker).

## 10. Validation Criteria

- Todos los AC-00x verdes.
- `./gradlew testDebugUnitTest` y el job instrumentado pasan.
- `backend/.venv/bin/python -m pytest tests/integration/api/test_sms_webhook.py` pasa.
- Sin nuevas dependencias Gradle; sin `npm run build`.
- Migración Alembic para `last_seen_at` generada y lista (aplicar a mano en prod).

## 11. Related Specifications / Further Reading

- `backend/app/api/v1/webhooks.py` (`sms_webhook`), `backend/app/api/v1/sms_automation.py`
- Memoria proyecto: `project_sms_bank_automation.md`
- App: `README.md`, `CLAUDE.md`, `BRANDING.md` del repo `qvaarbi-sms`
