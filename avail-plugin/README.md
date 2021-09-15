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

Inside the `avail { ... }` block is where you can configure your Avail project. 
The following are the options available for configuration:

* ***rootsDirectory*** - (*optional; default defaults to project directory*) 
  The absolute filepath to the base roots directory where your Avail 
  project's roots are located.
   

* ***repositoryDirectory*** - (*optional; default defaults to project directory*)  
  The directory to place your build `.repo` files for the included Avail roots.
 

* ***useAvailStdLib*** - `true` indicates that the `avail-stdlib` Avail root jar 
  with the same version as this plugin will be copied into the `rootsDirectory` 
  and included as a root in when the Avail Workbench is launched (*see 
 `assembleAndRunWorkbench` task in the Plugin Tasks section*). Defaults to `true`.


* ***workbenchName*** - Provides a custom name for the workbench jar created and 
  run from the `assembleAndRunWorkbench` task. Defaults to "workbench".


* ***root(name, uri, optional action)*** - Adds an `AvailRoot` to the project. 
  The URI indicates the file location of the root directory as a file directory 
  or a jar file. This root is included in the Avail Workbench when`runWorkbench` 
  is launched. This does not result in copying the root to the`rootsDirectory`,
  it only points to location of the root. You can add an optional lambda 
  that accepts the created `AvailRoot` and is executed when the task
  `initializeAvail` is run (*see Avail Plugin Tasks*).


* ***root(name, optional action)*** - Adds an Avail root to the `rootsDirectory`. 
  This sets the root URI as: `"$rootsDirectory/$name"`, defaulting to using the 
  `file://` scheme for the URI. It is expected that the root has already been 
  created. You can add an optional lambda that accepts the created `AvailRoot` 
  and is executed when the task `initializeAvail` is run (*see Avail Plugin 
  Tasks*)
  

* ***createRoot(rootName, optional initializer)*** - Add an `AvailRoot` that is
   created if it does not already exist when `initializeAvail` is run. The 
   function takes an optional lambda that accepts the newly created root. The 
   following can be called/set in the body of the optional `initializer`:
  * **module(name, fileExtension, module initializer)** - Creates an Avail 
        Module at the top level of the root directory. It takes the base 
        module name, an optional file extension (*uses "avail" by default*), 
        and a lambda that accepts the newly created module. The following 
        options can be set in the `module initializer`:
    * `moduleHeaderCommentBody` - The raw text to be included in the file 
      header comment.
    * `versions` - The String list of versions to be included in the Avail 
       Module header's `Versions` section.
    * `extends` - The String list of Avail Modules to be included in the Avail 
       Module header's `Extends` section.
    * `uses` - The String list of Avail Modules to be included in the Avail 
      Module header's `Uses` section.
    * `modulePackage(name, fileExtension, module initializer)` - Creates an 
        Avail Module package (directory) at the top level of the root directory.
        It takes the base module name, an optional file extension (*uses 
        "avail" by default*), and a lambda that accepts the newly created 
        module. It creates both the package directory and creates the 
        representative Module in that directory. The same options can be set 
        in the `module initializer` as were set for the `module` function.
      

* ***workBenchDependency(dependency)*** - Add a local Jar as a dependency to be 
   included in the `workbench.jar` (fatjar). The dependency should be the 
   absolute path to the jar to be included.


* ***moduleHeaderCommentBodyFile*** - The absolute path to a file containing the 
   raw file comment header that may optionally be included in the 
   `AvailModule`s generated by the `createRoot` function.


* ***moduleHeaderCommentBody*** - A string to optionally include in the comment 
  header that may optionally be included in the `AvailModule`s generated by the 
  `createRoot` function. This takes precedence over `moduleHeaderCommentBodyFile`.

## Plugin Tasks
The following represent the tasks provided by the Avail Plugin.

![Avail Plugin Tasks](readme-resources/tasks.jpg?raw=true)
 
* ***initializeAvail*** - Initializes the Avail project. It will create both 
  the roots' and repositories' directories if they don't exist. It will also 
  move the `avail-stdlib` jar into the roots directory if `useAvailStdLib` is 
  set to `true` (*see Configuration section*). It creates the roots specified
  in the `avail` extension through the `createRoot` function. It will also 
  run the arbitrary lambdas included with the `root` functions (*see 
  Configuration section*). This is only run if a user explicitly runs it.
  The following is the result of running `initializeAvail` for the
  [example](#Example) included at the end of this README.
   
    ![Generated Output](readme-resources/generated.jpg?raw=true)
   

* ***assembleAndRunWorkbench*** - Builds the Avail Workbench fatjar using all 
  the necessary base dependencies as well as the additional jar dependencies 
  added using `workBenchDependency` in the `avail` extension. This task can 
  be configured to use `dependsOn` if there are any tasks that should be 
  run before building `workbench.jar`, such as `dependsOn(jar)` if `workbench` 
  is to include the root project's built jar file. Once built, it launches 
  an instance of the Avail Workbench using the configured Avail Roots in the 
  `avail` extension block. This task can be run multiple times to launch 
  additional workbenches. Changing the `avail` extension configuration between
  runs enables you to have concurrently running workbenches with potentially 
  different roots, dependencies, or even jar name.


* ***printAvailConfig*** - Prints the Avail configuration details to standard out.

### Example
The following is an example `build.gradle.kts` file that uses the Avail Plugin.

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.5.21"
    // Add the Avail plugin
	id("com.avail.plugin") version "1.6.0.20210910.181950"
}

