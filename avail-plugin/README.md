Avail Gradle Plugin
===============================================================================

The Avail Gradle Plugin, `avail-plugin`, provides Avail-based project setup 
using a Gradle Plugin. It is can be found on [Github](https://github.com/orgs/AvailLang/packages?repo_name=Avail).

## Overview
The plugin provides:
 * The inclusion of the `org.availlang:avail-core` dependency in the 
   `implementation` `Configuration` of your project.
 * Initializes your Avail Roots directory
 * Initializes your Avail Repositories directory
 * Optionally includes the Avail Standard Library, `org.
   availlang:avail-stdlib` as an Avail Root for your Avail project.
 * A human readable printable configuration of your Avail Project.
 
## Setup

To make the plugin accessible you must include the following in the repositories 
area of your Gradle build file: 

**`build.gradle.kts`**
```kotlin
maven {
	setUrl("https://maven.pkg.github.com/AvailLang/Avail")
	metadataSources {
		mavenPom()
		artifact()
	}
	credentials {
		username = "anonymous"
		// A public key read-only token for Avail downloads.
		password = "gh" + "p_z45vpIzBYdnOol5Q" + "qRCr4x8FSnPaGb3v1y8n"
	}
}
```

**`build.gradle`**
```groovy
maven {
	url 'https://maven.pkg.github.com/AvailLang/Avail'
	metadataSources {
		mavenPom()
		artifact()
	}
	credentials {
		username = "anonymous"
		// A public key read-only token for Avail downloads.
		password = "gh" + "p_z45vpIzBYdnOol5Q" + "qRCr4x8FSnPaGb3v1y8n"
	}
}
```

To include the plugin, you must add it with the corresponding version in 
your Gradle build file:

**`build.gradle.kts`**
```kotlin
id("com.avail.plugin") version "1.6.0.20210910.181950"
```

**`build.gradle`**
```groovy
id 'com.avail.plugin' version '1.6.0.20210910.181950'
```

## Configuration
The plugin adds a Project extension, `AvailExtension`. This is added at 
the top level of the buildscript using the keyword, `avail`.

```kotlin
avail {
	// Custom configuration goes here
}
```

This is where you can configure your avail project. The following are the 
options available for configuration:

 * `rootsDirectory` - (*optional; has default*) The absolute filepath to the 
   base roots directory where your Avail project's roots are located.
 * `repositoryDirectory` - (*optional; has default*) The directory to place your 
   build `.repo` files for the included Avail roots.
 * `useAvailStdLib` - `true` indicates that the `avail-stdlib` Avail root jar 
   with the same version as this plugin should be copied into the 
   `rootsDirectory` and included as a root in when the Avail Workbench is 
   launched (*see `runWorkbench` task in the Plugin Tasks section*).
 * `root(name, uri, optional action)` - Adds an `AvailRoot` to the project. The 
   URI indicates the file location of the root directory as a file directory or 
   a jar file. This root is included in the Avail Workbench when`runWorkbench` 
   is launched. This does not result in copying the root to the`rootsDirectory`,
   it only points to location of the root. You can add an optional lambda 
   that accepts the created `AvailRoot` and is executed when the task
   `initializeAvail` is run (*see Avail Plugin Tasks*).
 * `root(name, optional action)` - Adds an Avail root to the `rootsDirectory`. 
   This sets the root URI as: `"$rootsDirectory/my-avail-root"`, defaulting 
   to using the`file://` scheme for the URI. It is expected that the root 
   has already been created. You can add an optional lambda that accepts the 
   created `AvailRoot` and is executed when the task `initializeAvail` is 
   run (*see Avail Plugin Tasks*)
 * `createRoot(rootName, optional initializer)` - Add an `AvailRoot` that is
   created if it does not already exist when `initializeAvail` is run. The 
   function takes an optional lambda that accepts the newly created root. The 
   following can be called/set in the body of the `initializer`:
      * `module(name, fileExtension, module initializer)` - Creates an Avail 
        Module at the top level of the root directory. It takes the base 
        module name, an optional file extension (*uses "avail" by default*), 
        and a lambda that accepts the newly created module. The following 
        options can be set in the `module initializer`:
         * `copyrightBody` - The raw text to be included in the file header 
           comment.
         * `versions` - The String list of versions to be included in the 
           Avail Module header's `Versions` section.
         * `extends` - The String list of Avail Modules to be included in the
           Avail Module header's `Extends` section.
        * `uses` - The String list of Avail Modules to be included in the
          Avail Module header's `Uses` section.
      * `modulePackage(name, fileExtension, module initializer)` - Creates an 
        Avail Module package (directory) at the top level of the root directory.
        It takes the base module name, an optional file extension (*uses 
        "avail" by default*), and a lambda that accepts the newly created 
        module. It creates both the package directory and creates the 
        representative Module in that directory. The same options can be set 
        in the `module initializer` as were set for the `module` function.
 * `workBenchDependency(dependency)` - Add a local Jar as a dependency to be 
   included in the `workbench.jar` (fatjar). The dependency should be the 
   absolute path to the jar to be included.
 * `copyrightBodyFile` - The absolute path to a file containing the raw file 
   comment header that may optionally be included in the `AvailModule`s 
   generated by the `createRoot` function.
 * `copyrightBody` - A string to optionally include in the comment header that 
   may optionally be included in the `AvailModule`s generated by the 
   `createRoot` function. This takes precedence over `copyrightBodyFile`.

## Plugin Tasks
The following represent the tasks provided by the Avail Plugin.

![Avail Plugin Tasks](readme-resources/tasks.jpg?raw=true)

 * `_basicAvailSetup` - Task that is run when the build script is first run and 
   the plugin is created. This is a dependency for all other tasks. It will 
   create both the roots and repositories if they don't exist. It will also 
   move the `avail-stdlib` jar into the roots directory if `useAvailStdLib` 
   is set to `true` (*see Configuration section*). 
 * `initializeAvail` - Initializes the Avail project creating the roots 
   specified in the `avail` extension through the `createRoot` function. It 
   will also run the arbitrary lambdas included with the `root` functions 
   (*see Configuration section*). This is only run if a user explicitly runs it.
 * `assembleWorkbench` - Builds the Avail Workbench fatjar using all the 
   necessary base dependencies as well as the additional jar dependencies 
   added using `workBenchDependency` in the `avail` extension. This task can 
   be configured to use `dependsOn` if there are any tasks that should be 
   run before building `workbench.jar`, such as `dependsOn(jar)` if 
   `workbench` is to include the root project's built jar file.
 * `printAvailConfig` - Prints the Avail configuration details to screen 
   standard out.
 * `runWorkbench` - This launches an instance of the Avail Workbench using 
   your configured Avail Roots.

### Example
The following is an example `build.gradle.kts` file that uses the Avail Plugin.

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
   kotlin("jvm") version "1.5.21"
   id("com.avail.plugin") version "1.6.0.20210910.181950"
}

