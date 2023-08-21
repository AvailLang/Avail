Avail Gradle Plugin
===============================================================================
[![Maven Central](https://img.shields.io/badge/maven--central-v2.0.0.alpha19-0f824e)](https://plugins.gradle.org/plugin/org.availlang.avail-plugin)

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
 * Provides access to Gradle task that will launch a customizable Avail 
   Workbench.
 
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
following is an example that uses the version `2.0.0.alpha01`:

**`build.gradle.kts`**
```kotlin
id("org.availlang.avail-plugin") version "2.0.0.alpha19"
```

**`build.gradle`**
```groovy
id 'org.availlang.avail-plugin' version '2.0.0.alpha19'
```

## Configuration
The plugin adds the ability to add Avail source libraries as dependency jars 
that need to be resolved in the `dependencies` section of the build file. Use 
the configuration `availLibrary`. For example:

```kotlin
dependencies {
    implementation("org.foo:myProject:1.2.3")
    // Will be added as a library in your roots directory
    availLibrary("com.somewhere.cool:avail-cool-lib:5.4.3")
}
```

The plugin adds the Project extension, `AvailExtension`, to the host project. 
This is added at the top level of the buildscript using the keyword, `avail`.

```kotlin
avail {
	// Custom configuration goes here
}
```

Inside the `avail { ... }` block is where you can configure your Avail project. 
The following are the options available for configuration:

* ***rootsDirectory*** (`string`)- The absolute filepath to the base roots' 
  directory where your Avail project's roots are located. Defaults to
  `"$projectDir/avail/roots"`.
   

* ***repositoryDirectory***  (`string`)- The absolute path to the directory to 
  place your build `.repo` files for the included Avail roots. Defaults to 
  `"$projectDir/avail/repositories"`.
 

* ***useAvailStdLib*** (`AvailStandardLibrary.() -> Unit`) - A function that 
  accepts a lambda that allows for the configuration of the 
  `AvailStandardLibrary` pulled in as a jar, `avail-stdlib-<VERSION>.jar` as an 
  Avail root jar with the same version as this plugin which will be copied into 
  the `rootsDirectory`and included as a root in the Avail Workbench when it is  
  launched (*see `assembleAndRunWorkbench` task in the Plugin Tasks section*).
  The fields that can be configured in the body of the lambda are:
    * `name` (`string`)-  The name of the root for the standard library actually 
      defaults to "avail". It only needs to be set if the root should be named
      something other than "avail".
    * `jarLibBaseName` (`string`) - The base name the `avail-stdlib` jar file 
      that should be named without the `.jar` extension. This will be used 
      to construct the Avail root uri.
    * `stdlibVersion` (`string`) - The version of the 
      `avail-stdlib:<VERSION>` to use. By default, the most recently released
      version is used indicated by the version being set to `+`.
  This function *must be called* in order for the Avail standard library to be
  included as a root. If no customization is needed, this function should be 
  called with an empty lambda:
```kotlin
  useAvailStdLib {}
```

* ***root*** (`name: string, optional uri: string, optional action: lambda`) - 
  Function that adds an `AvailRoot` to the project. The URI indicates the file 
  location of the root directory as a file directory or a jar file. This 
  root will be included in the Avail Workbench when`assembleAndRunWorkbench` is 
  launched. This does not result in copying the root to the`rootsDirectory`, 
  it only points to location of the root. You can add an optional lambda that 
  accepts the created `AvailRoot`and is executed when the task `initializeAvail`
  is run (*see Avail Plugin Tasks*). Defaults the `uri` to the 
  `"$rootsDirectory/$name"`` and defaults` action` to an empty lambda.
  

* ***createRoot*** (`rootName: string`) - 
  Function that adds an `AvailRoot` that is created if it does not already 
  exist when `initializeAvail` is run. It returns the `CreateRoot` so that 
  it may be initialized. `CreateRoot` can be customized using the following:
  * **module** (`name: string, fileExtension: string = ".avail"`) - Function 
    that creates and answers an Avail Module at the top level of the 
    root directory. It takes the base module name, an optional `fileExtension` 
   (*uses "avail" by default*). The following options can be set on it:
    * `moduleHeaderCommentBody` (`string`)- The raw text to be included in the 
      file header comment.
    * `versions` (`string list`) - The list of versions to be included in the
      Avail Module header's `Versions` section.
    * `extends` (`string list`) - The list of Avail Modules to be included in
      the Avail  Module header's `Extends` section.
    * `uses` (`string list`) - The list of Avail Modules to be included in 
      the Avail Module header's `Uses` section.
  * **modulePackage** (`name: string, fileExtension: string`)- Function that 
    creates and answers an Avail Module package (directory) at the top level of 
    the root directory. It takes the base module name, an optional file 
    extension (*uses "avail" by default*). It creates both the package 
    directory and creates the representative Module in that directory. The 
    same options can be set on the returned module as were set for the 
    `module` function.
  

* ***moduleHeaderCommentBodyFile*** (`string`)- The absolute path to a file 
  containing the raw file comment header that may optionally be included in the 
  `AvailModule`s generated by the `createRoot` function.


* ***moduleHeaderCommentBody*** (`string`) - Optionally include in the comment 
  header that may optionally be included in the `AvailModule`s generated by the 
  `createRoot` function. This takes precedence over `moduleHeaderCommentBodyFile`.

### Dependency Configuration
The plugin also adds the custom `Configuration`, `availWorkbench`, to the host
project. Any dependency added to the `availWorkbench` configuration in the 
dependencies section of the build script will be included in the build of the
workbench launched by the task, **assembleAndRunWorkbench** (*See Plugin Tasks 
section*).

```kotlin
dependencies { 
    testImplementation(kotlin("test"))
    // This adds the listed dependencies to the custom build of the 
    // workbench fat jar launched from the task assembleAndRunWorkbench
    availWorkbench("org.some.dependency:someProject:9.8.7")
    availWorkbench("com.some.other:dependency:7.8.9")
}
```

## Plugin Tasks
The section details the tasks created by the Avail Plugin as well as the task 
types made available.

### Task Types
The Avail plugin provides some custom Gradle Tasks, `AvailWorkbenchTask`.

#### AvailWorkbenchTask
This allows you to create a task that launches a workbench 
(`avail:avail-workbench:<VERSION>`). An Avail workbench is launched by 
creating a fatjar that includes all the necessary dependencies for it to run.
The jar is placed in `$buildDir/workbench`.

Just like any other Gradle task, this task can be configured. You can use
`dependsOn` if there are any tasks that should be run before building your
custom workbench jar, such as `dependsOn(jar)` if the workbench is to include
the root project's built jar file.

Everytime the workbench is launched, it prints the details of the launch to 
standard out. Here are the options for configuring `AvailWorkbenchTask`.

* ***workbenchJarBaseName*** (`string`)- Provides a custom name for the 
  workbench jar created by this task. Defaults to the name provided for the 
  task. Jar will be placed in `"$buildDir/workbench"`.


* ***workbenchVersion*** (`string`) - The version of the Avail Workbench to use
  to create the custom workbench to be launched 
  (`avail:avail-workbench:<VERSION>`).


* ***rebuildWorkbenchJar*** (`boolean`) - `true` indicates the jar should be 
  rebuilt every time the task is run; `false` indicates that the jar will not be 
  recreated if it exists. The task is defaulted to `false`.


* ***repositoryDirectory*** (`string`)- The directory to place your build 
 `.repo` files for the Avail roots used by the workbench. This defaults to the 
 `repositoryDirecyory` listed in the `avail` extension configuration body.


* ***maximumJavaHeap*** (`string`)- The maximum heap size that will be 
  presented as a VM option upon running an Avail Workbench. `-Xmx` will be 
  prefixed with the provided option. You must provide a value of the format: 
  `<size>[g|G|m|M|k|K]`. The default value is set to `"6g"`.


* ***workbenchJarDependencies(`dependency: string`)*** - Add a local Jar as a 
  dependency to be included in the `"workbenchJarBaseName".jar` (fatjar). 
  The dependency should be the absolute path to the jar to be included.


* ***dependency*** (`string`) - The string that identifies the dependency such 
  as `org.package:myLibrary:2.3.1`.


* ***dependency*** (`Dependency`) - The `Dependency` to include such as 
  `dependency(project.dependencies.project(":avail-java-ffi"))`.


* ***vmOption*** (`option: string`) - A function that adds a VM option for 
  running the jar.


* ***root*** (`name: string, uri: string`) - Add function that accepts and 
  Avail root name and its URI to the workbench when it runs.

### Created
The following tasks are created by the Avail plugin and placed in the`avail` 
task group when the plugin is applied.

![Avail Plugin Tasks](readme-resources/tasks.jpg?raw=true)
 
* ***initializeAvail*** - Initializes the Avail project. It will create both 
  the roots' and repositories' directories if they don't exist. It will also 
  move the `avail-stdlib` jar into the roots' directory if `useAvailStdLib` is 
  set configured (*see [Configuration](#configuration) section*). It creates the 
  roots specified in the `avail` extension through the `createRoot` function.
  It will also run the arbitrary lambdas included with the `root` functions 
  (*see Configuration section*). This is only run if a user explicitly runs it.
  The following is the result of running `initializeAvail` for the
  [example](#example) included at the end of this README.
   
    ![Generated Output](readme-resources/generated.jpg?raw=true)
   

* ***assembleAndRunWorkbench*** - This is the default `AvailWorkbenchTask` 
  provided by the plugin. It uses the following settings:
  * All Avail roots included in the `avail` extension block (*added by `root` 
    and `createRoot` options*) will be the Avail roots the workbench starts 
    up pointed to.
  * The jar will be called `workbench.jar`.
  * The jar will not be rebuilt if it exists (`rebuildWorkbenchJar = false`)
  * Will include all dependencies added to the `availWorkbench` 
    configuration in the `dependencies` section of the build script.
  * Includes no local jar dependencies (`workbenchJarDependencies` is empty 
    by default.)
  * The task does not depend on any other task.
  * Is provided maximum heap space of `6g` (`maximumJavaHeap = "6g"`).
  * In addition to the VM option that adds in the Avail roots, it starts 
    using these options:
    * `-ea`
    * `-XX:+UseCompressedOops`
    * `-DavailDeveloper=true`

* ***printAvailConfig*** - Prints the Avail configuration details to 
  standard out. Note that this will provide you with the VM arguments needed 
  to have the Avail Runtime point to your Avail project roots and repository 
  when running your application. This can be found in the printed section, 
  `VM Arguments to include for Avail Runtime:`. The following is an example of 
  the output of this task:

```
========================= Avail Configuration =========================
    Standard Library Version: 1.6.1.rc1
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
