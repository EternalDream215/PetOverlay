#!/bin/sh
# Gradle wrapper script - downloads and runs Gradle
# Auto-generated for CI/CD

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java \
  $DEFAULT_JVM_OPTS \
  $JAVA_OPTS \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"