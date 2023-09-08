SAMPLE PROJECT OVERVIEW
--------------------------------------------------------------------------------

![Version](https://img.shields.io/badge/v2.0.0.alpha01-0f824e)

***NOTE: 8/20/2023 The current state of this example is not expected to work***
***and should not be used at this time. This uses alpha version tools that are*** 
***in flux. This README will be updated to reflect accurate usage for the*** 
***2. 0.0 production release of the Avail toolchain. Once the tools are***
***finalized, this sample will be updated to a working state.***

This represents a project that uses Avail in a JVM project with bindings in 
Avail to functions written in Kotlin. This project will be updated over time to 
reflect changes to Avail as well as to further demonstrate how Avail can be 
used in a JVM project.

SETUP
--------------------------------------------------------------------------------
The sample project uses Gradle to set up the project environment. It 
utilizes the `buildSrc` directory for managing state used in `build.gradle.
kts`.

It uses the [Avail Plugin](https://github.com/AvailLang/gradle-plugin/tree/development) to set up Avail:

```kotlin
id("org.availlang.avail-plugin") version Versions.availGradle
```

### Simulate Project Setup
The directory `roots/` is created by the Avail Gradle task, `initializeAvail` 
in the task group, `avail`. Delete the `avail` directory and run 
`initializeAvail` to simulate setting up the Avail project from scratch.

**NOTE** ***This will delete the file,***
`roots/my-avail-root/App.avail/Foreign Interface.avail`. 
***This file will need to be added back as it is not code generated by the***
***Gradle setup. A copy of the file can be found in:*** 

[etc/Foreign Interface.avail](etc/Foreign%20Interface.avail).

***Simply copy it into:***

[roots/my-avail-root/App.avail/](roots/my-avail-root/App.avail)

FOREIGN FUNCTION INTERFACE
--------------------------------------------------------------------------------

The foreign function interface is defined in:

[roots/my-avail-root/App.avail/Foreign Interface.avail](roots/my-avail-root/App.avail/Foreign%20Interface.avail)

This is where the bindings are created to the native JVM functionality.

RUNNING
--------------------------------------------------------------------------------
Use the Avail Gradle task, `printAvailConfig` in the task group, `avail` to 
print the configuration. Include the VM Arguments listed in the printout in 
when running your JVM application so that both your Avail Roots and the 
Avail Repository is included for use by your application.

If using IntelliJ, the project can be run from the Run Configuration, 
`Launch Project Workbench`.

It can also be run by the following steps from the terminal:
1. `./gradlew shadowJar` : *Builds the uber-jar*
2. `java -jar sample-hybrid-project-all.jar` : *Runs the jar launching an* 
   *Anvil Workbench*

SAMPLE
--------------------------------------------------------------------------------
The `sample-project` configures an Avail project using the Avail Gradle Plugin. 
When run using the VM arguments presented in the output of`printAvailConfig`, 
it creates an `AvailRuntime` based upon the Avail Plugin `avail` project 
configuration. It then compiles a targeted Avail Module in the created 
Avail Root, `my-avail-root`.
 * `App.kt` - The `main` application file. This represents the core running 
   application. TODO THIS NEEDS TO BE UPDATED TO USE new Avail Projects
 * `AvailContext` - This contains functionality used to create your Avail 
   `Project`. The `Project` gives you access to an `AvailRuntime` and also 
   allows you to build a module. 
 * `Foreign Functions.avail` - This contains example functionality for using
   POJOs, code written in Java in the module `avail-java-ffi` module, inside 
   Avail code. It also contains examples for writing tests. At the time of
   writing, this is the most interesting module in this example.

The `main` function provides an example as to how one can set up their own 
Avail project inside their own JVM project.