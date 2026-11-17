# Tracker Widgets (Android)

Home-screen widgets for the claude.ai Tracker: **Food & water**, **Deadlines**, **Money**, **Schedule & study**.

## How the data gets there
The Tracker runs inside Claude, so the app can't read it directly. Since Tracker version 66, while the Tracker is open
(phone or laptop), it writes key/value rows to the **Widget** tab of a private Google Sheet: when something changes,
and at least every 15 minutes. Since version 67, rows `json1`, `json2`, … hold one JSON object with everything the
widgets draw (Health, Money, today's schedule for both people, School items, Inbox suggestions, applied phone entries).

A small Apps Script on that sheet (deployed as a web app with a secret key) is the app's link to it:
- `GET …/exec?k=KEY` returns the Widget tab and the ids still waiting in the queue.
- `POST {k, entries:[…]}` adds one row per entry to the **Phone Queue** tab.

While the Tracker is open it reads Phone Queue every 30 seconds, applies each entry the way its own buttons would,
and clears the row. Entries: `water {oz}`, `food {text}`, `supps {on}`, `money {dir,date,amount,type,source,card}`,
`prog {id,v}` (a School status), `sug {key,do,card}` (Add/Skip an Inbox suggestion), `text {from,body,app}` and
`quick {text}` (both sorted by the Inbox with Claude). Until the Tracker has applied an entry, the widgets draw it as pending (⏳).

The widgets are only as fresh as the last time the Tracker was open; each one says how long ago that was.

## Widgets
- **Health:** calories, protein, water and caffeine; +8 / +16 / bottle water buttons, Supplements, Add food.
- **Money:** checking balance, this month's spending, recent entries; Spent / Received / Moved open the same form as the Tracker's.
- **Today:** you and Miranda side by side: what's on now, what's next, and the rest of the day.
- **School:** upcoming work with the Tracker's time-left chips and its status buttons (To do / Doing / Done; exams: Not started / Studying / Ready / Taken).
- **Inbox:** what the Tracker found in email and texts, with Add (or which card paid) / Skip, and Quick add.

## Texts (optional)
With "Send my texts to the Tracker's Inbox" on and notification access granted, `TextListener` turns each incoming
message notification from the texting app into a `text` entry (sender and message). Sent messages and sign-in codes are skipped.

## Setup
1. Install `TrackerWidgets.apk` on the phone (allow installs from the app you open it with).
2. Open **Tracker Widgets**, paste the phone link (`…/exec?k=…`), tap **Save and test**.
3. Long-press the home screen → Widgets → Tracker Widgets.

A published CSV link of the Widget tab still works for reading only.

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
