package org.availlang.artifact

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.artifact.jar.AvailArtifactJarBuilder
import org.availlang.artifact.jar.JvmComponent
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailRootManifest
import org.availlang.artifact.roots.AvailRoot
import org.availlang.json.JSONArray
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.json
import org.availlang.json.jsonArray
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * The [JSONFriendly] plan that describes the build plan for an [AvailArtifact].
 *
 * Fields marked as **REQUIRED** *MUST* be populated; the build will fail
 * otherwise.
 *
 * @author Richard Arriaga
 * 
 * @property version
 *   The version to give to the created artifact. **REQUIRED**
 * @property outputLocation
 *   The [AvailLocation] where the [AvailArtifact] file will be written.
 *   **REQUIRED**
 * @property artifactType
 *   The [AvailArtifactType] of the [AvailArtifact] to create. **REQUIRED**
 * @property jvmComponent
 *   The [JvmComponent] if any to be used. **REQUIRED**
 * @property implementationTitle
 *   The title of the artifact being created. **REQUIRED**
 * @property artifactDescription
 *   The description of the [AvailArtifact] used in the [AvailArtifactManifest].
 * @property digestAlgorithm
 *   The [MessageDigest] algorithm to use to create the digests for all the
 *   root's contents. This must be a valid algorithm accessible from
 *   [java.security.MessageDigest.getInstance].
 * @property rootNames
 *   The list of [AvailRoot.name]s to add to the artifact. **REQUIRED**
 * @property includedFiles
 *   The list of [AvailLocation] - target path (including file name) inside
 *   artifact for it to be placed [Pair]s.
 * @property jarFileLocations
 *   The list of [AvailLocation]s of the [jars][JarFile] to add to the artifact.
 * @property zipFileLocations
 *   The list of [AvailLocation]s of [ZipFile]s to add to the artifact.
 * @property directoryLocations
 *   The list of [AvailLocation] directories whose contents should be added to
 *   the artifact jar.
 * @property customManifestItems
 *   A map of manifest attribute string name to the string value to add as
 *   additional fields to the manifest file of an Avail artifact.
 */
