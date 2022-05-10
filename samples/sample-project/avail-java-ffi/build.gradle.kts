plugins {
    java
}

group = "org.availlang.sample"
version = "1.0"
description = "Java foreign function interface (FFI) to be accessed using " +
    "Avails Pojos"

repositories {
    mavenCentral()
    maven {
        url = uri("${rootProject.rootDir}/libs/")
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
        kotlinOptions.jvmTarget = "17"
    }

    withType<JavaCompile>() {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}
