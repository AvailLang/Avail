# Experimental Packaging of the Anvil IDE
The following are the instructions for packaging the Anvil IDE as an executable 
targeting the different operating systems. Note that this is currently in the
experimental phase and has not been fully vetted at time of writing.

1. Run the Gradle task `:avail:shadowJar`
2. Locate the jar in the project root build directory: 
   `avail/build/libs/anvil.jar`
3. From the terminal/powershell run the `jpackage` command as specified below
   for the targeted operating system while on targeted operating system.

## Mac
*NOTE: Mac will not open the application as it is not signed. You will have 
to permit the file to run from Mac's Settings -> Privacy & Security*
```shell
jpackage --input avail/build/libs/ --name Anvil --main-jar anvil.jar --main-class avail.project.AvailProjectManagerRunner --icon avail/src/main/resources/workbench/AvailHammer.icns --type dmg
```

## Windows
```shell
jpackage --input .\avail\build\libs --name Anvil --main-jar anvil.jar --main-class avail.project.AvailProjectManagerRunner --type msi --icon .\avail\src\main\resources\workbench\AvailHammer.ico
```
## Linux Debian
```shell
jpackage --input avail/build/libs/ --name Anvil --main-jar anvil.jar --main-class avail.project.AvailProjectManagerRunner --icon avail/src/main/resources/workbench/AvailHammer.png --type deb
```

## Linux Red Hat
```shell
jpackage --input avail/build/libs/ --name Anvil --main-jar anvil.jar --main-class avail.project.AvailProjectManagerRunner --icon avail/src/main/resources/workbench/AvailHammer.png --type rpm
```