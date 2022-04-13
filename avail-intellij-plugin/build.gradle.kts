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
//    implementation("org.slf4j:slf4j-nop:2.0.0-alpha5")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    pluginName.set("anvil")
    plugins.set(listOf("com.intellij.java"))
    version.set("2021.3.1")
}
tasks {
    patchPluginXml {
        changeNotes.set("""Initial Development""".trimIndent())
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
        //doFirst { cleanupAllJars() }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
