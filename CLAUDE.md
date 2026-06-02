# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A free, open-source Android app (`tech.bogomolov.incomingsmsgateway`) that forwards incoming SMS to a user-configured URL as an HTTP POST with a JSON body. No backend, no accounts. The repo is intentionally a "stable, minimal" build — it is maintained but not actively expanded, so prefer small, focused changes over large refactors.

## Build & test

The project uses Gradle (wrapper committed, Gradle 7.6) and the legacy Android plugin (AGP 7.3.1, `compileSdkVersion 33`, `minSdkVersion 14`). Source is Java, not Kotlin. Gradle 7.6 does **not** support JDK 21 — build with JDK 17 (e.g. `JAVA_HOME=<jdk17> ./gradlew ...`).

```bash
./gradlew assembleDebug        # build debug APK
./gradlew assembleRelease      # build release APK (minify disabled)
./gradlew testDebugUnitTest    # run the JVM unit tests (no device needed)
./gradlew connectedAndroidTest # run the instrumented tests (REQUIRES a connected device/emulator)
```

Most tests live in `app/src/androidTest/` and are **instrumented tests** — they run on a device/emulator, not the local JVM. There are also a few pure-JVM unit tests in `app/src/test/` (e.g. `ForwardingConfigPrepareMessageTest`) that run with `testDebugUnitTest`; prefer adding tests there when the logic under test touches no Android APIs. Run a single instrumented test class with:

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=tech.bogomolov.incomingsmsgateway.WebhookCallerTest
```

CI runs both suites via GitHub Actions (`.github/workflows/tests.yml`): a fast JVM unit-test job, and an emulator job for the instrumented tests.

`local.properties` is **git-ignored** (not committed); it points to the Android SDK locally, and CI falls back to the runner's `ANDROID_HOME`.

## Architecture

The data flow is: **SMS arrives → matched against configs → a WorkManager job POSTs to the webhook with retries.**

- **`SmsReceiverService`** — a foreground `Service` (the "F" status-bar icon). It does *not* statically register the SMS receiver in the manifest; instead it registers `SmsBroadcastReceiver` dynamically at runtime via `registerReceiver`. The service is what keeps SMS listening alive. Started by `MainActivity` when at least one config exists, and re-started on device boot by `BootCompletedReceiver`.

- **`SmsBroadcastReceiver.onReceive`** — the core dispatch logic. Reconstructs the message from PDUs, loads all `ForwardingConfig`s, and for each config checks: sender matches (or config sender is the asterisk wildcard `*`), SMS forwarding is enabled, and SIM slot matches. SIM slot detection is heuristic (`detectSim`) because the bundle key for SIM slot is OEM-specific — it probes many known key names. Matching configs are dispatched via `callWebHook`, which enqueues a `OneTimeWorkRequest` with `NetworkType.CONNECTED` constraint and exponential backoff.

- **`RequestWorker`** (a WorkManager `Worker`) — runs the actual HTTP call off the main thread. Reads config from input `Data`, delegates to `Request`, and maps the result to `Result.success/retry/failure`. Retries stop once `getRunAttemptCount()` exceeds the config's max retries (default 10). WorkManager handles the backoff and waiting-for-network, which is how "retry with exponential backoff" is implemented.

- **`Request`** — wraps `HttpURLConnection`. Returns one of three string constants: `RESULT_SUCCESS`, `RESULT_RETRY` (non-2xx response or `IOException` → triggers a WorkManager retry), `RESULT_ERROR` (malformed URL, bad headers, SSL setup failure → permanent failure). Supports per-config custom headers (JSON object, string values only), chunked vs fixed-length streaming, and an "ignore SSL" mode (uses `TLSSocketFactory` + allow-all hostname verifier).

- **`ForwardingConfig`** — the model and persistence layer. Each forwarding rule is one `SharedPreferences` entry, stored as a JSON string keyed by a generated `timestamp_random` key. `getAll()` deserializes every entry and is the single source of truth for configs. Note the legacy-format fallback: an entry whose value does not start with `{` is treated as a bare URL string from an old app version, with defaults filled in. When adding a new config field, add a `KEY_*` constant, a getter/setter, include it in `save()`'s JSON, and add a guarded read in `getAll()` (use `json.has(...)` checks so old stored configs still load).

- **`prepareMessage`** — builds the request body by substituting placeholders into the user's template: `%from%`, `%text%`, `%sentStamp%` (SMS-reported send time), `%receivedStamp%` (device receive time), `%sim%`. `%text%` is JSON-escaped via Apache Commons Text; the others are not, so they must sit inside JSON string quotes in the template. Substitution uses `String.replaceAll`, whose replacement argument treats `$`/`\` specially, so the user-controlled values (`%from%`, `%text%`, `%sim%`) are wrapped in `Matcher.quoteReplacement` — keep that wrapping when editing, or a sender/SIM containing `$` throws `IndexOutOfBoundsException`.

- **UI** — `MainActivity` (list + permission request + Syslog dialog), `ListAdapter` (renders configs, toggles), `ForwardingConfigDialog` (add/edit form). The Syslog viewer shells out to `logcat` via `Runtime.exec` filtered to this package.

## Conventions & gotchas

- Bump `versionCode` **and** `versionName` in `app/build.gradle` for any release.
- Templates and config values are stored as raw JSON in SharedPreferences; preserve backward compatibility with old stored entries (see `getAll()` fallback above) — do not assume a field is present.
- `usesCleartextTraffic="true"` and the ignore-SSL path are intentional, to support self-hosted/local endpoints.
- The app keeps deep `minSdkVersion 14` compatibility; much code branches on `Build.VERSION.SDK_INT`. Keep new code working on old APIs or guard it.
- F-Droid distributes this app, and metadata lives in `fastlane/metadata/`.
