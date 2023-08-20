package org.availlang.artifact.environment.location

import java.io.File

/**
 * The canonical representation of an invalid [AvailLocation].
 *
 * @author Richard Arriaga
 *
 * @property problem
 *   Text explaining the reason the location is invalid.
 *
 * @constructor
 * Construct an [InvalidLocation].
 *
 * @param path
 *   The [AvailLocation.path].
 * @param problem
 *   Text explaining the reason the location is invalid.
 * @param rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
class InvalidLocation constructor (
	path: String,
	val problem: String,
	rootNameInJar: String?
): AvailLocation(LocationType.invalid, Scheme.INVALID, path, rootNameInJar)
{
	override val fullPathNoPrefix: String get() = path

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = InvalidLocation(
		"$path${File.separator}$relativePath", problem, rootNameInJar)
}
