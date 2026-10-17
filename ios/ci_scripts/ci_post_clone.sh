#!/bin/sh
set -eu

# Symbol upload runs during archive only when the scoped CI credential is set.
if [ -n "${POSTHOG_CLI_API_KEY:-}" ]; then
  brew install posthog/tap/posthog-cli
fi
