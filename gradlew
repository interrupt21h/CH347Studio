#!/usr/bin/env sh
# Wrapper launcher: Android Studio also supports running Gradle directly.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo 'Gradle is not installed and gradle-wrapper.jar is not bundled. Open this project in Android Studio or install Gradle 8.11.1.' >&2
exit 1
