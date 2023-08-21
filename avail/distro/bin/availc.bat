REM ***  Launches the Avail CLI.  Assumes %AVAIL_HOME% is already set.

set temp_cp=%AVAIL_HOME%\distro\lib\avail-cli.jar
java -Xmx4g -jar "%temp_cp%"
