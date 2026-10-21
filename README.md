# PocketFOSS

A lightweight, F-Droid-compatible app store client for Android.

Classic Holo interface, no AndroidX, no Material, no Compose: just the Android framework APIs.
The APK stays well under 1 MB.

## Features

- Browse, search, install and update apps from F-Droid-style repositories (index v2)
- **Repository management**: add, enable/disable, sync and delete repositories;
  `fdroidrepos://` links open the add dialog pre-filled
- **Verified indexes**: `entry.jar` signature checked against a pinned fingerprint,
  index SHA-256 checked against the signed `entry.json`, rollback protection
- **Device-aware**: versions that do not fit your API level or ABI are skipped
- **Anti-Features**: shown on each app page; hide whole kinds from the Available list
  (Tracking is hidden by default, like F-Droid)
- **About** tab with libraries, license and credits
- SHA-256 verification of every downloaded APK; installation through `PackageInstaller`

## Requirements

- Android 5.0+ (`minSdk 21`), built against API 35
- Android 8.0+ asks you to allow installs from PocketFOSS the first time

## Adding a repository

Open the menu, choose **Repositories**, then **Add repository**. You need:

1. the repository address (`https://example.org/fdroid/repo`)
2. the SHA-256 fingerprint of its signing key (64 hex characters, colons allowed)

Both can be pasted as one link: `https://example.org/repo?fingerprint=AB:CD:...`
(also `fdroidrepos://...`). Plain `http://` and repositories without an `entry.jar` (index v1 only)
are not supported. The repository is synced immediately and removed again if verification fails.

## Security model

```
pinned fingerprint -> entry.jar signature -> entry.json -> index SHA-256 -> index-v2.json -> APK SHA-256
```

- No trust-on-first-use: a repository without a fingerprint cannot be added.
- An index older than the last one seen for a repository is rejected.
- APK signatures are not compared with repository metadata; Android itself enforces signature
  continuity when updating an installed app.

## Build

```
gradle wrapper        # once, generates the Gradle wrapper
./gradlew assembleDebug
```

Requires JDK 17. `gradle.properties` pins `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk`;
change or remove that line if your JDK lives elsewhere.

The theme opts out of Android 15's forced edge-to-edge layout (`windowOptOutEdgeToEdgeEnforcement`),
which works while `targetSdk` is 35. Raising it to 36 needs real window-inset handling.

## Releasing

Pushing a tag that starts with `v` runs `.github/workflows/release.yml`:

```
# 1. set versionName (and bump versionCode) in app/build.gradle.kts, e.g. 0.2.0
# 2. commit, then:
git tag v0.2.0
git push origin v0.2.0
```

Before publishing, the workflow checks that the tag equals `v` + `versionName`, then builds the release
APK. It attaches `PocketFOSS-v0.2.0.apk` and a `.sha256` file to a new GitHub release with generated
notes. Tags containing `-` (for example `v0.3.0-rc1`) are marked as pre-releases.

### Signing

Add these repository secrets (Settings, Secrets and variables, Actions):

| Secret              | Value                                   |
|---------------------|-----------------------------------------|
| `KEYSTORE_BASE64`   | the keystore file, base64-encoded       |
| `KEYSTORE_PASSWORD` | keystore password                       |
| `KEY_ALIAS`         | key alias                               |
| `KEY_PASSWORD`      | key password                            |

```
keytool -genkeypair -v -keystore release.jks -alias pocketfoss \
        -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks      # paste the output into KEYSTORE_BASE64
```

Without `KEYSTORE_BASE64` the workflow still runs but publishes `PocketFOSS-<tag>-unsigned.apk`,
which Android will not install. Keep the keystore safe: updates must be signed with the same key.

## Project layout

```
app/src/main/java/org/user14923929/pocketfoss/
  data/     index parser, models, repository input parsing, About data
  net/      HTTP, entry.jar verification, repository sync
  install/  PackageInstaller session and result receiver
  store/    SQLite storage (repositories and apps)
  ui/       main screen with tabs, app details, repositories, anti-features
  util/     anti-feature definitions, settings, installed-version helper
```

## Status

Early version. Not done yet: app icons and screenshots, background update checks,
several versions per app, index v1 repositories.

## License

GPL-3.0
