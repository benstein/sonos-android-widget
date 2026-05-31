# Sonos Android Widget Design

## Goal

Build a personal Android home-screen widget for Ben's Pixel 10 on Android 16+. The widget controls the Sonos system on the home Wi-Fi network without Sonos cloud login, Play Store release work, or a home-server bridge.

The first version controls a configured Sonos room, such as `Dining Room`. Sonos group behavior should make that room's current group follow the command.

## Product Scope

In scope:

- Native Android app written in Kotlin.
- Sideloadable debug APK installable with `adb`.
- One 4x1 widget optimized for Pixel Launcher.
- Responsive smaller layout that hides artwork first.
- Play/pause, previous, and next controls.
- Current track title, artist, room name, and artwork when available.
- A small settings screen to choose the target room.
- Local LAN control through Sonos UPnP/SOAP.

Out of scope:

- Sonos cloud API login.
- Remote access away from home Wi-Fi.
- Play Store packaging.
- Multi-household support.
- Volume, grouping controls, search, queues, favorites, alarms, or service browsing.

## Android App Structure

The project will be a standard Android Gradle project:

- `app`: Android application module.
- `MainActivity`: simple settings screen for room selection and basic status.
- `SonosWidgetProvider`: AppWidget provider for rendering the widget.
- `WidgetActionReceiver`: receives widget button taps.
- `WidgetRefreshWorker`: periodic refresh and manual refresh work.
- `SonosRepository`: app-facing API for discovery, state, and commands.
- `SonosClient`: HTTP/SOAP client for player services.
- `SonosDiscovery`: SSDP discovery and device description loading.
- `SonosXml`: parsing helpers for device descriptions, topology, transport state, track metadata, and artwork.

The app will use `RemoteViews` rather than Jetpack Glance. This keeps the widget dependency surface small and fits the required layout.

## Permissions

Required:

- `INTERNET` for HTTP/SOAP requests to Sonos players.
- `ACCESS_NETWORK_STATE` to detect Wi-Fi or network availability.
- `CHANGE_WIFI_MULTICAST_STATE` so SSDP discovery can acquire a multicast lock when needed.

Android 16 local network access is currently opt-in for enforcement. The app should also declare `NEARBY_WIFI_DEVICES` with `usesPermissionFlags="neverForLocation"` so it remains easy to test with Android 16 local network restrictions enabled and gives us a path for future Android versions.

## Sonos Control Model

Discovery uses SSDP to find Sonos players on the LAN. For each candidate player, the app loads the device description and extracts the room name, UUID, service URLs, and base URL.

The selected room is stored in local preferences with:

- Room name.
- Player UUID when known.
- Last known IP and port.

Before transport commands, the app resolves the selected room's current group coordinator through Sonos topology data. Commands are sent to the coordinator's `AVTransport` service:

- `GetTransportInfo` to determine play state.
- `GetPositionInfo` to fetch current track metadata.
- `GetCurrentTransportActions` to decide whether previous and next should be enabled.
- `Play`, `Pause`, `Next`, and `Previous` for controls.

If the stored IP fails, the app re-runs discovery and matches by UUID first, then room name.

## Widget Layout

Default size is 4x1. The default layout contains:

- Album artwork.
- Room name.
- Track title.
- Artist.
- Previous button.
- Play/pause button.
- Next button.

The compact layout removes artwork and keeps track text plus controls. The switch happens through Android responsive widget layouts based on available size.

Previous and next remain visible. If Sonos reports that an action is unavailable, the button is dimmed and its tap is ignored.

Artwork comes from the current track metadata when present. Relative artwork URLs are resolved against the player base URL. Missing or failed artwork uses a local fallback block.

## Refresh Behavior

The widget refreshes when:

- The widget is added or resized.
- The user taps any control.
- The user changes the configured room.
- Periodic background refresh runs.

Button taps should update optimistically where safe, then fetch current state after the command completes. Periodic refresh can be modest because this is a personal widget; a 15 to 30 minute interval is enough. Taps provide the fresh state when Ben is actively using it.

No long-running foreground service is required.

## Error Handling

Quiet failure states are preferred:

- If Sonos is unavailable, show `Sonos unavailable`, keep the room name, disable controls, and retry on the next refresh or tap.
- If Wi-Fi is off, show `Wi-Fi unavailable`.
- If no room is configured, show `Choose room` and open settings on tap.
- If discovery finds multiple rooms with the same name, prefer the stored UUID and show the duplicate only in settings.
- If a SOAP call returns an unsupported-action error, refresh transport actions and dim that button.

## Testing

Unit tests:

- Parse sample Sonos device descriptions.
- Parse topology XML and identify the group coordinator.
- Parse DIDL-Lite track metadata for title, artist, and artwork.
- Parse `GetCurrentTransportActions`.
- Build SOAP envelopes for play, pause, previous, next, and state fetches.

Integration-style tests:

- Use a fake local HTTP server to return Sonos-like XML.
- Verify command endpoints, SOAP action headers, and fallback behavior.

Manual verification:

- Install the debug APK with `adb`.
- Open the app and choose `Dining Room`.
- Add the 4x1 widget to Pixel Launcher.
- Confirm artwork, title, artist, and room render.
- Test play/pause, previous, and next with Spotify.
- Test a streaming/radio source where previous or next may be unavailable.
- Resize the widget smaller and confirm artwork hides first.

## References

- Android widget sizing: https://developer.android.com/design/ui/mobile/guides/widgets/sizing
- Android flexible widget layouts: https://developer.android.com/develop/ui/views/appwidgets/layouts
- Android local network permission: https://developer.android.com/privacy-and-security/local-network-permission
- Sonos AVTransport planning reference: https://sonos.svrooij.io/services/av-transport
- UPnP AVTransport service specification: https://upnp.org/specs/av/UPnP-av-AVTransport-v2-Service.pdf
