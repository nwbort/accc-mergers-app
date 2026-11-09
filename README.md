# mergers.fyi for Android

An Android app for [mergers.fyi](https://mergers.fyi), the tracker for the
ACCC's merger register. It reads the same static JSON the site serves, so it
needs no server of its own.

- **Dashboard**: matters under assessment, phase 1 durations, recent
  determinations and filings, the fortnight's deadlines and outcome counts.
- **Mergers**: the whole register, searchable by name, party or industry,
  filtered by phase, status or what you follow, sorted as on the site.
- **Merger pages**: the outcome header, key dates, what's coming up, the
  description, parties, industries, commentary, Tribunal and Federal Court
  reviews, the full timeline with documents and determination reasons, and
  similar mergers.
- **Following**: follow a merger (as the site's Track button does) and the
  app is told within minutes of mergers.fyi publishing a change to it
  ([push](#push-notifications)), notifying new documents, determinations and
  moved deadlines. Following a declined waiver also follows
  the notification it's re-filed as. Optionally follow every phase 2 matter, or
  be told about every new filing.
- **Widgets**: *Coming up* puts the next dates on the home screen, for every
  merger or only the ones you follow, and only the kinds of date you choose
  (determinations, consultations, competition concerns, public benefit,
  hearings); *Search* is a bar that opens the app's search.

Android 17 (API 37) only. Kotlin, Jetpack Compose with Material 3,
Navigation 3, WorkManager, DataStore and kotlinx.serialization.

## Installing

Every push to `main` builds, tests, launches the APK on an emulator and
publishes it as a [release](../../releases). Open the Releases page on the
phone and tap the `.apk`. Other branches stop at an APK artifact on the
workflow run.

## Signing

The repo is public, so the sideload key isn't committed. Add two repository
secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the PKCS12 keystore, base64-encoded (key alias `mergers`) |
| `SIGNING_PASSWORD` | its store and key password |

Without them CI still builds, signed with a throwaway debug key and with a
warning on the run. An APK signed one way won't install over one signed the
other way, so add the secrets before the first install you mean to keep.

## Push notifications

mergers.fyi messages the app over Firebase Cloud Messaging when something it
follows changes, so it checks then; without push it checks every two hours.
Each phone subscribes to one topic per thing it follows (`merger-MN-01016`,
`industry-G`, `list`, `all`), so the follow list never leaves the phone. The
site's side is `.github/workflows/android-push.yml` in nwbort/accc-mergers,
whose `docs/android-push.md` has the one-off Firebase setup. For this repo:

| Where | What |
|---|---|
| `app/google-services.json`, or the `GOOGLE_SERVICES_JSON` secret | the Firebase project's Android config (not secret: it's inside every APK) |

Without it the app builds and works as before, checking on its schedule.

## Building locally

JDK 21 and an Android SDK with platform 37:

    ./gradlew testDebugUnitTest assembleRelease

`AppTest` drives every screen under Robolectric against a snapshot of the
site's data (`app/src/test/resources/data/`) and writes PNGs to
`app/build/screenshots/`.
