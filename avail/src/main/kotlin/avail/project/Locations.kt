/*
 * Locations.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.project

import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import java.net.URI

/**
 * The supported [URI.scheme]s.
 *
 * @author Richard Arriaga
 */
enum class Scheme constructor(val prefix: String)
{
	/**
	 * The canonical representation of an invalid scheme.
	 */
	INVALID(""),

	/**
	 * A file location.
	 */
	FILE("file://"),

	/**
	 * A JAR file.
	 */
	JAR("jar:/")
}

/**
 * Represents a location of a URI location for something related to the
 * Avail project.
 *
 * @author Richard Arriaga
 *
 * @property locationType
 *   The [LocationType] member that represents the type of this location.
 * @property scheme
 *   The [Scheme] for this location.
 * @property path
 *   The path to this location.
 */
sealed class ProjectLocation constructor(
	val locationType: LocationType,
	val scheme: Scheme,
	val path: String
): JSONFriendly
{
	/**
	 * Answer the full path to the location.
	 *
	 * @param projectDirectory
	 *   The root of the project directory.
	 */
	abstract fun fullPath(projectDirectory: String): String

	/**
	 * Are the contents of this location editable by this project? `true`
	 * indicates it is; `false` otherwise.
	 */
	open val editable: Boolean = false

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(ProjectLocation::locationType.name) { write(locationType.name) }
			at(ProjectLocation::scheme.name) { write(scheme.name) }
			at(ProjectLocation::path.name) { write(path) }
		}
	}

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is ProjectLocation) return false

		if (path != other.path) return false

		return true
	}

	override fun hashCode(): Int
	{
		return path.hashCode()
	}

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
				projectDirectory: String,
				path: String,
				scheme: Scheme
			): ProjectLocation =
				InvalidLocation(
					path,
					"Location type is literally $name, which not allowed.")
		},

		/** The path is relative to the user's home directory. */
		home
		{
			override fun location(
				projectDirectory: String,
				path: String,
				scheme: Scheme
			): ProjectLocation = UserHome(path, scheme)
		},

		/** The path is relative to the project root directory. */
		project
		{
			override fun location(
				projectDirectory: String,
				path: String,
				scheme: Scheme
			): ProjectLocation = ProjectHome(path, scheme)
		},

		/** The path is relative to the user's home directory. */
		absolute
		{
			override fun location(
				projectDirectory: String,
				path: String,
				scheme: Scheme
			): ProjectLocation = UserHome(path, scheme)
		};

		/**
		 * Extract a [ProjectLocation] of this type from the provided
		 * [scheme].
		 *
		 * @param projectDirectory
		 *   The path of the root directory of the project.
		 * @param path
		 *   The already extracted path.
		 * @param scheme
		 *   The [JSONObject] to extract the rest of the data from.
		 */
		protected abstract fun location (
			projectDirectory: String,
			path: String,
			scheme: Scheme
		): ProjectLocation

		companion object
		{
			/**
			 * The set of valid names of [LocationType].
			 */
			private val validNames: Set<String> =
				values().map { it.name }.toSet()

			/**
			 * Read a [ProjectLocation] from the provided JSON.
			 *
			 * @param projectDirectory
			 *   The path of the root directory of the project.
			 * @param obj
			 *   The [JSONObject] to read from.
			 * @return
			 *   A [ProjectLocation]. If there is a problem reading the value, a
			 *   [InvalidLocation] will be answered.
			 */
			fun from (
				projectDirectory: String,
				obj: JSONObject
			): ProjectLocation
			{
				val path = try
				{
					obj.getString(ProjectLocation::path.name)
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed configuration file: no 'path' " +
							"specified for a Location")
					e.printStackTrace()
					return InvalidLocation(
						"", "missing path")
				}
				val raw = try
				{
					obj.getString(ProjectLocation::locationType.name)
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed configuration file: Location missing " +
							"location type")
					e.printStackTrace()
					return InvalidLocation(
						path,
						"missing ${ProjectLocation::locationType.name}")
				}
				if (!validNames.contains(raw))
				{
					System.err.println(
						"Malformed configuration file: $raw is not a " +
							"valid ${ProjectLocation::locationType.name} value")
					return InvalidLocation(
						path,
						"invalid value for " +
							"${ProjectLocation::locationType.name}: $raw")
				}
				val scheme = try
				{
					Scheme.valueOf(obj.getString(ProjectLocation::scheme.name))
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed configuration file: Location missing " +
							"scheme type")
					e.printStackTrace()
					return InvalidLocation(
						path,
						"missing ${ProjectLocation::scheme.name}")
				}
				return valueOf(raw).location(projectDirectory, path, scheme)
			}
		}
	}

	companion object
	{
		/**
		 * Read a [ProjectLocation] from the provided JSON.
		 *
		 * @param projectDirectory
		 *   The path of the root directory of the project.
		 * @param obj
		 *   The [JSONObject] to read from.
		 * @return
		 *   A [ProjectLocation]. If there is a problem reading the value, a
		 *   [InvalidLocation] will be answered.
		 */
		fun from (
			projectDirectory: String,
			obj: JSONObject
		): ProjectLocation = LocationType.from(projectDirectory, obj)
	}
}

/**
 * The canonical representation of an invalid [ProjectLocation].
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
 *   The [ProjectLocation.path].
 * @param problem
 *   Text explaining the reason the location is invalid.
 */
class InvalidLocation constructor (
	path: String,
	val problem: String
): ProjectLocation(LocationType.invalid, Scheme.INVALID, path)
{
	override fun fullPath(projectDirectory: String): String = path

	init
	{
		// TODO establish a logs file
	}
}

/**
 * The location that is path relative to the user's home directory.
 *
 * @author Richard Arriaga
 */
class UserHome constructor (
	path: String,
	scheme: Scheme
): ProjectLocation(LocationType.home, scheme, path)
{
	override fun fullPath(projectDirectory: String): String =
		"${scheme.prefix}${System.getProperty("user.home")}/$path"
}

/**
 * The location that is supplied as an absolute path.
 *
 * @author Richard Arriaga
 */
class Absolute constructor (
	path: String,
	scheme: Scheme
): ProjectLocation(LocationType.absolute, scheme, path)
{
	override fun fullPath(projectDirectory: String): String =
		"${scheme.prefix}$path"
}

/**
 * The location that is path relative to the project's home directory. By
 * default things that can be edited in this location (e.g. text files) are
 * considered editable by the project.
 *
 * @author Richard Arriaga
 */
class ProjectHome constructor (
	path: String,
	scheme: Scheme
): ProjectLocation(LocationType.project, scheme, path)
{
	override fun fullPath(projectDirectory: String): String =
		"${scheme.prefix}$projectDirectory/$path"

	override val editable: Boolean = true
}
