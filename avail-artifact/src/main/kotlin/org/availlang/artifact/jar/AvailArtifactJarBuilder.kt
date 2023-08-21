package org.availlang.artifact.jar

import org.availlang.artifact.*
import org.availlang.artifact.ArtifactDescriptor.Companion.artifactDescriptorFileName
import org.availlang.artifact.ArtifactDescriptor.Companion.artifactDescriptorFilePath
import org.availlang.artifact.AvailArtifact.Companion.artifactRootDirectory
import org.availlang.artifact.AvailArtifact.Companion.availDigestsPathInArtifact
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailArtifactManifest.Companion.availArtifactManifestFile
import org.availlang.artifact.manifest.AvailArtifactManifest.Companion.manifestFileName
import org.availlang.artifact.manifest.AvailRootManifest
import org.availlang.artifact.roots.AvailRoot
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.*
import java.util.zip.ZipFile

/**
 * Construct an Avail library jar.
 *
 * @author Richard Arriaga
 *
 * @property outputLocation
 *   The output file location to write the jar to.
 * @property implementationVersion
 *   The version of the library being written
 *   ([Attributes.Name.IMPLEMENTATION_VERSION]).
 * @property implementationTitle
 *   The title of the artifact being created that will be added to the jar
 *   manifest ([Attributes.Name.IMPLEMENTATION_TITLE]).
 * @property availArtifactManifest
 *   The [AvailArtifactManifest] to be added to the jar file.
 * @property jarManifestMainClass
 *   The main class for the Jar ([Attributes.Name.MAIN_CLASS]).
 * @property customManifestItems
 *   A map of manifest attribute string name to the string value to add as
 *   additional fields to the manifest file of an Avail artifact.
 */
