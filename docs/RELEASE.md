# Releasing Shakecast

## One-time setup

1. **Upload key.** Run `scripts/make-keystore.sh` from the repo root. It creates `shakecast-upload.jks` and `keystore.properties`, both gitignored. Back the `.jks` file up outside the repo. On another machine the same values can be supplied through `SHAKECAST_KEYSTORE`, `SHAKECAST_KEYSTORE_PASSWORD`, `SHAKECAST_KEY_ALIAS` and `SHAKECAST_KEY_PASSWORD`.
2. **Play Console.** Create the app and enrol in Play App Signing. Upload the first bundle built with the key above; Play keeps the app signing key and your key becomes the upload key.
3. **Sentry (optional).** Create a Sentry project and put its DSN in `shakecast.sentryDsn` in `~/.gradle/gradle.properties` (the user-level file, not the tracked one in the repo) or in the `SHAKECAST_SENTRY_DSN` environment variable. Leave the tracked `gradle.properties` value blank so the DSN never lands in a commit. The switch in Settings > About only appears when the build carries a DSN. Stack traces stay readable without uploading mapping files because release builds keep names (`-dontobfuscate`); the mapping file is still written to `app/build/outputs/mapping/release/` if you later want it.
4. **Supabase.** Rerun `supabase/schema.sql` on the project after upgrading; the in-app Delete account button calls a `delete_account` function it creates, and until then the button reports that the function is missing. For a public listing, the project URL and anon key should be baked into the build rather than typed by the user. Until then the Settings fields remain.

## Every release

1. Bump `shakecast.versionCode` (must increase on every upload) and `shakecast.versionName` in `gradle.properties`.
2. Build and test:

   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest bundleRelease
   ```

   The bundle is at `app/build/outputs/bundle/release/app-release.aab`. For a signed APK to sideload on a phone use `assembleRelease` and `app/build/outputs/apk/release/app-release.apk`.
3. Install the release APK on a real phone and run through: search and subscribe, download, play with the screen off, shake, notification buttons, sign in and sync, OPML export. R8 only runs in release builds, so this is the first time shrinking is exercised.
4. Upload the bundle to a Play testing track, then promote.

## Play listing requirements

- **Privacy policy URL:** `https://github.com/rogerjbos/shakecast/blob/main/PRIVACY.md` (also linked from Settings > About).
- **Account deletion:** the app has in-app deletion under Settings > Sync > Delete account. Play also asks for a web URL describing how to delete an account; the privacy policy's "Deleting your account" section serves, so give the same URL.
- **Data safety form**, as the app behaves today:
  - Collected only when the user turns the feature on: email address (sync account), app activity in the form of subscriptions, play positions and settings (sync), crash logs and device diagnostics (crash reports). No device or other IDs: the Sentry per-install id is stripped before sending.
  - Not shared with third parties for advertising. Supabase and Sentry act as processors for the optional features above.
  - Data is encrypted in transit (HTTPS to Supabase and Sentry; podcast feeds use whatever the publisher offers, and the app permits plain HTTP because many feeds and enclosures still use it).
  - Users can request deletion in-app.
- **Target SDK:** 36, which Play requires for new apps and updates since August 2026.
- **Foreground service type** `mediaPlayback` is declared; Play's policy form for foreground services should cite background audio playback.
- **Android Auto:** the app declares the Auto media descriptor; Play may require submitting the Android Auto review form before Auto features go live.

## What release builds change

- R8 shrinks code and resources with the rules in `app/proguard-rules.pro`; names are kept, so crash traces read like debug ones.
- The HTTP logging interceptor and Timber debug tree are compiled out; warnings and errors go to Sentry breadcrumbs instead, if enabled.
- The app id is `com.rogerbos.shakecast` without the `.debug` suffix, so a release build installs beside the debug build with its own data.
