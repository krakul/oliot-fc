@echo off
echo Welcome to SensX SDK getting script

:: -------------------------------------------------------------------------------
:: This scripts downloads Sensthys SensX EXTREME Java SDK from Sensthys website
:: and publishes it in local Maven repository.
:: -------------------------------------------------------------------------------

:: URL on 2022 January. Version 1.0.3.
set SDK_URL=https://www.sensthys.com/wp-content/uploads/2020/11/Java_SDK.zip

:: Local zip file
set SDK_ZIP=sensx_java_sdk.zip

:: Jar relative path inside zip
set SDK_ZIP_JAR=Java_SDK/javajar.jar

:: Local jar file
set SDK_JAR=sensx.jar

:: -------------------------------------------------------------------------------

:: Try to download
echo Downloading...
call curl %SDK_URL% --output %SDK_ZIP%
IF %ERRORLEVEL% NEQ 0 (
	exit /b
)

:: Try to extract
echo Extracting...
call tar --extract -O --file=%SDK_ZIP% %SDK_ZIP_JAR% > %SDK_JAR%
IF %ERRORLEVEL% NEQ 0 (
	exit /b
)

:: Try to install Jar in local repository
echo Installing...
call mvn install:install-file ^
  -Dfile=%SDK_JAR%        ^
  -DgroupId="sensthys"    ^
  -DartifactId="sensx"    ^
  -Dversion=1.0.3         ^
  -Dpackaging=jar         ^
  -DgeneratePom=true
IF %ERRORLEVEL% NEQ 0 (
	exit /b
)

:: Remove temp files
echo Clean-up...
del %SDK_JAR%
del %SDK_ZIP%

:: Finish
echo Success