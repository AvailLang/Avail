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
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class AvailLibraryDependency
{
	/**
	 * The name of the root as it will be used by Avail.
	 */
	var name: String

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
	 * @constructor
	 * Construct an [AvailLibraryDependency].
	 *
	 * @param name
	 *   The name of the root as it will be used by Avail.
	 * @param dependency
	 *   The target library's dependency string of the form
	 *   ```
	 *   "group:artifactName:version"
	 *   ```
	 */
	constructor(name: String, dependency: String)
	{
		val split = dependency.split(":")
		if (split.size != 3)
		{
			throw AvailPluginException(
				"Received a malformed AvailLibraryDependency: $dependency. " +
					"It must follow the format: \"group:artifactName:version\"")
		}
		this.name = name
		this.group = split[0]
		this.artifactName = split[1]
		this.version = split[2]
	}

	/**
	 * @constructor
	 * Construct an [AvailLibraryDependency].
	 *
	 * @param name
	 *   The name of the root as it will be used by Avail.
	 * @param group
	 *   The dependency's group name.
	 * @param artifactName
	 *   The name of the artifact. This corresponds to the base name the library
	 *   jar file that should be named without the version or `.jar` extension.
	 *   This will be used to construct the [AvailRoot.uri].
	 * @param version
	 *   The version of the Avail library to use.
	 */
	constructor(
		name: String,
		group: String,
		artifactName: String,
		version: String)
	{
		this.name = name
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
			name,
			AvailLibraries(
				"$libRelativeDir/$artifactName-$version.jar",
				Scheme.JAR,
				// These libraries have a single rootNameInJar equal to the
				// name of the directory, and presumably the name of the module
				// root used by the project.
				name))
}
