REM ***  Launches the Avail CLI.  Assumes %AVAIL_HOME% is already set.

set temp_cp=%AVAIL_HOME%\distro\lib\avail-cli-1.4-all.jar
java -Xmx4g -jar "%temp_cp%"
