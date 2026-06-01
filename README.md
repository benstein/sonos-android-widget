# Sonos Android Widget

A home-screen widget for Android that controls a Sonos system over the local
Wi-Fi network. It talks to Sonos players directly with UPnP/SOAP, so there is no
Sonos cloud login, no account, and nothing leaves the LAN.

This is a personal, sideloaded project rather than a Play Store app.

## What it does

- Play/pause, previous, and next for a chosen Sonos room.
- Shows the current track title, artist, room name, and album artwork.
- Settings screen to pick the target room from players found on the network.
- Commands follow Sonos grouping: they go to the room's current group
  coordinator, so the whole group responds.
- Responsive widget layout. The 4x1 layout shows artwork; the compact layout
  drops artwork first and keeps the track text and controls.

Tapping a control updates the widget optimistically and then refreshes from the
player. A modest periodic background refresh keeps the display roughly current
between taps.

## How it works

Discovery uses SSDP to find Sonos players on the LAN. For each player the app
loads the device description to get the room name, UUID, service URLs, and base
URL. The selected room is stored locally (name, UUID, last known IP/port). If
the stored IP stops responding, discovery re-runs and re-matches by UUID, then
by room name.

Transport control uses the player `AVTransport` service: `GetTransportInfo`,
`GetPositionInfo`, and `GetCurrentTransportActions` for state, and `Play`,
`Pause`, `Next`, `Previous` for commands. When Sonos reports an action is
unavailable (for example, skipping on a radio stream), the button is dimmed and
its tap is ignored.

The widget is built with `RemoteViews` rather than Jetpack Glance to keep the
dependency surface small.

## Requirements

- Android 16 (API 36). `compileSdk`, `minSdk`, and `targetSdk` are all 36.
- JDK 17.
- The Android SDK installed locally. Create a `local.properties` file pointing
  at it (this file is gitignored):

  ```properties
  sdk.dir=/path/to/Android/sdk
  ```

## Build and install

```bash
# Build the debug APK
./gradlew assembleDebug

# Install to a connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open the app, choose a room, and add the "Sonos Controller" widget from the
launcher's widget picker.

## Tests

```bash
./gradlew test
```

Unit tests cover the Sonos parsing and SOAP layers: device descriptions,
topology and group-coordinator selection, DIDL-Lite track metadata, transport
actions, and SOAP envelope construction. Repository tests use fake clients to
verify command routing and fallback behavior.

## Permissions

- `INTERNET` for HTTP/SOAP requests to players.
- `ACCESS_NETWORK_STATE` to detect network availability.
- `CHANGE_WIFI_MULTICAST_STATE` for the multicast lock used during SSDP
  discovery.
- `NEARBY_WIFI_DEVICES` (`neverForLocation`) for Android local network access.

## Out of scope

Sonos cloud API, remote access away from home Wi-Fi, Play Store packaging,
multi-household support, and volume/grouping/search/queue/favorites controls.

## Project layout

```
app/src/main/java/com/superduper/sonoswidget/
  MainActivity.kt            Settings / room selection
  SonosWidgetProvider.kt     AppWidget provider
  WidgetActionReceiver.kt    Handles widget button taps
  WidgetUpdater.kt           Builds and pushes widget state
  SonosAppLauncher.kt        Opens the official Sonos app
  sonos/                     Discovery, HTTP/SOAP client, XML parsing, repository
  storage/                   Local preferences
  widget/                    RemoteViews rendering and widget state
```

Design notes and the implementation plan live under `docs/`.