@Suppress("unused")
class AvailArtifactJarBuilder constructor(
	private val outputLocation: String,
	private val implementationVersion: String,
	private val implementationTitle: String,
	private val availArtifactManifest: AvailArtifactManifest,
	private val jarManifestMainClass: String = "",
	private val customManifestItems: Map<String, String> = mapOf())
{
	/**
	 * The [JarOutputStream] to package the library into.
	 */
	private val jarOutputStream: JarOutputStream

	/**
	 * The set of entries that have been added to the Jar.
	 */
	private val added = mutableSetOf<String>()

	init
	{
		val manifest = Manifest()
		manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
		manifest.mainAttributes[Attributes.Name("Build-Time")] = formattedNow
		manifest.mainAttributes[Attributes.Name.IMPLEMENTATION_VERSION] = implementationVersion
		manifest.mainAttributes[Attributes.Name.IMPLEMENTATION_TITLE] = implementationTitle
		customManifestItems.forEach { (k, v) ->
			manifest.mainAttributes[Attributes.Name(k)] = v
		}
		if (jarManifestMainClass.isNotEmpty())
		{
			manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = jarManifestMainClass
		}

		jarOutputStream =
			JarOutputStream(FileOutputStream(outputLocation), manifest)
		jarOutputStream.putNextEntry(JarEntry("META-INF/"))
		jarOutputStream.closeEntry()
		added.add("META-INF/")
		jarOutputStream.putNextEntry(JarEntry("$artifactRootDirectory/"))
		jarOutputStream.closeEntry()
		added.add("$artifactRootDirectory/")
		jarOutputStream.putNextEntry(JarEntry(artifactDescriptorFilePath))
		jarOutputStream.write(
			PackageType.JAR.artifactDescriptor.serializedFileContent)
		jarOutputStream.closeEntry()
		added.add(artifactDescriptorFilePath)
	}

	/**
	 * Ensure the path uses only forward slashes, `/`, by replacing any
	 * backslashes, `\`, with forward slashes.
	 *
	 * @param path
	 *   The path to canonicalize.
	 * @return
	 *   The canonicalized path.
	 */
	private fun canonicalizePath (path: String): String =
		path.replace("\\", "/")

	/**
	 * Add a Module Root to the Avail Library Jar.
	 *
	 * @param targetRoot
	 *   The [AvailRoot] path to the root to add.
	 */
	fun addRoot (targetRoot: AvailRoot)
	{
		val rootPath = targetRoot.absolutePath.removePrefix(Scheme.JAR.prefix)
		val root = File(rootPath)
		if (!root.isDirectory)
		{
			if (rootPath.endsWith("jar"))
			{
				addJar(JarFile(root))
				return
			}
			throw AvailArtifactException(
				"Failed to add module root, ${targetRoot.name}; provided " +
					"root path, $rootPath, is not a directory")
		}
		val rootEntryName = canonicalizePath(
			"$artifactRootDirectory/${targetRoot.name}/")
		jarOutputStream.putNextEntry(
			JarEntry(rootEntryName))
		added.add(rootEntryName)
		jarOutputStream.closeEntry()

		val sourceDirPrefix =
			AvailArtifact.rootArtifactSourcesDir(targetRoot.name)
		root.walk()
			.forEach { file ->
				val pathRelativeName =
					canonicalizePath(
						"$sourceDirPrefix${file.absolutePath.removePrefix(rootPath)}" +
						if (file.isDirectory) "/" else "")
				jarOutputStream.putNextEntry(JarEntry(pathRelativeName))
				added.add(pathRelativeName)
				if (file.isFile)
				{
					val fileBytes = file.readBytes()
					jarOutputStream.write(fileBytes)
				}
				jarOutputStream.closeEntry()
			}
		val entryName = canonicalizePath(
			"${AvailArtifact.rootArtifactDigestDirPath(targetRoot.name)}/$availDigestsPathInArtifact/")
		jarOutputStream.putNextEntry(JarEntry(entryName))

		added.add(entryName)
		jarOutputStream.closeEntry()

		val digestFileName = AvailArtifact
			.rootArtifactDigestFilePath(targetRoot.name)
		val digest = DigestUtility
			.createDigest(rootPath, targetRoot.digestAlgorithm)
		jarOutputStream.putNextEntry(JarEntry(digestFileName))
		jarOutputStream.write(digest.toByteArray(Charsets.UTF_8))
		added.add(digestFileName)
		jarOutputStream.closeEntry()
	}

	/**
	 * Add the contents of the Jar file to the jar being built.
	 *
	 * @param jarFile
	 *   The String path to the jar to add.
	 */
	fun addJar (jarFile: JarFile)
	{
		val jarSimpleName = File(jarFile.name).name
		jarFile.entries().asIterator().forEach {
			when (it.name)
			{
				"META-INF/", "$artifactRootDirectory/" -> {} // Do not add it
				"META-INF/MANIFEST.MF" ->
				{
					val adjustedManifest =
						"META-INF/$jarSimpleName/MANIFEST.MF"
					jarOutputStream.putNextEntry(JarEntry(adjustedManifest))
					added.add(adjustedManifest)
					val bytes = ByteArray(it.size.toInt())
					val stream = DataInputStream(
						BufferedInputStream(
							jarFile.getInputStream(it), it.size.toInt()))
					stream.readFully(bytes)
					jarOutputStream.write(bytes)
					jarOutputStream.closeEntry()
				}
				artifactDescriptorFilePath ->
				{
					val adjustedDescriptor =
						canonicalizePath(
							"$artifactRootDirectory/$jarSimpleName/$artifactDescriptorFileName")
					jarOutputStream.putNextEntry(
						JarEntry(adjustedDescriptor))
					added.add(adjustedDescriptor)
					val bytes = ByteArray(it.size.toInt())
					val stream = DataInputStream(
						BufferedInputStream(
							jarFile.getInputStream(it), it.size.toInt()))
					stream.readFully(bytes)
					jarOutputStream.write(bytes)
					jarOutputStream.closeEntry()
				}
				availArtifactManifestFile ->
				{
					val adjustedAvailManifest =
						canonicalizePath(
							"$artifactRootDirectory/$jarSimpleName/$manifestFileName")
					val entry = JarEntry(adjustedAvailManifest)
					jarOutputStream.putNextEntry(entry)
					added.add(adjustedAvailManifest)
					val bytes = ByteArray(it.size.toInt())
					val stream = DataInputStream(
						BufferedInputStream(
							jarFile.getInputStream(it), it.size.toInt()))
					stream.readFully(bytes)
					jarOutputStream.write(bytes)
					jarOutputStream.closeEntry()
				}
				else ->
				{
					if (!added.contains(it.name))
					{
						jarOutputStream.putNextEntry(JarEntry(it))
						if (it.size > 0)
						{
							val bytes = ByteArray(it.size.toInt())
							val stream = DataInputStream(
								BufferedInputStream(
									jarFile.getInputStream(it), it.size.toInt()))
							stream.readFully(bytes)
							jarOutputStream.write(bytes)
						}
						added.add(it.name)
						jarOutputStream.closeEntry()
					}
				}
			}
		}
	}

	/**
	 * Add the contents of the zip file to the jar being built.
	 *
	 * @param zipFile
	 *   The String path to the zip to add.
	 */
	fun addZip (zipFile: ZipFile)
	{
		zipFile.entries().asIterator().forEach {
			if (!added.add(it.name))
			{
				jarOutputStream.putNextEntry(JarEntry(it))
				if (it.size > 0)
				{
					val bytes = ByteArray(it.size.toInt())
					val stream = DataInputStream(
						BufferedInputStream(
							zipFile.getInputStream(it), it.size.toInt()))
					stream.readFully(bytes)
					jarOutputStream.write(bytes)
				}
				jarOutputStream.closeEntry()
			}
		}
	}

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
	fun addFile (file: File, targetDirectory: String)
	{
		require(!file.isDirectory)
		{
			"Expected $file to be a file not a directory!"
		}

		val pathRelativeName = canonicalizePath(
			"$targetDirectory/${file.name}")
		if (!added.add(pathRelativeName))
		{
			jarOutputStream.putNextEntry(JarEntry(pathRelativeName))
			if (file.isFile)
			{
				val fileBytes = file.readBytes()
				jarOutputStream.write(fileBytes)
			}
			jarOutputStream.closeEntry()
		}
	}

	/**
	 * Add the contents of the directory to the jar being built.
	 *
	 * @param dir
	 *   The String path to the directory to add.
	 */
	fun addDir (dir: File)
	{
		require(dir.isDirectory)
		{
			"Expected $dir to be a directory!"
		}
		dir.walk().forEach { file -> addFile(file, dir.name) }
	}

	/**
	 * Finish writing the zip file to disk. This closes the [JarOutputStream].
	 */
	fun finish ()
	{
		jarOutputStream.putNextEntry(
			JarEntry(availArtifactManifestFile))
		jarOutputStream.write(
			availArtifactManifest.fileContent.toByteArray(Charsets.UTF_8))
		added.add(availArtifactManifestFile)
		jarOutputStream.closeEntry()

		jarOutputStream.finish()
		jarOutputStream.flush()
		jarOutputStream.close()
	}

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
		customManifestItems: Map<String, String>
	): AvailArtifactJarBuilder
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
		return jarBuilder
	}
}
