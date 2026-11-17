# Tracker Widgets (Android)

Home-screen widgets for the claude.ai Tracker: **Food & water**, **Deadlines**, **Money**, **Schedule & study**.

## How the data gets there
The Tracker runs inside Claude, so the app can't read it directly. Since Tracker version 66, while the Tracker is open
(phone or laptop), its Schedule section writes key/value rows to the **Widget** tab of a private Google Sheet:
when a number changes, and at least every 15 minutes.
That tab is published to the web as CSV, and the app downloads it every ~30 minutes (or when you tap ↻).

Keys: `updated`, `kcal`, `kcal_target`, `protein`, `protein_target`, `water_oz`, `water_goal_oz`, `caffeine_mg`,
`supplements`, `due1`–`due5` (`name|class|YYYY-MM-DD HH:MM|exam/hw`), `checking`, `checking_min`, `month_spent`, `month`,
`now` (`title|until …`), `next` (`title|when`), `study_min`, `studying`.

The widgets are only as fresh as the last time the Tracker was open; each one says how long ago that was.

## Setup
1. On a computer: Google Sheets → the sheet → File → Share → Publish to web → **Widget** tab, **CSV** → Publish. Copy the link.
2. Install `TrackerWidgets.apk` on the phone (allow installs from the app you open it with).
3. Open **Tracker Widgets**, paste the link, tap **Save and test**.
4. Long-press the home screen → Widgets → Tracker Widgets.

Publishing makes the Widget tab readable by anyone who has that (long, random) link. Only that tab is published.

## Build and release
Needs Java 17, the Android SDK (platform 35) and Gradle 8.9.
```
gradle --no-daemon assembleDebug -PupdateRepo=aarianselby2000/tracker-widgets
```
Output: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed; updates must be signed with the same debug key).

To publish a version: raise `versionCode`/`versionName` in `app/build.gradle`, build, then
`gh release create b<versionCode> TrackerWidgets.apk --title "<versionName>" --notes "<what changed>"`.
The app's **Check for update** button reads the latest release, compares its `b<number>` tag with the installed
build number, downloads the attached `.apk` and opens Android's installer.

## Code
- `WidgetData.java`: download + parse the CSV, keep the last good copy.
- `Widgets.java`: builds each widget's view; tap opens the Tracker, ↻ refreshes.
- `BaseWidget.java` / `RefreshWorker.java`: WorkManager refresh every 30 min and on demand.
- `MainActivity.java`: setup screen (paste link, test, see values).
