@ECHO OFF

SET DIR=%~dp0
SET APP_BASE_NAME=%~n0

SET DEFAULT_JVM_OPTS=

SET CLASSPATH=%DIR%\gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%CLASSPATH%" (
  ECHO Downloading Gradle wrapper...
  powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.7-bin.zip' -OutFile '%DIR%\gradle-8.7-bin.zip'"
  powershell -Command "Expand-Archive -Path '%DIR%\gradle-8.7-bin.zip' -DestinationPath '%DIR%\gradle'"
  move "%DIR%\gradle\gradle-8.7\lib\gradle-wrapper.jar" "%DIR%\gradle\wrapper\gradle-wrapper.jar"
  rmdir /S /Q "%DIR%\gradle\gradle-8.7"
  del "%DIR%\gradle-8.7-bin.zip"
)

"%DIR%\gradle\wrapper\gradle-wrapper" %*
