---
name: release
description: Use when cutting an Ezymusy release (version bump, tag, signed APK on GitHub Releases) or regenerating the Baseline Profile.
---

# Release — Ezymusy

## Cut a release
1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`. Commit as `build: release X.Y.Z`.
2. Check locally: `./gradlew testDebugUnitTest lint detekt assembleRelease`. Local release builds are signed
   with the debug key so they install; only CI uses the real key.
3. After merge to `main`, tag `vX.Y.Z` and push the tag. `.github/workflows/release.yml` then:
   fails unless the tag equals `versionName`, decodes the keystore, runs `assembleRelease`, and publishes
   `ezymusy-vX.Y.Z.apk` + `mapping-vX.Y.Z.txt` with `gh release create --generate-notes`.
- Secrets in the GitHub repo: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. A copy of the
  keystore is at `~/ezymusy-keys/`. Never commit `*.jks` / `*.keystore` (gitignored).
- Users update via GitHub Releases / Obtainium; there's no in-app updater. Same signing key forever, or
  updates won't install.

## Baseline Profile
- Generator: `baselineprofile/.../BaselineProfileGenerator.kt` (cold start until "Shuffle all" shows).
- Output, committed: `app/src/release/generated/baselineProfiles/baseline-prof.txt`. CI has no device, so it
  only consumes the committed file. Regenerate after big startup-path changes.
- Run: `./gradlew :app:generateReleaseBaselineProfile` with a **physical phone** attached (minSdk 28 for the
  generator; `useConnectedDevices = true`). **Never start an emulator for this**, despite the old comment in
  `baselineprofile/build.gradle.kts` mentioning the Small_Phone AVD.
- `profileinstaller` is a dependency because sideloaded installs only get AOT through it — don't remove it.

## Gotchas
- R8 strips NewPipe/Rhino/protobuf reflection targets; a release that can't add playlists means a keep rule is
  missing in `app/proguard-rules.pro` (see fix in 0.1.2). Test the release APK on a phone, not just debug.
