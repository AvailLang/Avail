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

The `main` function provides an example as to how one can set up their own 
Avail project inside their own JVM project.
