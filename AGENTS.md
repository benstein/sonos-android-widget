# AGENTS.md

Guidance for AI coding agents working in this repo. The human-facing overview is
in [README.md](README.md); this file is the detail an agent needs before
changing code.

## What this is

A personal, sideloaded Android widget that controls Sonos over the local Wi-Fi
network using UPnP/SOAP. No Sonos cloud, no account, no Play Store. Kotlin, with
a `RemoteViews` AppWidget.

## Build, test, run

- Build the debug APK: `./gradlew assembleDebug`
  (output: `app/build/outputs/apk/debug/app-debug.apk`)
- Install to a device/emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Run all unit tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "*SonosXmlTest"`
- Requires JDK 17. Gradle is pinned to 8.13 via the wrapper.
- Needs a `local.properties` (gitignored) with `sdk.dir=/path/to/Android/sdk`.
- Tests are JVM unit tests only; there are no instrumented/device tests.
  `app/build.gradle.kts` sets `unitTests.isReturnDefaultValues = true`, so
  Android framework calls return defaults in tests instead of throwing.

## Hard constraints — do not "fix" these

- `compileSdk`, `minSdk`, and `targetSdk` are all **36** (Android 16). Lowering
  `minSdk` breaks the local-network-permission model the app is built around.
  Leave them at 36 unless explicitly asked to change them.
- The widget UI is `RemoteViews`, **not** Jetpack Glance/Compose. This is
  deliberate to keep the dependency surface small. Don't introduce Glance or
  Compose for the widget.
- LAN-only. Do not add the Sonos cloud API, OAuth, or any off-network path.
- Dependency-light on purpose. Networking is Java `HttpURLConnection`; XML uses
  the JDK parsers. The only declared dependency is JUnit (test-only). Don't pull
  in OkHttp, Retrofit, an XML library, etc. without a real reason.

## Architecture

- `sonos/SonosDiscovery.kt` — SSDP discovery. Acquires a Wi-Fi `MulticastLock`
  (`"sonos-widget-ssdp"`) before the M-SEARCH and releases it after, then loads
  each player's device description.
- `sonos/SonosHttpClient.kt`, `SonosSoap.kt`, `SonosXml.kt` — HTTP/SOAP
  transport, SOAP envelope construction, and XML parsing (device description,
  topology, DIDL-Lite track metadata, transport actions).
- `sonos/SonosModels.kt` — the data types (`SonosPlayer`, `SonosPlayback`,
  `ZoneGroupMember`, etc.).
- `sonos/SonosRepository.kt` — the app-facing API behind the `SonosGateway`
  interface. It resolves the selected room's group **coordinator** before every
  command and gates `next`/`previous` on the reported transport actions. Returns
  `SonosResult.Available` / `SonosResult.Unavailable`.
- `storage/SonosPrefs.kt` — selected room (name, UUID, last IP/port) in
  SharedPreferences.
- `widget/WidgetRenderer.kt` + `widget/WidgetState.kt` — build the RemoteViews.
  `SonosWidgetProvider`, `WidgetActionReceiver`, and `WidgetUpdater` wire up the
  AppWidget lifecycle, taps, and pushing updated views.
- `MainActivity.kt` — settings screen for room selection.
- `SonosAppLauncher.kt` — opens the official Sonos app from the widget.

Control flow for a tap: `WidgetActionReceiver` → `SonosRepository` (resolve
coordinator, send the AVTransport command) → fetch fresh state → `WidgetUpdater`
pushes new RemoteViews.

## Conventions

- Package root: `com.superduper.sonoswidget`.
- Tests live in `app/src/test/kotlin/...` mirroring the main package. Use the
  fake `SonosGateway` pattern (see `SonosRepositoryTest`) instead of touching the
  network; parsing tests feed sample XML strings (see `SonosXmlTest`).
- Log tag is `SonosWidget`.
- Existing test classes: `BuildConfigSmokeTest`, `SonosAppLauncherTest`,
  `SonosRepositoryTest`, `SonosSoapTest`, `SonosXmlTest`, `WidgetStateTest`. Add
  tests alongside the layer you change.

## Gotchas

- Failures are quiet by design. Surface states like `Sonos unavailable`,
  `Wi-Fi unavailable`, and `Choose room` rather than crashing. Keep that.
- Group behavior matters: commands go to the coordinator so the whole group
  follows. Don't send commands straight to the selected player.
- `239.255.255.250:1900` is the standard SSDP multicast address, not a hardcoded
  device. The `RINCON_*` and `192.168.x.x` values in tests are fixtures.

## Where the design lives

- `docs/superpowers/specs/2026-05-31-sonos-android-widget-design.md` — the
  product/design spec.
- `docs/superpowers/plans/2026-05-31-sonos-android-widget.md` — the
  implementation plan.
