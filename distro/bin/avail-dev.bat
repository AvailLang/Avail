REM ***  Launches the Avail workbench.  Assumes %AVAIL_HOME% is already set.

set temp_cp=%AVAIL_HOME%\distro\lib\avail-workbench.jar
java -Xmx4g -jar "%temp_cp%"
