#!/usr/bin/env bash
# Render build step (no Docker). Render's native runtimes have no Java, so this downloads a
# JDK 21 into the service directory, builds the jar, and leaves both in place for the start step.
# Tests are not run here: they need a PostgreSQL test database (run them locally with mvnw verify).
set -euo pipefail

JDK_DIR="$PWD/.jdk"
if [ ! -x "$JDK_DIR/bin/java" ]; then
  echo "Downloading Eclipse Temurin JDK 21..."
  mkdir -p "$JDK_DIR"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    | tar -xz -C "$JDK_DIR" --strip-components=1
fi
export JAVA_HOME="$JDK_DIR"
"$JAVA_HOME/bin/java" -version

chmod +x ./mvnw
./mvnw -B -q -DskipTests package
cp target/inventory-backend-*.jar app.jar
echo "Built $(ls -lh app.jar)"
