REM ***  Launches the Avail workbench.  Assumes %AVAIL_HOM% is already set.

set temp_cp=%AVAIL_HOME%\distro\lib\avail-workbench-1.4.jar

REM ***  This sets up the "avail" and "examples" roots.  Editing and saving
REM ***  with the Preferences window will update the Windows per-user app data,
REM ***  which will only be used next time if the (-D)availRoots definition is
REM ***  removed from the command line.

java -Xmx4g -DavailRoots="%temp_roots%" -jar "%temp_cp%"
