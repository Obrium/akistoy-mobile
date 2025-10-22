#!/usr/bin/env sh

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_BASE_NAME=$(basename "$0")

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS=""

CLASSPATH=$DIR/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "Downloading Gradle wrapper..."
  mkdir -p "$DIR/gradle/wrapper"
  curl -sSL "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -o "$DIR/gradle-8.7-bin.zip"
  unzip -q "$DIR/gradle-8.7-bin.zip" -d "$DIR/gradle"
  mv "$DIR/gradle/gradle-8.7/lib/gradle-wrapper.jar" "$DIR/gradle/wrapper/gradle-wrapper.jar"
  rm -rf "$DIR/gradle/gradle-8.7" "$DIR/gradle-8.7-bin.zip"
fi

exec "$DIR/gradle/wrapper/gradle-wrapper" "$@"
