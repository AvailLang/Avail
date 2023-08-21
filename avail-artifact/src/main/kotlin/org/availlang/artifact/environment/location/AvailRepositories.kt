package org.availlang.artifact.environment.location

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.AvailEnvironment.availHomeRepos
import java.io.File.separator

/**
 * The [AvailHome] location that is path relative to
 * [AvailEnvironment.availHomeRepos].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailRepositories].
 *
 * @param path
 *   The path relative to the [AvailEnvironment.availHomeRepos] directory.
 * @param scheme
 *   The [Scheme] of the location.
 * @param rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
open class AvailRepositories constructor (
	path: String = "",
	scheme: Scheme = Scheme.FILE,
	rootNameInJar: String?
): AvailHome(path, scheme, LocationType.availRepositories, rootNameInJar)
{
	override val fullPathNoPrefix: String get() =
		"$availHomeRepos$separator$path"

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailRepositories(
		"$path$separator$relativePath", scheme, rootNameInJar)
}

/**
 * The location of an Avail repository file in the
 * [AvailEnvironment.availHomeRepos] directory.
 *
 * **NOTE** As this is not a directory, but a file [relativeLocation] returns
 * a location that is in the same directory as this represented file.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailRepository].
 *
 * @param repoName
 *   The name of the Avail repository file.
 */
@Suppress("unused")
class AvailRepository constructor(
	repoName: String
): AvailRepositories(repoName, rootNameInJar = null)
{
	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailRepositories(relativePath, scheme, rootNameInJar)
}
