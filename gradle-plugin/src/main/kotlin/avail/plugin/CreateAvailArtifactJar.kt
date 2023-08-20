/*
 * CreateDigestsFileTask.kt
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

package avail.plugin

import org.availlang.artifact.ArtifactDescriptor
import org.availlang.artifact.AvailArtifactType
import org.gradle.api.tasks.*
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailRootManifest
import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.artifact.jar.AvailArtifactJarBuilder
import org.availlang.artifact.jar.JvmComponent
import org.availlang.artifact.roots.AvailRoot
import org.gradle.api.DefaultTask
import java.io.File
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Property
import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * Perform all tasks necessary to package the Avail Standard Library as an
 * [AvailArtifact].
 *
 * This performs the following tasks:
 * 1. Creates the [ArtifactDescriptor] file.
 * 2. Creates the [AvailArtifactManifest] file.
 * 3. Creates source digests file.
 *
 * @author Richard Arriaga
 */
abstract class CreateAvailArtifactJar : DefaultTask()
{
	/**
	 * The [AvailExtension] for the given project.
	 */
	private val availExtension: AvailExtension get() = project.extensions
		.findByType(AvailExtension::class.java)!!

	/**
	 * The name of the [Configuration] that the dependencies are added to.
	 */
	private val configName = "_createAvailArtifact$name"

	/**
	 * The [Configuration] for adding [dependencies][Dependency] to be included
	 * in the workbench jar.
	 */
	private val localConfig: Configuration by lazy {
		project.configurations.create(configName)
	}

	/**
	 * The base name of the artifact.
	 */
	@get:Input
	abstract val artifactName: Property<String>

	/**
	 * The version to give to the created artifact
	 * ([Attributes.Name.IMPLEMENTATION_VERSION]).
	 */
	@get:Input
	abstract val version: Property<String>

	/**
	 * The [AvailArtifactType] of the [AvailArtifact] to create.
	 */
	@Input
	var artifactType: AvailArtifactType = AvailArtifactType.APPLICATION

	/**
	 * The [ProjectHome] of the [AvailProject] configuration file to use as
	 * the source of information project specific details to include in the
	 * artifact build. This defaults to [AvailProject.CONFIG_FILE_NAME],
	 * "avail-config.json" in the project directory.
	 */
	@Input
	var projectFileLocation: ProjectHome =
		ProjectHome(
			AvailProject.CONFIG_FILE_NAME,
			Scheme.FILE,
			project.projectDir.absolutePath,
			null)

	/**
	 * The [JvmComponent] that describes any JVM components being packaged in
	 * the artifact or [JvmComponent.NONE] if none.
	 */
	@Input
	var jvmComponent: JvmComponent = JvmComponent.NONE

	/**
	 * The description of the [AvailArtifact] used in the
	 * [AvailArtifactManifest].
	 */
	@Input
	var artifactDescription: String = ""

	/**
	 * The title of the artifact being created that will be added to the jar
	 * manifest ([Attributes.Name.IMPLEMENTATION_TITLE]).
	 */
	@Input
	var implementationTitle: String = project.name

	/**
	 * The [Attributes.Name.MAIN_CLASS] for the manifest or an empty string if
	 * no main class set. This should be the primary main class for starting
	 * the application.
	 */
	@Input
	var jarManifestMainClass: String = ""

	/**
	 * The [MessageDigest] algorithm to use to create the digests for all the
	 * Avail roots' contents included in the artifact. This must be a valid
	 * algorithm accessible from [java.security.MessageDigest.getInstance].
	 */
	@Suppress("unused")
	@Input
	var artifactDigestAlgorithm: String = "SHA-256"

	/**
	 * The absolute path to the directory location where the jar file is to be
	 * written.
	 *
	 * It is set to the following by default:
	 * ```
	 * "${project.buildDir}/libs/"
	 * ```
	 */
	@Input
	var outputDirectory = "${project.buildDir}/libs/"

	/**
	 * A map of manifest attribute string name to the string value to add as
	 * additional fields to the manifest file of an Avail artifact.
	 */
	private val customManifestItems = mutableMapOf<String, String>()

	/**
	 * The absolute path to the jar file that will be created.
	 *
	 * It is set to the following by default:
	 * ```
	 * "$outputDirectory$artifactName-$version.jar"
	 * ```
	 */
	private val targetOutputJar: String get()
	{
		val suffix =
			if(version.get().isNotBlank()) { "-$version.jar" } else { ".jar" }
		return "$outputDirectory${artifactName.get()}$suffix"
	}

