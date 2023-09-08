/*
 * build.gradle.kts
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import avail.plugin.CreateAvailArtifactJar
import org.availlang.artifact.AvailArtifactType.APPLICATION
import org.availlang.artifact.AvailArtifactType.LIBRARY
import org.availlang.artifact.PackageType.JAR
import org.availlang.artifact.environment.location.AvailRepositories
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme.FILE
import org.availlang.artifact.environment.project.AvailProject.Companion.ROOTS_DIR
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
    kotlin("jvm") version Versions.kotlin

    // Import the Avail Plugin into the build script
    id("org.availlang.avail-plugin") version Versions.availGradle

    // Used to create a runnable Uber Jar
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.availlang.sample"
version = "2.0.0.alpha02"

repositories {
    mavenLocal()
    mavenCentral()
}

val jvmTarget = 17
val jvmTargetString = "17"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.jvmTarget))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.jvmTarget))
    }
}

dependencies {
    // Dependency prevents SLF4J warning from being printed
    // see: http://www.slf4j.org/codes.html#noProviders

    // Can add an Avail library dependency as a jar available in one of the
    // repositories listed in the repository section
    implementation("org.availlang:avail:2.0.0.alpha23")

    // Downloads avail library to ~/.avail/libraries
    avail("org.availlang:avail-stdlib:2.0.0.alpha23-1.6.1.alpha14")

    testImplementation(kotlin("test"))
}

// This block configures an AvailExtension instance that is used by the Avail
// Gradle plugin for configuring the Avail application.
avail {
    // A description for this Avail project.
    projectDescription =
        "This description goes into the Avail manifest in the jar!"

    // The version of the Avail VM to target. This is used to specify the
    // version of the Avail VM when launching Anvil
    availVersion = "2.0.0.alpha23"

    // The name of the Avail project. This will be the name of the Avail project
    // config file. It defaults to the Gradle project name.
    name = "sample-hybrid"

    // Adds an Avail library from a dependency from one of the Gradle
    // repositories.
    includeAvailLibDependency(
        rootName = "avail-stdlib",
        rootNameInJar = "avail",
        dependency = "org.availlang:avail-stdlib:2.0.0.alpha23-1.6.1.alpha14")


    // Specify the AvailLocation where to write the .repo files to. This
    // defaults to the Avail home repos, AvailRepositories, directory in the
    // user's home directory: <user-home>/.avail/repositories
    repositoryDirectory = AvailRepositories(rootNameInJar = null)

    // The AvailLocation directory where the project's Avail roots exist, not
    // imported libraries. By default, this is in AvailProject.ROOTS_DIR at the
    // top level of the project which is the value currently set here.
    rootsDirectory = ProjectHome(
        ROOTS_DIR,
        FILE,
        project.projectDir.absolutePath,
        null)

    // Point to a file that contains the file header comment body to be used
    // by all generated modules.
    moduleHeaderCommentBodyFile = "$projectDir/copyright.txt"

    projectRoot("other-root")

    // Add this new root to the roots directory and create it. Will only create
    // files in this root that do not already exist.
    createProjectRoot("my-avail-root").apply{
        val customHeader =
            "Copyright © 1993-2022, The Avail Foundation, LLC.\n" +
                "All rights reserved."
        // Add a module package to this created root. Only happens if file does
        // not exist.
        modulePackage("App").apply{
            // Specify module header for package representative.
            versions = listOf("Avail-1.6.1")
            // The modules to extend in the Avail header.
            extends = listOf("Avail", "Configurations", "Network")
            // Add a module to this module package.
            addModule("Configurations").apply {
                // Specify module header for this module.
                versions = listOf("Avail-1.6.1")
                // The modules to list in the uses section in the Avail header.
                uses = listOf("Avail")
                // Override the module header comment from
                // moduleHeaderCommentBodyFile
                moduleHeaderCommentBody = customHeader
            }
            // Add a module package to this module package.
            addModulePackage("Network").apply {
                println("Setting up Network.avail")
                versions = listOf("Avail-1.6.1")
                uses = listOf("Avail")
                extends = listOf("Server")
                moduleHeaderCommentBody = customHeader
                addModule("Server").apply {
                    versions = listOf("Avail-1.6.1")
                    uses = listOf("Avail")
                    moduleHeaderCommentBody = customHeader
                }
            }
        }

        // Add a module to the top level of the created root.
        module("Scripts").apply {
            versions = listOf("Avail-1.6.1")
            uses = listOf("Avail")
            moduleHeaderCommentBody = customHeader
        }
    }

    // This represents a PackageAvailArtifact. It is used to configure the
    // creation of an Avail artifact.
    artifact {
        // The AvailArtifactType; either LIBRARY or APPLICATION. The default
        // is APPLICATION.
        artifactType = APPLICATION

        // The PackageType that indicates how the Avail artifact is to be
        // packaged. Packaging as a JAR is the default setting. At time of
        // writing on JAR files were supported for packaging.
        packageType = JAR

        // The base name to give to the created artifact. This defaults to the
        // project name.
        artifactName = project.name

        // The version that is set for the artifact. This is set to the
        // project's version by default.
        version = project.version.toString()

        // The [Attributes.Name.IMPLEMENTATION_TITLE inside the JAR file
        // MANIFEST.MF.
        implementationTitle = "Avail Sample Hybrid Application"

        // The [Attributes.Name.MAIN_CLASS] for the manifest or an empty string
        // if no main class set. This should be the primary main class for
        // starting the application.
        jarManifestMainClass = "org.availlang.sample.AppKt"

        // The location to place the artifact. The value shown is the default
        // location.
        outputDirectory = "${project.buildDir}/libs/"

        // The MessageDigest algorithm to use to create the digests for all the
        // Avail roots' contents. This must be a valid algorithm accessible from
        // `java.security.MessageDigest.getInstance`.
        artifactDigestAlgorithm = "SHA-256"

        // Add a file to the artifact
//        addFile(File("a/file/somewhere.txt"), "target/dir/in/artifact")

        // Add a JAR file (`JarFile`) to the artifact
//        addJar(myJarFile)

        // Add a zip file (`ZipFile`) to the artifact
//        addZipFile(myZipFile)

        // Add directory to the artifact
//        addDirectory(File("some/directory"))

        // Add a dependency to the artifact that will be resolved by this task
        dependency("org.availlang:avail-json:1.2.0")

        // Add a module dependency to the artifact that will be resolved by this
        // task
        dependency(project.dependencies.create(project(":avail-java-ffi")))
    }
}

// A helper getter to obtain the AvailExtension object configured in the
// `avail {}` block above.
val availExtension get() = project.extensions
    .findByType(avail.plugin.AvailExtension::class.java)!!

tasks {
    jar {
        doLast {
            // This re-creates the JAR, deleting the present JAR first. This
            // is done due to the publishing sanity check introduced in Gradle
            // 6.3 that does an internal check to confirm that the jar was
            // effectively constructed by the standard JAR task in some
            // predetermined internal order. This problem manifests with this
            // error message:
            // `Artifact <TARGET JAR>.jar wasn't produced by this build.`
            // At the time of writing this was the only solution identified so
            // far that overcame the issue.
            availExtension.createArtifact()
        }
    }

//     This is the task that uses the configuration done in the AvailExtension
//     block, `avail {}`, to construct the artifact JAR. It is not necessary to
//     add this to the tasks, it is only here to demonstrate its existence for
//     completeness.
    availArtifactJar {

    }

    // This demonstrates the use of CreateAvailArtifactJar task to create a task
    // that constructs a custom Avail artifact.
    val myCustomArtifactJar by creating(CreateAvailArtifactJar::class.java)
    {
        // Ensure project is built before creating jar.
        dependsOn(build)

        // The version to give to the created artifact
        // ([Attributes.Name.IMPLEMENTATION_VERSION]). This is a required field.
        version.set("1.2.3")

        // The base name of the artifact. This is a required field.
        artifactName.set("my-custom-artifact")

        // The AvailArtifactType; either LIBRARY or APPLICATION. The default
        // is APPLICATION.
        artifactType = LIBRARY

        // The description of the Avail artifact added to the artifacts
        // AvailArtifactManifest.
        artifactDescription = "A description of the Avail artifact " +
            "constructed by this custom task."

        // The [Attributes.Name.IMPLEMENTATION_TITLE inside the JAR file
        // MANIFEST.MF. This defaults to Project.name
        implementationTitle = "Avail Sample Hybrid Application"

        // Add an AvailRoot to this custom Avail artifact. Note that the added
        // root MUST be present in the AvailExtension (avail {}) configuration
        // added with either:
        //	 * AvailExtension.includeAvailLibDependency
        //	 * AvailExtension.includeStdAvailLibDependency
        addRoot("my-avail-root")

        // Add a file to the artifact
//        addFile(File("a/file/somewhere.txt"), "target/dir/in/artifact")

        // Add a JAR file (`JarFile`) to the artifact
//        addJar(myJarFile)

        // Add a zip file (`ZipFile`) to the artifact
//        addZipFile(myZipFile)

        // Add directory to the artifact
//        addDirectory(File("some/directory"))

        // Add a dependency to the artifact that will be resolved by this task
        dependency("org.availlang:avail-json:1.2.0")

        // Add a module dependency to the artifact that will be resolved by this
        // task
        dependency(project.dependencies.create(project(":avail-java-ffi")))
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = jvmTargetString
    }

    withType<JavaCompile> {
        sourceCompatibility = jvmTargetString
        targetCompatibility = jvmTargetString
    }
    jar {
        manifest.attributes["Main-Class"] =
            "avail.project.AvailProjectWorkbenchRunner"
        archiveVersion.set("")
    }

    shadowJar {
        archiveVersion.set("")
        destinationDirectory.set(file("./"))
    }

    test {
        useJUnit()
        val toolChains =
            project.extensions.getByType(JavaToolchainService::class)
        javaLauncher.set(
            toolChains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(
                    Versions.jvmTarget))
            })
        testLogging {
            events = setOf(FAILED)
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}
