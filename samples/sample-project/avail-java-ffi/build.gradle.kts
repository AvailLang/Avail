plugins {
    java
}

group = "org.availlang.sample"
version = "1.0"
description = "Java foreign function interface (FFI) to be accessed using " +
    "Avails Pojos"

repositories {
    mavenCentral()
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
        kotlinOptions.jvmTarget = "11"
    }

    withType<JavaCompile>() {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
}