	/**
	 * The list of [AvailRoot]s to add to the artifact jar.
	 */
	private val roots = mutableListOf<AvailRoot>()

	/**
	 * Add the Avail root to be included in the artifact jar.
	 *
	 * **NOTE** The root must be present in the [AvailExtension],`avail {}`,
	 * added with either:
	 *  * [AvailExtension.includeAvailLibDependency]
	 *  * [AvailExtension.includeStdAvailLibDependency]
	 *
	 * @param root
	 *   The [AvailRoot.name] of the [AvailRoot] to include in the artifact.
	 */
	@Suppress("unused")
	fun addRoot (root: String)
	{
		val targetRoot = availExtension.roots[root]
		if (targetRoot == null)
		{
			System.err.println(
				"Added AvailRoot, $root, to CreateAvailArtifactJar task, " +
					"$name in $group group, but this root has not been " +
					"configured for use in the AvailExtension section, " +
					"avail {}, of the build script. To add this root to " +
					"the AvailExtension configuration block use " +
					"`includeAvailLibDependency()`.")
			return
		}

		roots.add(targetRoot)
	}

	/**
	 * The list of [File] - target directory inside artifact for it to be placed
	 * [Pair]s.
	 */
	private val includedFiles = mutableListOf<Pair<File, String>>()

	/**
	 * Add a singular [File] to be written in the specified target directory
	 * path inside the jar.
	 *
	 * @param file
	 *   The [File] to write. Note this must not be a directory.
	 * @param targetDirectory
	 *   The path relative directory where the file should be placed inside the
	 *   jar file.
	 */
	@Suppress("unused")
	fun addFile (file: File, targetDirectory: String)
	{
		require(!file.isDirectory)
		{
			"Expected $file to be a file not a directory!"
		}

		includedFiles.add(file to targetDirectory)
	}

	/**
	 * The list of [JarFile]s to add to the artifact jar.
	 */
	private val jars = mutableListOf<JarFile>()

	/**
	 * Add the [JarFile] to be included in the artifact jar.
	 *
	 * @param jar
	 *   The [JarFile].
	 */
	@Suppress("unused")
	fun addJar (jar: JarFile)
	{
		jars.add(jar)
	}

	/**
	 * The list of [ZipFile]s to add to the artifact jar.
	 */
	private val zipFiles = mutableListOf<ZipFile>()

	/**
	 * Add the [ZipFile] to be included in the artifact jar.
	 *
	 * @param zipFile
	 *   The [ZipFile].
	 */
	@Suppress("unused")
	fun addZipFile (zipFile: ZipFile)
	{
		zipFiles.add(zipFile)
	}

	/**
	 * The list of [File] directories whose contents should be added to
	 * the artifact jar.
	 */
	private val directories = mutableListOf<File>()

	/**
	 * Add the [directory][File] to be included in the artifact jar. This must
	 * be a directory: [File.isDirectory].
	 *
	 * @param file
	 *   The [File] directory to add.
	 */
	@Suppress("unused")
	fun addDirectory (file: File)
	{
		directories.add(file)
	}

	/**
	 * Add a dependency to be included in the jar.
	 *
	 * @param dependency
	 *   The string that identifies the dependency such as:
	 *   `org.package:myLibrary:2.3.1`.
	 */
	@Suppress("unused")
	fun dependency (dependency: String)
	{
		localConfig.dependencies.add(project.dependencies.create(dependency))
	}

	/**
	 * Add a dependency to be included in the jar.
	 *
	 * @param dependency
	 *   The [Dependency] to add.
	 */
	@Suppress("unused")
	fun dependency (dependency: Dependency)
	{
		localConfig.dependencies.add(dependency)
	}

	/**
	 * Add a custom field to the manifest file of an Avail artifact.
	 *
	 * @param key
	 *   The [Attributes.Name] String name of the entry to add.
	 * @param value
	 *   The value to associate with the key.
	 */
	@Suppress("unused")
	fun addManifestAttributeEntry (key: String, value: String)
	{
		customManifestItems[key] = value
	}

	init
	{
		group = "build"
		description = "Create an Avail artifact jar."
	}