class AvailArtifactBuildPlan private constructor(
	var version: String = "",
	var outputLocation: AvailLocation? = null,
	var artifactType: AvailArtifactType = AvailArtifactType.LIBRARY,
	var jvmComponent: JvmComponent = JvmComponent.NONE,
	var implementationTitle: String = "",
	var jarMainClass: String = "",
	var artifactDescription: String = "",
	var digestAlgorithm: String = "SHA=256",
	var rootNames: MutableList<String> = mutableListOf(),
	var includedFiles: MutableList<Pair<AvailLocation, String>> = mutableListOf(),
	var jarFileLocations: MutableList<AvailLocation> = mutableListOf(),
	var zipFileLocations: MutableList<AvailLocation> = mutableListOf(),
	var directoryLocations: MutableList<AvailLocation> = mutableListOf(),
	var customManifestItems: MutableMap<String, String> = mutableMapOf()
): JSONFriendly
{
	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::version.name) { write(version) }
			at(::outputLocation.name) {
				outputLocation?.writeTo(this) ?: writeNull()
			}
			at(::artifactType.name) { write(artifactType.name) }
			at(::jvmComponent.name) { write(jvmComponent) }
			at(::implementationTitle.name) { write(implementationTitle) }
			at(::jarMainClass.name) { write(jarMainClass) }
			at(::artifactDescription.name) { write(artifactDescription) }
			at(::digestAlgorithm.name) { write(digestAlgorithm) }
			at(::rootNames.name) { writeStrings(rootNames) }
			at(::includedFiles.name) {
				writePairsAsArrayOfPairs(includedFiles.iterator()) { f, s ->
					f to s.json
				}
			}
			at(::jarFileLocations.name) { writeArray(jarFileLocations) }
			at(::zipFileLocations.name) { writeArray(zipFileLocations) }
			at(::directoryLocations.name) { writeArray(directoryLocations) }
			at(::customManifestItems.name) {
				writeMapString(customManifestItems)
			}
		}
	}

	/**
	 * Build the [AvailArtifactJar] described by this [AvailArtifactBuildPlan].
	 *
	 * @param project
	 *   The [AvailProject] that owns this [AvailArtifactBuildPlan].
	 * @param success
	 *   Accepts the path to the created [AvailArtifactJar] upon successful
	 *   creation.
	 * @param failure
	 *   Accepts an error message and a `nullable` [Throwable] in the event of
	 *   a failed build of the [AvailArtifactJar].
	 */
	@Suppress("unused")
	fun buildAvailArtifactJar (
		project: AvailProject,
		success: (String) -> Unit,
		failure: (String, Throwable?) -> Unit)
	{
		val out = outputLocation ?: return Unit.apply {
			failure("No artifact output location defined", null)
		}
		val errorString = buildString {
			if (version.isBlank())
			{
				append("\n\tMissing version")
			}
			@Suppress("SENSELESS_COMPARISON")
			if (out == null)
			{
				append("\n\tMissing outputLocation")
			}
			if (implementationTitle.isBlank())
			{
				append("\n\tMissing implementationTitle")
			}
		}
		if (errorString.isNotBlank())
		{
			failure(
				"Avail Artifact Build Plan Failed Validation:\n$errorString",
				null)
			return
		}
		try
		{
			File(out.fullPathNoPrefix).apply {
				File(parent).mkdirs()
				delete()
			}

			val roots = project.roots.values.map { it.availRoot }
			val manifestMap = mutableMapOf<String, AvailRootManifest>()
			rootNames.forEach {
				val r = project.roots[it] ?: return@forEach
				manifestMap[r.name] = r.manifest(digestAlgorithm)
			}
			val jarBuilder = AvailArtifactJarBuilder(
				out.fullPathNoPrefix,
				version,
				implementationTitle,
				AvailArtifactManifest.manifestFile(
					artifactType,
					manifestMap,
					artifactDescription,
					jvmComponent),
				jarMainClass,
				customManifestItems)
			roots.forEach { jarBuilder.addRoot(it) }
			includedFiles.forEach {
				jarBuilder.addFile(File(it.first.fullPathNoPrefix), it.second)
			}
			jarFileLocations.forEach {
				jarBuilder.addJar(JarFile(File(it.fullPathNoPrefix)))
			}
			zipFileLocations.forEach {
				jarBuilder.addZip(ZipFile(File(it.fullPathNoPrefix)))
			}
			directoryLocations.forEach {
				jarBuilder.addDir(File(it.fullPathNoPrefix))
			}
			jarBuilder.finish()
			success(out.fullPathNoPrefix)
		}
		catch (e: Throwable)
		{
			failure("Failed to build ${out.fullPathNoPrefix}", e)
		}
	}

	override fun toString(): String = implementationTitle

	/**
	 * Construct a [AvailArtifactBuildPlan] from the provided [JSONObject].
	 *
	 * @param projectDirectory
	 *   The home directory of the [AvailProject].
	 * @param obj
	 *   The [JSONObject] that contains the contents of the
	 *   [AvailArtifactBuildPlan] to be extracted.
	 */
	constructor(projectDirectory: String, obj: JSONObject): this()
	{
		version = obj.getStringOrNull(::version.name) ?: ""
		outputLocation = obj.getObjectOrNull(::outputLocation.name)?.let {
			AvailLocation.from(projectDirectory, it)
		}
		obj.getStringOrNull(::artifactType.name)?.let {
			try
			{
				artifactType = AvailArtifactType.valueOf(it)
			}
			catch (e: IllegalArgumentException)
			{
				AvailArtifactException("Invalid Avail Artifact Type: $it")
					.printStackTrace()
			}
		}
		obj.getObjectOrNull(::jvmComponent.name)?.let {
			try
			{
				jvmComponent = JvmComponent.from(it)
			}
			catch (e: Throwable)
			{
				AvailArtifactException(
					"Problem accessing Avail Artifact Manifest " +
						"jvmComponent.",
					e).printStackTrace()
			}
		}
		implementationTitle =
			obj.getStringOrNull(::implementationTitle.name) ?: ""
		jarMainClass = obj.getStringOrNull(::jarMainClass.name) ?: ""
		artifactDescription =
			obj.getStringOrNull(::artifactDescription.name) ?: ""
		digestAlgorithm =
			obj.getStringOrNull(::digestAlgorithm.name) ?: ""
		obj.getArrayOrNull(::rootNames.name)?.strings?.apply {
			rootNames.addAll(this)
		}
		obj.getArrayOrNull(::includedFiles.name)?.forEach { arr ->
			(arr as JSONArray)
			if (arr.isEmpty()) return@forEach
			includedFiles.add(
				AvailLocation.from(projectDirectory, arr.getObject(0)) to
				arr.getString(1))
		}
		obj.getArrayOrNull(::jarFileLocations.name)?.let { arr ->
			arr.forEach {
				jarFileLocations.add(
					AvailLocation.from(projectDirectory, it as JSONObject))
			}
		}
		obj.getArrayOrNull(::zipFileLocations.name)?.let { arr ->
			arr.forEach {
				zipFileLocations.add(
					AvailLocation.from(projectDirectory, it as JSONObject))
			}
		}
		obj.getArrayOrNull(::directoryLocations.name)?.let { arr ->
			arr.forEach {
				directoryLocations.add(
					AvailLocation.from(projectDirectory, it as JSONObject))
			}
		}
		obj.getObjectOrNull(::customManifestItems.name)?.let { o ->
			o.forEach { (k, v) ->
				customManifestItems[k] = v.string
			}
		}
	}

	companion object
	{
		/**
		 * The name of the file that contains the [AvailArtifactBuildPlan]s.
		 */
		@Suppress("MemberVisibilityCanBePrivate")
		const val ARTIFACT_PLANS_FILE = "artifact-plans.json"

		/**
		 * Read the [AvailArtifactBuildPlan]s from disk.
		 *
		 * @param projectFileName
		 *   The name of the project.
		 * @param projectPath
		 *   The absolute path to the [AvailProject] directory.
		 * @return
		 *   The list of [AvailArtifactBuildPlan]s.
		 */
		fun readPlans (
			projectFileName: String,
			projectPath: String
		): MutableList<AvailArtifactBuildPlan> =
			jsonArray(File(
				"${AvailEnvironment.projectConfigPath(
					projectFileName, projectPath)}/$ARTIFACT_PLANS_FILE"
			).readText()) {}.map {
				AvailArtifactBuildPlan(
					projectPath, it as JSONObject)
			}.toMutableList()
	}
}
