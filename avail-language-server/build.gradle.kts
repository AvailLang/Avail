plugins {
    kotlin("jvm") version "1.9.0"
}

group = "org.availlang"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // https://github.com/eclipse-lsp4j/lsp4j
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    api(project(":avail"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
