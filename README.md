# Shakecast

A glove-friendly Android podcast player. Shake the phone hard to skip ahead 30 seconds; a headset double-click, a volume-key chord, oversized notification buttons, and automatic ad detection cover the rest.

## Features

- Search podcasts via the iTunes Search API and subscribe.
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
- Remembered manual skips: spans you skip by hand are saved per episode and skipped automatically next time, on every synced phone.
- Automatic ad skipping: the phrase/regex detector from `podcast3r/skip_ads.py` is ported to Kotlin and runs on the feed's `<podcast:transcript>`. Blocks are only trusted when the transcript lines up with the local file, so dynamically inserted ads never cause content to be skipped.

## Build

Requires JDK 17 or 21 and Android SDK platform 36.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Install

```bash
/Users/rogerjbos/Library/Android/sdk/platform-tools/adb devices -l
/gradlew installDebug
```

## Syncing between phones

Subscriptions, play positions and played state can be kept in step across phones through a free [Supabase](https://supabase.com) project:

1. Create a project, open the SQL editor and run `supabase/schema.sql`.
2. In Settings > Sync on the first phone, enter the project URL (Project Settings > API) and the anon / publishable key, tap **Generate** for a sync key, and turn sync on.
3. On every other phone enter the same URL, key and sync key.

The schema is additive: rerun `supabase/schema.sql` after upgrading to pick up new tables. Each phone pushes its own changes and pulls the rest. Newer timestamps win, a played episode on one phone is deleted on the others, and a paused episode jumps to the position the other phone reached. Downloads stay per phone.
