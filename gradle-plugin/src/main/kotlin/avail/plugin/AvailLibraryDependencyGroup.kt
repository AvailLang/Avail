package avail.plugin

import org.availlang.artifact.environment.location.AvailLibraries
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.roots.AvailRoot
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

/**
 * Helper used to provide configurability to add an Avail library that is
 * available from a Maven repository.
 *
 * TODO do not use; in development
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
open class AvailLibraryDependencyGroup
{
	/**
	 * The list of root names of the roots to use from the library dependency
	 * artifact.
	 */
	var rootNames: List<String>

	/**
	 * The dependency's group name.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var group: String

	/**
	 * The name of the artifact. This corresponds to the base name the library
	 * jar file that should be named without the version or `.jar` extension.
	 * This will be used to construct the [AvailRoot.location].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var artifactName: String

	/**
	 * The version of the Avail library to use.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var version: String

	/**
	 * @constructor
	 * Construct an [AvailLibraryDependencyGroup].
	 *
	 * @param dependency
	 *   The target library's dependency string of the form
	 *   ```
	 *   "group:artifactName:version"
	 *   ```
	 * @param rootNames
	 *   The list of root names of the roots to use from the library dependency
	 *   artifact.
	 */
	constructor(dependency: String, vararg rootNames: String)
	{
		val split = dependency.split(":")
		if (split.size != 3)
		{
			throw AvailPluginException(
				"Received a malformed AvailLibraryDependency: $dependency. " +
					"It must follow the format: \"group:artifactName:version\"")
		}
		this.rootNames = rootNames.toList()
		this.group = split[0]
		this.artifactName = split[1]
		this.version = split[2]
	}

	/**
	 * @constructor
	 * Construct an [AvailLibraryDependencyGroup].
	 *
	 * @param group
	 *   The dependency's group name.
	 * @param artifactName
	 *   The name of the artifact. This corresponds to the base name the library
	 *   jar file that should be named without the version or `.jar` extension.
	 *   This will be used to construct the [AvailRoot.location].
	 * @param version
	 *   The version of the Avail library to use.
	 * @param rootNames
	 *   The list of root names of the roots to use from the library dependency
	 *   artifact.
	 */
	constructor(
		group: String,
		artifactName: String,
		version: String,
		vararg rootNames: String)
	{
		this.rootNames = rootNames.toList()
		this.group = group
		this.artifactName = artifactName
		this.version = version
	}

	/**
	 * The target library's dependency string of the form:
	 * ```
	 * "group:artifactName:version"
	 * ```
	 */
	val dependencyString get() = "$group:$artifactName:$version"

	/**
	 * Create a [Dependency] for this [AvailLibraryDependencyGroup].
	 *
	 * @param project
	 *   The [Project] to use to create the dependency.
	 * @return
	 *   The Avail library [Dependency].
	 */
	internal fun dependency (project: Project): Dependency =
		project.dependencies.create(dependencyString)

	/**
	 * Provide the list of corresponding [AvailRoot]s for this
	 * [AvailLibraryDependencyGroup].
	 *
	 * @param libRelativeDir
	 *   The [AvailProject.ROOTS_DIR] relative directory where the jar file
	 *   should be.
	 */
	internal fun root(libRelativeDir: String): List<AvailRoot> =
		rootNames.map { name ->
			AvailRoot(
				name,
				AvailLibraries(
					"$libRelativeDir/$artifactName-$version.jar",
					Scheme.JAR,
					rootNameInJar = name))
		}.toList()
}
