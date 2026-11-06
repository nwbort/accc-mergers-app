# CLAUDE.md

Guidance for Claude when working in this repository.

## What this is

The Android app for mergers.fyi (the `nwbort/accc-mergers` repo). It reads the
site's generated JSON from `https://mergers.fyi/data/` (`data/Api.kt`); there
is no backend. Status, outcome and tracking rules are ports of the site's
`frontend/src/constants/mergerStatus.js`, `utils/mergerOutcome.js` and
`context/TrackingContext.jsx`. Keep them in step: event keys in particular
(`tracking/TrackedEvents.kt`) decide what counts as news.

## Building

The APK the user installs comes from GitHub Actions: every push runs
`.github/workflows/build.yml`, which runs the unit tests, builds the release
APK, launches it on an API 37 emulator and uploads it as an artifact. Pushes
to `main` also publish it as a GitHub release. After pushing, check that
workflow run and fix it if it's red.

Building locally before pushing is fine
(`./gradlew testDebugUnitTest assembleRelease`, JDK 21 + Android SDK), but
never ask the user to build or install anything by hand.

The repo is public: the signing key comes from the `SIGNING_KEYSTORE_BASE64`
and `SIGNING_PASSWORD` secrets and is never committed (`/signing/` is
ignored).

`AppTest` drives every screen under Robolectric against the fixture snapshot
in `app/src/test/resources/data/` (with "today" pinned to 6 Nov 2026) and
writes PNGs to `app/build/screenshots/`. Look at them after UI changes.
