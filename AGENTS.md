# AGENTS.md

Guidance for AI coding agents working in this repo. The human-facing overview is
in [README.md](README.md); this file is the detail an agent needs before
changing code.

## What this is

A personal, sideloaded Android project that controls Sonos over the local Wi-Fi
network using UPnP/SOAP. No Sonos cloud, no account, no Play Store. Kotlin. Three
Gradle modules:

- `:app` — the phone app: a `RemoteViews` AppWidget plus a voice-announce feature
  (`TalkActivity`) that speaks an arbitrary phrase on every Sonos speaker.
- `:sonos-core` — the reusable Sonos transport (discovery, SOAP, XML, the announce
  primitive). Android library, mostly JVM-testable. Used by `:app`.
- `:wear` — a Wear OS remote that captures speech on the watch and forwards the
  text to the phone over the Data Layer; the phone does all the Sonos work.

## Build, test, run

- Build the phone APK: `./gradlew :app:assembleDebug`
  (output: `app/build/outputs/apk/debug/app-debug.apk`)
- Build the watch APK: `./gradlew :wear:assembleDebug`
  (output: `wear/build/outputs/apk/debug/wear-debug.apk`)
- Install the phone app: `adb -s <phone> install -r app/build/outputs/apk/debug/app-debug.apk`
- Install the watch app: `adb -s <watch> install -r wear/build/outputs/apk/debug/wear-debug.apk`
  (the watch and phone apps share `applicationId` so the Data Layer pairs them; both
  must be signed with the same key — debug keystore handles this automatically)
- Run all unit tests: `./gradlew test` (logic lives in `:sonos-core`)
- Run a single test class: `./gradlew :sonos-core:test --tests "*SonosXmlTest"`
- Drive an announcement without the mic (for testing the pipeline):
  `adb shell "am start -n com.superduper.sonoswidget/.announce.TalkActivity --es announce_now 'bring me a beer'"`
  (the watch has the same hook via `.wear.WearTalkActivity --es send_now '...'`)
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
- LAN-only. Do not add the Sonos cloud API, OAuth, or any off-network path. STT
  uses the system speech recognizer and TTS is on-device; the announcement clip is
  served from a phone-hosted HTTP server on the LAN. Sonos is never reached off-network.
- Dependency-light on purpose. Networking is Java `HttpURLConnection`; XML uses the
  JDK parsers; the clip server is a hand-rolled `ServerSocket`. `:sonos-core` has no
  runtime dependencies. The one exception is `play-services-wearable` in `:app` and
  `:wear`, required for watch<->phone messaging. Don't pull in OkHttp, Retrofit, an
  XML library, Compose, etc. without a real reason.

## Architecture

`:sonos-core` (`sonos-core/src/main/java/com/superduper/sonoswidget/sonos/`):

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
- `sonos/SonosAnnouncer.kt` — the whole-house announce primitive. `SonosAnnouncer`
  plays a clip on every group coordinator and restores each one's volume, URI,
  queue position, and play state, behind the `AnnounceTransport` seam.
  `NetworkAnnounceTransport` is the real UPnP impl (adds `RenderingControl` volume
  and the extra AVTransport calls). `SonosGateway` was intentionally left unchanged
  so the widget's fakes/tests still hold; announce SOAP lives here instead.

`:app` voice-announce (`app/src/main/java/com/superduper/sonoswidget/announce/`):

- `TalkActivity.kt` — push-to-talk screen: speech → 3s cancel window → `AnnounceService`.
  Reached from MainActivity's "Talk to speakers" button and the `announce_now`
  deep-link. Deliberately NOT a launcher activity — a second launcher icon made the
  package's launcher target ambiguous, so the app ships one icon ("Sonos Remote",
  `app_name`) for both the widget config and push-to-talk.
- `WavTextToSpeech.kt` — on-device TTS → WAV plus playback duration from the header.
- `ClipServer.kt` — hand-rolled `ServerSocket` HTTP server; serves the WAV to all
  speakers concurrently (handles Range), reachable at the phone's Wi-Fi IP (`LocalAddress.kt`).
- `Announcer.kt` — ties synth → serve → `SonosAnnouncer` together (blocking; on a worker).
- `AnnounceService.kt` — short foreground service used by the phone-initiated path.
- `AnnounceMessageListener.kt` — `WearableListenerService` for the watch path; runs
  `Announcer` synchronously under a wakelock (no FGS, since it runs in the background).

`:wear` (`wear/src/main/java/com/superduper/sonoswidget/wear/`):

- `WearTalkActivity.kt` — watch remote: speech → `MessageClient` send on `/announce`.

- `storage/SonosPrefs.kt` (in `:app`) — selected room (name, UUID, last IP/port) in
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

- Package root: `com.superduper.sonoswidget` (with `.sonos`, `.announce`, `.wear`
  sub-packages). The `:wear` module's `namespace` is `.wear` but its `applicationId`
  is the shared `com.superduper.sonoswidget` — don't change that, it's what pairs the
  Data Layer.
- Sonos-logic tests live in `sonos-core/src/test/kotlin/...`; widget/app tests in
  `app/src/test/kotlin/...`. Use the fake `SonosGateway` / `AnnounceTransport` /
  `SonosHttpClient` pattern instead of touching the network; parsing tests feed
  sample XML strings.
- Log tag is `SonosWidget` across all modules.
- Test classes in `:sonos-core`: `SonosRepositoryTest`, `SonosSoapTest`,
  `SonosXmlTest`, `SonosAnnouncerTest`, `SonosSoapAnnounceTest`. In `:app`:
  `BuildConfigSmokeTest`, `SonosAppLauncherTest`, `WidgetStateTest`. Add tests
  alongside the layer you change.

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
