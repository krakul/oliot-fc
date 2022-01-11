@echo off
echo Welcome to CAEN RFID Library installing script

:: -------------------------------------------------------------------------------
:: This scripts installs CAEN RFID Library in local Maven repository.
:: -------------------------------------------------------------------------------

:: Local jar file
set SDK_JAR=CAENRFIDLibrary.jar
set RTX_JAR=RXTXcomm.jar

:: -------------------------------------------------------------------------------

:: Try to install Jar in local repository
echo Installing...
call mvn install:install-file ^
  -Dfile=%SDK_JAR%        ^
  -DgroupId="caen"         ^
  -DartifactId="proton"    ^
  -Dversion=1.0            ^
  -Dpackaging=jar          ^
  -DgeneratePom=true
IF %ERRORLEVEL% NEQ 0 (
  exit /b
)

call mvn install:install-file ^
  -Dfile=%RTX_JAR%        ^
  -DgroupId="gnu"         ^
  -DartifactId="io"    ^
  -Dversion=1.0            ^
  -Dpackaging=jar          ^
  -DgeneratePom=true
IF %ERRORLEVEL% NEQ 0 (
  exit /b
)


:: Finish
echo Success