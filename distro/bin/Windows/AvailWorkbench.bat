REM ***  Launches the Avail workbench.  Adjust as needed, such as the path to
REM ***  the Avail home directory.

set AVAIL_HOME=C:\Users\Avail\workspace\Avail
set temp_cp=%AVAIL_HOME%\distro\lib\AvailDev.jar
set temp_cp=%temp_cp%;%AVAIL_HOME%\distro\lib\Avail.jar
set temp_cp=%temp_cp%;%AVAIL_HOME%\distro\lib\AvailServer.jar
set temp_cp=%temp_cp%;%AVAIL_HOME%\libraries\asm-6.0.jar
set temp_cp=%temp_cp%;%AVAIL_HOME%\libraries\asm-analysis-6.0.jar
set temp_cp=%temp_cp%;%AVAIL_HOME%\libraries\asm-util-6.0.jar
set temp_cp=%temp_cp%;%AVAIL_HOME%\libraries\jsr305.jar

REM ***  This sets up the "avail" and "examples" roots.  Editing and saving
REM ***  with the Preferences window will update the Windows per-user app data,
REM ***  which will only be used next time if the (-D)availRoots definition is
REM ***  removed from the command line.

set temp_roots=avail=%AVAIL_HOME%\repositories\avail.repo,%AVAIL_HOME%\distro\src\avail
set temp_roots=%temp_roots%;examples=%AVAIL_HOME%\repositories\examples.repo,%AVAIL_HOME%\distro\src\examples

java -Xmx2g -DavailRoots="%temp_roots%" -cp "%temp_cp%" com.avail.environment.AvailWorkbench
