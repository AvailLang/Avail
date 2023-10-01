import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

plugins {
    kotlin("jvm") version "1.9.10"
    `maven-publish`
    publishing
    signing
    id("org.jetbrains.dokka") version "1.8.20"
}

group = "org.availlang"
version = "2.0.0.alpha22"  // For next publish.

repositories {
    mavenLocal()
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    api("org.availlang:avail-json:2.1.2")
    testImplementation(kotlin("test"))
}

val isReleaseVersion =
    !version.toString().toUpperCaseAsciiOnly().endsWith("SNAPSHOT")

///////////////////////////////////////////////////////////////////////////////
//                       Publish Utilities
///////////////////////////////////////////////////////////////////////////////
val ossrhUsername: String get() =
    System.getenv("OSSRH_USER") ?: ""
val ossrhPassword: String get() =
    System.getenv("OSSRH_PASSWORD") ?: ""

private val credentialsWarning =
    "Missing OSSRH credentials.  To publish, you'll need to create an OSSRH " +
            "JIRA account. Then ensure the user name, and password are available " +
            "as the environment variables: 'OSSRH_USER' and 'OSSRH_PASSWORD'"

/**
 * Check that the `publish` task has access to the necessary credentials.
 */
fun checkCredentials ()
{
    if (ossrhUsername.isEmpty() || ossrhPassword.isEmpty())
    {
        System.err.println(credentialsWarning)
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
    withType<Test> {
        val toolChains =
            project.extensions.getByType(JavaToolchainService::class)
        javaLauncher.set(
            toolChains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(targetJVM))
            })
        testLogging {
            events = setOf(TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }

    val sourceJar by creating(Jar::class) {
        description = "Creates sources JAR."
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    val dokkaHtml by getting(org.jetbrains.dokka.gradle.DokkaTask::class)

    val javadocJar by creating(Jar::class)
    {
        dependsOn(dokkaHtml)
        description = "Creates Javadoc JAR."
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        archiveClassifier.set("javadoc")
        from(dokkaHtml.outputDirectory)
    }

    jar {
        manifest.attributes["Implementation-Version"] =
            project.version
        doFirst {
            delete(fileTree("$buildDir/libs").matching {
                include("**/*.jar")
                exclude("**/*-all.jar")
            })
        }
    }

//    artifacts {
//        add("archives", sourceJar)
//    }
    publish {
        checkCredentials()
        dependsOn(build)
        dependsOn(sourceJar)
        dependsOn(javadocJar)
    }
}

signing {
    useGpgCmd()
    sign(the<PublishingExtension>().publications)
}

publishing {
    repositories {
        maven {
            url = if (isReleaseVersion)
            {
                // Release version
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            else
            {
                // Snapshot
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            }
            println("Publishing snapshot: $isReleaseVersion")
            println("Publishing URL: $url")
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }

    publications {

        create<MavenPublication>("avail-artifact") {
            pom {
                groupId = project.group.toString()
                name.set("Avail Artifact")
                packaging = "jar"
                description.set(
                    "This module provides utilities for working with "
                    + "Avail artifacts.")
                url.set("https://www.availlang.org/")
                licenses {
                    license {
                        name.set("BSD 3-Clause \"New\" or \"Revised\" License")
                        url.set("https://github.com/AvailLang/avail-storage/blob/main/LICENSE")
                    }
                }
                scm {
                    connection.set("TODO")
                    developerConnection.set("TODO")
                    url.set("TODO")
                }
                developers {
                    developer {
                        id.set("richATAvail")
                        name.set("Richard Arriaga")
                    }
                }
            }
            val sourceJar = tasks.getByName("sourceJar") as Jar
            val javadocJar = tasks.getByName("javadocJar") as Jar
            from(components["java"])
            artifact(sourceJar)
            artifact(javadocJar)
        }
    }
}
