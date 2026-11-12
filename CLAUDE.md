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

Push (`notify/Push.kt`): the site's `android-push.yml` diffs the manifest
between deploys and sends a "check now" message over FCM to a topic per
changed file; each phone subscribes to the topics for what it follows
(`TopicSyncWorker`), and `PushService` runs `CheckWorker.checkNow`. The topic
names must match `scripts/push/fcm.py`; both test suites pin them. Firebase is
configured from `app/google-services.json` (or the `GOOGLE_SERVICES_JSON`
secret in CI), read in `app/build.gradle.kts` without the Google Services
plugin. Without it there's no push and the schedule stays at 2 hours; once a
phone has subscribed, it drops to 12.

A followed id can be one the register doesn't have yet (typed into search:
"Follow anyway"; `MergerIds.parse` checks it's MN/WA plus five digits). It's
kept in `TrackingState.awaiting`, isn't fetched while the manifest lacks it,
and isn't a failure to retry. `fcm.py` messages `merger-{id}` when an id
first appears, so its usual topic covers it. When it appears, nothing of it
was marked seen, so its whole history is the news.

Links: the manifest claims mergers.fyi's `/`, `/mergers`, `/mergers/…` and
`/industries…` (PDFs under them excluded), verified by the site's
`frontend/public/.well-known/assetlinks.json`, which names the release key's
SHA-256. `SiteLink.from` maps a link to a screen; one it can't place goes back
to the browser. `openUrl` always pins a browser package, or a "view on the
site" would come straight back into the app.

Recent events (the dashboard, below Coming up) is the site's
`RecentEventsTimeline` over `recent-events.json`. Which types it lists and
their order within a day are `data/RecentEventTypes.kt`, a port of the site's
`frontend/src/constants/recentEventTypes.js` (`shown` and key order): keep the
two in step. A type the app doesn't know reads as Other.

Catch me up (`ui/DigestScreen.kt`, `Route.Digest`) is the site's weekly
digest page (`frontend/src/pages/Digest.jsx`) over `digest.json`. The card under
the dashboard's decision times opens it, and so does a link to `/digest`,
`#section` included, as the weekly email sends them (`SiteLink.Digest`). Which
sections it shows, their order, names, anchors and colours, and what each row
says, are `data/Digests.kt`. Keep those in step with `Digest.jsx` and
`DIGEST_COLOR_KEYS`. Each section has its own `MaterialShapes` glyph
(`DigestSection.shape()` in the screen; a decision takes its outcome's
`outcomeShape`), on its count card and its heading. The test fixture is a copy of the site's archived digest
for 26 Oct to 1 Nov 2026. Down its end edge, a scrubber (`SectionRail` in `ui/SectionRail.kt`) has a
mark per section in that shape and colour: a vertical drag from the edge moves
the list, with a light tick per card and a firmer tap per section, and names
the section beside the finger. It watches the edge from the list's parent, so a
sideways swipe from a card's edge still follows or unfollows. At its foot, a native email signup (`EmailSignup`,
`AppViewModel.subscribeToDigest`) posts to the site's signup worker's
`/app-signup` route (`data/DigestSignup.kt`), which emails a link to confirm
(double opt-in) because the app can't show the website's Turnstile check. The
tests swap in `FakeSignup` (`MergersApp.digestSignup`). Google Play asks a form
like this to say what's collected and link the privacy policy: keep both.

Widgets (`widget/`): plain `RemoteViews`, no Glance. Coming up
(`UpcomingWidget`) lists `upcoming-events.json` from the copy kept on the
phone, filtered by each widget's `UpcomingConfig` (followed only, and the
`EventKind`s hidden; set in `UpcomingWidgetConfigActivity`). It redraws when
the app's copy or the follow list changes, and hourly, which also queues a
fetch (`UpcomingRefreshWorker`). `EventKind` groups the site's event types as
`eventTypeColors` colours them; its tiles are drawn from `res/` (the launcher
picks day or night), so a new type or colour goes in both places. Search
(`SearchWidget`) sends `MainActivity.ACTION_SEARCH`. In `AppTest`, `widgetShot`
draws a widget's `RemoteViews` inside an `AppWidgetHostView` (a list takes no
rows anywhere else).

Tabs (`ui/App.kt`): Dashboard, Mergers and Following change only from the
bottom bar, never by swiping across the screen (Material's navigation bar
guidance keeps swipes for things within a screen). Each keeps its scroll and
state while another shows. Tapping the tab already showing goes back to it
from a merger or industry open over it, and at the tab itself scrolls it to
the top (`AppViewModel.tapTab`, `ScrollToTopOnReselect`).

What the Following tab lists under New is marked read when you leave the tab
(another tab, or anything opened over it), and by a pull to refresh. Only what
was listed while it showed: what arrives while you're elsewhere stays new
(`AppViewModel.markReadOnLeavingFollowing`, `pullFollowing`).

Settings (`ui/SettingsScreen.kt`) has no tab: it opens from the cog on the
dashboard's top bar, from the Following tab (the same cog, and the Alerts row
that says how alerts are set; the switches themselves live only in Settings,
under Notifications), and from the phone's App info ("Additional settings in the
app", `ACTION_APPLICATION_PREFERENCES`). The theme choice (`ui/theme/Appearance.kt`)
is kept in plain preferences and handed to `UiModeManager.setApplicationNightMode`
too, so the window and status bar follow it. In Compose, use `isDarkTheme()`,
not `isSystemInDarkTheme()`, which ignores the setting.

Look and feel: Material 3 Expressive. `MergersTheme` is `MaterialExpressiveTheme`
with `MotionScheme.expressive()` and rounder `Shapes`; `material3` is pinned
ahead of the Compose BOM (`libs.versions.toml`) because the BOM's 1.4 lacks
most Expressive components. Use the theme's pieces rather than one-offs:
`LoadingIndicator` (`Loading()`, `RefreshBox`), the `…Emphasized` type styles
for headlines and numbers, toggle buttons with `shapes`, and `motionScheme`
specs for app animations. Each outcome has its own `MaterialShapes` outline
(`outcomeShape` in `ui/Common.kt`), used by `StatusGlyph` (which leads
each `MergerCard`) and large and faint behind the dashboard hero and a merger's
header, not on cards, where a list of them reads as repetitive; a new status
needs one there too.
Event tiles are the six-sided cookie in the app (`TileShape`) and in the
widget (`res/drawable/widget_tile.xml`, the same outline as a path): change
both together. A `MergerCard` grows into its merger's header through
`Modifier.sharedMerger` (`SharedTransitionLayout` round `NavDisplay` in
`MergersApp`); an entry takes part only inside `Shared { }`. On a merger's
page, follow, share and open live in a floating toolbar, and the top bar takes
the header's colour and the merger's name once the header has scrolled off.
Following and unfollowing give a toggle haptic (`rememberFollowHaptics`).
A merger card swiped either way, a quarter of its width or a flick, follows
or unfollows it and springs back (`SwipeToFollow` in `ui/Common.kt`, behind
`MergerCard`'s `onSwipe` and the digest's cards, with
`rememberMergerFollowToggle` for the snackbar and Undo); a screen reader gets
the same as the card's custom action. It isn't Material's `SwipeToDismissBox`,
whose threshold is skipped when the finger stops before release (it then
takes half the card). On the digest, a decided card doesn't show the
following bookmark, since its determination button sits where it would.

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
