# Shakecast

A full-featured, yet simple, glove-friendly Android podcast player with many ways to skip ads:
* Shake the phone to skip ahead 30 seconds
* a headset double-click will also skip ahead
* a volume-key chord (up then down within 1/2 second)
* extra large `skip ahead 30s` button

## Features

- Search podcasts via the iTunes Search API, or paste a feed address (private member feeds with credentials in the URL work too). Import and export subscriptions as OPML from Settings.
- Android Auto: the car screen browses Downloads, Queue and Shows, and the next/previous buttons on cars, watches and Bluetooth units act as skip forward/back. Wear OS's built-in media controls show the same buttons for any active session.
- Home filters: unplayed, in progress, downloaded or all; by show; and a title search.
- Feeds refresh every few hours (WorkManager) and on pull-to-refresh.
- The latest 3 episodes of each show download automatically on Wi-Fi and are deleted when played.
- Background playback with Media3 and a media notification whose buttons are skip back / play-pause / skip forward.
- Skip inputs:
  - **Shake**: peak-counting accelerometer detector tuned to ignore walking and running. Sensitivity is adjustable in Settings, with a live test readout.
  - **Headset button**: 1 click play/pause, 2 clicks skip forward, 3 clicks skip back. Next/previous keys also map to skip.
  - **Volume chord** (opt-in): volume up then down within half a second skips forward; down then up skips back. Works with the screen off while Shakecast is the active media session.
  - **Big button**: the bottom third of the Now Playing screen is one large skip button.
- Queue and continuous play: "Play next" or "Add to queue" from any episode's menu; when an episode ends the queue plays, then the newest unplayed download.
- Sleep timer with a fade-out, including "end of episode".
- Chapters from Podcasting 2.0 `<podcast:chapters>` JSON or ID3 CHAP frames: chapter list, previous/next buttons, ticks on the seek bar, optional shake-to-next-chapter, and chapters titled like ads are skipped automatically.
- Trim silence and a volume boost of up to +9 dB.
- Per-show intro and outro skip, synced with the subscription.
- Settings sync: everything except the Supabase project details follows you between phones, newest change wins.
- Remembered manual skips: spans you skip by hand are saved per episode and skipped automatically next time, on every synced phone.
- Automatic ad skipping: the phrase/regex detector from `podcast3r/skip_ads.py` is ported to Kotlin and runs on the feed's `<podcast:transcript>`. Blocks are only trusted when the transcript lines up with the local file, so dynamically inserted ads never cause content to be skipped.

## Build

Requires JDK 17 or 21 and Android SDK platform 36.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Release builds are shrunk by R8 (names are kept, since the code is public) and signed with the upload key described in [docs/RELEASE.md](docs/RELEASE.md), which also covers versioning, publishing a release, and opt-in crash reporting through Sentry. The privacy policy is in [PRIVACY.md](PRIVACY.md) and linked from Settings > About.

## Install

Download the latest `shakecast-<version>.apk` from the [Releases page](https://github.com/rogerjbos/shakecast/releases) on your phone and open it. Android asks once to allow installs from your browser or Files app; allow it, then tap Install. Later releases install over the top and keep your library. [Obtainium](https://github.com/ImranR98/Obtainium) can watch the Releases page and install updates for you. Android 10 or newer is required.

To build and run from source:

```bash
/Users/rogerjbos/Library/Android/sdk/platform-tools/adb devices -l
./gradlew installDebug
```

## Syncing between phones

Subscriptions, play positions, played state, manual skips and settings can be kept in step across phones through a free [Supabase](https://supabase.com) project. Store builds have a project baked in (see [docs/RELEASE.md](docs/RELEASE.md)), so users only create an account under Settings > Sync and sign in with it on their other phones. Builds without one show URL and key fields for your own project:

1. Create a project, open the SQL editor and run `supabase/schema.sql`. Under Authentication > Providers make sure Email is enabled; turn off "Confirm email" if you would rather skip the confirmation step.
2. In Settings > Sync on the first phone, enter the project URL (Project Settings > API) and the anon / publishable key, then tap **Create account** with an email and password.
3. On every other phone enter the same URL and key and **Sign in** with that account. Turn sync on.

Every row is owned by a Supabase Auth user and row level security lets a signed-in user read and write only their own rows, so one person's data cannot be reached with another's credentials. The phone keeps the session's access and refresh tokens in private app storage, refreshes them silently, and never stores the password. Signing in pushes the phone's whole library so a new account starts with everything the phone already has.

Rerun `supabase/schema.sql` after upgrading; it is safe to run repeatedly. Upgrading from a build that used a shared sync key drops those tables, and each phone repopulates the account on its first sync after signing in. Each phone pushes its own changes and pulls the rest. Newer timestamps win, a played episode on one phone is deleted on the others, and a paused episode jumps to the position the other phone reached. Downloads stay per phone.
