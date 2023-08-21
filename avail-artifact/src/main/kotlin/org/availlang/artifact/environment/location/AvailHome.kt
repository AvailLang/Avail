package org.availlang.artifact.environment.location

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLocation.LocationType
import java.io.File

/**
 * The [UserHome] location that is path relative to
 * [AvailEnvironment.availHome].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailHome].
 *
 * @param path
 *   The path relative to the [AvailEnvironment.availHome] directory.
 * @param scheme
 *   The [Scheme] of the location.
 * @param locationType
 *   The [LocationType].
 * @param rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
open class AvailHome constructor (
	path: String,
	scheme: Scheme,
	locationType: LocationType = LocationType.availHome,
	rootNameInJar: String?
): UserHome(path, scheme, locationType, rootNameInJar)
{
	override val fullPathNoPrefix: String get() =
		"${AvailEnvironment.availHome}${File.separator}$path"

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailHome(
		"$path${File.separator}$relativePath",
		scheme,
		locationType,
		rootNameInJar)
}
