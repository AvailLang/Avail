package org.availlang.artifact.environment.location

import org.availlang.artifact.environment.location.AvailLocation.LocationType
import java.io.File

/**
 * The [AvailLocation] that is path relative to the user's home directory.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [UserHome].
 *
 * @param path
 *   The path relative to the user's home directory.
 * @param scheme
 *   The [Scheme] of the location.
 * @param locationType
 *   The [LocationType].
 * @param rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
open class UserHome constructor (
	path: String,
	scheme: Scheme,
	locationType: LocationType = LocationType.home,
	rootNameInJar: String?
): AvailLocation(locationType, scheme, path, rootNameInJar)
{
	override val fullPathNoPrefix: String get() =
		"${System.getProperty("user.home")}${File.separator}$path"

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = UserHome(
		"$path${File.separator}$relativePath",
		scheme,
		locationType,
		rootNameInJar)
}
