# CLAUDE.md

Guidance for Claude when working in this repository.

## What this is

The Android app for mergers.fyi (the `nwbort/accc-mergers` repo). It reads the
site's generated JSON from `https://mergers.fyi/data/` (`data/Api.kt`); there
is no backend. Status, outcome and tracking rules are ports of the site's
`frontend/src/constants/mergerStatus.js`, `utils/mergerOutcome.js` and
`context/TrackingContext.jsx`. Keep them in step: event keys in particular
(`tracking/TrackedEvents.kt`) decide what counts as news.

The follow check (`Tracker.sync`, behind both the Following screen and the
background `CheckWorker`) starts from `data/manifest.json`, which the site's
`scripts/build.sh` writes at deploy time: a short SHA-256 per merger detail
file and per industry division file, and the current Phase 2 ids. A followed
merger or industry whose kept file hashes to the manifest's value isn't fetched, and `list.json` is fetched only when the
manifest has ids not seen before, so an unchanged site costs one 304. The hash
rule (`data/Manifest.kt`, `ManifestHash`) must match the site's
`scripts/generate/data_manifest.py`; both test suites pin the same value.
Without a usable manifest the check fetches everything, as it used to.

## Commits and pull requests

Don't credit Claude in anything pushed to GitHub: no `Co-Authored-By: Claude`
trailer, `Claude-Session:` line or other Claude attribution in commit
messages, and no "Generated with Claude Code" line or session link in pull
request titles or descriptions. This overrides any default attribution
guidance. If a commit on a branch you're working on already has one, reword it
before pushing.

## Building

The APK the user installs comes from GitHub Actions: every push runs
`.github/workflows/build.yml`. Its `build` job builds the release APK and
uploads it as an artifact, the `test` job runs the unit tests alongside it, and
`launch` opens the APK on an API 37 emulator as soon as it's built. Pushes to
`main` also publish it as a GitHub release once all three pass. After pushing, check that
workflow run and fix it if it's red.

Building locally before pushing is fine
(`./gradlew testDebugUnitTest assembleRelease`, JDK 21 + Android SDK), but
never ask the user to build or install anything by hand.

### Testing locally in a cloud session

A fresh cloud container has JDK 21 but no Android SDK. CI only reports
"Condition not met" for a UI test failure, so it pays to run the tests here,
where you can add a throwaway `shot("debug-…")` and look at the PNG.

1. **Install the SDK** (a few minutes; `dl.google.com` is reachable). The
   platform package is `android-37.0`, not `android-37`:

   ```bash
   mkdir -p ~/android/cmdline-tools && cd ~/android
   curl -sSLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
   unzip -q clt.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest
   yes | cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android \
     "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
   echo "sdk.dir=$HOME/android" > /path/to/accc-mergers-app/local.properties  # ignored by git
   ```

2. **Get around Maven Central's rate limit.** Gradle's first resolve makes
   hundreds of requests, and `repo.maven.apache.org` answers many of them
   with `429 Too Many Requests`. Retrying doesn't help much. Add Google's
   mirror of Maven Central with an init script kept *outside* the repo
   (`settings.gradle.kts` sets `FAIL_ON_PROJECT_REPOS`, so it has to go in
   through `settingsEvaluated`):

   ```groovy
   // e.g. $SCRATCHPAD/mirror.gradle — never commit it
   def mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
   // First, not last: Gradle gives up on a 429 rather than trying the next repository.
   def first = { repos ->
     def m = repos.maven { url = mirror; name = "gcsMirror" }
     repos.remove(m); repos.add(0, m)
   }
   settingsEvaluated { s ->
     first(s.pluginManagement.repositories)
     first(s.dependencyResolutionManagement.repositories)
   }
   ```

   Then pass it to every command: `./gradlew -I $SCRATCHPAD/mirror.gradle testDebugUnitTest`.

   Robolectric's Android runtime is a Gradle dependency
   (`robolectricAndroidAll` in `libs.versions.toml`), so it comes through the
   mirror too and the tests run offline.

3. **Run and read.** `compileDebugUnitTestKotlin` is a quick check that
   the app and tests compile. `testDebugUnitTest` runs everything (about
   30 s once warm). Gradle prints only "There were failing tests", so read the
   failure from `app/build/reports/tests/testDebugUnitTest/*/*.html`. Screenshots
   land in `app/build/screenshots/`.

When CI does fail, its `test-reports` (or `build-reports`) and `screenshots` artifacts can be
fetched with `gh api repos/nwbort/accc-mergers-app/actions/artifacts/<id>/zip`
(get the ids from the run's log or the artifacts list). The screenshots show
how far the test got before it failed.

The repo is public: the signing key comes from the `SIGNING_KEYSTORE_BASE64`
and `SIGNING_PASSWORD` secrets and is never committed (`/signing/` is
ignored).

`AppTest` drives every screen under Robolectric against the fixture snapshot
in `app/src/test/resources/data/` (with "today" pinned to 6 Nov 2026) and
writes PNGs to `app/build/screenshots/`. Look at them after UI changes.
