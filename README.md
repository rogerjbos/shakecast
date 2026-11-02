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
- Automatic ad skipping: the phrase/regex detector from `podcast3r/skip_ads.py` is ported to Kotlin and runs on the feed's `<podcast:transcript>`, or a skip-list JSON served from the Mac running the podcast3r pipeline. Blocks are only trusted when their duration matches the local file, so dynamically inserted ads never cause content to be skipped.

## Build

Requires JDK 17 or 21 and Android SDK platform 36.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Syncing between phones

Subscriptions, play positions and played state can be kept in step across phones through a free [Supabase](https://supabase.com) project:

1. Create a project, open the SQL editor and run `supabase/schema.sql`.
2. In Settings > Sync on the first phone, enter the project URL (Project Settings > API) and the anon / publishable key, tap **Generate** for a sync key, and turn sync on.
3. On every other phone enter the same URL, key and sync key.

Each phone pushes its own changes and pulls the rest. Newer timestamps win, a played episode on one phone is deleted on the others, and a paused episode jumps to the position the other phone reached. Downloads stay per phone.

## Serving skip-lists from the Mac

```
cd /Users/rogerbos/Node_HOME/podcast3r/transcripts
python3 -m http.server 8787
```

Then set the skip-list server in Settings to `http://<mac-ip>:8787` (use `http://10.0.2.2:8787` on the emulator). Files are looked up as `<podcast>_<episode>_skiplist.json` using the same sanitised name that `transcribe.py` writes.
