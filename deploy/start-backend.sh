#!/bin/sh
set -eu
MESSENGER_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
: "${MESSENGER_CONFIG:=$MESSENGER_HOME/config/messenger.properties}"
export MESSENGER_CONFIG
if [ ! -r "$MESSENGER_CONFIG" ]; then
  printf '%s\n' "Missing configuration: $MESSENGER_CONFIG" >&2
  exit 1
fi
exec java -jar "$MESSENGER_HOME/messenger.jar" --spring.profiles.active=prod "$@"
