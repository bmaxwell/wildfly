@echo off
rem -------------------------------------------------------------------------
rem JBoss Bootstrap Script for Windows
rem -------------------------------------------------------------------------

rem $Id$

@if not "%ECHO%" == ""  echo %ECHO%
@if "%OS%" == "Windows_NT" setlocal
rem Set to all parameters by default
set SERVER_OPTS=%*

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=.\
)

pushd "%DIRNAME%.."
set "RESOLVED_JBOSS_HOME=%CD%"
popd

if "x%JBOSS_HOME%" == "x" (
  set "JBOSS_HOME=%RESOLVED_JBOSS_HOME%"
)

pushd "%JBOSS_HOME%"
set "SANITIZED_JBOSS_HOME=%CD%"
popd

if "%RESOLVED_JBOSS_HOME%" NEQ "%SANITIZED_JBOSS_HOME%" (
    echo WARNING JBOSS_HOME may be pointing to a different installation - unpredictable results may occur.
)

rem Read command-line args.
:READ-ARGS
if "%1" == "" (
   goto MAIN
) else if "%1" == "-secmgr" (
   set SECMGR=true
)
shift
goto READ-ARGS

:MAIN


rem Read an optional configuration file.
if "x%DOMAIN_CONF%" == "x" (
   set "DOMAIN_CONF=%DIRNAME%domain.conf.bat"
)
if exist "%DOMAIN_CONF%" (
   echo Calling "%DOMAIN_CONF%"
   call "%DOMAIN_CONF%" %*
) else (
   echo Config file not found "%DOMAIN_CONF%"
)

set DIRNAME=

if "%OS%" == "Windows_NT" (
  set "PROGNAME=%~nx0%"
) else (
  set "PROGNAME=domain.bat"
)

rem Setup JBoss specific properties
set "JAVA_OPTS=-Dprogram.name=%PROGNAME% %JAVA_OPTS%"

if "x%JAVA_HOME%" == "x" (
  set  JAVA=java
  echo JAVA_HOME is not set. Unexpected results may occur.
  echo Set JAVA_HOME to the directory of your local JDK to avoid this message.
) else (
  if not exist "%JAVA_HOME%" (
    echo JAVA_HOME "%JAVA_HOME%" path doesn't exist
    goto END
  ) else (
    echo Setting JAVA property to "%JAVA_HOME%\bin\java"
    set "JAVA=%JAVA_HOME%\bin\java"
  )
)

rem Add -server to the JVM options, if supported
"%JAVA%" -server -version 2>&1 | findstr /I hotspot > nul
if not errorlevel == 1 (
  set "PROCESS_CONTROLLER_JAVA_OPTS=%PROCESS_CONTROLLER_JAVA_OPTS% -server"
  set "HOST_CONTROLLER_JAVA_OPTS=%HOST_CONTROLLER_JAVA_OPTS% -server"
)

rem Find run.jar, or we can't continue
if exist "%JBOSS_HOME%\jboss-modules.jar" (
    set "RUNJAR=%JBOSS_HOME%\jboss-modules.jar"
) else (
  echo Could not locate "%JBOSS_HOME%\jboss-modules.jar".
  echo Please check that you are in the bin directory when running this script.
  goto END
)