group = "com.avail.sample"
version = "1.0.0"

repositories {
   mavenCentral()
}

dependencies {
   testImplementation(kotlin("test"))
}

avail {
   // Indicates whether or not the Avail Standard Library, `avail-stdlib`
   // should be imported into the roots directory
   useAvailStdLib = true
   copyrightBodyFile = "$projectDir/copyright.txt"
   rootsDirectory = "$projectDir/avail/my-roots"

   // Add this new root to the roots directory and create it.
   createRoot("my-avail-root") { root ->
      // Creates a Module Package with module associated module file.
      root.modulePackage("App") { modulePackage ->
         // The list of version that will be placed in the `Versions` section 
         // of an Avail header.
         modulePackage.versions = listOf("Avail-1.0.6")
         // The list of Avail Modules that will be placed in the `Extends` 
         // section of an Avail header that this module will extend. 
         modulePackage.extends = listOf("Avail")
      }

      root.module("Scripts") { module ->
         // The list of version that will be placed in the `Versions` section 
         // of an Avail header.
         module.versions = listOf("Avail-1.0.6")
         // The list of Avail Modules that will be placed in the `Uses` 
         // section of an Avail header that this module can use in its `Body`.
         module.uses = listOf("Avail")
         
         // Overrides all inherited module comment header text.
         module.copyrightBody =
            "Copyright © 1993-2021, The Avail Foundation, LLC.\n" +
                    "All rights reserved."
      }
   }

   // Add a root existing somewhere else.
   root("imported-library", "jar:/Users/Some/Place/Else/imported-library.jar")
   {
      // Will be run as part of the initialize task
      println("imported-library action has run!!!")
   }

   // Includes a jar in the fatjar, workbench.jar used to run the workbench
   // with your project roots.
   workBenchDependency("${buildDir}/libs/sample-1.0.0.jar")
}
tasks {
   assembleWorkbench {
      // This task is run before launching the Avail Workbench. In this
      // example, the `dependsOn(jar)` is used as the Workbench fatjar is to
      // include this sample project's jar file. This is only needed if you
      // require a task be complete before the Workbench jar is built.
      dependsOn(jar)
   }
}

tasks.test {
   useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
   kotlinOptions.jvmTarget = "16"
}

```
Running the `printAvailConfig` task for the above configuration will print 
the following to standard out:

```
================== Avail Configuration ==================
	Avail Version: 1.6.0.20210910.181950
	Repository Location: /Users/MyUser/Development/MyProject/avail/repositories
	Roots Location: /Users/MyUser/Development/MyProject/avail/my-roots
	Included Roots:
		-my-avail-root: /Users/MyUser/Development/MyProject/avail/my-roots/my-avail-root
		-imported-library: jar:/Users/Some/Place/Else/imported-library.jar
	Included Workbench Dependencies:
		/Users/MyUser/Development/MyProject/build/libs/sample-1.0.0.jar	Workbench VM Options:
		--ea
		--XX:+UseCompressedOops
		--Xmx12g
		--DavailDeveloper=true
		--DavailRoots=my-avail-root=/Users/MyUser/Development/MyProject/avail/my-roots/my-avail-root;imported-library=jar:/Users/Some/Place/Else/imported-library.jar;avail=jar:/Users/MyUser/Development/MyProject/avail/my-roots/avail-stdlib.jar
		--Davail.repositories=/Users/MyUser/Development/MyProject/avail/repositories
=========================================================
```
