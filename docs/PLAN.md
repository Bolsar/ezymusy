# Ezymusy — YouTube-link audio streaming player (Android)

## Context
User wants Android app (Kotlin + Jetpack Compose) where user adds YouTube links (single video, playlist, mix) and app streams **audio only**, with **background playback**, shuffle across all links, lock-screen controls. Repo empty (only README.md).

Background play of YouTube content is forbidden by YouTube API Developer Policies and enforced server-side since Jan 2026, so Play Store route impossible. Decision: **open-source extractor app, NewPipe-style** — GPLv3, public GitHub, distributed via F-Droid/IzzyOnDroid + GitHub Releases (Obtainium). Trust via open source + verified developer registration.

## Settled decisions (grilling)
| # | Decision |
|---|---|
| Distribution | Open source GPLv3, F-Droid/IzzyOnDroid + GitHub Releases, no in-app updater |
| Extractor | NewPipeExtractor (JitPack), behind one small resolver object |
| Mixes (`list=RD…`) | Live: fetch pages on demand, never stored as tracks |
| Storage | Room: links + expanded track metadata (videoId, title, duration, thumb). Never stream URLs |
| Input | Paste field + Android share intent (`ACTION_SEND text/plain`) |
| Offline | None. Online streaming only |
| Shuffle all | Flat shuffle over all tracks of links with `includeInShuffle=true`; each mix adds its first page |
| Continue after kill | No. Start fresh |
| Controls | Play/pause, next/prev, seek, shuffle, repeat off/one/all, notification + lock screen + Bluetooth via `MediaSessionService` |
| Broken track | Mark `unavailable`, skip to next; snackbar after 3 consecutive failures ("extractor may be outdated, update app") |
| Re-sync | Auto on app launch, playlists only, throttled to once per 6h per link |
| SDK | minSdk 26, targetSdk 36, core library desugaring on |
| Structure | Single `app` module, manual DI (`AppContainer`), ViewModel + StateFlow |
| Screens | Library, Link detail, Now Playing (+ mini player bar) |
| Identity | package `com.ezymusy.app`, name "Ezymusy" |

## Stack
Compose BOM + Material 3, Navigation Compose, Media3 (`exoplayer`, `session`), Room (KSP), Coil (thumbnails), OkHttp (NewPipe `Downloader`), `com.github.TeamNewPipe:NewPipeExtractor` via JitPack, `desugar_jdk_libs`. Pin latest versions at implementation time.

## Files (all new)
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, Gradle wrapper, `LICENSE` (GPLv3)
- `app/src/main/AndroidManifest.xml` — INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS; `PlaybackService` with `foregroundServiceType="mediaPlayback"` + `MediaSessionService` intent filter; MainActivity share intent-filter
- `App.kt` — `Application`, holds `AppContainer` (db, repository, resolver); calls `NewPipe.init(OkHttpDownloader)`
- `data/Db.kt` — Room DB, `LinkEntity(id, url, type VIDEO|PLAYLIST|MIX, title, thumb, includeInShuffle, lastSyncedAt)`, `TrackEntity(id, linkId FK cascade, videoId, title, durationSec, thumb, position, unavailable)`, DAO
- `data/YouTube.kt` — NewPipe wrapper:
  - `parse(url)`: link type via `ServiceList.YouTube.getLinkTypeByUrl`, mix = `list` param starts with `RD`
  - `fetchTracks(url)`: `PlaylistInfo.getInfo` + `getMoreItems` loop (playlist), `StreamInfo` (video)
  - `fetchMixPage(url, page)`: one page for live mixes
  - `resolveAudioUrl(videoId)`: `StreamInfo.getInfo` → highest `averageBitrate` audio stream; in-memory cache ~5h
  - `OkHttpDownloader` (NewPipe `Downloader` impl, ~30 lines)
- `data/Repository.kt` — addLink, deleteLink, toggleShuffle, syncPlaylists (throttled), shuffleAllQueue, markUnavailable
- `playback/PlaybackService.kt` — `MediaSessionService` + ExoPlayer with `AudioAttributes(C.USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC)`, `handleAudioBecomingNoisy`, `WAKE_MODE_NETWORK`. MediaItems use URI `yt://<videoId>`; `ResolvingDataSource.Factory` maps to real stream URL at load time (ExoPlayer preloads next item, so no gap). Player.Listener: `onPlayerError` → mark unavailable, skip, count failures; near end of mix queue → append next mix page
- `ui/MainActivity.kt` — NavHost, `MediaController` connection, handles share intent, requests POST_NOTIFICATIONS
- `ui/LibraryScreen.kt`, `ui/LinkDetailScreen.kt`, `ui/NowPlayingScreen.kt` (+ `MiniPlayer`), `ui/PlayerViewModel.kt`, `ui/LibraryViewModel.kt`