rem Setup directories, note directories with spaces do not work
set "CONSOLIDATED_OPTS=%JAVA_OPTS% %SERVER_OPTS%"
:DIRLOOP
echo(%CONSOLIDATED_OPTS% | findstr /r /c:"^-Djboss.domain.base.dir" > nul && (
  for /f "tokens=1,2* delims==" %%a IN ("%CONSOLIDATED_OPTS%") DO (
    for /f %%i IN ("%%b") DO set "JBOSS_BASE_DIR=%%~fi"
  )
)
echo(%CONSOLIDATED_OPTS% | findstr /r /c:"^-Djboss.domain.config.dir" > nul && (
  for /f "tokens=1,2* delims==" %%a IN ("%CONSOLIDATED_OPTS%") DO (
    for /f %%i IN ("%%b") DO set "JBOSS_CONFIG_DIR=%%~fi"
  )
)
echo(%CONSOLIDATED_OPTS% | findstr /r /c:"^-Djboss.domain.log.dir" > nul && (
  for /f "tokens=1,2* delims==" %%a IN ("%CONSOLIDATED_OPTS%") DO (
    for /f %%i IN ("%%b") DO set "JBOSS_LOG_DIR=%%~fi"
  )
)

for /f "tokens=1* delims= " %%i IN ("%CONSOLIDATED_OPTS%") DO (
  if %%i == "" (
    goto ENDDIRLOOP
  ) else (
    set CONSOLIDATED_OPTS=%%j
    GOTO DIRLOOP
  )
)

:ENDDIRLOOP

rem check the PROCESS_CONTROLLER_JAVA_OPTS
set "X_JAVA_OPTS=%PROCESS_CONTROLLER_JAVA_OPTS%"
:JAVAOPTLOOP
rem Ensure to disable the -secmgr if the -Djava.security.manager property is found
echo(%X_JAVA_OPTS% | findstr /r /c:"^-Djava.security.manager" > nul && (
  if "%SECMGR%" == "true" (
    echo ERROR: Cannot use -secmgr when the java.security.manager property is set in the JAVA_OPTS. Disabling -secmgr.
    set SECMGR=false
  )
)

for /f "tokens=1* delims= " %%i IN ("%X_JAVA_OPTS%") DO (
  if %%i == "" (
    goto ENDJAVAOPTLOOP
  ) else (
    set X_JAVA_OPTS=%%j
    GOTO JAVAOPTLOOP
  )
)
:ENDJAVAOPTLOOP


rem Setup JBoss specific properties

rem Set default module root paths
if "x%JBOSS_MODULEPATH%" == "x" (
  set  "JBOSS_MODULEPATH=%JBOSS_HOME%\modules"
)

rem Set the domain base dir
if "x%JBOSS_BASE_DIR%" == "x" (
  set  "JBOSS_BASE_DIR=%JBOSS_HOME%\domain"
)
rem Set the domain log dir
if "x%JBOSS_LOG_DIR%" == "x" (
  set  "JBOSS_LOG_DIR=%JBOSS_BASE_DIR%\log"
)
rem Set the domain configuration dir
if "x%JBOSS_CONFIG_DIR%" == "x" (
  set  "JBOSS_CONFIG_DIR=%JBOSS_BASE_DIR%\configuration"
)

rem Set the module options
set "MODULE_OPTS="
if "%SECMGR%" == "true" (
    set "MODULE_OPTS=-secmgr"
)

echo ===============================================================================
echo.
echo   JBoss Bootstrap Environment
echo.
echo   JBOSS_HOME: "%JBOSS_HOME%"
echo.
echo   JAVA: "%JAVA%"
echo.
echo   JAVA_OPTS: "%JAVA_OPTS%"
echo.
echo ===============================================================================
echo.

:RESTART
"%JAVA%" %PROCESS_CONTROLLER_JAVA_OPTS% ^
 "-Dorg.jboss.boot.log.file=%JBOSS_LOG_DIR%\process-controller.log" ^
 "-Dlogging.configuration=file:%JBOSS_CONFIG_DIR%/logging.properties" ^
    -jar "%JBOSS_HOME%\jboss-modules.jar" ^
    %MODULE_OPTS% ^
    -mp "%JBOSS_MODULEPATH%" ^
     org.jboss.as.process-controller ^
    -jboss-home "%JBOSS_HOME%" ^
    -jvm "%JAVA%" ^
    %MODULE_OPTS% ^
    -mp "%JBOSS_MODULEPATH%" ^
    -- ^
    "-Dorg.jboss.boot.log.file=%JBOSS_LOG_DIR%\host-controller.log" ^
    "-Dlogging.configuration=file:%JBOSS_CONFIG_DIR%/logging.properties" ^
    %HOST_CONTROLLER_JAVA_OPTS% ^
    -- ^
    -default-jvm "%JAVA%" ^
    %*

if ERRORLEVEL 10 goto RESTART

:END
if "x%NOPAUSE%" == "x" pause

:END_NO_PAUSE