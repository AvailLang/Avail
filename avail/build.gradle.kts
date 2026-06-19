/*
 * build.gradle.kts
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

plugins {
	kotlin("jvm") version "2.3.10"
	id("java")
	`maven-publish`
	publishing
	signing
	id("org.jetbrains.dokka") version "2.1.0"
	id("com.gradleup.shadow") version "9.3.1"
}

repositories {
	mavenLocal()
	mavenCentral()
}

group = "org.availlang"
version = "2.0.0.alpha28"

/** The version of Kotlin to be used by Avail. */
val kotlin = "2.3.10"

/** The `com.gradleup.shadow` version. */
val shadow = "9.3.1"

/** The `org.jetbrains:annotations` version. */
val kotlinAnnotations = "26.0.2-1"

/** The `org.ow2.asm` version. */
val asmVersion = "9.9.1"

/** The `com.weblookandfeel:weblaf-ui` version.*/
val weblafVersion = "1.2.14"

/** The `io.methvin:directory-watcher` version. */
val directoryWatcherVersion = "0.19.1"

/** The version of the Apache Commons library to use. */
val apacheCommonsVersion = "4.5.0"

/** The `com.google.code.findbugs:jsr305` version. */
val jsrVersion = "3.0.2"

/** The `org.junit.jupiter:junit-jupiter` version. */
val junitVersion = "6.0.2"

/** The language version of Kotlin. */
val kotlinLanguage = "2.3.10"

/**
 * The JDK toolchain version. Used for compiling Java/Kotlin, running tests,
 * launching the workbench, and as the runtime image bundled inside Anvil.app
 * by the `packageApp` task.
 */
val jvmTarget = 26

/**
 * The bytecode level emitted by `javac` and `kotlinc`. Lags the toolchain
 * when the Kotlin compiler doesn't yet support the toolchain's bytecode
 * level — Kotlin 2.3.10 silently falls back to 25 on a JDK 26 toolchain,
 * so Java needs to follow suit to avoid a target mismatch.
 */
val jvmBytecodeTarget = 25

/** String form of [jvmBytecodeTarget] for `JavaCompile.sourceCompatibility`. */
val jvmTargetString = jvmBytecodeTarget.toString()

/**
 * The list of compile-time arguments to be used during Kotlin compilation.
 */
val freeCompilerArgs = listOf<String>()

/** The language version of Kotlin. */
val languageVersion = kotlinLanguage

/**
 * The current time as a String in the format `yyyy-MM-ddTHH:mm:ss.SSSZ`.
 */
val formattedNow: String get()
{
	val formatter = DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ")
		.withLocale(Locale.getDefault())
		.withZone(ZoneId.of("UTC"))
	return formatter.format(Instant.now())
}

val isReleaseVersion =
	!version.toString().endsWith("SNAPSHOT", ignoreCase = true)

/** The root Avail distribution directory name. */
val distroDir = "distro"

/** The relative path to the Avail distribution source directory. */
val distroSrc = systemPath(distroDir, "src")

/** The relative path to the Avail distribution lib directory. */
val distroLib = systemPath(distroDir, "lib")

/**
 * The root project directory path, captured early for configuration cache
 * compatibility.
 */
val rootDirPath: String = rootProject.layout.projectDirectory.asFile.absolutePath

/** The path to the bootstrap package. */
val bootstrapPackagePath =
	systemPath("avail", "tools", "bootstrap")

/**
 * The [Project.getProjectDir] relative path of the bootstrap package.
 */
val relativePathBootstrap =
	systemPath("src", "main", "kotlin", bootstrapPackagePath)

/**
 * [Project.getProjectDir]-relative path to the generated JVM classes.
 */
val buildClassesPath = "classes/kotlin/main"

/**
 * The build time string of the form: "yyyy-MM-ddTHH:mm:ss.SSSZ",
 * representing the time of the build.
 */
val built: String get() = formattedNow

