@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if not exist "%JAVA_EXE%" (
    echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
    echo.
    echo Please set the JAVA_HOME variable in your environment to match the
    echo location of your Java installation.
    exit /b 1
)

if not exist "%JAR%" (
    echo ERROR: Gradle wrapper JAR not found: %JAR%
    exit /b 1
)

setlocal enabledelayedexpansion
for /F "usebackq delims==" %%%%A in ("%APP_HOME%\gradle\wrapper\gradle-wrapper.properties") do set %%%%A
endlocal & set DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% %GRADLE_OPTS% %JAVA_OPTS%

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
