/*
 * build.gradle.kts
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import avail.plugin.AvailWorkbenchTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
    kotlin("jvm") version Versions.kotlin
    id("avail.avail-plugin") version Versions.avail
}

group = "org.availlang.sample"
version = "1.0"

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
        (this as JavaToolchainSpec).languageVersion.set(
            JavaLanguageVersion.of(Versions.jvmTarget))
    }
}

dependencies {
    // The module that is the foreign function interface that provides Pojos
    // written in Java that is usable by Avail.
    implementation(project(":avail-java-ffi"))

    // Dependency prevents SLF4J warning from being printed
    // see: http://www.slf4j.org/codes.html#noProviders
//    implementation("org.slf4j:slf4j-nop:${Versions.slf4jnop}")

    // Can add an Avail library dependency as a jar available in one of the
    // repositories listed in the repository section
    // availLibrary("avail:example-lib:1.2.3")
    testImplementation(kotlin("test"))
    implementation("avail:avail-stdlib:${Versions.avail}")
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
    }

    // Specify where the main Avail roots' directory is located.
    rootsDirectory = "$projectDir/avail/my-roots"
    // Specify where to write the .repo files to.
    repositoryDirectory = "$projectDir/avail/my-repos"

    // Point to a file that contains the file header comment body to be used
    // by all generated modules.
    moduleHeaderCommentBodyFile = "$projectDir/copyright.txt"

    // Add this new root to the roots directory and create it. Will only create
    // files in this root that do not already exist.
    createRoot("my-avail-root").apply{
        val customHeader =
            "Copyright © 1993-2021, The Avail Foundation, LLC.\n" +
                "All rights reserved."
        // This specifies that this root should be package into a jar.
        packageContext =
            AvailLibraryPackageContext("myJar", "$buildDir/libs").apply {
                // Add any key-value pairs to the manifest included in the jar.
                manifestPairs["some-key"] = "some-value"
                // An action that will happen after the jar file is created.
                postPackageAction = {
                    println(
                        "Hi there, this is where the file is: ${it.absolutePath}")
                }
            }
        // Add a module package to this created root. Only happens if file does
        // not exist.
        modulePackage("App").apply{
            // Specify module header for package representative.
            versions = listOf("Avail-1.6.0")
            // The modules to extend in the Avail header.
            extends = listOf("Avail", "Configurations", "Network")
            // Add a module to this module package.
            addModule("Configurations").apply {
                // Specify module header for this module..
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

        // Add a module to the top level of the created root.
        module("Scripts").apply {
            versions = listOf("Avail-1.6.0")
            uses = listOf("Avail")
            moduleHeaderCommentBody = customHeader
        }
    }
}

tasks {
    // Customize task that runs default workbench.
    assembleAndRunWorkbench {
        // This task is customizable in the same manner as any
        // AvailWorkbenchTask.
        dependency(project.dependencies.project(":avail-java-ffi"))
    }

    // Add your own custom task to assemble and launch an Avail workbench.
    val myWorkbenchTask by creating(AvailWorkbenchTask::class.java)
    {
        group = "My Tasks"
        description = "My custom workbench build."
        dependsOn(jar)
        workbenchJarBaseName = "myCustomWorkbench"
        rebuildWorkbenchJar = true
        maximumJavaHeap = "6g"
        dependsOn(jar)
        // Can specify a directly accessible jar
        workbenchLocalJarDependency("$buildDir/libs/sample-project.jar")

        // Dependency prevents SLF4J warning from being printed
        // see: http://www.slf4j.org/codes.html#noProviders
//        dependency("org.slf4j:slf4j-nop:${Versions.slf4jnop}")
        dependency(project.dependencies.project(":avail-java-ffi"))
        root("my-avail-root", "$projectDir/avail/my-roots/my-avail-root")
        root(
            "avail",
            "jar:$projectDir/avail/my-roots/avail-stdlib.jar")
        vmOption("-ea")
        vmOption("-XX:+UseCompressedOops")
        vmOption("-DavailDeveloper=true")
    }

    withType<KotlinCompile>() {
        kotlinOptions.jvmTarget = jvmTargetString
    }

    withType<JavaCompile>() {
        sourceCompatibility = jvmTargetString
        targetCompatibility = jvmTargetString
    }
    jar {
        archiveVersion.set("")
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
