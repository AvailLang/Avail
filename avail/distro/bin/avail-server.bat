REM ***  Launches the Avail server.  Assumes %AVAIL_HOME% is already set.

set temp_cp=%AVAIL_HOME%\distro\lib\avail-server.jar
java -Xmx4g -jar "%temp_cp%"
