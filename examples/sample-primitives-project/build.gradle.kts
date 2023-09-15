import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"

    // Import the Avail Plugin into the build script
    id("org.availlang.avail-plugin") version "2.0.0.alpha20"
}

group = "org.availlang"
version = "1.0.0.alpha01"
description = "JVM-based primitives written in Kotlin to be accessed using " +
    "Avail's primitive linker"

repositories {
    mavenCentral()
}

val targetJVM = 17
val kotlinLanguage = 1.7

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJVM))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJVM))
    }
}

dependencies {
    // Must use the Avail VM to build primitives
    implementation("org.availlang:avail:2.0.0.alpha23")

    // Downloads avail library to ~/.avail/libraries
    avail("org.availlang:avail-stdlib:2.0.0.alpha23-1.6.1.alpha14")
}

avail {
    // A description for this Avail project.
    projectDescription =
        "This description goes into the Avail manifest in the jar!"

    // The version of the Avail VM to target. This is used to specify the
    // version of the Avail VM when launching Anvil
    availVersion = "2.0.0.alpha23"

    // The name of the Avail project. This will be the name of the Avail project
    // config file. It defaults to the Gradle project name.
    name = "sample-linking-primitives"

    // Adds an Avail library from a dependency from one of the Gradle
    // repositories.
    includeAvailLibDependency(
        rootName = "avail-stdlib",
        rootNameInJar = "avail",
        dependency = "org.availlang:avail-stdlib:2.0.0.alpha23-1.6.1.alpha14")

    // Add this new root to the roots directory and create it. Will only create
    // files in this root that do not already exist.
    createProjectRoot("sounds-root").apply {
        val customHeader =
            "Copyright © 1993-2023, The Avail Foundation, LLC.\n" +
                "All rights reserved."
        // Add a module package to this created root. Only happens if file does
        // not exist.
        modulePackage("App").apply {
        // Specify module header for package representative.
            versions = listOf("Avail-1.6.1")
            // The modules to extend in the Avail header.
            extends = listOf("Avail", "Sounds")
            // Add a module to this module package.
            addModule("Sounds").apply {
                // Specify module header for this module.
                versions = listOf("Avail-1.6.1")
                // The modules to list in the uses section in the Avail header.
                uses = listOf("Avail")
                // Override the module header comment from
                // moduleHeaderCommentBodyFile
                moduleHeaderCommentBody = customHeader
            }
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = targetJVM.toString()
        targetCompatibility = targetJVM.toString()
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = targetJVM.toString()
            freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
            languageVersion = "1.9"
        }
    }

    // Builds the jar and copies it into the Avail root, `my-avail-root`.
    register<Copy>("copyJarToAvailRoot") {
        group = "build"
        dependsOn(build)
        from(layout.buildDirectory.file(
            "libs/${project.name}-${project.version}.jar"))
        into(file("$projectDir${File.separator}roots${File.separator}" +
            "sounds-root${File.separator}App.avail"
        ))
        rename {
            "sounds.jar"
        }
    }

    setupProject {
        doLast {
            // TODO copy Frog.wav from resources to
            //  roots/sounds-root/App.avail/Frog.wav
        }
    }
}

kotlin {
    jvmToolchain(17)
}
