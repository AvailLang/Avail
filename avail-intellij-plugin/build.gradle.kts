plugins {
    kotlin("jvm") version Versions.kotlin
    id("org.jetbrains.intellij") version Versions.intellij
    id("avail.avail-plugin") version Versions.avail
}

group = "avail.plugin"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.slf4j:slf4j-nop:2.0.0-alpha5")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("2021.2.3")
}
tasks {
    patchPluginXml {
        changeNotes.set("""
            Initial Development        """.trimIndent())
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
        kotlinOptions.jvmTarget = "11"
    }

    withType<JavaCompile>() {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
}
