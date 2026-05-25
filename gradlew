#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")"; pwd)
java -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
