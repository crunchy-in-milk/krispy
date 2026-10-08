# Krispy enhanced Home

Krispy is based on upstream Jellyfin Android TV `release-0.20.z` at
`5b515f538f1303b823808a4ab76184cfb93e15d7`.

Enable **Enhanced home screen** under **Settings → Customization**. The
`krispy_enhanced_home_screen` preference defaults to off and changes take
effect while Home is visible.

## Behavior

- Classic Home retains upstream background selection, row loading, spacing,
  pagination, and focus navigation.
- Enhanced Home adds a non-focusable artwork and details header above regular
  media rows.
- Artwork sits at the upper right behind the existing controls, with a dark
  description area fading into the image and a lower fade behind the posters.
- Enhanced Home uses Latest TV Shows and Latest Movies headings, left-aligned
  poster captions, and year/rating subtitles. Other libraries retain their names.
- Latest TV episodes are represented by their series, deduplicated in latest
  order, with the series unwatched count and series navigation.
- The Media row uses wide artwork bands with library-aware fallback icons.
  Focusing a library replaces the normal details header with subtle blurred
  ambient artwork while keeping the background dark and readable.
- Focused posters use the existing blue accent for their border.
- The header shows the series year range, network or studio, runtime, and rating
  when Jellyfin provides them. Continuing series use Present as the end year.
  Missing metadata is omitted.
- Toggling the option reloads the Home rows so their styling and headings update
  together with the header.
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
Metadata tests cover year ranges, missing years and ratings, studio fallback,
poster subtitle formatting, ambient library detection, and Latest TV series
promotion. Navigation tests cover repeated Back events without removing the
root Settings entry.

Device acceptance should cover rapid D-pad navigation, 720p/1080p/4K layouts,
font scaling, missing artwork, settings overlays, leaving and returning to
Home, and enabling or disabling the feature while Home is visible.
