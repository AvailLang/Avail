plugins {
    kotlin("jvm") version "1.9.10"
}

group = "org.availlang"
version = "0.0.1"

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
