<h1 align="center">Krispy</h1>
<p align="center">An independent Jellyfin client for Android TV</p>

Krispy is an Android TV, Nvidia Shield, and Amazon Fire TV client for Jellyfin
servers. It is derived from
[Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) and is
distributed under GPL-2.0. Krispy is an independent project and is not
published or endorsed by the Jellyfin project.

This source starts from upstream `release-0.20.z` commit
`5b515f538f1303b823808a4ab76184cfb93e15d7`.

## Enhanced home screen

Enable **Settings > Customization > Enhanced home screen** for an artwork-led
Home layout, richer media cards, ambient library artwork, and series-level
Latest TV entries with unwatched counts. It is off by default. See
[the enhanced Home guide](docs/krispy-enhanced-home.md).

## Favorites tab

Enable **Settings > Customization > Favorites tab** to replace Search in the
top navigation with a unified Favorites view for movies, shows, episodes,
music, playlists, and available Live TV channels. This option is independent
of Enhanced home screen and is off by default.

## Enhanced detail screens

Enable **Settings > Customization > Enhanced detail screens** for larger artwork,
sharp backdrops, and compact movie and TV metadata. It is off by default and
works independently of Enhanced home screen. See
[the detail screen guide](docs/krispy-enhanced-details.md).

## Artwork editing

Administrators can open **Edit artwork** from a movie, series, season, or
episode's Other options. Krispy can search Jellyfin's image providers and
replace or delete posters, backdrops, and logos. Artwork editing works with
both classic and enhanced detail screens.

## Building

Use JDK 21, the included Gradle wrapper, and an Android SDK with the compile SDK
specified in `gradle/libs.versions.toml`.

```powershell
$env:ANDROID_HOME = 'C:\Users\daniel\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :app:assembleDebug testDebugUnitTest `
  -Pkrispy.version=0.3.0 `
  -Pkrispy.version.code=10008 `
  --no-daemon --console=plain
```

The debug APK is written under `app/build/outputs/apk/debug` with application
ID `io.github.crunchyinmilk.krispy.debug`.

Release builds require explicit version inputs and the permanent Krispy signing
key. Keep every signing secret outside the repository. See
[the update and release contract](docs/krispy-updates.md) for the required
properties and GitHub Release assets.

## Upstream and license

Krispy keeps required Jellyfin attribution, library names, protocol terms, and
the upstream Kotlin namespace. Changes should be rebased or ported only from
the verified `release-0.20.z` lineage unless the project deliberately adopts a
new upstream release base.

Source is licensed under [GPL-2.0](LICENSE).
