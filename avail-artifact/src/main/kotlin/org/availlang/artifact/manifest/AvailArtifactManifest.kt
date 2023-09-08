package org.availlang.artifact.manifest

import org.availlang.artifact.*
import org.availlang.artifact.AvailArtifact.Companion.artifactRootDirectory
import org.availlang.artifact.environment.project.AvailProject.Companion.STYLE_FILE_NAME
import org.availlang.artifact.environment.project.AvailProject.Companion.TEMPLATE_FILE_NAME
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.jsonPrettyPrintWriter
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * The interface that for the manifest that describes the contents of the
 * [AvailArtifact] of the associated artifact.
 *
 * Implementations of this interface are versioned using [artifactVersion] which
 * should be used to dispatch manifest construction from an Avail Manifest File.
 *
 * As new manifest file components are added to this interface, new version
 * implementations should be added and existing versions should be back-filled
 * with logic to handle the new state from existing manifests built under the
 * older version.
 *
 * @author Richard Arriaga
 */
sealed interface AvailArtifactManifest: JSONFriendly
{
	/**
	 * The packaging version that the contents of the artifact was packaged
	 * under.
	 */
	val artifactVersion: Int

	/**
	 * The [AvailArtifactType] that describes the nature of the Avail artifact.
	 */
	val artifactType: AvailArtifactType

	/**
	 * The UTC timestamp that represents when the artifact was constructed. Must
	 * be created using [formattedNow] when newly constructing an artifact.
	 */
	val constructed: String

	/**
	 * A description of the artifact.
	 */
	val description: String

	/**
	 * The map of the [AvailRootManifest]s keyed by [AvailRootManifest.name]
	 * that are present in the artifact.
	 */
	val roots: Map<String, AvailRootManifest>

	/**
	 * Answer tje [StylingGroup] for the given [AvailRootManifest.name] or
	 * `null` if root not found in this [AvailRootManifest].
	 */
	fun stylesFor(root: String): StylingGroup? =
		roots[root]?.styles

	/**
	 * Answer the [TemplateGroup] for the given [AvailRootManifest.name] or
	 * `null` if root not found in this [AvailRootManifest].
	 */
	fun templatesFor(root: String): TemplateGroup? =
		roots[root]?.templates

	/**
	 * The String file contents of this [AvailArtifactManifest].
	 */
	val fileContent: String get() =
		jsonPrettyPrintWriter {
			this@AvailArtifactManifest.writeTo(this)
		}.toString()

	/**
	 * Write this [AvailArtifactManifest] to the provided [File].
	 *
	 * @param targetFile
	 *   The file to write to.
	 */
	fun writeFile (targetFile: File)
	{
		targetFile.writeText(fileContent)
	}

	/**
	 * Update the project templates and stylesheets for the given
	 * [AvailProjectRoot] from this [AvailArtifactManifest] if root present in
	 * manifest.
	 *
	 * @param root
	 *   The [AvailProjectRoot] to update.
	 * @param rootNameInJar
	 *   The name of the root inside the [AvailArtifactManifest].
	 * @param createdList
	 *   List of strings that may contain [TEMPLATE_FILE_NAME] and
	 *   [STYLE_FILE_NAME] indicating those config files were newly created and
	 *   should be populated with the corresponding settings from the root's
	 *   jar.
	 */
	fun updateRoot (
		root: AvailProjectRoot,
		rootNameInJar: String,
		createdList: List<String>)
	{
		if (createdList.isEmpty()) return
		val u = roots[rootNameInJar] ?: return
		if (createdList.contains(STYLE_FILE_NAME))
		{
			// The styles file was newly created so import the styles from the
			// jar
			root.styles.updateFrom(u.styles)
			root.saveStylesToDisk()
		}
		if (createdList.contains(TEMPLATE_FILE_NAME))
		{
			// The templates file was newly created so import the styles from
			// the jar
			val merged = root.templateGroup.mergeOnto(u.templates)
			root.templateGroup.templates.clear()
			root.templateGroup.templates.putAll(merged.templates)
			root.saveTemplatesToDisk()
		}
	}

