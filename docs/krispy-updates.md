# Krispy identity and GitHub updates

Krispy is based on upstream Jellyfin Android TV `release-0.20.z` at
`5b515f538f1303b823808a4ab76184cfb93e15d7`.

## App identity

- Release application ID: `io.github.crunchyinmilk.krispy`
- Debug application ID: `io.github.crunchyinmilk.krispy.debug`
- Display names: **Krispy** and **Krispy (debug)**
- SDK client name: **Krispy**
- Release assets: `krispy-v<version>.apk`
- Update metadata: `krispy-update.json`

The Kotlin namespace remains `org.jellyfin.androidtv` because the application
is derived from Jellyfin Android TV. Upstream attribution, dependencies, and
single-"n" Jellyfin identifiers are retained where technically or legally
required.

The launcher icon and TV banner use Krispy artwork. Provider authorities,
preference backup paths, update storage, and FileProvider paths use the Krispy
application identity.

## Update behavior

Open **Settings → Updates** to check, download, cancel, and install an update.
Settings checks asynchronously and shows an available release in the Updates
button's caption. Home does not display update notices. Automatic checks are
limited to once every 12 hours; manual checks bypass that interval. Network
failures do not block startup or playback.

The app reads the latest full release from:

`https://api.github.com/repos/crunchy-in-milk/krispy/releases/latest`

Each release must include exactly one signed APK and one
`krispy-update.json` file. Example metadata:

```json
{
  "versionCode": 10000,
  "versionName": "0.1.0",
  "apkAsset": "krispy-v0.1.0.apk",
  "sha256": "<64 hexadecimal characters from the built APK>"
}
```

The updater compares numeric `versionCode` values. It verifies the configured
repository and HTTPS asset location, response size limits, exact APK length,
SHA-256, application ID, Android compatibility, version name/code, and signing
certificate before offering installation. Android still requires the user to
approve the install.

## Build and signing

Local version inputs are `krispy.version` and `krispy.version.code`.
The public 0.2.0 release uses version code `10006`, above the `10005` used for
0.1.4-dev previews, so those installations can receive the official update.
Release builds require both:

```powershell
$env:ANDROID_HOME = 'C:\Users\daniel\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Set-Location 'C:\Users\daniel\Documents\Coding\Android Apps\krispy'
.\gradlew.bat :app:assembleRelease testDebugUnitTest -Pkrispy.version=0.2.0 -Pkrispy.version.code=10006 --no-daemon --console=plain
```

Release signing uses:

- `krispy.keystore.file` / `KRISPY_KEYSTORE_FILE`
- `krispy.keystore.password` / `KRISPY_KEYSTORE_PASSWORD`
- `krispy.signing.key.alias` / `KRISPY_SIGNING_KEY_ALIAS`
- `krispy.signing.key.password` / `KRISPY_SIGNING_KEY_PASSWORD`

For automatic local signing, copy `release-signing.properties.example` to
`release-signing.properties` in the project root and fill in the existing key's
passwords locally. Gradle reads this file on each build, so subsequent releases
need no password prompt. The real file is ignored by Git and must never be
committed or uploaded. It contains plaintext passwords; keep it private on this
computer. Java properties syntax requires escaping backslashes in passwords.
Use forward slashes for Windows paths, as shown in the example. Explicit Gradle
properties and environment variables take precedence over this file.

Release builds fail before compilation if signing settings are missing or the
keystore file does not exist. Debug builds continue to use their automatic
development key.

Keep the permanent Krispy key and its secure backup. Changing the application
ID or signing key after release prevents ordinary in-place updates.

The GitHub repository, release, tag, APK, metadata, and workflow are external
publication actions and require their separately approved phase.
