import org.availlang.artifact.AvailArtifactType
import org.availlang.artifact.PackageType
import org.availlang.artifact.environment.location.AvailRepositories
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProject

plugins {
    id("java")

    // Import the Avail Plugin into the build script
    id("org.availlang.avail-plugin") version "2.0.0.alpha20"

}

group = "org.availlang.sample"
version = "2.0.0.alpha02"
description = "Java foreign function interface (FFI) to be accessed using " +
    "Avail's library linker"

repositories {
    mavenLocal()
    mavenCentral()
}

val jvmTarget = 17
val jvmTargetString = "17"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmTarget))
    }
}

dependencies {
    // Downloads avail library to ~/.avail/libraries
    avail("org.availlang:avail-stdlib:2.0.0.alpha23-1.6.1.alpha14")
}

// This block configures an AvailExtension instance that is used by the Avail
// Gradle plugin for configuring the Avail application.
avail {
    // A description for this Avail project.
    projectDescription =
        "This description goes into the Avail manifest in the jar!"

    // The version of the Avail VM to target. This is used to specify the
    // version of the Avail VM when launching Anvil
    availVersion = "2.0.0.alpha23"

    // The name of the Avail project. This will be the name of the Avail project
    // config file. It defaults to the Gradle project name.
    name = "sample-linking-library"

    // Adds an Avail library from a dependency from one of the Gradle
    // repositories.
    includeAvailLibDependency(
        rootName = "avail-stdlib",
        rootNameInJar = "avail",
        dependency = "org.availlang:avail-stdlib:2.0.0.alpha23-1.6.1.alpha14")


    // Specify the AvailLocation where to write the .repo files to. This
    // defaults to the Avail home repos, AvailRepositories, directory in the
    // user's home directory: <user-home>/.avail/repositories
    repositoryDirectory = AvailRepositories(rootNameInJar = null)

    // The AvailLocation directory where the project's Avail roots exist, not
    // imported libraries. By default, this is in AvailProject.ROOTS_DIR at the
    // top level of the project which is the value currently set here.
    rootsDirectory = ProjectHome(
        AvailProject.ROOTS_DIR,
        Scheme.FILE,
        project.projectDir.absolutePath,
        null)

    // Point to a file that contains the file header comment body to be used
    // by all generated modules.
    moduleHeaderCommentBodyFile = "$projectDir/copyright.txt"

    projectRoot("other-root")

    // Add this new root to the roots directory and create it. Will only create
    // files in this root that do not already exist.
    createProjectRoot("my-avail-root").apply{
        val customHeader =
            "Copyright © 1993-2022, The Avail Foundation, LLC.\n" +
                "All rights reserved."
        // Add a module package to this created root. Only happens if file does
        // not exist.
        modulePackage("App").apply{
            // Specify module header for package representative.
            versions = listOf("Avail-1.6.1")
            // The modules to extend in the Avail header.
            extends = listOf("Avail", "Configurations", "Network")
            // Add a module to this module package.
            addModule("Configurations").apply {
                // Specify module header for this module.
                versions = listOf("Avail-1.6.1")
                // The modules to list in the uses section in the Avail header.
                uses = listOf("Avail")
                // Override the module header comment from
                // moduleHeaderCommentBodyFile
                moduleHeaderCommentBody = customHeader
            }
            // Add a module package to this module package.
            addModulePackage("Network").apply {
                println("Setting up Network.avail")
                versions = listOf("Avail-1.6.1")
                uses = listOf("Avail")
                extends = listOf("Server")
                moduleHeaderCommentBody = customHeader
                addModule("Server").apply {
                    versions = listOf("Avail-1.6.1")
                    uses = listOf("Avail")
                    moduleHeaderCommentBody = customHeader
                }
            }
        }

        // Add a module to the top level of the created root.
        module("Scripts").apply {
            versions = listOf("Avail-1.6.1")
            uses = listOf("Avail")
            moduleHeaderCommentBody = customHeader
        }
    }

    // This represents a PackageAvailArtifact. It is used to configure the
    // creation of an Avail artifact.
    artifact {
        // The AvailArtifactType; either LIBRARY or APPLICATION. The default
        // is APPLICATION.
        artifactType = AvailArtifactType.APPLICATION

        // The PackageType that indicates how the Avail artifact is to be
        // packaged. Packaging as a JAR is the default setting. At time of
        // writing on JAR files were supported for packaging.
        packageType = PackageType.JAR

        // The base name to give to the created artifact. This defaults to the
        // project name.
        artifactName = project.name

        // The version that is set for the artifact. This is set to the
        // project's version by default.
        version = project.version.toString()

        // The [Attributes.Name.IMPLEMENTATION_TITLE inside the JAR file
        // MANIFEST.MF.
        implementationTitle = "Avail Sample Hybrid Application"

        // The [Attributes.Name.MAIN_CLASS] for the manifest or an empty string
        // if no main class set. This should be the primary main class for
        // starting the application.
        jarManifestMainClass = "org.availlang.sample.AppKt"

        // The location to place the artifact. The value shown is the default
        // location.
        outputDirectory = "${project.buildDir}/libs/"

        // The MessageDigest algorithm to use to create the digests for all the
        // Avail roots' contents. This must be a valid algorithm accessible from
        // `java.security.MessageDigest.getInstance`.
        artifactDigestAlgorithm = "SHA-256"

        // Add a file to the artifact
//        addFile(File("a/file/somewhere.txt"), "target/dir/in/artifact")

        // Add a JAR file (`JarFile`) to the artifact
//        addJar(myJarFile)

        // Add a zip file (`ZipFile`) to the artifact
//        addZipFile(myZipFile)

        // Add directory to the artifact
//        addDirectory(File("some/directory"))

        // Add a dependency to the artifact that will be resolved by this task
        dependency("org.availlang:avail-json:1.2.0")

        // Add a module dependency to the artifact that will be resolved by this
        // task
        // dependency(project.dependencies.create(project(":some-module")))
    }
}

// A helper getter to obtain the AvailExtension object configured in the
// `avail {}` block above.
val availExtension get() = project.extensions
    .findByType(avail.plugin.AvailExtension::class.java)!!

tasks {
    withType<JavaCompile> {
        sourceCompatibility = jvmTargetString
        targetCompatibility = jvmTargetString
    }
    jar {
        manifest.attributes["Main-Class"] =
            "avail.project.AvailProjectWorkbenchRunner"
        archiveVersion.set("")
    }
}
