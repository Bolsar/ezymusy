# Ezymusy

Open-source Android app that plays the audio of YouTube links (videos, playlists, mixes) in the background, with lock-screen controls.

Built with Kotlin, Jetpack Compose, [Media3](https://developer.android.com/media/media3) and [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor). Licensed under GPLv3.

> Not on Google Play: YouTube's policies forbid background playback in third-party apps. Install from GitHub Releases (or with Obtainium).

## Build
Requirements: JDK 17, Android SDK (compileSdk 37).

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lint detekt
```

## E2E
With an emulator or device connected and [Maestro](https://docs.maestro.dev) installed:

```sh
./gradlew installDebug
maestro test .maestro/
```

## Docs
- [docs/PLAN.md](docs/PLAN.md): product decisions and milestones
- [DESIGN.md](DESIGN.md): design tokens and UI rules
