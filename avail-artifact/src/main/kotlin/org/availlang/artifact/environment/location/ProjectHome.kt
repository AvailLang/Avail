package org.availlang.artifact.environment.location

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.project.AvailProject
import java.io.File

/**
 * The [AvailLocation] that is path relative to the project's home directory. By
 * default things that can be edited in this location (e.g. text files) are
 * considered editable by the project.
 *
 * @author Richard Arriaga
 *
 * @property projectHome
 *   The absolute path to the [AvailProject] directory.
 *
 * @constructor
 * Construct an [ProjectHome].
 *
 * @param path
 *   The absolute path to the [AvailProject] directory.
 * @param scheme
 *   The [Scheme] of the location.
 * @param projectHome
 *   The absolute path to the [AvailProject] directory.
 * @param rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
open class ProjectHome constructor (
	path: String,
	scheme: Scheme,
	protected val projectHome: String,
	rootNameInJar: String?
): AvailLocation(LocationType.project, scheme, path, rootNameInJar)
{
	override val fullPathNoPrefix: String get() =
		"$projectHome${File.separator}$path"

	override val editable: Boolean = scheme != Scheme.JAR

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = ProjectHome(
		"$path${File.separator}$relativePath",
		scheme,
		projectHome,
		rootNameInJar)
}

/**
 * The [ProjectHome] for the [AvailProject] configuration files directory.
 *
 * These all appear in the project top level directory `.avail`.
 *
 * @author Richard Arriaga
 *
 * @property projectFileName
 *   The name of the project.
 *
 * @constructor
 * Construct an [ProjectConfig].
 *
 * @param path
 *   The absolute path to the [AvailProject] directory.
 * @param projectFileName
 *   The name of the project's [AvailProject] file.
 * @param scheme
 *   The [Scheme] of the location.
 * @param projectHome
 *   The absolute path to the [AvailProject] directory.
 * @param rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
open class ProjectConfig constructor(
	path: String,
	protected val projectFileName: String,
	scheme: Scheme,
	projectHome: String,
	rootNameInJar: String?
): ProjectHome(path, scheme, projectHome, rootNameInJar)
{
	override val fullPathNoPrefix: String get() =
		AvailEnvironment.projectConfigPath(projectFileName, projectHome) +
			"${File.separator}$path"

	override val editable: Boolean = scheme != Scheme.JAR

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = ProjectConfig(
		"$path${File.separator}$relativePath",
		projectHome,
		scheme,
		projectHome,
		rootNameInJar)
}
