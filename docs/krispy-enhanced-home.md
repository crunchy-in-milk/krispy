# Krispy enhanced Home

Krispy is based on upstream Jellyfin Android TV `release-0.20.z` at
`5b515f538f1303b823808a4ab76184cfb93e15d7`.

Enable **Enhanced home screen** under **Settings → Customization**. The
`krispy_enhanced_home_screen` preference defaults to off and changes take
effect while Home is visible.

## Behavior

- Classic Home retains upstream background selection, row loading, spacing,
  pagination, and focus navigation.
- Enhanced Home adds a non-focusable artwork and details header above the
  existing rows.
- Item backdrops are tried before parent backdrops. Failed candidates are
  skipped and an empty result uses the themed background.
- Artwork loading waits for 200 ms of stable focus. Focus and pagination remain
  immediate, and stale work is cancelled during navigation.
- The previous artwork and metadata remain together until their replacement is
  ready.
- The Home ViewModel suppresses the global backdrop only while enhanced Home is
  active and restores normal behavior when the feature is disabled or Home is
  left.
- Existing Coil caching is reused and requests are bounded to the display size.

PR #5632 was used only as a design reference. Its commits were not
cherry-picked. The current upstream preference system, row presenters,
background service, and Home tree remain in place.

## Verification

The unit tests cover classic-mode isolation, initial enabled state, live
toggles, debounce, retaining the displayed hero while loading, cancellation,
inactive Home preference changes, and parent-art fallback.

Device acceptance should cover rapid D-pad navigation, 720p/1080p/4K layouts,
font scaling, missing artwork, settings overlays, leaving and returning to
Home, and enabling or disabling the feature while Home is visible.
