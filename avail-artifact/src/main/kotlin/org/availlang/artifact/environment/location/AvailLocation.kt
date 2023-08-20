package org.availlang.artifact.environment.location

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import java.util.Objects

/**
 * Represents a location of a URI location for something related to an
 * [AvailEnvironment] project.
 *
 * @author Richard Arriaga
 *
 * @property locationType
 *   The [LocationType] member that represents the type of this location.
 * @property scheme
 *   The [Scheme] for this location.
 * @property path
 *   The path to this location.
 * @property rootNameInJar
 *   If the path indicates a jar file, this is the name of the root to use
 *   within that file.
 */
abstract class AvailLocation constructor(
	val locationType: LocationType,
	val scheme: Scheme,
	val path: String,
	val rootNameInJar: String?
): JSONFriendly
{
	/**
	 * Answer the full path to the location.
	 */
	val fullPath: String get() = buildString {
		append(scheme.optionalPrefix)
		append(fullPathNoPrefix)
		rootNameInJar?.let { r ->
			append('#')
			append(r) }
	}

	/**
	 * Answer the full path to the location.
	 */
	abstract val fullPathNoPrefix: String

	/**
	 * Are the contents of this location editable by this project? `true`
	 * indicates it is; `false` otherwise.
	 */
	open val editable: Boolean = false

	/**
	 * Create a new [AvailLocation] relative to this one.
	 *
	 * @param relativePath
	 *   The extended path relative to this location's [path].
	 * @param scheme
	 *   The [Scheme] of the referenced location.
	 * @param locationType
	 *   The [LocationType] of the new location.
	 */
	abstract fun relativeLocation (
		relativePath: String,
		scheme: Scheme,
		locationType: LocationType
	): AvailLocation

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::locationType.name) { write(locationType.name) }
			at(::scheme.name) { write(scheme.name) }
			at(::path.name) { write(path) }
			rootNameInJar?.let { r -> at(::rootNameInJar.name) { write(r)} }
		}
	}

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is AvailLocation) return false
		if (path != other.path) return false
		if (!Objects.equals(rootNameInJar, other.rootNameInJar)) return false
		return true
	}

	override fun hashCode(): Int
	{
		return path.hashCode()
	}

	override fun toString(): String = fullPath

	/**
	 * The acceptable path location types.
	 *
	 * @author Richard Arriaga
	 */
	@Suppress("EnumEntryName")
	enum class LocationType
	{
		/**
		 * Canonical representation of an invalid selection. Should not be used
		 * explicitly in the configuration file; it is only present to handle
		 * error situations with broken config files.
		 */
		invalid
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation =
				InvalidLocation(
					path,
					"Location type is literally $name, which not allowed.",
					rootNameInJar)
		},

		/** The path is relative to the [AvailEnvironment.availHome]. */
		availHome
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation = AvailHome(
				path, scheme, rootNameInJar = rootNameInJar)
		},

		/** The path is relative to the user's home directory. */
		home
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation = UserHome(
				path, scheme, rootNameInJar = rootNameInJar)
		},

		/** The path is relative to the [AvailEnvironment.availHomeLibs]. */
		availLibraries
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation = AvailLibraries(path, scheme, rootNameInJar)
		},

		/** The path is relative to the [AvailEnvironment.availHomeRepos]. */
		availRepositories
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation = AvailRepositories(path, scheme, rootNameInJar)
		},

		/**
		 * The path is relative to the [AvailEnvironment.availSdk].
		 */
		availSdks
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation = AvailSdks(path, scheme)
		},

		/** The path is relative to the project root directory. */
		project
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation = ProjectHome(
				path, scheme, pathRelativeSuffix, rootNameInJar)
		},

		/** The path is absolute. */
		absolute
		{
			override fun location(
				pathRelativeSuffix: String,
				path: String,
				scheme: Scheme,
				rootNameInJar: String?
			): AvailLocation = Absolute(path, scheme, rootNameInJar)
		};

		/**
		 * Extract a [AvailLocation] of this type from the provided
		 * [scheme].
		 *
		 * @param pathRelativeSuffix
		 *   The path suffix relative to the [AvailLocation].
		 * @param path
		 *   The already extracted path.
		 * @param scheme
		 *   The [JSONObject] to extract the rest of the data from.
		 * @param rootNameInJar
		 *   If the path indicates a jar file, this is the name of the root to
		 *   use within that file.
		 */
		abstract fun location (
			pathRelativeSuffix: String,
			path: String,
			scheme: Scheme,
			rootNameInJar: String?
		): AvailLocation

		companion object
		{
			/**
			 * The set of valid names of [LocationType].
			 */
			private val validNames: Set<String> =
				values().map { it.name }.toSet()

			/**
			 * Read a [AvailLocation] from the provided JSON.
			 *
			 * @param projectDirectory
			 *   The path of the root directory of the project.
			 * @param obj
			 *   The [JSONObject] to read from.
			 * @return
			 *   A [AvailLocation]. If there is a problem reading the value, a
			 *   [InvalidLocation] will be answered.
			 */
			fun from (
				projectDirectory: String,
				obj: JSONObject
			): AvailLocation
			{
				val path = try
				{
					obj.getString(AvailLocation::path.name)
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed configuration file: no 'path' " +
							"specified for a Location")
					e.printStackTrace()
					return InvalidLocation("", "missing path", "")
				}
				val locationTypeName = AvailLocation::locationType.name
				val raw = try
				{
					obj.getString(locationTypeName)
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed configuration file: Location missing " +
							"location type")
					e.printStackTrace()
					return InvalidLocation(
						path, "missing $locationTypeName", "")
				}
				if (!validNames.contains(raw))
				{
					System.err.println(
						"Malformed configuration file: $raw is not a " +
							"valid $locationTypeName value")
					return InvalidLocation(
						path,
						"invalid value for $locationTypeName: $raw",
						"")
				}
				val scheme = try
				{
					Scheme.valueOf(obj.getString(AvailLocation::scheme.name))
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed configuration file: Location missing " +
							"scheme type")
					e.printStackTrace()
					return InvalidLocation(
						path, "missing ${AvailLocation::scheme.name}", "")
				}
				val rootNameInJarName = AvailLocation::rootNameInJar.name
				val rootNameInJar = try
				{
					if (obj.containsKey(rootNameInJarName))
					{
						obj.getString(rootNameInJarName)
					}
					else null
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed configuration file: Expected string for " +
							"optional $rootNameInJarName")
					e.printStackTrace()
					return InvalidLocation(
						path, "invalid $rootNameInJarName", "")
				}
				return valueOf(raw).location(
					projectDirectory, path, scheme, rootNameInJar)
			}
		}
	}

	companion object
	{
		/**
		 * Read a [AvailLocation] from the provided JSON.
		 *
		 * @param projectDirectory
		 *   The path of the root directory of the project.
		 * @param obj
		 *   The [JSONObject] to read from.
		 * @return
		 *   A [AvailLocation]. If there is a problem reading the value, a
		 *   [InvalidLocation] will be answered.
		 */
		fun from (
			projectDirectory: String,
			obj: JSONObject
		): AvailLocation = LocationType.from(projectDirectory, obj)
	}
}
