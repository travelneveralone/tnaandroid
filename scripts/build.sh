#!/usr/bin/env bash
# Builds the Android app for one environment. Usually called as `tna android beta|prod`.
#
#   build.sh beta   APK "TNA β" (com.travelneveralone.android.beta), beta backend - for us, not clients
#   build.sh prod   with keystore.properties: signed AAB for Google Play (upload it by hand
#                   in Play Console - there is no API upload yet);
#                   without it: APK "TNA" (com.travelneveralone.android), prod backend,
#                   the file you send to people directly (see ДИСТРИБУЦИЯ.md)
#
# Nothing is uploaded anywhere: there is no Play account yet. The script builds,
# prints where the file is and opens it in Finder.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ENV="${1:-}"
case "$ENV" in beta|prod) ;; *) echo "usage: $0 beta|prod" >&2; exit 64 ;; esac

# Same defaults as ДИСТРИБУЦИЯ.md; an already exported value wins.
if [ -z "${JAVA_HOME:-}" ]; then
  JDK="$(ls -d /opt/homebrew/Cellar/openjdk@21/*/libexec/openjdk.jdk/Contents/Home 2>/dev/null | sort | tail -1 || true)"
  [ -n "$JDK" ] && export JAVA_HOME="$JDK"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[ -n "${JAVA_HOME:-}" ] || { echo "JAVA_HOME is not set and openjdk@21 is not in Homebrew" >&2; exit 69; }

# Build number shown in Profile, same shape as iOS (YYMMDD.N there, YYMMDD.HHMM
# here); versionCode must only grow, so it counts minutes since 2024-01-01.
TNA_BUILD="${TNA_BUILD:-$(date +%y%m%d.%H%M)}"
TNA_CODE="${TNA_CODE:-$(( ($(date +%s) - 1704067200) / 60 ))}"
GRADLE_BUILD_ARGS=(-PtnaBuild="$TNA_BUILD" -PtnaCode="$TNA_CODE")

if [ "$ENV" = prod ] && [ -f keystore.properties ]; then
  ./gradlew "${GRADLE_BUILD_ARGS[@]}" :app:bundleProdRelease
  OUT="app/build/outputs/bundle/prodRelease/app-prod-release.aab"
  NOTE="upload it in Play Console → TNA → Testing/Production → Create release"
elif [ "$ENV" = prod ]; then
  ./gradlew "${GRADLE_BUILD_ARGS[@]}" :app:assembleProdDebug
  OUT="app/build/outputs/apk/prod/debug/app-prod-debug.apk"
  NOTE="no keystore.properties, so this is the direct-install APK, not a Play build"
else
  ./gradlew "${GRADLE_BUILD_ARGS[@]}" :app:assembleBetaDebug
  OUT="app/build/outputs/apk/beta/debug/app-beta-debug.apk"
  NOTE="beta base - for us, not for clients"
fi

[ -f "$OUT" ] || { echo "build finished but $OUT is missing" >&2; exit 1; }
printf '\n\033[32mandroid %s, build %s:\033[0m %s\n%s\n' "$ENV" "$TNA_BUILD" "$ROOT/$OUT" "$NOTE"
command -v open >/dev/null && open -R "$OUT" || true
