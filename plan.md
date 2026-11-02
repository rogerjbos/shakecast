# Shakecast: glove-friendly Android podcast player

## Context

Roger could not find an Android podcast player they remembered building; a full-disk search showed none exists (the only podcast project, `podcast3r`, is a web app). This plan creates one from scratch as a native Android app.

The goal is a podcast player whose standout feature is skipping ads without touching the screen, for example while wearing gloves. Shaking the phone hard skips ahead 30 s. Secondary hands-free inputs (headset buttons, volume-key chord, oversized notification buttons) and automatic ad detection round it out. The app also needs the basics: find shows via the iTunes Search API, subscribe, refresh feeds, auto-download recent episodes on Wi-Fi, and play them in the background.

### Decisions already made with Roger
- Project: `/Users/rogerbos/Node_HOME/shakecast`, package `com.rogerbos.shakecast`, app name "Shakecast". Git-initialised, single Gradle module.
- Scope: full MVP in one pass.
- Skip inputs: shake (primary), headset/Bluetooth button (double = +30 s, triple = -15 s), volume-button chord, oversized +30/-15 buttons in the notification and Now Playing screen, automatic ad detection.
- Ad detection: port the phrase/regex detector from `podcast3r/skip_ads.py`. Inputs on the phone are the feed's `<podcast:transcript>` URL, or a skip-list JSON served from the Mac (podcast3r pipeline output). No on-device Whisper. Episodes with neither fall back to manual skipping.
- Downloads: latest 3 episodes per subscribed show, unmetered network only, delete the file when the episode is marked played. WorkManager periodic refresh plus pull-to-refresh.

### Toolchain facts (verified)
- Android Studio 2025.2 with bundled JBR 21. CLI JDK is Temurin 17 only. Use `jvmTarget 17` and no `jvmToolchain` block so both `./gradlew` and Studio builds work.
- SDK at `/Users/rogerjbos/Library/Android/sdk`: platform android-35 and build-tools 35.0.0 only. `androidx.core 1.17.0` and `activity-compose 1.11.0` (the versions in the polkadot catalog) declare minCompileSdk 36, so **compileSdk 36 is mandatory**; step 1 installs `platforms;android-36` with `sdkmanager` (at `cmdline-tools/latest/bin/sdkmanager`). targetSdk 35, minSdk 29.
- Emulator AVD `wisp35` (API 35, arm64, accelerometer + gyroscope enabled). No physical device attached.
- Prof18 RSS-Parser 6.1.x is compiled with Kotlin 2.4 and cannot be consumed by Kotlin 2.2.21, and no RSS library exposes `podcast:transcript`; the feed parser is hand-rolled on `XmlPullParser`.