## Flow
1. User pastes/shares URL → `parse` → insert Link → for VIDEO/PLAYLIST fetch & store tracks (progress shown); MIX stores link only.
2. Tap track / "Shuffle all" → build `List<MediaItem>` (`yt://id` + metadata) → `MediaController.setMediaItems` → play.
3. ExoPlayer opens `yt://id` → resolver fetches audio URL → streams. Service keeps playing in background with media notification.
4. Launch → `syncPlaylists()` for playlist links older than 6h.

## Smoothness pipeline (accepted)
1. Warm-up at launch: `NewPipe.init` + speculatively resolve first track of likely queue.
2. Resolve-ahead: on track i start, resolve i+1, i+2 (max 2 concurrent), in-memory cache keyed by videoId, TTL from URL `expire` param.
3. Media3 `PreloadConfiguration` (~10s of next item) → gapless, instant Next.
4. `DefaultLoadControl`: bufferForPlaybackMs ~500, max buffer ~3 min.
5. 403 recovery: drop cache entry, re-resolve, resume same position.
6. Format: Opus itag 251 → m4a itag 140 fallback. Always best quality (Q25 A).
7. Shuffle: ExoPlayer `DefaultShuffleOrder` (Fisher-Yates).
8. UI: Room Flow + LazyColumn keys, Coil cache, Baseline Profile, R8 release.
Targets: first audio <1s warm / <2.5s cold, 0 gap transitions, Next <300ms. Measure in M4.

## Milestones (accepted, one PR each)
M1 single video → background play + lock-screen controls · M2 playlists + Room + Library/Detail + share intent · M3 shuffle-all + live mixes + auto re-sync + skip broken · M4 smoothness pipeline + full Now Playing (big artwork, blurred bg, Palette colors) · M5 CI (push: build/lint/test; tag: signed APK → GitHub Releases) + Baseline Profile.

## Workflow
- Agent: Bolsar/mobileDev plugin (`mobile-dev`, `ui-anti-slop`, `ship-change`, `code-review`, `design-system-setup`, `motion-and-feedback`).
- Claude does everything per milestone: branch → code → verify → commit → push → PR → fresh-subagent review → merge.
- Anti-slop: ui-anti-slop rules + tokens-only theme + detekt with mrmans0n compose-rules + Android Lint (new warnings fail) + Roborazzi screenshots (light/dark/200% font).

## Round 6–7 decisions
- Autonomy: Claude does everything incl. risky-category merges (permissions, Room, CI, signing, agent config). User observes.
- Repo `Bolsar/ezymusy`: flip to public at start (first action after "go").
- Visual identity: **Dark minimal** — near-black surfaces, single electric-lime accent (~`#C6F432`), Space Grotesk only. Tokens locked in `DESIGN.md`.
- Device testing: existing `Small_Phone` AVD (API 37, arm64), started headless (`emulator -avd Small_Phone -no-window -no-snapshot-save`) for test runs, killed after. adb at `~/Library/Android/sdk/platform-tools/adb`. No phone pairing.
- Maestro E2E: runs on that emulator. Install at start (`curl -fsSL https://get.maestro.mobile.dev | bash`). One flow per milestone in `.maestro/`, run via mobile-dev-android `verify --flow`. `testTagsAsResourceId = true`. Lock-screen/background checks via `adb shell input keyevent KEYCODE_SLEEP` + `dumpsys media_session` (Maestro can't drive lock screen).
- mobileDev plugins installed (`mobile-dev@bolsar`, `mobile-dev-android@bolsar`). Invoke `mobile-dev:mobile-dev` via Skill first. Verify script ships in the `mobile-dev-android` pack (0.7.0+).
- Keystore: generated in M5, copy at `~/ezymusy-keys/`, GitHub secrets; user backs up in password manager.

## Verification
- One JVM unit test: `parse()` classifies video / playlist / mix / garbage URLs correctly.
- Manual on device (`./gradlew installDebug`):
  - Add single video, playlist, mix via paste and via YouTube app Share.
  - Play, lock screen → audio continues, lock-screen + notification controls work, Bluetooth headset buttons work, unplug headphones pauses.
  - Shuffle all mixes tracks from all included links; excluded link absent.
  - Mix queue keeps refilling past first page.
  - Add deleted/private video in playlist → skipped + greyed out.
  - Airplane mode mid-play → errors handled, no crash.
