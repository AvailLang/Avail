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

 * `rootsDirectory` - The absolute filepath to the base roots directory where 
   your Avail project's roots are located.
 * `repositoryDirectory` - The directory to place your build `.repo` files 
   for the included Avail roots.
 * `useAvailStdLib` - `true` indicates that the `avail-stdlib` Avail root jar 
   with the same version as this plugin should be copied into the 
   `rootsDirectory` and included as a root in when the Avail Workbench is 
   launched (*see `runWorkbench` task in the Plugin Tasks section*).
 * `root(name, uri)` - Adds an Avail root to the project. The URI indicates 
   the file location of the root directory as a file directory or a jar file.
   This root is included in the Avail Workbench when `runWorkbench` is 
   launched. This does not result in copying the root to the `rootsDirectory`,
   it only points to location of the root. 
 * `root(name)` - Adds an Avail root to the `rootsDirectory`. This sets the 
   root URI as: `"$rootsDirectory/my-avail-root"`, defaulting to using the 
   `file://` scheme for the URI. It is expected that the root has already 
   been created.
 * `workBenchDependency(dependency)` - Add a local Jar as a dependency to be 
   included in the `workbench.jar` (fatjar). The dependency should be the 
   absolute path to the jar to be included.

## Plugin Tasks
The following represent the tasks provided by the Avail Plugin.

![Avail Plugin Tasks](readme-resources/tasks.jpg?raw=true)

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
    this.useAvailStdLib = true
   
    // Sets the roots directory
    rootsDirectory = "/Users/MyUser/Development/MyProject/roots"

    // Sets the repositories directory
    rootsDirectory = "/Users/MyUser/Development/MyProject/repositories"
   
    // Add this root to the roots directory. This sets the root URI as:
    // `"$rootsDirectory/my-avail-root"`, defaulting to using the `file://` 
    // scheme for the URI.
    root("my-avail-root")
   
    // Add this root to the roots directory. The version allows you to specify
    // the URI location of a root anywhere on your system. At present Avail
    // supports roots from a file system using the `file://` scheme or a jar
    // using the URI scheme`jar:/`
    root("imported-library", "jar:/Users/Some/Place/Else/imported-library.jar")

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
	Repository Location: /Users/MyUser/Development/MyProject/repositories
	Roots Location: /Users/MyUser/Development/MyProject/roots
	Included Roots:
		-avail: jar:/Users/MyUser/Development/MyProject/roots/avail-stdlib.jar
		-my-avail-root: /Users/MyUser/Development/MyProject/roots/my-avail-root
		-imported-library: jar:/Users/Some/Place/Else/imported-library.jar
	Included Workbench Dependencies:
		/Users/MyUser/Development/MyProject/build/libs/sample-1.0.0.jar
=========================================================
```