/**
 * The [Project.getProjectDir] relative path of the built bootstrap package.
 */
val relativePathBootstrapClasses =
	systemPath(buildClassesPath, bootstrapPackagePath)

kotlin {
	jvmToolchain {
		languageVersion = JavaLanguageVersion.of(jvmTarget)
		vendor = JvmVendorSpec.ORACLE
	}
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(jvmTarget)
		vendor = JvmVendorSpec.ORACLE
	}
}

dependencies {
	api("org.availlang:avail-storage:1.1.1")
	api(project(":avail-artifact"))
	api("org.jetbrains.kotlin:kotlin-reflect:$kotlin")
	implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
	implementation("com.google.code.findbugs:jsr305:$jsrVersion")
	implementation(
		"org.apache.commons:commons-collections4:$apacheCommonsVersion")
	implementation("org.ow2.asm:asm:$asmVersion")
	implementation("org.ow2.asm:asm-analysis:$asmVersion")
	implementation("org.ow2.asm:asm-tree:$asmVersion")
	implementation("org.ow2.asm:asm-util:$asmVersion")
	implementation("io.methvin:directory-watcher:$directoryWatcherVersion")
	implementation("com.formdev:flatlaf:3.7.1")
	implementation("com.thizzer.jtouchbar:jtouchbar:1.0.0")
	compileOnly("org.jetbrains:annotations:$kotlinAnnotations")
	testImplementation(platform("org.junit:junit-bom:$junitVersion"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Compute the Avail roots. This is needed to properly configure "test".
val availRoots: String by lazy { computeAvailRootsForTest() }
tasks {
	withType<JavaCompile> {
		options.encoding = "UTF-8"
		sourceCompatibility = jvmTargetString
		targetCompatibility = jvmTargetString
	}

	withType<KotlinCompile> {
		compilerOptions {
			freeCompilerArgs = listOf()
			version = kotlinLanguage
		}
	}
	// Access JavaToolchainService at project level for configuration cache compatibility
	val javaToolchains = extensions.getByType(JavaToolchainService::class)
	withType<Test> {
		javaLauncher =
			javaToolchains.launcherFor {
				languageVersion.set(JavaLanguageVersion.of(jvmTarget))
			}
		testLogging {
			events = setOf(TestLogEvent.FAILED)
			exceptionFormat = TestExceptionFormat.FULL
			showExceptions = true
			showCauses = true
			showStackTraces = true
		}
	}

	val generated = layout.buildDirectory.dir("generated-resources")

	// Generate the list of all primitives, which a running Avail system uses
	// during setup to reflectively identify the complete catalog of primitives.
	val generatePrimitivesList by registering(GenerateFileManifestTask::class) {
		description =
			"Generate the list of all primitives in All_Primitive.txt."
		basePath = layout.projectDirectory.dir("src/main/kotlin").asFile.path
		val prefix = "$basePath${File.separator}"
		inputs.files(
			fileTree(basePath) {
				include("avail/interpreter/primitive/**/P_*.kt")
			})
		outputs.dir(generated)
		outputFilePath = "avail/interpreter/All_Primitives.txt"
		fileNameTransformer = {
			// Transform from a relative path to a fully qualified class name.
			replaceFirst(".kt", "")
				.replace(prefix, "")
				.replace(File.separator, ".")
		}
	}

	sourceSets.main {
		resources {
			srcDir(generatePrimitivesList)
		}
	}

	test {
		useJUnitPlatform()
		println("Java version for tests: $javaVersion")
		minHeapSize = "4g"
		maxHeapSize = "6g"
		enableAssertions = true
		systemProperty("availRoots", availRoots)
		testLogging {
			events("passed", "skipped", "failed")
		}
		systemProperty("java.awt.headless", "true")
	}

	jar {
		manifest.attributes["Implementation-Version"] = project.version
		manifest.attributes["Build-Time"] = formattedNow
		manifest.attributes["SplashScreen-Image"] =
			"workbench/AvailWBSplash.png"
		manifest.attributes["Main-Class"] = "avail.project.AvailProjectManagerRunner"
		manifest.attributes["Application-Name"] = "Anvil"
		// The All_Primitives.txt file must be added to the build resources
		// directory before we can build the jar.
	}

	shadowJar {
		archiveClassifier.set("anvil")
		archiveVersion.set("")
		destinationDirectory.set(file("../"))
	}

	val `package` by registering(DefaultTask::class) {
		description = "Create Anvil Jar"
		group = "anvil"
		description = "Package anvil.jar"
		dependsOn(shadowJar)
	}

	val packageAndRun by registering(JavaExec::class) {
		dependsOn(`package`)
		group = "anvil"
		description = "Package anvil.jar and run the Avail Project Manager"
		classpath = files("../avail-anvil.jar")
	}

	val run by registering(JavaExec::class) {
		group = "anvil"
		description = "Run the Avail Project Manager for an already built anvil.jar"
		classpath = files("../avail-anvil.jar")
	}

	// Native macOS bundling via jpackage. Produces a self-contained
	// Anvil.app under avail/build/jpackage whose Contents/runtime is a
	// jlinked image of the same JDK the workbench is compiled and run
	// against — i.e., whatever java.toolchain resolves to. Bumping the
	// project's jvmTarget automatically retargets the bundled runtime.
	val anvilRuntimeDir = layout.buildDirectory.dir("anvil-runtime")
	val anvilJpackageDir = layout.buildDirectory.dir("jpackage")
	val anvilAppName = "Anvil"
	val anvilMainClass = "avail.project.AvailProjectManagerRunner"
	val anvilIcon = layout.projectDirectory.file(
		"src/main/resources/workbench/AvailHammer.icns")
	val anvilModules = listOf(
		"java.base", "java.desktop", "java.logging", "java.naming",
		"java.prefs", "java.management", "java.net.http",
		"java.scripting", "java.sql", "java.xml", "java.compiler",
		"jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki",
		"jdk.attach", "jdk.localedata", "jdk.zipfs",
		"jdk.management", "jdk.unsupported.desktop",
		"jdk.jdwp.agent", "jdk.security.auth"
	).joinToString(",")

	val anvilJdkLauncher =
		javaToolchains.launcherFor(java.toolchain)
	val anvilJpackageInputDir =
		layout.buildDirectory.dir("jpackage-input")

	val jlinkAnvilRuntime by registering(Exec::class) {
		group = "anvil"
		description = "Build a slim JDK runtime image for Anvil.app."
		val metadata = anvilJdkLauncher.get().metadata
		val jdkHome = metadata.installationPath.asFile
		val out = anvilRuntimeDir.get().asFile
		outputs.dir(out)
		inputs.property("modules", anvilModules)
		inputs.property("jdk", metadata.javaRuntimeVersion)
		executable = File(jdkHome, "bin/jlink").absolutePath
		args = listOf(
			"--module-path", File(jdkHome, "jmods").absolutePath,
			"--add-modules", anvilModules,
			"--no-header-files",
			"--no-man-pages",
			"--compress=zip-6",
			"--output", out.absolutePath
		)
		doFirst { if (out.exists()) out.deleteRecursively() }
	}

	val stageAnvilApp by registering(Copy::class) {
		group = "anvil"
		description = "Stage avail-anvil.jar for jpackage."
		dependsOn(`package`)
		from(rootProject.layout.projectDirectory.file("../avail-anvil.jar"))
		into(anvilJpackageInputDir)
	}

	val packageApp by registering(Exec::class) {
		group = "anvil"
		description =
			"Assemble a self-contained Anvil.app via jpackage."
		dependsOn(stageAnvilApp)
		dependsOn(jlinkAnvilRuntime)
		val jdkHome =
			anvilJdkLauncher.get().metadata.installationPath.asFile
		val destDir = anvilJpackageDir.get().asFile
		val appBundle = File(destDir, "${anvilAppName}.app")
		val runtime = anvilRuntimeDir.get().asFile
		val input = anvilJpackageInputDir.get().asFile
		val iconPath = anvilIcon.asFile.absolutePath
		val appVersion = project.version.toString()
			.substringBefore('-')
			.split('.')
			.take(3)
			.joinToString(".") { it.toIntOrNull()?.toString() ?: "0" }
		val copyrightYear = LocalDate.now().year.toString()
		inputs.dir(anvilJpackageInputDir)
		inputs.dir(anvilRuntimeDir)
		inputs.file(anvilIcon)
		inputs.property("appVersion", appVersion)
		inputs.property("mainClass", anvilMainClass)
		outputs.dir(anvilJpackageDir)
		executable = File(jdkHome, "bin/jpackage").absolutePath
		args = listOf(
			"--type", "app-image",
			"--name", anvilAppName,
			"--app-version", appVersion,
			"--vendor", "The Avail Foundation, LLC.",
			"--copyright",
			"Copyright © 1993-$copyrightYear, " +
				"The Avail Foundation, LLC.",
			"--description",
			"Anvil, the Avail Workbench IDE.",
			"--icon", iconPath,
			"--input", input.absolutePath,
			"--main-jar", "avail-anvil.jar",
			"--main-class", anvilMainClass,
			"--runtime-image", runtime.absolutePath,
			"--java-options", "-ea",
			"--java-options", "-Xmx6g",
			"--java-options", "--enable-native-access=ALL-UNNAMED",
			"--java-options",
			$$"-splash:$APPDIR/avail-anvil.jar!" +
				"/workbench/AvailWBSplash.png",
			"--mac-package-identifier", "org.availlang.anvil",
			"--mac-package-name", anvilAppName,
			"--dest", destDir.absolutePath
		)
		doFirst {
			if (appBundle.exists()) appBundle.deleteRecursively()
			destDir.mkdirs()
		}
	}

	val packageAppAndRun by registering(Exec::class) {
		group = "anvil"
		description =
			"Build a self-contained Anvil.app and launch it."
		dependsOn(packageApp)
		val appBundle = File(
			anvilJpackageDir.get().asFile, "${anvilAppName}.app")
		commandLine("open", appBundle.absolutePath)
	}

	/**
	 * Remove released libraries.
	 *
	 * See [scrubReleases] in `Build.kt`.
	 */
	val scrubReleases by registering(Delete::class) {
		description =
			"Removes released libraries. See `scrubReleases` in `Build.kt`."
		scrubReleases(this)
	}

	// Update the dependencies of "clean".
	clean { dependsOn(scrubReleases) }

	val sourceJar by registering(Jar::class) {
		description = "Creates sources JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("sources")
		from(sourceSets["main"].allSource)
	}

	dokka {
		moduleName = "Avail"
		dokkaPublications.html {
			suppressInheritedMembers.set(true)
			failOnWarning.set(true)
		}
		dokkaSourceSets.main {
			sourceLink {
				localDirectory.set(file("src/main/kotlin"))
				remoteUrl("https://github.com/AvailLang/Avail/blob/main/src/main/kotlin")
				remoteLineSuffix.set("#L")
			}
		}
		pluginsConfiguration.html {
			//customStyleSheets.from("styles.css")
			//customAssets.from("logo.png")
			footerMessage.set("(c) The Avail Foundation")
		}
	}

	val javadocJar by registering(Jar::class) {
		// Dokka 2 task name for the html publication
		dependsOn("dokkaGeneratePublicationHtml")
		description = "Creates Javadoc JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("javadoc")
	}

	publish {
		PublishingUtility.checkCredentials()
		dependsOn(build)
		dependsOn(javadocJar)
	}

	/**
	 * Copy the generated bootstrap property files into the build directory so
	 * that the executable tools can find them as resources.
	 *
	 * See [relocateGeneratedPropertyFiles].
	 */
	val relocateGeneratedPropertyFiles by registering(Copy::class) {
		description =
			"Copy the generated bootstrap property files into the build " +
				"directory, that the executable tools can find them as " +
				"resources. See " +
				"`AvailBootstrapModule.relocateGeneratedPropertyFiles`."
		group = "bootstrap"
		relocateGeneratedPropertyFiles(this)
	}

	// Update the dependencies of "classes".
	classes { dependsOn(relocateGeneratedPropertyFiles) }

	/** Bootstrap Primitive_<lang>.properties for the current locale. */
	val generatePrimitiveNames by registering(JavaExec::class) {
		description =
			"Bootstrap Primitive_<lang>.properties for the current locale."
		group = "bootstrap"
		mainClass.set("avail.tools.bootstrap.PrimitiveNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		systemProperties["java.awt.headless"] = "true"
		dependsOn(classes)
	}

	/** Bootstrap ErrorCodeNames_<lang>.properties for the current locale. */
	val generateErrorCodeNames by registering(JavaExec::class) {
		description =
			"Bootstrap ErrorCodeNames_<lang>.properties for the current locale."
		group = "bootstrap"
		mainClass.set("avail.tools.bootstrap.ErrorCodeNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		systemProperties["java.awt.headless"] = "true"
		dependsOn(classes)
	}

	/** Bootstrap ErrorCodeNames_<lang>.properties for the current locale. */
	val generateSpecialObjectNames by registering(JavaExec::class) {
		description =
			"Bootstrap ErrorCodeNames_<lang>.properties for the current locale."
		group = "bootstrap"
		mainClass.set("avail.tools.bootstrap.SpecialObjectNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		systemProperties["java.awt.headless"] = "true"
		dependsOn(classes)
	}

	/**
	 * Gradle task to generate all bootstrap `.properties` files for the current
	 * locale.
	 */
	val generateAllNames by registering {
		description =
			"Gradle task to generate all bootstrap `.properties` files for " +
				"the current locale."
		group = "bootstrap"
		dependsOn(generatePrimitiveNames)
		dependsOn(generateErrorCodeNames)
		dependsOn(generateSpecialObjectNames)
	}

	/**
	 * Generate the new bootstrap Avail modules for the current locale.
	 *
	 * This is used in "projectGenerateBootStrap".
	 */
	val internalGenerateBootstrap by registering(JavaExec::class) {
		description =
			"Generate the new bootstrap Avail modules for the current locale." +
				"\n\tThis is used in Project.generateBootStrap."
		group = "internal"
		mainClass.set("avail.tools.bootstrap.BootstrapGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		systemProperties["java.awt.headless"] = "true"
		dependsOn(classes)
	}

	/**
	 * Gradle task to generate the new bootstrap Avail modules for the current
	 * locale and copy them to the appropriate location for distribution.
	 */
	register("generateBootstrap", Copy::class) {
		description =
			"Gradle task to generate the new bootstrap Avail modules for the " +
				"current locale and copy them to the appropriate location " +
				"for distribution."
		group = "bootstrap"
		projectGenerateBootStrap(this)
	}

	/**
	 * Populate `VmVersion.kt` with the VM [version] of Avail.
	 */
	register("generateVmVersion", DefaultTask::class)
	{
		description = "Populate `VmVersion.kt` with the VM version of Avail."
		val generatedVmVersion = file(
			"${projectDir.absolutePath}/src/main/resources/VmVersionTemplate.txt"
		).readText()
			.replace("{{YEAR}}", LocalDate.now().year.toString())
			.replace("{{VERSION}}", version.toString())
		file("${projectDir.absolutePath}/src/main/kotlin/avail/VmVersion.kt")
			.writeText(generatedVmVersion)

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
				username = PublishingUtility.ossrhUsername
				password = PublishingUtility.ossrhPassword
			}
		}
	}

	publications {

		create<MavenPublication>("avail") {
			pom {
				groupId = project.group.toString()
				name.set("Avail Programming Language")
				packaging = "jar"
				description.set("This module provides the entire Avail programming language.")
				url.set("https://www.availlang.org/")
				licenses {
					license {
						name.set("BSD 3-Clause \"New\" or \"Revised\" License")
						url.set("https://github.com/AvailLang/avail-storage/blob/main/LICENSE")
					}
				}
				scm {
					connection.set("scm:git:git@github.com:AvailLang/Avail.git")
					developerConnection.set("scm:git:git@github.com:AvailLang/Avail.git")
					url.set("https://github.com/AvailLang/Avail")
				}
				developers {
					developer {
						id.set("markATAvail")
						name.set("Mark van Gulik")
					}
					developer {
						id.set("toddATAvail")
						name.set("Todd Smith")
					}
					developer {
						id.set("richATAvail")
						name.set("Richard Arriaga")
					}
					developer {
						id.set("leslieATAvail")
						name.set("Leslie Schultz")
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

/**
 * Remove released libraries; both the contents of "distroLib" and the
 * publication staging directory, build/libs.
 *
 * @param task
 *   The [Delete] task responsible for performing the operation.
 */
fun Project.scrubReleases (task: Delete)
{
	task.run {
		// distro/lib
		delete(fileTree("${rootDirPath}/${distroLib}")
			.matching { include("**/*.jar") })
		// And the publication staging directory, build/libs
		delete(fileTree(layout.buildDirectory.dir("libs").get().asFile)
			.matching { include("**/*.jar") })
	}
}

/**
 * Construct a single file that names all files matching a given pattern inside
 * a directory structure.  Each entry is the file name relative to the basePath,
 * followed by a linefeed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class GenerateFileManifestTask: DefaultTask()
{
	/**
	 * The base path String containing files to scan. This path is stripped from
	 * each file name prefix prior to invoking the [fileNameTransformer].
	 */
	@Input
	lateinit var basePath: String

	/**
	 * A transformer from the basePath-relative file name to a string to write
	 * to the output file.
	 */
	@Input
	var fileNameTransformer: String.() -> String = { this }

	/**
	 * The path to the output file, relative to outputs.files.singleFile.
	 */
	@Input
	var outputFilePath = ""

	@TaskAction
	fun createFileManifest()
	{
		val primitivesSourceTree = inputs.files
		val baseSourcePath = "$basePath/"
		val allFileNames = primitivesSourceTree.map {
			it.absolutePath
				.replace("\\\\", "/")
				.replaceFirst(baseSourcePath, "")
				.fileNameTransformer()
		}.sorted().joinToString("\n")
		val outBase = outputs.files.singleFile
		val outFile = outBase.resolve(outputFilePath)
		if (!outFile.exists() || outFile.readText() != allFileNames)
		{
			outFile.parentFile.mkdirs()
			outFile.writeText(allFileNames)
		}
	}
}

/**
 * Answer an [AvailRoot] relative to this [Project.getProjectDir].
 *
 * @param name
 *   The [AvailRoot.name].
 * @return
 *   An [AvailRoot].
 */
fun availRoot(name: String): AvailRoot
{
	val rootURI = systemPath(rootDirPath, distroSrc, name)
	println("AvailRoot(${rootURI.length}): $rootURI")

	return AvailRoot(
		name,
		File(rootURI).toURI())
}

/**
 * Compute the Avail roots. This is needed to properly configure "test".
 *
 * @return
 *   The concatenated string of `root=root-path` separated by `;`.
 */
fun computeAvailRootsForTest (): String =
	listOf("avail", "builder-tests", "examples", "website")
		.joinToString(";") { availRoot(it).rootString }

/**
 * Construct an operating system-specific file path using [File.separator].
 *
 * @param path
 *   The locations to join using the system separator.
 * @return
 *   The constructed String path.
 */
fun systemPath(vararg path: String): String =
	path.toList().joinToString(File.separator) { s ->
		@Suppress("RedundantRequireNotNullCall")
		checkNotNull(s)
		s
	}

/**
 * `Avail` Root represents an Avail source root.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property name
 *   The name of the root.
 * @property uri
 *   The [URI] location of the root.
 */
data class AvailRoot constructor(val name: String, val uri: URI)
{
	/** The VM Options, `-DavailRoot`, root string. */
	val rootString: String by lazy { "$name=$uri" }
}

/**
 * Copy the generated bootstrap property files into the build directory so that
 * the executable tools can find them as resources.
 *
 * @param task
 *   The [Copy] task in which this code is executed.
 */
fun Project.relocateGeneratedPropertyFiles (task: Copy)
{
	val pathBootstrap =
		fileTree(systemPath(
			rootDirPath,
			relativePathBootstrap))
	val movedPropertyFiles = file(systemPath(
		layout.buildDirectory.get().asFile.absolutePath,
		"classes",
		"kotlin",
		"main",
		"avail",
		"tools",
		"bootstrap"))
	val lang = System.getProperty("user.language")
	pathBootstrap.include(systemPath("**", "*_${lang}.properties"))
	// This is a lie, but it ensures that this rule will not run until after the
	// Kotlin source is compiled.
	pathBootstrap.builtBy("compileKotlin")
	task.inputs.files + pathBootstrap
	task.outputs.dir(movedPropertyFiles)
	task.from(pathBootstrap)
	task.into(movedPropertyFiles)
	task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

/**
 * Generate the new bootstrap Avail modules for the current locale and copy them
 * to the appropriate location for distribution.
 *
 * @param task
 *   The [Copy] task in which this code is executed.
 */
fun Project.projectGenerateBootStrap(task: Copy)
{
	val source = systemPath("$projectDir", "src", "main", "resources")
	val lang = System.getProperty("user.language")
	val pathBootstrap = fileTree(systemPath(
		source,
		"avail",
		"tools",
		"bootstrap",
		"generated",
		lang,
		"Bootstrap.avail"))
	task.inputs.files + pathBootstrap
	val distroBootstrap =
		file(systemPath(
			"$projectDir",
			distroSrc,
			"avail",
			"Avail.avail",
			"Foundation.avail",
			"Bootstrap.avail"))
	task.outputs.dir(distroBootstrap)

	task.group = "bootstrap"
	task.dependsOn(tasks.getByName("internalGenerateBootstrap"))

	task.from(pathBootstrap)
	task.into(distroBootstrap)

	task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

/**
 * A utility object for publishing.
 *
 * @author Richard Arriaga
 */
object PublishingUtility
{
	/**
	 * The Sonatype username used for publishing.
	 */
	val ossrhUsername: String get() =
		System.getenv("OSSRH_USER") ?: ""

	/**
	 * The Sonatype password used for publishing.
	 */
	val ossrhPassword: String get() =
		System.getenv("OSSRH_PASSWORD") ?: ""

	/**
	 * The warning that indicates the system does not have environment variables
	 * for publishing credentials.
	 */
	private const val CREDENTIALS_WARNING =
		"Missing OSSRH credentials.  To publish, you'll need to create an OSSRH " +
			"JIRA account. Then ensure the user name, and password are available " +
			"as the environment variables: 'OSSRH_USER' and 'OSSRH_PASSWORD'"

	/**
	 * Check that the publisher has access to the necessary credentials.
	 */
	fun checkCredentials ()
	{
		if (ossrhUsername.isEmpty() || ossrhPassword.isEmpty())
		{
			System.err.println(CREDENTIALS_WARNING)
		}
	}
}
