---
name: e2e-testing
description: Use when reproducing a bug on a real phone, verifying a fix end-to-end, or adding/running Maestro E2E flows for Ezymusy.
---

# E2E testing — Ezymusy

**Never install or start an Android emulator.** Emulators eat disk and freeze this laptop. Physical phone only.
(`docs/PLAN.md` and README mention an AVD / emulator — obsolete, ignore.)

Current state: five Maestro flows in `.maestro/` (last green on an emulator, 2026-10-03 — see
`.mobile-agent-proof/*/summary.txt`), Maestro installed at `~/.maestro/bin/maestro`, no instrumented tests in
`app`, no device attached by default. If you couldn't run on a device, say "E2E not run" — never claim
end-to-end verification.

## Setup (physical phone)
1. Phone: Settings → About → tap Build number 7× → Developer options → USB debugging (or Wireless debugging).
2. USB: plug in, accept the RSA prompt. Wireless: `adb pair <ip:port>` (pairing code), then `adb connect <ip:port>`.
3. `adb devices` must list the phone as `device` (not `unauthorized`).
   `adb` is `~/Library/Android/sdk/platform-tools/adb`.
4. `./gradlew installDebug`, then `adb shell monkey -p com.ezymusy.app 1` to launch.
5. Backend: none of ours — the phone needs internet; YouTube is hit live through NewPipeExtractor.

## Seed / reset data
- Fresh state: `adb shell pm clear com.ezymusy.app` (flows do this via `launchApp: clearState: true`).
- Add a link: paste into the Library field (`link_input` → `add_link`), or share text from another app:
  `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '<url>' com.ezymusy.app`
  (SEND filter in the manifest, handled in `MainActivity.kt`).
- Test links used by the flows: playlist `https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI`,
  mix `https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ`.
- DB is `databases/ezymusy.db` (debug build, so `adb shell run-as com.ezymusy.app …` can read/copy it).
  `m3b-skip-broken.yaml` needs a hand-prepared DB: one saved playlist whose position-1 track's videoId is
  `AAAAAAAAAAA` (title `BROKEN`). Pull with `run-as … cat`, edit with host `sqlite3`, push back; force-stop first.
- Logs: `adb logcat --pid=$(adb shell pidof com.ezymusy.app)`; playback timings: `adb logcat -s Perf`.

## Run Maestro flows
- All: `maestro test .maestro/` (m3b fails without its DB prep — run it alone after prepping).
- One: `maestro test .maestro/m2-library.yaml`.
- Flows select composables by `testTag` (exposed as resource ids via `testTagsAsResourceId` in `MainActivity`).
  New UI that a flow touches needs a `testTag`.
- Lock screen / background playback can't be driven by Maestro: `adb shell input keyevent KEYCODE_SLEEP`, then
  `adb shell dumpsys media_session` to confirm the session is still playing.

## Bug-reproduction procedure
1. Write the user's steps down (tap by tap, which link, which setting).
2. Reproduce manually on the phone; capture logcat. Can't reproduce → stop, report what you tried.
3. Write a Maestro flow in `.maestro/` (or extend one) that fails the same way; pure logic → also a JVM test.
4. Fix at the root cause.
5. Rerun the flow and the manual steps.
6. Report exactly how you verified (device model, steps, flow name).

## Gotchas
- Every flow needs network and live YouTube; a failing flow may be YouTube/extractor drift, not your change.
  Check logcat for extraction errors before debugging UI.
- Adding a playlist can take a while: flows wait up to 60 s for "Playlist · N tracks".
- `m4-now-playing.yaml` writes `docs/screenshots/m4-now-playing.png`.
