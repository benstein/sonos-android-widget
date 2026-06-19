# David's Song + Broadcast Speakers — Design

Date: 2026-06-19
Status: Approved, building on branch `davids-song`

## Goal

A one-tap button that plays a specific song (a Sonos Favorite, default "David's
Song") on a configurable set of speakers, and a shared **broadcast speakers**
setting that both the push-to-talk announcement and the song play target. All
LAN-only over UPnP/SOAP; no cloud.

## Decisions (from brainstorming)

- **Track reference:** a **Sonos Favorite**, matched by name. Robust — the
  favorite stores the exact account-correct Spotify URI + metadata. The user adds
  it once in the Sonos app.
- **Target speakers:** a persisted **multi-select list of rooms** (default: all).
  Both push-to-talk and the song play on the selected rooms. Separate from the
  widget's single "selected room".
- **Song grouping:** the song explicitly groups the selected rooms under one lead
  and plays, leaving them grouped (no restore — the song should keep playing).
- **Broadcast targeting:** the announcement plays on the group coordinators that
  serve the selected rooms (snapshot/restore unchanged). Caveat: if a selected
  room is grouped with a non-selected room at that moment, the non-selected room
  also hears the clip. Accepted for v1 (no re-grouping for a short announcement).
- **QR / deep link:** deferred. The trigger flows through a service with a
  favorite name, so a `sonosremote://play?favorite=…` deep link is a thin add later.
- **Button placement:** a full-width pill below the volume slider on the Talk screen.

## Components

### `:sonos-core`

- `SonosServices.contentDirectoryControlUrl` parsed from the device description
  (additive, like `renderingControlControlUrl`).
- **Browse favorites:** `SonosSoap` gains a ContentDirectory `Browse` envelope
  (`ObjectID=FV:2`, `BrowseDirectChildren`). `SonosXml.parseFavorites(resultDidl)`
  returns `SonosFavorite(title, uri, metadata)` per item, reading `<dc:title>`,
  `<res>`, and `<r:resMD>`. Reuses the namespace-stripping parser and the
  escape/unescape handling already used for restore.
- **Grouping:** join a player to a lead with
  `SetAVTransportURI(player, "x-rincon:RINCON_<leadUuid>")`.
- **Target-room resolution:** map a set of room names to their current group
  coordinators (empty set = all). Used by both features.
- **`SonosFavoritePlayer`** orchestration behind a `FavoritePlayTransport` seam
  (parallels `AnnounceTransport`): resolve target rooms → pick a lead → browse
  favorites → match by name (case-insensitive, trimmed) → group the target rooms
  to the lead → `SetAVTransportURI(uri, metadata)` + `Play`. `NetworkFavoritePlayer`
  is the real impl; a fake powers tests. Returns an outcome (favorite, speaker count)
  or a not-found result.
- **Announce targeting:** `SonosAnnouncer.announce(...)` takes `targetRooms`;
  `NetworkAnnounceTransport.coordinatorsFor(targetRooms)` filters to coordinators
  serving those rooms (empty = all).

### `:app`

- `SonosPrefs`: `broadcastRooms: Set<String>` (room names, empty = all) and
  `quickPlayFavorite: String` (default "David's Song").
- `QuickPlayService` — short foreground service (mirrors `AnnounceService`) that
  runs `SonosFavoritePlayer` on a worker; notification "Playing <favorite>".
- Talk screen: a **David's Song pill** below the volume slider (`ic_music_note`),
  label = `quickPlayFavorite`. Tap → `QuickPlayService`. The push-to-talk path now
  passes `broadcastRooms` to `AnnounceService`.
- **Settings** screen (the former "Widget Setup", reached via the gear): a
  **Broadcast speakers** room checklist (multi-select, persisted), a **David's
  Song** favorite-name field, plus the existing widget-room selection and Open Sonos.

## Error handling (quiet, app-style)

- Favorite not found → "Couldn't find favorite '<name>'".
- No speakers / no selected rooms online → "No Sonos speakers found".
- Network failure → soft toast.

## Testing

`:sonos-core` unit tests with fakes: favorites DIDL parsing from sample `FV:2`
XML; the Browse envelope; `SonosFavoritePlayer` orchestration (browses, matches
by name, groups all target rooms to the lead, plays uri+metadata; favorite-not-found
path); announce coordinator filtering by target rooms (subset and empty=all).
On-device verification on the user's Sonos when back on LAN.
