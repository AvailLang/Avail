REM ***  Launches the Avail workbench.  Assumes %AVAIL_HOM% is already set.

set temp_cp=%AVAIL_HOME%\distro\lib\avail-workbench-1.4.jar

REM ***  This sets up the "avail" and "examples" roots.  Editing and saving
REM ***  with the Preferences window will update the Windows per-user app data,
REM ***  which will only be used next time if the (-D)availRoots definition is
REM ***  removed from the command line.

set temp_roots=avail=%HOMEPATH%\repositories\avail.repo,%AVAIL_HOME%\distro\src\avail
set temp_roots=%temp_roots%;examples=%HOMEPATH%\repositories\examples.repo,%AVAIL_HOME%\distro\src\examples

java -Xmx4g -DavailRoots="%temp_roots%" -cp "%temp_cp%" com.avail.environment.AvailWorkbench
