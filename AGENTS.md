# Project Memory — Ezymusy

Ezymusy is an open-source (GPLv3) Android app (Kotlin, Jetpack Compose, Media3, NewPipeExtractor) that plays the
audio of YouTube links in the background. Not on Google Play by design; ships as signed APKs on GitHub Releases.

`CLAUDE.md` is a symlink to this file — edit `AGENTS.md`.

## Repo layout
```
app/src/main/java/com/ezymusy/app/
  App.kt          App + AppContainer (manual DI: OkHttp, YouTube, Repository, audio SimpleCache, AutoConnect)
  MainActivity.kt NavHost, MediaController, share intent, testTagsAsResourceId for Maestro
  core/data/      Db.kt: Room entities, LibraryDao, migrations, Repository (all in one file)
  core/youtube/   YouTube (NewPipe wrapper + stream-URL cache), YouTubeLink.parse
  core/playback/  PlaybackService (MediaSessionService + ExoPlayer, skip-broken, resolve-ahead, mix refill)
  core/bluetooth/ AutoConnect
  core/designsystem/ Theme.kt tokens (colors, type, Dimens), shared widgets
  feature/library/ feature/player/   screens + ViewModels
app/schemas/       Room schema JSONs (exported; commit new versions with a migration)
app/src/release/generated/baselineProfiles/  generated Baseline Profile, committed — regenerate, never hand-edit
baselineprofile/   com.android.test module that generates it (see .agents/skills/release)
.maestro/          Maestro E2E flows, one per milestone
config/detekt.yml  detekt config (defaults + io.nlopez compose rules)
```
- Read `DESIGN.md` before any UI change. `docs/PLAN.md` holds product decisions but is gitignored (local only);
  its emulator/AVD instructions are obsolete — physical phone only.
- Don't touch: `build/`, `*/build/`, `.gradle/`, `.kotlin/`, `.idea/`, `local.properties`,
  `.mobile-agent-proof/` (gitignored run logs), `third_party/` (vendored Inter font licence).

## Terminology
- **Link** — one pasted/shared YouTube URL, a `LinkEntity` keyed by unique `sourceId`. Types: VIDEO, SHORTS,
  PLAYLIST, MIX.
- **Videos / Shorts group** — single videos are not their own Links; each joins the one VIDEO or SHORTS Link
  (`sourceId` `"videos"` / `"shorts"`).
- **Mix** — a `list=RD…` URL. Live: never stored as tracks; pages are fetched on demand and appended near the
  end of the queue. `RDCLAK…` lists are treated as playlists (album playlists (?)).
- **Track** — one video row of a Link (`TrackEntity`); `unavailable` = YouTube won't serve it (greyed, skipped).
- **Shuffle all** — every playable track of Links with `includeInShuffle`, plus the first page of each included
  Mix, deduped by videoId.
- **Strikes / extractor outdated** — 3 failed tracks in a row (`MAX_STRIKES`) flip `extractorOutdated`: YouTube
  likely changed and NewPipeExtractor needs a bump.
- **`yt://<videoId>`** — MediaItem URI; the real stream URL is resolved when ExoPlayer opens it.
- **Resolve-ahead** — resolving the next items' stream URLs while the current one plays.
- **Sync** — on launch, playlists last synced >6 h ago are refetched (`Repository.syncStale`).
- **Auto-connect** — optional: connect a chosen paired Bluetooth device and Shuffle all on app open (?).
- **M1–M5** — the original milestones; Maestro flow names use them.

## Run & test
Needs JDK 17, Android SDK (compileSdk 37) and `local.properties` with `sdk.dir=…`.
- Install: nothing beyond the SDK; Gradle fetches deps (NewPipeExtractor from JitPack).
- Run locally: `./gradlew installDebug` — physical phone over adb only (**never use an emulator**).
- Unit tests: `./gradlew testDebugUnitTest`; one class:
  `./gradlew testDebugUnitTest --tests 'com.ezymusy.app.core.youtube.YouTubeLinkTest'`.
- Lint/static analysis: `./gradlew lint detekt` (lint has `warningsAsErrors`, so any new warning fails).
- Before finishing (= CI): `./gradlew testDebugUnitTest lint detekt assembleRelease`.
- E2E: Maestro flows in `.maestro/` — see `.agents/skills/e2e-testing`.
- Releases and Baseline Profile: see `.agents/skills/release`.

Unit tests are plain JVM JUnit 4 on pure functions (`parse`, `classify`, `bestAudio`, …); no Robolectric, no
androidTest in `app`. Release builds run R8 + resource shrinking; NewPipe/Rhino/protobuf keep rules live in
`app/proguard-rules.pro` — a broken release usually means a missing keep rule there.

## Conventions
- Feature UI uses `MaterialTheme` roles and `Dimens` only: no `Color(0x…)`, raw `.dp` or hardcoded strings
  outside `core/designsystem/` (strings go in `res/values/strings.xml`).
- Every screen ships loading, empty, error and offline states. Copy is specific ("Play link", not "Submit").
- Interactive composables get a `testTag` (snake_case, e.g. `add_link`, `track_row`) — Maestro selects by id.
- Room: never store stream URLs. Schema change = bump version, add an AutoMigration or `Migration`, commit the
  new `app/schemas/…/N.json`.
- Catch `CancellationException` and rethrow before catching `Exception`; one failing item (playlist, mix) must
  not stop the rest.
- Library version bumps are deliberate (lint's version checks are disabled).
- Mark deliberate shortcuts with `// ponytail:` naming the ceiling and the upgrade path.
- Commits: Conventional Commits (`feat(library): …`, `fix(release): …`, `build: release 0.3.0`).

## Lessons learned
When the user corrects a mistake, add a one-line entry to Lessons learned. When a lesson recurs, promote it
to Conventions. Remove lessons that no longer apply. Format: `- YYYY-MM-DD <mistake> → <what to do instead>`.