	companion object
	{
		/**
		 * The version that represents the current structure under which Avail
		 * libs are packaged in the artifact.
		 */
		internal const val CURRENT_MANIFEST_VERSION = 1

		/**
		 * Check to see if the provided value is a valid ordinal for a variant
		 * in [PackageType].
		 *
		 * @param proposedOrdinal
		 *   The proposed ordinal to check.
		 * @return
		 *   `true` if the ordinal represents a valid variant of [PackageType];
		 *   `false` otherwise.
		 */
		fun isValidVersion (proposedOrdinal: Int): Boolean =
			proposedOrdinal in 0..CURRENT_MANIFEST_VERSION

		/**
		 * The name of the [AvailArtifactManifest] file. The file extension is
		 * `txt` to indicate that this is a text file to ensure that it is
		 * understood that the file is "human-readable". By not adhering to
		 * a `.json` file extension, it allows the file format to evolve over
		 * time.
		 */
		const val manifestFileName = "avail-artifact-manifest.txt"

		/**
		 * The path within this artifact of the [AvailArtifactManifest].
		 */
		const val availArtifactManifestFile =
			"$artifactRootDirectory/$manifestFileName"

		/**
		 * Answer an [AvailArtifactManifest] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] tp extract the [AvailArtifactManifest] from.
		 * @return
		 *   The extracted [AvailArtifactManifest].
		 * @throws AvailArtifactException
		 *   If there is any problem extracting the [AvailArtifactManifest].
		 */
		fun from (obj: JSONObject): AvailArtifactManifest
		{
			val version =
				try
				{
					obj.getNumber(
						AvailArtifactManifest::artifactVersion.name).int
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem accessing Avail Artifact Manifest Version.", e)
				}
			return when (version)
			{
				1 -> AvailArtifactManifestV1.fromJSON(obj)
				else ->
					throw AvailArtifactException("Invalid Avail Artifact: " +
							"Version $version is not in the valid range of " +
							"known artifact versions," +
							" [1, $CURRENT_MANIFEST_VERSION].")
			}
		}

		/**
		 * Construct an [AvailArtifactManifest] and write it to the target file.
		 *
		 * @param artifactType
		 *   The [AvailArtifactType] that represents the type of
		 *   [AvailArtifact] that the [AvailArtifactManifest] represents.
		 * @param targetFile
		 *   The file to write to.
		 * @param roots
		 *   The map of the [AvailRootManifest]s keyed by
		 *   [AvailRootManifest.name] that are present in the artifact.
		 * @param description
		 *   The artifact's description.
		 * @return
		 *   The file byte contents.
		 */
		@Suppress("unused")
		fun writeManifestFile (
			artifactType: AvailArtifactType,
			targetFile: File,
			roots: Map<String, AvailRootManifest>,
			description: String)
		{
			AvailArtifactManifestV1(
				artifactType,
				formattedNow,
				roots,
				description
			).writeFile(targetFile)
		}

		/**
		 * Answer an [AvailArtifactManifest].
		 *
		 * @param artifactType
		 *   The [AvailArtifactType] that represents the type of
		 *   [AvailArtifact] that the [AvailArtifactManifest] represents.
		 * @param roots
		 *   The map of the [AvailRootManifest]s keyed by
		 *   [AvailRootManifest.name] that are present in the artifact.
		 * @param description
		 *   The artifact's description.
		 * @return
		 *   The constructed [AvailArtifactManifest].
		 */
		@Suppress("unused")
		fun manifestFile (
			artifactType: AvailArtifactType,
			roots: Map<String, AvailRootManifest>,
			description: String
		): AvailArtifactManifest =
				AvailArtifactManifestV1(
					artifactType,
					formattedNow,
					roots,
					description)

		/**
		 * Answer the [availArtifactManifestFile] contents.
		 *
		 * @param artifactType
		 *   The [AvailArtifactType] that represents the type of
		 *   [AvailArtifact] that the [AvailArtifactManifest] represents.
		 * @param roots
		 *   The map of the [AvailRootManifest]s keyed by
		 *   [AvailRootManifest.name] that are present in the artifact.
		 * @param description
		 *   The artifact's description.
		 * @return
		 *   The file byte contents.
		 */
		@Suppress("unused")
		fun createManifestFileContents (
			artifactType: AvailArtifactType,
			roots: Map<String, AvailRootManifest>,
			description: String

		): ByteArray =
			AvailArtifactManifestV1(
				artifactType,
				formattedNow,
				roots,
				description
			).fileContent.toByteArray(StandardCharsets.UTF_8)
	}
}