### Existing code to reuse or copy
- `/Users/rogerbos/Node_HOME/polkadot-app-android-v2/gradle/libs.versions.toml`: version numbers (AGP 8.12.3, Kotlin 2.2.21, KSP 2.3.2, Gradle 8.14.3, Compose BOM 2026.05.00, Hilt 2.57.2 + androidx.hilt 1.3.0, Room 2.8.2, WorkManager 2.10.5, OkHttp 5.2.1, Retrofit 3.0.0, kotlinx-serialization 1.7.3, coroutines 1.9.0, Coil 2.7.0, Media3 1.9.0, navigation 2.9.5).
- `polkadot-app-android-v2/app/src/main/java/io/pcf/polkadotapp/app/App.kt` lines 38–47: `HiltWorkerFactory` + `Configuration.Provider`.
- `polkadot-app-android-v2/feature/chats/impl/src/main/java/io/pcf/polkadotapp/feature_chats_impl/data/hop/download/FileDownloadWorker.kt` and `.../hop/transfer/HopTransferWork.kt` lines 23–35, 67–91: `@HiltWorker CoroutineWorker`, constraints builder, `ForegroundInfo`.
- `polkadot-app-android-v2/app/src/main/java/io/pcf/polkadotapp/app/root/presentation/debug/ShakeDetector.kt`: sensor register/unregister shape only; its algorithm is replaced.
- `polkadot-app-android-v2/app/src/main/res/xml/network_security_config.xml` and the WorkManager initializer removal in its `AndroidManifest.xml`.
- `podcast3r/skip_ads.py`: `detect_ads` (line 420), `merge_overlapping` (279), `fill_ad_gaps` (295), `detect_preroll` (338), `detect_postroll` (364), `compute_content_ranges` (506); strong phrases 48–143, contextual 145–166, regexes 178–201, intro markers 206–222, outro markers 225–246. Defaults: pad 2 s, merge gap 15 s, gap-fill 90 s, pre/post-roll window 300 s with 15 s minimum.
- `podcast3r/transcribe.py` line 38 `safe_filename` and line 178: skip-list stem is `safe_filename("<podcast title>_<episode title>")` (strip non `\w\s-`, whitespace to `_`, truncate 80) + `_skiplist.json`. The phone reproduces this key.
- `podcast3r/podcast_scraper.ts` line 108 (iTunes query) and lines 168–255 (RSS field mapping: `enclosure`, `media:content` fallback, `itunes:duration`, `podcast:transcript`, `guid` fallback to enclosure URL, `pubDate`, `itunes:image`). `podcast3r/src/lib/srt.ts` lines 10–46: SRT parser.
- `podcast3r/src/types/index.ts` lines 46–62: skip-list JSON `{totalDuration, adBlocks:[{start,end,reason}], contentRanges, adTimeSaved}`.
- Test fixtures: 32 `_whisper.json` + `_skiplist.json` pairs in `/Users/rogerbos/Node_HOME/podcast3r/transcripts/`.
- Coding rules from `polkadot-app-android-v2/CLAUDE.md`: UI strings in `strings.xml`; `Modifier` first parameter, never mutated; no early `return` in composables; one state object per ViewModel; `Result<T>` with `getOrThrow()` only in workers and tests; `sealed interface` over `sealed class`; camelCase package leaves; minimal comments. `/Users/rogerbos/Node_HOME/CLAUDE.md`: never pipe shell output through head/tail.

## Architecture

Single `:app` module, packages `data / domain / playback / ui / di`. Compose + Material3, navigation-compose with `@Serializable` routes, Hilt (KSP), Room, WorkManager, DataStore Preferences, OkHttp + Retrofit with `converter-kotlinx-serialization`, Coil, Media3 ExoPlayer + `media3-session`. No `build-logic`, flavors, or detekt. `domain/ads` and the three pure input detectors have no Android imports so they are JVM-testable.

Dependency rules: `ui` → `domain` + repositories + `PlayerConnection`; `playback` → repositories + settings; `data/remote|db|download|refresh` only reached through repositories and workers.

`app/build.gradle.kts`: compileSdk 36, targetSdk 35, minSdk 29, Java 17, `kotlin.compilerOptions.jvmTarget = JVM_17`, Compose, `room { schemaDirectory("$projectDir/schemas") }`, KSP for Room/Hilt/androidx.hilt. Wrapper `gradle-8.14.3-bin.zip`.

Catalog additions beyond polkadot's: `media3-session`, `media3-common`, `datastore-preferences 1.1.7`, `navigation-compose`, `hilt-navigation-compose`, `retrofit converter-kotlinx-serialization`, `kotlinx-coroutines-guava` (await on `MediaController.Builder.buildAsync()`), `timber 5.0.1`, `material-icons-extended`, test: junit 4.13.2, mockk 1.14.2, coroutines-test, work-testing.

## File layout (`app/src/main/java/com/rogerbos/shakecast/`)

