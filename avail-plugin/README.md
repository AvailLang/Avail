Avail Gradle Plugin
===============================================================================

The Avail Gradle Plugin, `avail-plugin`, provides Avail-based project setup 
using a Gradle Plugin. It can be found on [Github](https://github.com/orgs/AvailLang/packages?repo_name=Avail).

## Overview
The plugin provides:
 * The inclusion of the `org.availlang:avail-core` dependency in the 
   `implementation` `Configuration` of your project.
 * Initializes your Avail Roots directory, or uses a default.
 * Initializes your Avail Repositories directory, or uses a default.
 * Includes the Avail Standard Library, `org.availlang:avail-stdlib` as an 
   Avail Root for your Avail project by default but permits opting out of 
   using the Avail Standard Library.
 * A human-readable printable configuration of your Avail Project.
 
## Setup

To make the plugin accessible you must include the following in the 
repositories' area of your Gradle build file: 

**`build.gradle.kts`**
```kotlin
maven {
	// TODO probably can be removed as not going in Github repository
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
	// TODO probably can be removed as not going in Github repository
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

To include the plugin, you must add the plugin id, `avail.avail-plugin` and 
provide a corresponding Avail release version in your Gradle build file. The 
following is an example that imports the version `1.6.0.20210910.181950` of 
the `avail-core`, `avail-workbench`, and `avail-stdlib` releases (*The 
version should be changed to match your desired version of Avail.*):

**`build.gradle.kts`**
```kotlin
id("avail.avail-plugin") version "1.6.0.20210910.181950"
```

**`build.gradle`**
```groovy
id 'avail.avail-plugin' version '1.6.0.20210910.181950'
```

## Configuration
The plugin adds the Project extension, `AvailExtension`, to the host project. 
This is added at the top level of the buildscript using the keyword, `avail`.

```kotlin
avail {
	// Custom configuration goes here
}
```

Inside the `avail { ... }` block is where you can configure your Avail project. 
The following are the options available for configuration:

* ***rootsDirectory*** - (*optional; defaults to project directory in 
  avail/roots*) The absolute filepath to the base roots' directory where your 
  Avail project's roots are located.
   

* ***repositoryDirectory*** - (*optional; defaults to project directory in 
  avail/repositories*) The directory to place your build `.repo` files for 
  the included Avail roots.
 

* ***useAvailStdLib*** - `true` indicates that the `avail-stdlib` Avail root jar 
  with the same version as this plugin will be copied into the `rootsDirectory` 
  and included as a root in the Avail Workbench when it is launched (*see 
 `assembleAndRunWorkbench` task in the Plugin Tasks section*). Defaults to `true`.


* ***workbenchName*** - Provides a custom name for the workbench jar created and 
  run from the `assembleAndRunWorkbench` task. Defaults to "workbench". Jar 
  will be placed in `"$buildDir/workbench"`.


* ***root(name, optional uri, optional action)*** - Adds an `AvailRoot` to the 
  project. The URI indicates the file location of the root directory as a 
  file directory or a jar file. This root will be included in the Avail 
  Workbench when `assembleAndRunWorkbench` is launched. This does not result 
  in copying the root to the`rootsDirectory`, it only points to location of the 
  root. You can add an optional lambda that accepts the created `AvailRoot` 
  and is executed when the task `initializeAvail` is run (*see Avail Plugin 
  Tasks*). Defaults the `uri` to the `"$rootsDirectory/$name"`` and defaults 
  `action` to an empty lambda.
  

* ***createRoot(rootName, optional initializer)*** - Add an `AvailRoot` that is
   created if it does not already exist when `initializeAvail` is run. The 
   function takes an optional lambda that accepts the newly created root. The 
   following can be called/set in the body of the optional `initializer`:
  * **module(name, fileExtension, module initializer)** - Creates an Avail 
        Module at the top level of the root directory. It takes the base 
        module name, an optional `fileExtension` (*uses "avail" by default*), 
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
      

* ***workbenchDependency(dependency)*** - Add a local Jar as a dependency to be 
   included in the `"$workbenchName".jar` (fatjar). The dependency should be 
  the absolute path to the jar to be included. It is possible


* ***moduleHeaderCommentBodyFile*** - The absolute path to a file containing the 
   raw file comment header that may optionally be included in the 
   `AvailModule`s generated by the `createRoot` function.


* ***moduleHeaderCommentBody*** - A string to optionally include in the comment 
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
	availWorkbench("come.some.other:dependency:7.8.9")
}
```

## Plugin Tasks
The following represent the tasks provided by the Avail Plugin.

![Avail Plugin Tasks](readme-resources/tasks.jpg?raw=true)
 
* ***initializeAvail*** - Initializes the Avail project. It will create both 
  the roots' and repositories' directories if they don't exist. It will also 
  move the `avail-stdlib` jar into the roots' directory if `useAvailStdLib` is 
  set to `true` (*see Configuration section*). It creates the roots specified
  in the `avail` extension through the `createRoot` function. It will also 
  run the arbitrary lambdas included with the `root` functions (*see 
  Configuration section*). This is only run if a user explicitly runs it.
  The following is the result of running `initializeAvail` for the
  [example](#Example) included at the end of this README.
   
    ![Generated Output](readme-resources/generated.jpg?raw=true)
   

* ***assembleAndRunWorkbench*** - Builds the Avail Workbench fatjar using all 
  the necessary base dependencies as well as the additional jar dependencies 
  added using `workbenchDependency` in the `avail` extension. This task can 
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
	id("avail.avail-plugin") version "1.6.0.20210910.181950"
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
    // This adds the listed dependency to the custom build of the workbench fat
    // jar.
    availWorkbench("org.some.dependency:someProject:9.8.7")
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
        // Creates a module package and package representative
        root.modulePackage("App") { modulePackage ->
            modulePackage.versions = listOf("Avail-1.0.6")
            modulePackage.extends = listOf("Avail")
        }
    
        // Creates a module 
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
    workbenchDependency("${buildDir}/libs/sample-1.0.0.jar")
	workbenchDependency("/Users/Person/local/libs/myLocal-1.2.4.jar")
}
tasks {
	assembleAndRunWorkbench {
            // This task is used to build and launch the Avail Workbench. In this
            // example, the `dependsOn(jar)` is used as the Workbench fatjar is to
            // include this sample project's jar file (see the included 
            // workbenchDependency above). This is only needed if you require a 
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
