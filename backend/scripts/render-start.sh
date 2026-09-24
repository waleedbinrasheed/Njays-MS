#!/usr/bin/env bash
# Render start step: runs the jar with the JDK downloaded during the build.
# Memory settings keep the JVM inside the 512 MB of Render's free instance.
set -euo pipefail
exec "$PWD/.jdk/bin/java" \
  -XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 \
  -jar app.jar
