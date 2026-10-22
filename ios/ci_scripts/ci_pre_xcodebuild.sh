#!/bin/sh
set -eu

# Stamp each Xcode Cloud build with its own build number so the archive, its
# dSYMs and the PostHog release all match the build in App Store Connect.
if [ -n "${CI_BUILD_NUMBER:-}" ]; then
  sed -i '' "s/CURRENT_PROJECT_VERSION = [^;]*;/CURRENT_PROJECT_VERSION = ${CI_BUILD_NUMBER};/g" \
    "${CI_PRIMARY_REPOSITORY_PATH}/ios/Grapevine.xcodeproj/project.pbxproj"
fi
