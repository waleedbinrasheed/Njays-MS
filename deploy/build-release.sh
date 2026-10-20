#!/usr/bin/env bash
# Same as build-release.bat, for a Linux/macOS build machine with Docker:
#   bash deploy/build-release.sh
# The client PC is Windows; the release contains the Windows .bat files.
# Images are built for the build machine's CPU: on an ARM Mac, add --platform linux/amd64.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="$(tr -d '[:space:]' < VERSION)"
OUT="../release/LeadingIMS-$VERSION"

# docker-compose.yml requires these at run time; building needs only placeholders.
export DB_PASSWORD=build-only ADMIN_INITIAL_PASSWORD=build-only

echo "=== Building images for version $VERSION ==="
docker compose -f docker-compose.yml -f docker-compose.build.yml build --pull
docker pull postgres:17-alpine
docker tag leadingims/api:latest "leadingims/api:$VERSION"
docker tag leadingims/web:latest "leadingims/web:$VERSION"

echo "=== Writing $OUT ==="
rm -rf "$OUT" "$OUT.zip"
mkdir -p "$OUT/scripts"
docker save -o "$OUT/images.tar" leadingims/api:latest leadingims/web:latest postgres:17-alpine
cp docker-compose.yml .env.example VERSION INSTALL.txt "$OUT/"
cp scripts/*.sh "$OUT/scripts/"
cp windows/*.bat "$OUT/"

echo "=== Zipping ==="
(cd "$OUT/.." && zip -qr "LeadingIMS-$VERSION.zip" "LeadingIMS-$VERSION")
echo "Release ready: $OUT.zip"
