---
title: QvaArbi SMS — Plantillas de reglas bancarias predefinidas (seed)
version: 1.0
date_created: 2026-07-10
owner: QvaArbi
tags: [architecture, app, android, onboarding]
---

# Introduction

Al instalar la app, las 3 reglas de reenvío para los remitentes bancarios cubanos
(**PAGOxMOVIL**, **ENZONA**, **ETECSA**) deben existir ya creadas. El usuario solo pega
el webhook global (URL + secret) y funciona; no tiene que crear cada regla a mano.

## 1. Purpose & Scope

**Propósito**: eliminar la fricción de onboarding — hoy el usuario debe crear 3 reglas
idénticas salvo el remitente. Se siembran automáticamente en el primer arranque.

**Alcance**: solo la app Android. Sin cambios de backend/web. UI nativa (Java/XML) en el
sistema de diseño existente; `/frontend-design` no aplica (genera React, no Android).

## 2. Definitions

- **Regla / ForwardingConfig**: entrada de reenvío (remitente → webhook) en SharedPreferences.
- **Seed**: creación automática de reglas por defecto en el primer arranque.
- **Webhook global**: par URL + secret único (feature previa) que heredan las reglas sin URL propia.

## 3. Requirements, Constraints & Guidelines

- **REQ-001**: En el primer arranque, la app DEBE crear 3 reglas: sender `PAGOxMOVIL`,
  `ENZONA`, `ETECSA`, con template JSON por defecto, headers por defecto, reintentos por
  defecto, firma HMAC habilitada y SMS habilitado.
- **REQ-002**: Las reglas sembradas DEBEN dejar la URL **vacía** para heredar el webhook
  global (el usuario solo configura URL+secret una vez en Ajustes).
- **REQ-003**: El seed DEBE ejecutarse una sola vez (flag persistente). Si el usuario borra
  luego las reglas, NO se vuelven a crear (respeta su intención).
- **REQ-004**: El seed NO DEBE duplicar un remitente que ya exista (p. ej. importado por
  backup): solo crea los que falten.
- **REQ-005**: El seed corre independientemente del permiso SMS (para que las reglas existan
  aunque el usuario aún no lo conceda).
- **GUD-001**: En la lista, una regla sin URL propia DEBE indicar visualmente que usa el
  "Webhook global de QvaArbi" en vez de mostrar una URL vacía.
- **CON-001**: Sin nuevas dependencias; reutiliza `ForwardingConfig` y sus defaults.

## 4. Interfaces & Data Contracts

### Regla sembrada (valores)
| Campo | Valor |
|---|---|
| sender | `PAGOxMOVIL` / `ENZONA` / `ETECSA` |
| url | `""` (hereda global) |
| template | `ForwardingConfig.getDefaultJsonTemplate()` |
| headers | `ForwardingConfig.getDefaultJsonHeaders()` |
| retriesNumber | `getDefaultRetriesNumber()` (10) |
| signHmacSha256 | `true` |
| isSmsEnabled | `true` |

Flag de seed: SharedPreferences `seed` → `seeded_bank_templates_v1` (bool).

## 5. Acceptance Criteria

- **AC-001**: Given instalación limpia, When abro la app, Then existen 3 reglas
  (PAGOxMOVIL, ENZONA, ETECSA) sin URL, firma ON.
- **AC-002**: Given ya se sembró y borro una regla, When reabro, Then NO reaparece.
- **AC-003**: Given una regla `ENZONA` ya existe, When corre el seed, Then no se duplica.
- **AC-004**: Given webhook global configurado, When llega un SMS de PAGOxMOVIL, Then se
  reenvía a la URL global firmado (sin editar la regla).
- **AC-005**: Given una regla sin URL, When la veo en la lista, Then muestra "Webhook global".

## 6. Test Automation Strategy

- **Instrumented** (`app/src/androidTest`): `TemplateSeederTest` — siembra 3, idempotencia
  (segundo run no duplica), respeta remitente preexistente, flag impide re-seed tras borrado.
- **JVM**: no aplica (SharedPreferences).

## 7. Rationale & Context

Las 3 reglas son idénticas salvo el remitente y el 95% de los usuarios necesitan exactamente
esas tres. Sembrarlas + heredar el webhook global reduce el onboarding a un solo paso (pegar
URL+secret). El flag evita el anti-patrón de re-crear lo que el usuario borró a propósito.

## 8. Dependencies & External Integrations

- **INF-001**: SharedPreferences (Android). Sin libs nuevas.
- **PLT-001**: Android `minSdk 21`.

## 9. Examples & Edge Cases

- Primer arranque limpio → 3 reglas.
- Backup importado con `ENZONA` → seed añade solo PAGOxMOVIL + ETECSA.
- Usuario borra las 3 → no reaparecen (flag).
- Sin webhook global → reglas existen pero no reenvían hasta configurarlo (esperado).

## 10. Validation Criteria

- AC-001..005 verdes; `TemplateSeederTest` pasa; app compila (`compileDebugJavaWithJavac`).
- Release: bump `minor` (feature) → rebuild → firmar → subir a R2.

## 11. Related Specifications / Further Reading

- `spec-architecture-qvaarbi-sms-logging-heartbeat.md` (webhook global)
- `ForwardingConfig.java`, `MainActivity.java`, `ListAdapter.java`