group = "com.avail.sample"
version = "1.0.0"

repositories {
    // mavenCentral is needed to resolve internal dependencies.
	mavenCentral()
    // Include the repository where the Avail Plugin can be located
	maven {
		setUrl("https://maven.pkg.github.com/AvailLang/Avail")
		metadataSources {
			mavenPom()
			artifact()
		}
		credentials {
			username = "anonymous"
			// A public key read-only token for Avail downloads.
			password = "[THE AVAIL PUBLIC KEY]"
		}
	}
}

dependencies {
	testImplementation(kotlin("test"))
}

avail {
	// Indicates whether or not the Avail Standard Library, `avail-stdlib`
	// should be imported into the roots directory
	useAvailStdLib = true

    // Provides a custom name for the workbench jar created and run from
    // the `assembleAndRunWorkbench` task. Defaults to "workbench".
	workbenchName = "sample-workbench"
    
    // Point to a file that contains the file header comment body to be used
    // by all generated modules.
	moduleHeaderCommentBodyFile = "$projectDir/copyright.txt"
    
    // Specify the roots directory where the roots should be located.
	rootsDirectory = "$projectDir/avail/my-roots"

	// Add this new root to the roots directory and create it.
	createRoot("my-avail-root") { root ->
		root.modulePackage("App") { modulePackage ->
			modulePackage.versions = listOf("Avail-1.0.6")
			modulePackage.extends = listOf("Avail")
		}

		root.module("Scripts") { module ->
			module.versions = listOf("Avail-1.0.6")
			module.uses = listOf("Avail")
			module.moduleHeaderCommentBody =
				"Copyright © 1993-2021, The Avail Foundation, LLC.\n" +
					"All rights reserved."
		}
	}

	// Adds a root that will not be located in the `rootsDirectory`. 
	root("imported-library", "jar:/Users/Some/Place/Else/imported-library.jar")
	{
		// Will be run as part of the initialize task
		println("imported-library action has run!!!")
	}

	// Includes a jar in the fatjar, workbench.jar, used to run the workbench
	// with your project roots. Note that this is the jar for this sample 
    // project. The dependency to create this so it can be included is set up 
    // in the `tasks.assembleAndRunWorkbench` configuration below.
	workBenchDependency("${buildDir}/libs/sample-1.0.0.jar")
}
tasks {
	assembleAndRunWorkbench {
		// This task is used to build and launch the Avail Workbench. In this
		// example, the `dependsOn(jar)` is used as the Workbench fatjar is to
		// include this sample project's jar file (see the included 
        // workbenchDependency above. This is only needed if you require a 
        // task be complete before the Workbench jar is built.
		dependsOn(jar)
	}

	test {
		useJUnitPlatform()
	}

	withType<KotlinCompile>() {
		kotlinOptions.jvmTarget = "16"
	}
}
```
Running the `printAvailConfig` task for the above configuration will print 
the following to standard out:

```shell
========================= Avail Configuration =========================
	Avail Version: 1.6.0.20210910.181950
	Repository Location: /Users/MyUser/Development/MyProject/avail/repositories
	Roots Location: /Users/MyUser/Development/MyProject/avail/my-roots
	Included Roots:
		-avail: jar:/Users/MyUser/Development/MyProject/avail/my-roots/avail-stdlib.jar
		-imported-library: jar:/Users/Some/Place/Else/imported-library.jar
		-my-avail-root: /Users/MyUser/Development/MyProject/avail/my-roots/my-avail-root
	Created Roots:
		my-avail-root
	Included Workbench Dependencies:
		/Users/MyUser/Development/MyProject/build/libs/sample-1.0.0.jar
	Workbench VM Options:
		--ea
		--XX:+UseCompressedOops
		--Xmx12g
		--DavailDeveloper=true
		--DavailRoots=avail=jar:/Users/MyUser/Development/MyProject/avail/my-roots/avail-stdlib.jar;imported-library=jar:/Users/Some/Place/Else/imported-library.jar;my-avail-root=/Users/MyUser/Development/MyProject/avail/my-roots/my-avail-root
		--Davail.repositories=/Users/MyUser/Development/MyProject/avail/repositories
========================================================================
```
