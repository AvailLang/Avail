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
val kotlinLanguage = 1.9

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
    implementation("org.availlang:avail:2.0.0.alpha27")

    // Downloads avail library to ~/.avail/libraries
    avail("org.availlang:avail-stdlib:2.0.0.alpha23-1.6.1.alpha15")
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
}

kotlin {
    jvmToolchain(17)
}
