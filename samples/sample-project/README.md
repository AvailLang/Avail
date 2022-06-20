SAMPLE PROJECT OVERVIEW
--------------------------------------------------------------------------------
This represents a project that uses Avail in a JVM project. This project will be
updated over time to reflect changes to Avail as well as to further 
demonstrate how Avail can be used in a JVM project.

SETUP
--------------------------------------------------------------------------------
The sample project uses Gradle to set up the project environment. It 
utilizes the `buildSrc` directory for managing state used in `build.gradle.
kts`.

It uses the [Avail Plugin](/avail-plugin/README.md) to set up Avail:

```kotlin
 id("avail.avail-plugin") version Versions.availStripeVersion
```

The directory `avail/` is created by the Avail Gradle task, `initializeAvail` 
in the task group, `avail`. Delete the `avail` directory and run 
`initializeAvail` to simulate setting up the Avail project from scratch.

### Avail Gradle Plugin Note
At the time of writing this, the Avail Gradle Plugin has yet to be made 
accessible from Gradle's repository. Additionally, the Avail libraries are not
yet available on Maven Central. Until such time as these become published 
publicly, users of this should publish Avail to Maven Local, 
`publishToMavenLocal`, and then publish the Avail Gradle Plugin to Maven Local, 
`publishToMavenLocal`. Then get the locally published new version found in:
```bash
../../avail-plugin//src/main/resources/releaseVersion.properties
```
and update `buildSrc/src/main/kotlin/Versions.avail` with that version.

RUNNING
--------------------------------------------------------------------------------
Use the Avail Gradle task, `printAvailConfig` in the task group, `avail` to 
print the configuration. Include the VM Arguments listed in the printout in 
when running your JVM application so that both your Avail Roots and the 
Avail Repository is included for use by your application.

SAMPLE
--------------------------------------------------------------------------------
The `sample-project` configures an Avail project using the Avail Gradle Plugin. 
When run using the VM arguments presented in the output of`printAvailConfig`, 
it creates an `AvailRuntime` based upon the Avail Plugin `avail` project 
configuration. It then compiles a targeted Avail Module in the created 
Avail Root, `my-avail-root`.
 * `App.kt` - The `main` application file. This represents the core running 
   application.
 * `AvailContext` - This contains functionality used to create your Avail 
   `Project`. The `Project` gives you access to an `AvailRuntime` and also 
   allows you to build a module. 
 * `Foreign Functions.avail` - This contains example functionality for using
   POJOs, code written in Java in the module `avail-java-ffi` module, inside 
   Avail code. It also contains examples for writing tests. At the time of
   writing, this is the most interesting module in this example.

The `main` function provides an example as to how one can set up their own 
Avail project inside their own JVM project.