	/**
	 * Construct the [AvailArtifactJar].
	 */
	@TaskAction
	fun createAvailArtifactJar ()
	{
		val projectFile = File(projectFileLocation.fullPathNoPrefix)
		val updatedRoots = mutableListOf<AvailRoot>()
		if (projectFile.exists())
		{
			AvailProject.from(projectFileLocation.fullPathNoPrefix).apply {
				this@CreateAvailArtifactJar.roots.map {
					val styles = this.roots[it.name]?.let { apr ->
						it.templateGroup.templates.putAll(
							apr.templateGroup.templates)
						apr.styles
					} ?: StylingGroup()
					updatedRoots.add(AvailRoot(
						it.name,
						it.location,
						it.digestAlgorithm,
						it.availModuleExtensions,
						it.entryPoints,
						it.templateGroup,
						styles,
						it.description,
						it.action))
				}
			}
		}
		else
		{
			updatedRoots.addAll(roots)
		}

		createAvailArtifactJar(
			version.get(),
			targetOutputJar,
			artifactType,
			jvmComponent,
			implementationTitle,
			jarManifestMainClass,
			artifactDescription,
			updatedRoots,
			includedFiles,
			jars,
			zipFiles,
			directories,
			customManifestItems,
			localConfig)
	}

	companion object
	{
		/**
		 * Create an [AvailArtifactJar].
		 *
		 * @param version
		 *   The version to give to the created artifact
		 *   ([Attributes.Name.IMPLEMENTATION_VERSION]).
		 * @param outputLocation
		 *   The Jar file location where the jar file will be written.
		 * @param artifactType
		 *   The [AvailArtifactType] of the [AvailArtifact] to create.
		 * @param jvmComponent
		 *   The [JvmComponent] if any to be used.
		 * @param implementationTitle
		 *   The title of the artifact being created that will be added to the
		 *   jar manifest ([Attributes.Name.IMPLEMENTATION_TITLE]).
		 * @param artifactDescription
		 *   The description of the [AvailArtifact] used in the
		 *   [AvailArtifactManifest].
		 * @param roots
		 *   The list of [AvailRoot]s to add to the artifact jar.
		 * @param includedFiles
		 *   The list of [File] - target directory inside artifact for it to be
		 *   placed [Pair]s.
		 * @param jars
		 *   The list of [JarFile]s to add to the artifact jar.
		 * @param zipFiles
		 *   The list of [ZipFile]s to add to the artifact jar.
		 * @param directories
		 *   The list of [File] directories whose contents should be added to
		 *   the artifact jar.
		 * @param customManifestItems
		 *   A map of manifest attribute string name to the string value to add as
		 *   additional fields to the manifest file of an Avail artifact.
		 * @param dependencyConfiguration
		 *   The configuration the dependencies are added to.
		 */
		fun createAvailArtifactJar (
			version: String,
			outputLocation: String,
			artifactType: AvailArtifactType,
			jvmComponent: JvmComponent,
			implementationTitle: String,
			jarMainClass: String,
			artifactDescription: String,
			roots: List<AvailRoot>,
			includedFiles: List<Pair<File, String>>,
			jars: List<JarFile>,
			zipFiles: List<ZipFile>,
			directories: List<File>,
			customManifestItems: Map<String, String>,
			dependencyConfiguration: Configuration)
		{
			println("Creating $outputLocation…")
			File(outputLocation).apply {
				File(parent).mkdirs()
				delete()
			}
			val manifestMap = mutableMapOf<String, AvailRootManifest>()
			roots.forEach {
				manifestMap[it.name] = it.manifest
			}

			val jarBuilder = AvailArtifactJarBuilder(
				outputLocation,
				version,
				implementationTitle,
				AvailArtifactManifest.manifestFile(
					artifactType,
					manifestMap,
					artifactDescription,
					jvmComponent),
				jarMainClass,
				customManifestItems)
			roots.forEach {
				println("Adding Root\n\t$it")
				jarBuilder.addRoot(it)
			}
			includedFiles.forEach { jarBuilder.addFile(it.first, it.second) }
			jars.forEach { jarBuilder.addJar(it) }
			zipFiles.forEach { jarBuilder.addZip(it) }
			directories.forEach { jarBuilder.addDir(it) }
			dependencyConfiguration.resolve().forEach {
				when
				{
					it.name.endsWith(".jar") ->
						jarBuilder.addJar(JarFile(it))
					it.name.endsWith(".zip") ->
						jarBuilder.addZip(ZipFile(it))
					it.isDirectory -> jarBuilder.addDir(it)
					else -> throw RuntimeException(
						"Failed to build $outputLocation: received dependency " +
							"${it.absolutePath} which did not resolved to a" +
							" jar, zip, or directory")
				}
			}
			jarBuilder.finish()
		}
	}
}