- Root: `ShakecastApp.kt` (Hilt app, `HiltWorkerFactory`, Timber, schedule periodic refresh), `MainActivity.kt` (requests `POST_NOTIFICATIONS` on 33+, hosts nav, connects/releases `PlayerConnection`).
- `di/`: `AppModule.kt` (OkHttp, `Json { ignoreUnknownKeys; isLenient }`, Retrofit for `https://itunes.apple.com/`, IO dispatcher), `DatabaseModule.kt`.
- `data/db/`: `ShakecastDatabase.kt`, `PodcastEntity.kt`, `EpisodeEntity.kt`, `PodcastDao.kt`, `EpisodeDao.kt`, `Converters.kt`.
- `data/remote/itunes/`: `ItunesSearchApi.kt`, `ItunesDto.kt` (filter results without `feedUrl`).
- `data/remote/rss/`: `RssFeedParser.kt` (namespace-aware `XmlPullParser`, match on `(namespace, localName)` with prefix fallback for `podcast:` and `itunes:`), `RssModels.kt` (`ParsedFeed`, `ParsedItem`, `parseDuration` for `"1:02:03"/"62:03"/"3723"`, RFC-1123 pubDate with lenient fallback), `RssFeedFetcher.kt` (conditional GET with ETag/Last-Modified).
- `data/remote/transcript/`: `TranscriptFetcher.kt`, `TranscriptParser.kt` (SRT, VTT, Podcasting 2.0 JSON `{segments:[{startTime,endTime,body}]}`, Whisper JSON; coalesce word-level segments into ~8 s / sentence chunks before detection).
- `data/remote/skiplist/`: `SkipListClient.kt` (GET `${baseUrl}/${key}_skiplist.json`, 404 → null), `SkipListDto.kt`, `SkipListKey.kt` (Kotlin port of `safe_filename`; use `[^\p{L}\p{N}_\s-]` because Kotlin's `\w` is ASCII-only by default).
- `data/settings/`: `Settings.kt`, `SettingsRepository.kt` (DataStore, one `Flow<Settings>`).
- `data/download/`: `EpisodeFileStore.kt` (`getExternalFilesDir(DIRECTORY_PODCASTS)/<podcastId>/<episodeId>.mp3`, duration via `MediaMetadataRetriever`), `EpisodeDownloadWorker.kt`, `DownloadScheduler.kt` (unique `OneTimeWorkRequest` per episode, `UNMETERED` + `requiresStorageNotLow`).
- `data/refresh/`: `FeedRefreshWorker.kt`, `RefreshScheduler.kt` (periodic 3 h, flex 30 min, `CONNECTED`; `enqueueNow()` for pull-to-refresh).
- `data/repository/`: `PodcastRepository.kt`, `EpisodeRepository.kt`, `AdBlockRepository.kt`.
- `domain/model/`: `Podcast`, `Episode`, `AdBlock(start, end, reason)` seconds, `TranscriptSegment`, `DownloadState` (NONE/QUEUED/DOWNLOADING/DOWNLOADED/FAILED), `AdSource` (NONE/SKIPLIST/TRANSCRIPT/MISALIGNED).
- `domain/ads/` (pure Kotlin): `AdPhrases.kt`, `AdPatterns.kt`, `AdDetector.kt`, `AdBlockAlignment.kt`.
- `domain/usecase/`: `SubscribeToPodcast`, `RefreshFeeds`, `MarkEpisodePlayed`, `ResolveAdBlocks`.
- `playback/`: `PlaybackService.kt`, `ShakecastPlayer.kt`, `ShakecastSessionCallback.kt`, `PlaybackCommands.kt`, `MediaButtonClickCounter.kt`, `VolumeChordDetector.kt`, `ShakeDetector.kt` + `AccelerometerSource.kt`, `AdSkipController.kt`, `PositionPersister.kt`, `MediaItemFactory.kt`, `Haptics.kt`, `PlayerConnection.kt`.
- `ui/`: `theme/Theme.kt`; `navigation/Routes.kt`, `ShakecastNavHost.kt` (Home, Search, PodcastDetail, NowPlaying, Settings); `components/` (`EpisodeRow`, `PodcastArtwork`, `BigSkipButton`, `AdMarkerSeekBar`, `MiniPlayerBar`); `home/`, `search/`, `podcast/`, `player/`, `settings/` each with Screen + ViewModel (+ `NowPlayingUiState`).
- `res/`: `values/strings.xml`, `xml/network_security_config.xml` (cleartext permitted, base config), notification and skip icons, launcher icons.
- Tests (`app/src/test/...`): `AdDetectorFixtureTest`, `AdDetectorTest`, `TranscriptParserTest`, `RssFeedParserTest`, `ShakeDetectorTest`, `MediaButtonClickCounterTest`, `VolumeChordDetectorTest`, `AdBlockAlignmentTest`, `AdSkipControllerTest` (fake player). Fixtures in `src/test/resources/fixtures/`: 6–8 whisper/skiplist pairs copied from podcast3r (include the zero-block Maggie Lake ones and the 19-block Thoughtful Money one), a sample RSS with `podcast:transcript`, one SRT, one VTT.

## Room schema (version 1)

`podcasts`: id PK, feedUrl (unique), itunesId?, title, author, description, artworkUrl, subscribedAt, lastRefreshedAt?, etag?, lastModified?, autoSkipAds (true), autoDownload (true).

`episodes`: id PK (used as `MediaItem.mediaId`), podcastId FK cascade (indexed), guid (unique with podcastId), title, description, pubDateMs, durationSec? (feed value, overwritten by measured file duration), enclosureUrl, enclosureType, enclosureLength?, transcriptUrl?, transcriptType?, downloadState, localFilePath?, downloadedAt?, positionMs (0), isPlayed (false), playedAt?, lastPlayedAt?, adSource, adBlocksJson? (serialised `List<AdBlock>`), adResolvedAt?.

Key DAO queries: `observeLatest(limit)` joined with podcasts for Home; `observeForPodcast`; `upsertAll` as insert-IGNORE then metadata-only update so position/download state survive refresh; `latestUnplayedForPodcast(id, 3)`; `downloadedNotInLatest(id, keepIds)` for pruning; `updatePosition`; `markPlayed`; `setDownloadState`; `setAdBlocks`; `lastPlayed()` for resumption.

## Data flows

1. Search: debounce 400 ms, min 2 chars → iTunes → list. Subscribe → upsert podcast → `RefreshFeeds(id)` inline → enqueue downloads for latest 3.
2. Refresh (worker every 3 h, pull-to-refresh, subscribe): conditional GET → parse → upsert episodes → enqueue downloads for latest 3 unplayed with `autoDownload` → prune played episodes outside the latest-3 set.
3. Download worker: `DOWNLOADING` → stream to `.part` → rename → measure duration → `DOWNLOADED` → `ResolveAdBlocks`. Retry with backoff up to 3 times, then `FAILED`. Start without `setForeground`; add `dataSync` foreground only if downloads get killed.
4. Play: `PlayerConnection.play(episode)` → `setMediaItem(localFile or stream URL, positionMs)`. Service loads ad blocks on `onMediaItemTransition`; registers the accelerometer on `onIsPlayingChanged(true)`.
5. Skip: every input ends in `player.seekForward()` / `seekBack()` on `ShakecastPlayer` with increments from Settings, plus haptic and a Timber log of the source.
6. Position saved every 5 s while playing and on pause/transition/destroy. `STATE_ENDED` → `MarkEpisodePlayed` → delete file → reset state → enqueue next download to backfill the slot.

## Playback service

`PlaybackService : MediaSessionService` (`@AndroidEntryPoint`). ExoPlayer with speech audio attributes, audio focus, becoming-noisy, `WAKE_MODE_LOCAL`, seek increments 30 s / 15 s. Wrapped in `ShakecastPlayer : ForwardingSimpleBasePlayer` which applies the tunable increments, removes `COMMAND_SEEK_TO_NEXT/PREVIOUS` from available commands, and (when the chord setting is on) reports a remote `DeviceInfo` (see volume chord).

`MediaSession.Builder(...).setCallback(...).setSessionActivity(NowPlaying intent).setMediaButtonPreferences(listOf(back15, forward30))` where the buttons are `CommandButton.Builder(ICON_SKIP_BACK_15 / ICON_SKIP_FORWARD_30)` with player commands `COMMAND_SEEK_BACK` / `COMMAND_SEEK_FORWARD` in `SLOT_BACK` / `SLOT_FORWARD`. `DefaultMediaNotificationProvider` then renders -15 / play-pause / +30 as the compact actions below API 33 and System UI honours the slots on 33+. If the user changes an increment away from 15/30, rebuild with the generic icons. Custom session commands `SKIP_FORWARD`, `SKIP_BACK`, `TOGGLE_AUTO_SKIP` exist for in-app controllers. Implement `onPlaybackResumption` returning the last-played episode. `onTaskRemoved` stops the service only when not playing.

Headset buttons (`onMediaButtonEvent`): for `MEDIA_PLAY_PAUSE` / `HEADSETHOOK` on `ACTION_DOWN` with repeat 0, feed `MediaButtonClickCounter` (window 450 ms); resolve 1 → play/pause, 2 → +30, 3+ → -15. Return `true` for DOWN and UP so Media3's built-in double-tap-to-next never fires. `MEDIA_NEXT` / `SKIP_FORWARD` / `FAST_FORWARD` → +30; `MEDIA_PREVIOUS` / `SKIP_BACKWARD` / `REWIND` → -15 (many headsets translate their own multi-clicks into these before Android sees them).

Ad auto-skip (`AdSkipController`): 500 ms tick on the main looper while playing. If natural playback crossed `block.start` since the last tick and more than 3 s of the block remains → `seekTo(block.end)`, double-tick haptic, emit a toast event. `onPositionDiscontinuity(SEEK)` resets the last position without skipping, so deliberate seeks into an ad are respected. Gated on global setting AND per-podcast flag AND `adSource in (SKIPLIST, TRANSCRIPT)`. Chosen over `PlayerMessage` because messages are per-item, skip on seeks, and cannot tell user seeks from playback. `AdMarkerSeekBar` draws blocks as translucent spans over the slider.

Shake (`ShakeDetector`, pure `onSample(tNs, x, y, z)`): accelerometer at `SENSOR_DELAY_GAME` (~50 Hz; `HIGH_SAMPLING_RATE_SENSORS` not needed below 200 Hz), magnitude in g using `event.timestamp`. Count rising-edge peaks above threshold spaced ≥ 80 ms; trigger when `peaksRequired` peaks fall inside `windowMs`; 1500 ms cooldown; ignore first 500 ms after registration. Presets: LOW 3.2 g / 3 peaks / 700 ms, MEDIUM (default) 2.7 g / 3 / 700, HIGH 2.2 g / 2 / 600. Walking (~1.5 g at 2 Hz) and running (~2.5 g at 3 Hz) stay below MEDIUM; UI warns HIGH may trigger while running. Registered only while playing (playback's wake lock keeps the sensor alive with the screen off; fall back to the wake-up accelerometer variant if a real device drops events). Settings has a live "test shake" peak counter.

Volume chord (`VolumeChordDetector`, default off): with the setting on, `ShakecastPlayer.getState()` reports `DeviceInfo(PLAYBACK_TYPE_REMOTE)` with the `STREAM_MUSIC` range and advertises the device-volume commands. Media3's legacy stub then routes hardware volume keys to `handleIncreaseDeviceVolume` / `handleDecreaseDeviceVolume` while our session is active, including with the screen off. Each press still adjusts `STREAM_MUSIC` immediately, so normal volume control keeps working. Chord: UP then DOWN within 500 ms → +30 s; DOWN then UP → -15 s (the pair cancels, so volume ends where it started). Limitations to state in the UI: only works while our session is the active media session; the system volume panel shows a generic slider; some OEM skins may ignore remote sessions; all `@UnstableApi`. Foreground-Activity key handling and an AccessibilityService were rejected (pocket use and heavy permissions respectively).

## Ad detection port

`AdDetector.detect(segments, totalDuration)` reproduces `skip_ads.py main()` ordering: two-pass keyword/regex/contextual detection with ±2 s pad and 15 s merge, plus pre-roll and post-roll, sort, merge again at 15 s, `fillAdGaps(90)`, then content ranges and `adTimeSaved`. Phrases and regexes copied verbatim. Fixture test asserts block start/end within 1e-6 and `adTimeSaved` within 0.01 for every fixture pair.

`ResolveAdBlocks(episodeId)` after download (or lazily when streaming): measure actual duration → if `skipListBaseUrl` set, fetch `<key>_skiplist.json`; accept when `|totalDuration - actual| ≤ 2 s` → `SKIPLIST`. Else if `transcriptUrl` set, fetch, parse, coalesce; accept when transcript end is within 2 s of actual → run detector → `TRANSCRIPT`. A mismatch means dynamically inserted ads shifted this download → `MISALIGNED`, auto-skip off, UI shows "ad markers unavailable: dynamic ads". Otherwise `NONE`; retry at most once per 24 h. Cache result in Room; never store transcripts.

## Settings

skipForwardSec 30, skipBackSec 15, shakeEnabled true, shakeSensitivity MEDIUM, volumeChordEnabled false, autoSkipAdsEnabled true, hapticsEnabled true, playbackSpeed 1.0, downloadWifiOnly true, episodesToKeepPerShow 3, autoDeleteWhenPlayed true, refreshIntervalHours 3, skipListBaseUrl "" (e.g. `http://<mac-ip>:8787`). Screen groups: Skipping, Ads (with "Test connection"), Downloads (with "Refresh now"), About.

## Manifest

Permissions: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS, WAKE_LOCK, VIBRATE, RECEIVE_BOOT_COMPLETED. Not needed: HIGH_SAMPLING_RATE_SENSORS, storage permissions, MODIFY_AUDIO_SETTINGS. `MainActivity` singleTask launcher; `PlaybackService` exported with `foregroundServiceType="mediaPlayback"` and the `androidx.media3.session.MediaSessionService` intent filter; `androidx.media3.session.MediaButtonReceiver` for `MEDIA_BUTTON`; remove the default WorkManager initializer; `networkSecurityConfig` with cleartext permitted.

## Implementation order

1. Scaffold: install platform 36, create project files, wrapper 8.14.3, Hilt app, hello-world Compose screen, `git init`. Verify `./gradlew assembleDebug` under JDK 17 and install on `wisp35`.
2. Pure domain + tests: models, `domain/ads`, `TranscriptParser`, fixtures. `./gradlew testDebugUnitTest` green.
3. Room + Settings.
4. Network: iTunes API, `RssFeedParser` + test, fetchers, skip-list client.
5. Repositories + use cases.
6. UI shell: theme, nav, Search, Home, PodcastDetail, Settings. Subscribe and refresh work on the emulator.
7. Downloads: file store, workers, schedulers, `HiltWorkerFactory`. Files appear under `/sdcard/Android/data/com.rogerbos.shakecast/files/Podcasts/`.
8. Playback service minimal: ExoPlayer, session, `MediaItemFactory`, `PlayerConnection`, mini player, position persistence. Confirm notification playback after swiping the app away (Android 14 FGS path).
9. Skip controls: `ShakecastPlayer`, button preferences, custom commands, `NowPlayingScreen` with `BigSkipButton` filling the bottom third (+30 primary, -15 strip above), `AdMarkerSeekBar`, speed.
10. Headset 1/2/3 clicks + test.
11. Shake + test, `AccelerometerSource`, register-on-play.
12. Volume chord + test.
13. Ad auto-skip + fake-player test, per-show toggle, MISALIGNED banner; test against a Mac `python3 -m http.server` serving `podcast3r/transcripts` reached from the emulator at `10.0.2.2:<port>`.
14. Polish: notification permission rationale, resumption, mark-played/delete/prune, task-removed handling, icon, strings audit.

## Verification

- Unit: `./gradlew testDebugUnitTest`; the fixture test must pass for all copied pairs.
- Build: `./gradlew assembleDebug` from Terminal (JDK 17) and a Studio sync (JBR 21).
- Emulator `wisp35`: search "Bankless", subscribe, watch latest 3 download, play from Home, kill app, resume from notification.
- Headset: `adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE` twice within 0.2 s → position jumps +30 s; `KEYCODE_MEDIA_NEXT` → +30 s.
- Shake: Extended Controls > Virtual sensors, or `adb emu sensor set acceleration` alternating ±40 m/s² at 100 ms; look for the "skip source=shake" log and the position jump; confirm a slow 2 Hz wobble does not trigger.
- Volume chord: with playback running and screen off (`KEYCODE_SLEEP`), `KEYCODE_VOLUME_UP` then `KEYCODE_VOLUME_DOWN` within 0.5 s → +30 s; single presses still change volume.
- Ad skip: serve a skip-list from the Mac, play the matching episode, confirm the jump at the first block with a haptic and toast, and that a manual seek into a block is not bounced.
- Final check on a real phone with gloves, since the emulator cannot validate OEM handling of remote-volume sessions or wake-up sensor delivery.

## Risks

- Android 14+ foreground-service start rules: only start playback from the app, the notification, or media buttons; keep `onPlaybackResumption` fast.
- Missing notification permission hides the media notification but playback still runs; show an in-app banner.
- `setMediaButtonPreferences`, slots, and `ForwardingSimpleBasePlayer` device-volume handlers are unstable API; pin Media3 1.9.0.
- Dynamic ad insertion shifts timestamps; the 2 s alignment guard disables auto-skip rather than skipping content. Offset recovery is a follow-up.
- iTunes Search allows roughly 20 requests per minute per IP; debounce and handle 403/429.
- Doze defers the 3 h refresh; accept it and rely on pull-to-refresh for immediacy.
- Feed quirks: missing guid, varied duration formats, HTML descriptions, huge feeds (cap 200 items), non-UTF-8 encodings, transcript URLs that 403 without a normal User-Agent.
