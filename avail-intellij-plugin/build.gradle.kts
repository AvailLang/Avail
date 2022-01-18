import avail.build.cleanupAllJars

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("com.github.johnrengelman.shadow")
}

group = "avail.plugin"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":avail-json"))
    implementation(project(":avail-storage"))
    implementation(project(":avail-core"))
    implementation(project(":avail-stdlib"))
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    pluginName.set("Avail IntelliJ Plugin")
    version.set("2021.3.1")
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

    jar {
        manifest.attributes["Implementation-Title"] = "Avail IntelliJ Plugin"
        manifest.attributes["Implementation-Version"] = project.version
        doFirst { cleanupAllJars() }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
