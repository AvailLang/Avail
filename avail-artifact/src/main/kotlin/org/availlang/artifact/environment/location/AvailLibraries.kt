package org.availlang.artifact.environment.location

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.AvailEnvironment.availHomeLibs
import java.io.File

/**
 * The [AvailHome] location that is path relative to
 * [AvailEnvironment.availHomeLibs].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailLibraries].
 *
 * @param path
 *   The path relative to the [AvailEnvironment.availHomeRepos] directory.
 * @param scheme
 *   The [Scheme] of the location.
 * @param rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
open class AvailLibraries constructor (
	path: String = "",
	scheme: Scheme = Scheme.FILE,
	rootNameInJar: String?
): AvailHome(path, scheme, LocationType.availLibraries, rootNameInJar)
{
	override val fullPathNoPrefix: String get() =
		"$availHomeLibs${File.separator}$path"

	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailLibraries(
		"$path${File.separator}$relativePath", scheme, rootNameInJar)
}

/**
 * An [AvailLibraries] location of an Avail library JAR file.
 *
 * **NOTE** As this is not a directory, but a file [relativeLocation] returns
 * a location that is in the same directory as this represented file.
 *
 * @constructor
 * Construct an [AvailLibraryJar].
 *
 * @param fileName
 *   The JAR file name.
 * @param rootNameInJar
 *   The name of the root to use within the JAR file.
 */
@Suppress("unused")
class AvailLibraryJar constructor(
	fileName: String,
	rootNameInJar: String
): AvailLibraries(fileName, Scheme.JAR, rootNameInJar)
{
	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailLibraries(relativePath, scheme, rootNameInJar)
}

/**
 * An [AvailLibraries] location of an Avail library directory of files.
 *
 * @constructor
 * An [AvailLibraryDirectory].
 *
 * @param dirName
 *   The name of the directory.
 * @param rootNameInJar
 *   The name of the root to use within the JAR file.
 */
@Suppress("unused")
class AvailLibraryDirectory constructor(
	dirName: String,
	rootNameInJar: String
): AvailLibraries(dirName, Scheme.JAR, rootNameInJar)
{
	override fun relativeLocation(
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation = AvailLibraries(relativePath, scheme, rootNameInJar)
}
