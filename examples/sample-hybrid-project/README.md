SAMPLE PROJECT OVERVIEW
--------------------------------------------------------------------------------

![Version](https://img.shields.io/badge/v2.0.0.alpha01-0f824e)

***NOTE: 8/27/2023 The current state of this example is: Expected to Work***

*This uses alpha version tools that are in flux. This README will be updated* 
*to reflect accurate usage for the2. 0.0 production release of the Avail* 
*toolchain. Once the tools are finalized, this sample will be updated to a* 
*working state relative to the lastest production release.*

This represents a project that uses Avail in a JVM project with Avail dynamic
pojo linking to functions written in Kotlin. This project will be updated over 
time to reflect changes to Avail as well as to further demonstrate how Avail can 
be used in a JVM project.

SETUP
--------------------------------------------------------------------------------
The sample project uses Gradle to set up the project environment. It 
utilizes the `buildSrc` directory for managing state used in `build.gradle.
kts`.

It uses the [Avail Gradle Plugin](../../gradle-plugin/README.md) to set up Avail:

```kotlin
id("org.availlang.avail-plugin") version Versions.availGradle
```

The usage of of the Avail Gradle Plugin is documented extensively in this
project's [build.gradle.kts](build.gradle.kts) file. Refer to it for plugin 
usage.

### Project Setup
As of now, the project is checked into Github already setup. To reset up the
project "from scratch" do the following:

1. delete directory `.avail`
2. delete file `sample-hybrid.json`
3. Run Gradle task `avail:setupProject`
```bash
./gradlew setupProject
```

FOREIGN FUNCTION INTERFACE
--------------------------------------------------------------------------------

The foreign function interface is defined in:

[Foreign Interface.avail](roots/my-avail-root/App.avail/Foreign%20Interface.avail)

This is where the bindings are created to the native JVM functionality. To
dynamically link a JAR, use Avail's `"Link pojos_"`. This is done inside
Foreign Interface.avail with the statement:

```
Link pojos "/my-avail-root/App/avail-java-ffi-1.0.jar";
```
This is linking the JAR file at the specified location directly inside the
`my-avail-root` module root. In order for a Jar file to be linked by Avail, it
***must*** be inside the module root that is linking it. 

RUNNING
--------------------------------------------------------------------------------
Use the Avail Gradle task, `anvil` in the task group, `avail` to launch the
Avail Project Manager. Select the "Open" button to open the sample project. 
Select `sample-hybrid.json` at the root of the project to open the project.

Double click on the modules to build them. Run the entry point `wrap_and print`:
![workbench](readme/workbench.jpg?raw=true)
