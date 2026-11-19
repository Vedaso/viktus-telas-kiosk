#!/usr/bin/env bash
#
# publicar.sh - builds the signed release APK on this machine and opens a DRAFT GitHub Release.
# Replaces .github/workflows/release.yaml (19/11/2026, Victor: "remove isso do github testes locais
# e deploy pela maquina"). The old workflow ran on every pushed tag v*; this one is run by hand.
#
#   bash scripts/publicar.sh
#
# Flow:
#   1. HEAD must carry the tag v<versionName> (versionName from app/build.gradle.kts); the script
#      pushes that tag to origin, as the old flow did when the tag was pushed.
#   2. Signing secrets come from Claude/secrets/viktus-telas-kiosk/keystore.env (parsed, never
#      sourced) and release.jks, which is copied to app/keystore.jks (gitignored) and always removed
#      on exit. Losing that keystore means the installed sticks can no longer update.
#   3. gradlew assembleRelease, APK renamed to ViktusTelas_v<versionName>.apk.
#   4. Draft release with the APK (draft: the owner publishes it by hand, as before), then a check
#      that the release exists and carries the APK.
#
# Needs: JDK, Android SDK (ANDROID_HOME or local.properties sdk.dir; platform 37), gh logged in.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

SECRETS_DIR="${KIOSK_SECRETS:-$HOME/OneDrive/Documentos/Claude/secrets/viktus-telas-kiosk}"
KEYSTORE_SRC="$SECRETS_DIR/release.jks"
ENV_FILE="$SECRETS_DIR/keystore.env"

[ -f "$ENV_FILE" ] || { echo "missing $ENV_FILE"; exit 1; }
[ -f "$KEYSTORE_SRC" ] || { echo "missing $KEYSTORE_SRC"; exit 1; }
for k in KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  v=$(grep -E "^$k=" "$ENV_FILE" | head -1 | cut -d= -f2-)
  [ -n "$v" ] || { echo "$k not found in $ENV_FILE"; exit 1; }
  export "$k=$v"
done

command -v gh >/dev/null 2>&1 || { echo "gh not found"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "java not found"; exit 1; }
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  echo "Android SDK not configured: set ANDROID_HOME or create local.properties with sdk.dir"
  exit 1
fi

VERSION_NAME=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
[ -n "$VERSION_NAME" ] || { echo "versionName not found in app/build.gradle.kts"; exit 1; }
TAG="v$VERSION_NAME"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null \
  || { echo "tag $TAG does not exist. Create it on the release commit: git tag $TAG"; exit 1; }
[ "$(git rev-parse "$TAG^{commit}")" = "$(git rev-parse HEAD)" ] \
  || { echo "tag $TAG does not point at HEAD: check out the tagged commit or move the tag"; exit 1; }
[ -z "$(git status --porcelain --untracked-files=no)" ] \
  || { echo "working tree has uncommitted changes"; exit 1; }

limpar() { rm -f app/keystore.jks; }
trap limpar EXIT INT TERM

echo "> push tag $TAG"
git push origin "$TAG"

cp "$KEYSTORE_SRC" app/keystore.jks

echo "> assembleRelease"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) ./gradlew.bat assembleRelease --stacktrace ;;  # gradlew may have CRLF in a Windows checkout
  *) sh gradlew assembleRelease --stacktrace ;;
esac

OUT=app/build/outputs/apk/release
META="$OUT/output-metadata.json"
[ -f "$META" ] || { echo "missing $META: build did not produce a release"; exit 1; }
BUILT_NAME=$(grep -o '"versionName": *"[^"]*"' "$META" | head -1 | sed -E 's/.*: *"([^"]*)"/\1/')
VERSION_CODE=$(grep -o '"versionCode": *[0-9]*' "$META" | head -1 | grep -o '[0-9]*$')
[ "$BUILT_NAME" = "$VERSION_NAME" ] || { echo "built versionName $BUILT_NAME differs from $VERSION_NAME"; exit 1; }

APK="$OUT/ViktusTelas_v$VERSION_NAME.apk"
mv "$OUT"/*.apk "$APK"
[ -s "$APK" ] || { echo "empty APK"; exit 1; }
echo "version $VERSION_NAME (code $VERSION_CODE): $APK"

NOTES="metadata/en-US/changelogs/${VERSION_CODE}.txt"
NOTES_ARGS=(--notes "")
if [ -f "$NOTES" ]; then
  NOTES_ARGS=(--notes-file "$NOTES")
else
  echo "no changelog at $NOTES, the release body will be empty"
fi

echo "> draft release $TAG"
gh release create "$TAG" "$APK" --draft --title "$TAG" "${NOTES_ARGS[@]}"

echo "> smoke: the release exists and carries the APK"
gh release view "$TAG" --json isDraft,assets --jq '"draft=\(.isDraft) assets=\([.assets[].name] | join(","))"' \
  | tee /dev/stderr | grep -q "ViktusTelas_v$VERSION_NAME.apk" \
  || { echo "release $TAG is missing the APK"; exit 1; }

echo "OK: draft release $TAG created. Publish it on GitHub when ready (the sticks download from there)."
