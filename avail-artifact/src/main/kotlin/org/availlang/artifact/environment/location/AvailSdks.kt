package org.availlang.artifact.environment.location

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.AvailEnvironment.availSdk
import java.io.File

/**
 * The [AvailHome] location that is path relative to
 * [AvailEnvironment.availSdk].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailSdk] location.
 *
 * @param path
 *   The path relative to the [AvailEnvironment.availSdk] directory.
 * @param scheme
 *   The [Scheme] of the location.
 */
open class AvailSdks constructor(
	path: String,
	scheme: Scheme
): AvailHome(path, scheme, LocationType.availSdks, null)
{
	override val fullPathNoPrefix: String get() =
		"$availSdk${File.separator}$path"

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailSdks(
		"$path${File.separator}$relativePath", scheme)
}

/**
 * The [AvailSdks] location that is an Avail SDK.
 *
 * **NOTE** As this is not a directory, but a file [relativeLocation] returns
 * a location that is in the same directory as this represented file.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailSdk] location.
 *
 * @param sdkName
 *   The name of the SDK JAR file.
 */
class AvailSdk constructor(sdkName: String): AvailSdks(sdkName, Scheme.JAR)
{
	override val fullPathNoPrefix: String get() =
		"$availSdk${File.separator}$path"

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailSdks(relativePath, scheme)
}
