Avail Gradle Plugin
===============================================================================
[![Maven Central](https://img.shields.io/badge/maven--central-v2.0.0.alpha20-0f824e)](https://plugins.gradle.org/plugin/org.availlang.avail-plugin)

***NOTE: Documentation is in flux and may not be accurate. Will be made*** 
***accurate for the official 2.0.0 production release***

The Avail Gradle Plugin, `avail-plugin`, provides Avail-based project setup 
using a Gradle Plugin. It can be found on [Github](https://github.com/orgs/AvailLang/packages?repo_name=Avail).

 * [Overview](#overview)
 * [Setup](#setup)
 * [Configuration](#configuration)
 * [Plugin Tasks](#plugin-tasks)
 * [Example](#example)
 * [Publishing](#local-publishing)

## Overview
The plugin provides:
 * Initializes your Avail Roots directory, or uses a default.
 * Initializes your Avail Repositories directory, or uses a default.
 * Optionally includes the Avail Standard Library, `avail:avail-stdlib`, either 
   the most recent version or the version of your choice as an Avail Root for 
   your Avail project by default but permits opting out of using the Avail 
   Standard Library.
 * A human-readable printable configuration of your Avail Project.
 * Provides access to Gradle task that will launch a Anvil for Avail 
   development.
 
## Setup
To make the plugin accessible you must insure you include the following in the 
repositories' area of your Gradle settings.gradle.kts: 

**`settings.gradle.kts`**
```kotlin
pluginManagement {
	repositories {
		mavenLocal()
		// Adds the gradle plugin portal back to the plugin repositories as
		// this is removed (overridden) by adding any repository here.
        // NOTE as of 5/13/2022 the plugin has yet to be published to the gradle
        // plugin repository and as such must be imported from a local repo.
		gradlePluginPortal()
	}
}
rootProject.name = "plugin-test"
```

**`build.gradle`**
```groovy
pluginManagement {
	repositories {
		mavenLocal()
		// Adds the gradle plugin portal back to the plugin repositories as
		// this is removed (overridden) by adding any repository here.
      // NOTE as of 5/13/2022 the plugin has yet to be published to the gradle
      // plugin repository and as such must be imported from a local repo.
		gradlePluginPortal()
	}
}
rootProject.name "plugin-test"
```

To include the plugin, you must add the plugin id, `avail.avail-plugin` and 
provide the corresponding plugin release version in your Gradle build file. The 
following is an example that uses the version `2.0.0.alpha20`:

**`build.gradle.kts`**
```kotlin
id("org.availlang.avail-plugin") version "2.0.0.alpha20"
```

**`build.gradle`**
```groovy
id 'org.availlang.avail-plugin' version '2.0.0.alpha20'
```

## Configuration
The plugin adds the ability to add Avail source libraries to the Avail home
libraries directory: `~/.avail/libraries` by adding them to  `dependencies` 
section of the build file. 

## Plugin Tasks
The section details the tasks created by the Avail Plugin as well as the task 
types made available.

### Task Types
The Avail plugin provides some custom Gradle Tasks, `AvailWorkbenchTask`.

#### AvailAnvilTask
This allows you to create a task that launches Anvil to be used to develop 
Avail(`avail:anvil`). Anvil is launched by creating a fatjar that includes all 
the necessary dependencies for it to run. The jar is placed in`$buildDir/anvil`.

### Created
The following tasks are created by the Avail plugin and placed in the`avail` 
task group when the plugin is applied.

![Avail Plugin Tasks](readme-resources/tasks.jpg?raw=true)
 
* ***setupProject*** - Initializes the Avail project as configured in the 
  `avail` extension block.
   

* ***anvil*** - This is the default `AvailAnvilTask` used to run Anvil for 
  Avail development.

* ***printAvailConfig*** - Prints the Avail configuration details to 
  standard out.

```
========================= Avail Configuration =========================
    Standard Library Version: 1.6.1.rc1
    Repository Location: /Users/MyUser/Development/MyProject/avail/repositories
    Roots Location: /Users/MyUser/Development/MyProject/avail/my-roots
    Included Roots:
      -avail: jar:/Users/MyUser/Development/MyProject/avail/my-roots/avail-stdlib.jar
      -imported-library: jar:/Users/Some/Place/Else/imported-library.jar
      -my-avail-root: /Users/MyUser/Development/MyProject/avail/my-roots/my-avail-root
    Created Roots:
      my-avail-root
        Package Root
          as: myJar.jar
          export to: /Users/Rich/Development/Avail/samples/sample-project/build/libs
        Root Contents:
          |－ App.avail
          |－－ App.avail
          |－－ Configurations.avail
          |－－ Network.avail
          |－－－ Network.avail
          |－－－ Server.avail
          |－ Scripts.avail
========================================================================
```

## Example
The following is an example `build.gradle.kts` file that uses the Avail Plugin.
You can see a full project example in [sample-project](../samples/sample-project).

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.6.21" 
        // Add the Avail plugin
	id("avail.avail-plugin") version "1.6.1.rc1"
}

group = "avail.sample"
version = "1.0.0"

repositories {
    // mavenCentral is needed to resolve internal dependencies.
    mavenCentral()
}

dependencies { 
    testImplementation(kotlin("test"))
    // This adds the listed dependency to the custom build of the workbench fat
    // jar.
    availWorkbench("org.some.dependency:someProject:9.8.7")
    implementation(project(":avail-java-ffi"))
}

avail {
    useStdAvailLib {
      // The name of the root for the standard library actually defaults to
      // "avail", so it is not necessary to include this line.
      name = "avail"
  
      // The base name the `avail-stdlib` jar file that should be named
      // without the `.jar` extension. This will be used to construct the
      // [AvailRoot.uri]. Not setting this will default jar name to be the
      // jar as it is retrieved from maven:
      //    `avail-stdlib-<AVAIL BUILD VERSION>.jar
      jarLibBaseName = "avail-stdlib"
      
      // OPTIONAL: The specific Avail Standard Library version. If not 
      // explicitly set, the most recently released version of the standard 
      // library will be used. The most recent version being used is indicated
      // by a version set to `+`.
      stdlibVersion = "1.5.11"
    }

    // Specify the roots directory where the roots should be located. This will
    // default to `"$projectDir/.avail/roots"` if not specified.
    rootsDirectory = "$projectDir/avail/my-roots"

    // The directory to place your build `.repo` files for the included 
    // Avail roots. This will default to `"$projectDir/.avail/repositories"` if
    // not specified.
    repositoryDirectory = "$projectDir/avail/my-repos"
    
    // Point to a file that contains the file header comment body to be used
    // by all generated modules.
    moduleHeaderCommentBodyFile = "$projectDir/copyright.txt"

    // Add this new root to the roots directory and create it. Will only create
    // files in this root that do not already exist.
    createRoot("my-avail-root").apply {
      // Create module file header text.
      val customHeader =
        "Copyright © 1993-2022, The Avail Foundation, LLC.\n" +
                "All rights reserved."
      
      // Enables this root to be packaged as a jar using the `packageRoots` task
      packageContext =
        AvailLibraryPackageContext("myJar", "$buildDir/libs").apply {
          // Key-value pairs to add to the manifest
          manifestPairs["some-key"] = "some-value"
          // Lambda that is run after the package is created.
          postPackageAction = {
            println(
              "Hi there, this is where the file is: ${it.absolutePath}")
          }
        }
        // Creates a module package and package representative
        modulePackage("App").apply { 
          // The versions to list in the Avail header
          versions = listOf("Avail-1.6.0")
          // The modules to extend in the Avail header.
          extends = listOf("Avail", "Configurations", "Network")
          // Add a module inside the `App` module package.
          addModule("Configurations").apply {
          // The versions to list in the Avail header
          versions = listOf("Avail-1.6.0")
          // The modules to list in the uses section in the Avail header.
          uses = listOf("Avail")
          // Override the module header comment from 
          // moduleHeaderCommentBodyFile
          moduleHeaderCommentBody = customHeader
        }

        // Add a module package to this module package.
        addModulePackage("Network").apply {
          println("Setting up Network.avail")
          versions = listOf("Avail-1.6.0")
          uses = listOf("Avail")
          extends = listOf("Server")
          moduleHeaderCommentBody = customHeader
          addModule("Server").apply {
            versions = listOf("Avail-1.6.0")
            uses = listOf("Avail")
            moduleHeaderCommentBody = customHeader
          }
        }
      }
    
        // Creates a module at the top level of the root.
        module("Scripts").apply {
            versions = listOf("Avail-1.0.6")
            uses = listOf("Avail")
            moduleHeaderCommentBody = customHeader
        }
    }
    
    // Adds a root that will not be located in the `rootsDirectory`. 
    root("imported-library", "jar:/Users/Some/Place/Else/imported-library.jar")
    {
      // Will be run as part of the initialize task
      println("imported-library action has run!!!")
    }
}
tasks {
    // This task is constructed by default as a way to launch the Avail 
    // workbench which includes all the Avail roots specified in the `avail`
    // extension above. It is of type
    assembleAndRunWorkbench {
        // This task is used to build and launch the Avail Workbench. In this
        // example, the `dependsOn(jar)` is used as the Workbench fatjar is to
        // include this sample project's jar file (see the included 
        // workbenchDependency above). This is only needed if you require a 
        // task be complete before the Workbench jar is built.
        dependsOn(jar)
    
        // This task is customizable in the same manner as any
        // AvailWorkbenchTask.
        workbenchDependency("${buildDir}/libs/sample-1.0.0.jar")
        dependency("org.foo:my-sample:1.2.3")
        
        // If a foreign function interface is being used in the project from 
        // a module in the project, we may require a `build` task be run before
        // the `assembleAndRunWorkbench` task can be run. In this event we must
        // add `dependsOn` for build:
        dependsOn(build)
    }

    val myWorkbenchTask by creating(AvailWorkbenchTask::class.java)
    {
        // Add to task group
        group = "My Tasks"
        description = "My custom workbench build."
        
        // Depends on jar task running first
        dependsOn(jar)
        
        // Base name of the workbench jar, the result of which placed in 
        // "$buildDir/workbench/myCustomWorkBench.jar".
        workbenchJarBaseName = "myCustomWorkBench"
        
        // true indicates the jar should be rebuilt on each run.
        rebuildWorkbenchJar = true
        
        // The heap should be set to 6 GB when the workbench is run.
        maximumJavaHeap = "6g"
        
        // Package this project's jar in with the workbench fatjar, hence 
        // `dependsOn(jar)`.
        workbenchLocalJarDependency("${buildDir}/libs/sample-1.0.0.jar")
        
        // Some other local jar is packaged in with the fatjar.
        workbenchDependency("/Users/Person/local/libs/myLocal-1.2.4.jar")
        
        // These are the dependencies that should be resolved and used when the
        // workbench is run.
        dependency("com.google.crypto.tink:tink:1.6.1")
//        dependency("org.slf4j:slf4j-nop:${Versions.slf4jnop}")
        // A project module dependency
        dependency(project.dependencies.project(":avail-java-ffi"))
        
        // The Avail roots to start the workbench with.
        root("my-avail-root", "$projectDir/avail/roots/my-avail-root")
        root("avail", "jar:$projectDir/avail/roots/avail-stdlib.jar")
        
        // The VM options to use when running the workbench jar. 
        vmOption("-ea")
        vmOption("-XX:+UseCompressedOops")
        vmOption("-DavailDeveloper=true")
    }
    
	test {
		useJUnitPlatform()
	}

	withType<KotlinCompile>() {
		kotlinOptions.jvmTarget = "17"
	}
}
```
Running the `printAvailConfig` task for the above configuration will print
the following to standard out:

```
========================= Avail Configuration =========================
    Standard Library Version: 1.5.11
    Repository Location: /Users/MyUser/Development/MyProject/avail/repositories
    VM Arguments to include for Avail Runtime:
      • -DavailRoots=avail=jar:/Users/Rich/Development/Avail/samples/sample-project/avail/my-roots/avail-stdlib.jar;my-avail-root=/Users/Rich/Development/Avail/samples/sample-project/avail/my-roots/my-avail-root
      • -Davail.repositories=/Users/Rich/Development/Avail/samples/sample-project/avail/my-repos
    Roots Location: /Users/MyUser/Development/MyProject/avail/my-roots
    Included Roots:
      -avail: jar:/Users/MyUser/Development/MyProject/avail/my-roots/avail-stdlib.jar
      -imported-library: jar:/Users/Some/Place/Else/imported-library.jar
      -my-avail-root: /Users/MyUser/Development/MyProject/avail/my-roots/my-avail-root
    Created Roots:
      my-avail-root
        Package Root
          as: myJar.jar
          export to: /Users/Rich/Development/Avail/samples/sample-project/build/libs
        Root Contents:
          |－ App.avail
          |－－ App.avail
          |－－ Configurations.avail
          |－－ Network.avail
          |－－－ Network.avail
          |－－－ Server.avail
          |－ Scripts.avail
========================================================================
```

## Local Publishing
To publish this project locally, use the task, `publishToMavenLocal` under the 
`publishing` group. This will publish your plugin to `~/.m2`.

You can pull in the plugin repository from the local maven repository by 
adding `mavenLocal()` to your `settings.gradle.kts` file as shown below. 
Make sure to add back the `gradlePluginPortal()` as this is the default that 
is stripped when adding anything to the `repositories` block in 
`pluginManagement`.

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        // Adds the gradle plugin portal back to the plugin repositories as
        // this is removed (overridden) by adding any repository here.
        gradlePluginPortal()
    }
}
rootProject.name = "plugin-test"
```
