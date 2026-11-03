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

"$KEYTOOL" -genkeypair -v \
  -keystore "$STORE" -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Shakecast, O=Roger Bos, C=US"

cat > keystore.properties <<PROPS
storeFile=$STORE
storePassword=$PASSWORD
keyAlias=$ALIAS
keyPassword=$PASSWORD
PROPS
chmod 600 keystore.properties

echo
echo "Wrote $STORE and keystore.properties (both gitignored)."
echo "Build a signed bundle with: ./gradlew bundleRelease"
