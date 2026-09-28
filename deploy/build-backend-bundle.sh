#!/bin/sh
set -eu
MESSENGER_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$MESSENGER_ROOT/backend"
./mvnw verify "$@"
MESSENGER_BUNDLE="$MESSENGER_ROOT/release/company-messenger-backend"
mkdir -p "$MESSENGER_BUNDLE/config" "$MESSENGER_BUNDLE/docs" "$MESSENGER_BUNDLE/systemd"
cp target/messenger-0.1.0-SNAPSHOT.jar "$MESSENGER_BUNDLE/messenger.jar"
cp "$MESSENGER_ROOT"/deploy/config/*.example "$MESSENGER_BUNDLE/config/"
cp "$MESSENGER_ROOT"/deploy/start-backend.* "$MESSENGER_BUNDLE/"
cp "$MESSENGER_ROOT"/deploy/systemd/*.service "$MESSENGER_BUNDLE/systemd/"
cp "$MESSENGER_ROOT"/docs/intranet-deployment.md "$MESSENGER_BUNDLE/docs/"
# Archive only known distributable files: never bundle a site's real properties/secrets.
cd "$MESSENGER_BUNDLE"
jar -tf messenger.jar > /dev/null
if command -v sha256sum > /dev/null 2>&1; then sha256sum messenger.jar > SHA256SUMS; else shasum -a 256 messenger.jar > SHA256SUMS; fi
tar -czf ../company-messenger-backend.tar.gz messenger.jar SHA256SUMS start-backend.sh start-backend.ps1 config/*.example docs/intranet-deployment.md systemd/company-messenger.service
printf '%s\n' "$MESSENGER_ROOT/release/company-messenger-backend.tar.gz"
