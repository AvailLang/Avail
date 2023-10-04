SAMPLE HYBRID PROJECT OVERVIEW
--------------------------------------------------------------------------------

![Version](https://img.shields.io/badge/v2.0.0.alpha02-0f824e)

***NOTE: 2023.09.23 The current state of this example is: Expected to Work***

*This uses alpha version tools that are in flux. This README will be updated* 
*to reflect accurate usage for the 2.0.0 production release of the Avail* 
*toolchain. Once the tools are finalized, this sample will be updated to a* 
*working state relative to the latest production release.*

This represents a project that creates a library written in Java that is linked 
in Avail with Avail's dynamic library linking. Any JVM language that compiles 
to Java bytecode packaged in a JAR can be linked. This project will be updated 
over time to reflect changes to Avail. 

SETUP
--------------------------------------------------------------------------------
The sample project uses Kotlin DSL Gradle to set up the project environment. 

It uses the [Avail Gradle Plugin](../../gradle-plugin/README.md) to set up Avail:

```kotlin
id("org.availlang.avail-plugin") version "2.0.0.alpha20"
```

The usage of the Avail Gradle Plugin is documented extensively in this
project's [build.gradle.kts](build.gradle.kts) file. Refer to it for plugin 
usage.

### Project Setup
As of now, the project is checked into Github already setup. To reset up the
project "from scratch" do the following:

1. Delete directory `.avail`
2. Delete file `sample-library-linking.json`
3. Run Gradle task `avail:setupProject`
    ```bash
    ./gradlew setupProject
    ```
4. Delete `roots/my-avail-root/App.avail/sample-library.jar`
5. Run Gradle task `build:copyJarToAvailRoot`
    ```bash
    ./gradlew copyJarToAvailRoot
    ```

FOREIGN FUNCTION INTERFACE
--------------------------------------------------------------------------------

The foreign function interface is defined in:

[Foreign Interface.avail](roots/my-avail-root/App.avail/Foreign%20Interface.avail)

This is where the bindings are created to the native JVM functionality. To
dynamically link a JAR, use Avail's `"Link library:_"`. This is done inside
`Foreign Interface.avail` with the statement:

```
Link library: "/my-avail-root/App/avail-java-ffi-1.0.jar";
```
This is linking the JAR file at the specified location directly inside the
`my-avail-root` module root. In order for a Jar file to be linked by Avail, it
***must*** be inside the module root that is linking it. 

***NOTE***: The linked JAR must be an uber-JAR, containing all dependencies, as 
Avail does not resolve and link any library dependencies not packaged in the 
JAR.

RUNNING
--------------------------------------------------------------------------------
Use the Avail Gradle task, `anvil` in the task group, `avail` to launch the
Avail Project Manager. Select the "Open" button to open the sample project. 
Select `sample-library-linking.json` at the root of the project to open the 
project.

Double-click on the modules to build them. Run the entry point `wrap_and print`:
![workbench](readme/workbench.jpg?raw=true)
