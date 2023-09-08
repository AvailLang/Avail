package avail.plugin

import org.availlang.artifact.environment.location.AvailLibraries
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailRootManifest
import org.availlang.artifact.roots.AvailRoot
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

/**
 * Helper used to provide configurability to add an Avail library that is
 * available from a Maven repository.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * 
 * @property rootName
 *   The name of the root as it will be used by Avail.
 * @property rootNameInJar
 *   The name of the target root to use inside the Jar. This is the
 *   [AvailRootManifest.name] in the [AvailArtifactManifest].
 * 
 * @constructor
 * Construct an [AvailLibraryDependency].
 *
 * @param rootName
 *   The name of the root as it will be used by Avail.
 * @param rootNameInJar
 *   The name of the target root to use inside the Jar. This is the
 *   [AvailRootManifest.name] in the [AvailArtifactManifest].
 * @param dependency
 *   The target library's dependency string of the form
 *   ```
 *   "group:artifactName:version"
 *   ```
 */
class AvailLibraryDependency constructor(
	var rootName: String,
	var rootNameInJar: String,
	dependency: String)
{
	/**
	 * The dependency's group name.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var group: String

	/**
	 * The name of the artifact. This corresponds to the base name the library
	 * jar file that should be named without the version or `.jar` extension.
	 * This will be used to construct the [AvailRoot.uri].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var artifactName: String

	/**
	 * The version of the Avail library to use.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var version: String

	/**
	 * The target library's dependency string of the form:
	 * ```
	 * "group:artifactName:version"
	 * ```
	 */
	val dependencyString get() = "$group:$artifactName:$version"

	/**
	 * Create a [Dependency] for this [AvailLibraryDependency].
	 *
	 * @param project
	 *   The [Project] to use to create the dependency.
	 * @return
	 *   The Avail library [Dependency].
	 */
	internal fun dependency (project: Project): Dependency =
		project.dependencies.create(dependencyString)

	/**
	 * Provide the corresponding [AvailRoot] for this [AvailLibraryDependency].
	 *
	 * @param libRelativeDir
	 *   The [AvailProject.ROOTS_DIR] relative directory where the jar file
	 *   should be.
	 */
	internal fun root(libRelativeDir: String): AvailRoot =
		AvailRoot(
			rootName,
			AvailLibraries(
				"$libRelativeDir/$artifactName-$version.jar",
				Scheme.JAR,
				rootNameInJar))

	init
	{
		val split = dependency.split(":")
		if (split.size != 3)
		{
			throw AvailPluginException(
				"Received a malformed AvailLibraryDependency: $dependency. " +
					"It must follow the format: \"group:artifactName:version\"")
		}
		this.group = split[0]
		this.artifactName = split[1]
		this.version = split[2]
	}
}
