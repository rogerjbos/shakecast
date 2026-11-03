#!/usr/bin/env bash
# Creates the Play upload keystore and keystore.properties next to the repo root.
# Both are gitignored. Back the .jks file up somewhere safe: Play App Signing lets
# you reset a lost upload key, but only after a support request.
set -euo pipefail
cd "$(dirname "$0")/.."

KEYTOOL="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}/bin/keytool"
STORE="shakecast-upload.jks"
ALIAS="upload"

if [[ -f "$STORE" ]]; then
  echo "$STORE already exists; delete it first if you really want a new key." >&2
  exit 1
fi

read -r -s -p "Keystore password (also used for the key): " PASSWORD; echo
read -r -s -p "Repeat password: " PASSWORD2; echo
[[ "$PASSWORD" == "$PASSWORD2" ]] || { echo "Passwords differ." >&2; exit 1; }
[[ ${#PASSWORD} -ge 8 ]] || { echo "Use at least 8 characters." >&2; exit 1; }
[[ "$PASSWORD" == "${PASSWORD#"${PASSWORD%%[![:space:]]*}"}" ]] || { echo "The password must not start with whitespace." >&2; exit 1; }

# The password goes to keytool through the environment, not the command line, so it never shows in `ps`.
export SHAKECAST_KEYSTORE_PASSWORD="$PASSWORD"
"$KEYTOOL" -genkeypair -v \
  -keystore "$STORE" -storepass:env SHAKECAST_KEYSTORE_PASSWORD -keypass:env SHAKECAST_KEYSTORE_PASSWORD \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Shakecast, O=Roger Bos, C=US"

# java.util.Properties treats backslashes as escapes, so double them.
ESCAPED="${PASSWORD//\\/\\\\}"
{
  printf 'storeFile=%s\n' "$STORE"
  printf 'storePassword=%s\n' "$ESCAPED"
  printf 'keyAlias=%s\n' "$ALIAS"
  printf 'keyPassword=%s\n' "$ESCAPED"
} > keystore.properties
chmod 600 keystore.properties

echo
echo "Wrote $STORE and keystore.properties (both gitignored)."
echo "Build a signed bundle with: ./gradlew bundleRelease"
