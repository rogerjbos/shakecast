# Shakecast privacy policy

Last updated: 2 November 2026

Shakecast is a podcast player for Android published by Roger Bos ("the developer"). This policy explains what the app does with your data. The short version: everything stays on your phone unless you turn on sync or crash reports, and both are off until you choose them.

## Data that stays on your phone

Shakecast stores the following in the app's private storage on your device:

- The podcasts you subscribe to and the episodes in their feeds.
- Downloaded audio files, play positions, played status, your queue and the spans you skipped by hand.
- Your settings, including shake sensitivity and skip lengths.

The developer has no access to any of this. It is removed when you uninstall the app. Downloaded audio and your sign-in session are excluded from Android cloud backup.

## Network requests the app makes

- **Podcast search** sends your search text to Apple's iTunes Search API.
- **Feeds, artwork, audio, transcripts and chapters** are fetched from the servers of each podcast's publisher or host. Those servers see your IP address and the app's user agent, as any podcast app's requests do. Shakecast sends nothing else.

Shakecast contains no advertising and no analytics.

## Sync (optional)

If you turn on sync and sign in, Shakecast stores the following in a [Supabase](https://supabase.com) project so your other phones can read it:

- Your email address and a password, held by Supabase Auth. The app never stores the password; it keeps a session token on the phone.
- Your subscriptions (feed addresses, titles, artwork addresses), per-show skip lengths, play positions, played status, remembered skips and app settings.

The Supabase project is the one whose address you enter in Settings. Supabase's own handling of that data is described in their [privacy policy](https://supabase.com/privacy). Rows are protected by row level security so a signed-in user can read and write only their own.

**Deleting your account:** Settings > Sync > Delete account removes the account and every row it owns immediately. Signing out keeps the account but removes the session from the phone.

## Crash reports (optional)

If you turn on "Send crash reports" in Settings > About, a report is sent to [Sentry](https://sentry.io) when the app crashes. It contains the stack trace, the app version, the Android version, the device model and short labels of recent warnings such as "Feed refresh failed". It does not contain your email address, feed addresses, show titles, file names, listening history or an advertising identifier. Sentry's handling of that data is described in their [privacy policy](https://sentry.io/privacy/). The switch is off by default and can be turned off at any time.

## Permissions

- **Notifications**: the playback controls notification and download progress.
- **Internet and network state**: fetching feeds and audio, and limiting downloads to unmetered networks.
- **Foreground service, wake lock**: playing audio with the screen off.
- **Vibration**: the short buzz that confirms a skip.
- **Body sensors are not used**; the accelerometer used for shake detection does not require a permission.

## Children

Shakecast is not directed at children under 13 and does not knowingly collect personal information from them.

## Changes and contact

Changes to this policy are published in this file, with the date above updated. Questions can be raised at <https://github.com/rogerjbos/shakecast/issues>.
