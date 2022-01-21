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

package org.availlang.ide.anvil.models

import org.availlang.ide.anvil.models.project.AvailProjectService
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter

/**
 * Represents a location of a URI location for something related to the
 * Avail project.
 *
 * @author Richard Arriaga
 *
 * @property locationType
 *   The [LocationType] member that represents the type of this location.
 * @property path
 *   The path to this location. This maybe a relative path or a non-relative
 *   path if [locationType] is [LocationType.network].
 */
sealed class ProjectLocation constructor(
	val locationType: LocationType,
	val path: String
): JSONFriendly
{
	/**
	 * Answer the full path to the location.
	 */
	abstract fun fullPath(service: AvailProjectService): String

	/**
	 * Are the contents of this location editable by this project? `true`
	 * indicates it is; `false` otherwise.
	 */
	open val editable: Boolean = false

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(ProjectLocation::locationType.name) { write(locationType.name) }
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
				service: AvailProjectService,
				path: String,
				jsonObject: JSONObject
			): ProjectLocation =
				InvalidLocation(
					service,
					path,
					"Location type is literally $name, which not allowed.")
		},

		/** The path is relative to the user's home directory. */
		home
		{
			override fun location(
				service: AvailProjectService,
				path: String,
				jsonObject: JSONObject
			): ProjectLocation = UserHome(path)
		},

		/** The path is relative to the project root directory. */
		project
		{
			override fun location(
				service: AvailProjectService,
				path: String,
				jsonObject: JSONObject
			): ProjectLocation = ProjectHome(path)
		},

		/**
		 * The path is not relative, but instead a network location.
		 */
		network
		{
			override fun location(
				service: AvailProjectService,
				path: String,
				jsonObject: JSONObject
			): ProjectLocation = NetworkLocation(path)
		};

		/**
		 * Extract a [ProjectLocation] of this type from the provided
		 * [jsonObject].
		 *
		 * @param service
		 *   The running [AvailProjectService].
		 * @param path
		 *   The already extracted path.
		 * @param jsonObject
		 *   The [JSONObject] to extract the rest of the data from.
		 */
		protected abstract fun location (
			service: AvailProjectService,
			path: String,
			jsonObject: JSONObject
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
			 * @param service
			 *   The running [AvailProjectService].
			 * @param obj
			 *   The [JSONObject] to read from.
			 * @return
			 *   A [ProjectLocation]. If there is a problem reading the value, a
			 *   [InvalidLocation] will be answered.
			 */
			fun from (
				service: AvailProjectService,
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
						"Malformed .idea/project.availconfig file: no 'path' specified")
					e.printStackTrace()
					return InvalidLocation(
						service, "", "missing path")
				}
				val raw = try
				{
					obj.getString(ProjectLocation::locationType.name)
				}
				catch (e: Throwable)
				{
					System.err.println("Malformed .idea/project.availconfig file.")
					e.printStackTrace()
					return InvalidLocation(
						service,
						path,
						"missing ${ProjectLocation::locationType.name}")
				}
				if (!validNames.contains(raw))
				{
					System.err.println(
						"Malformed .idea/project.availconfig file: $raw is not a " +
							"valid ${ProjectLocation::locationType.name} value")
					return InvalidLocation(
						service,
						path,
						"invalid value for " +
							"${ProjectLocation::locationType.name}: $raw")
				}
				return valueOf(raw).location(service, path, obj)
			}
		}
	}

	companion object
	{
		/**
		 * Read a [ProjectLocation] from the provided JSON.
		 *
		 * @param service
		 *   The running [AvailProjectService].
		 * @param obj
		 *   The [JSONObject] to read from.
		 * @return
		 *   A [ProjectLocation]. If there is a problem reading the value, a
		 *   [InvalidLocation] will be answered.
		 */
		fun from (
			service: AvailProjectService,
			obj: JSONObject
		): ProjectLocation = LocationType.from(service, obj)
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
 * @param service
 *   The running [AvailProjectService].
 * @param path
 *   The [ProjectLocation.path].
 * @param problem
 *   Text explaining the reason the location is invalid.
 */
class InvalidLocation constructor (
	service: AvailProjectService,
	path: String,
	val problem: String
): ProjectLocation(LocationType.invalid, path)
{
	override fun fullPath(service: AvailProjectService): String = path

	init
	{
		service.problems.add(LocationProblem(this))
	}
}

/**
 * The location that is path relative to the user's home directory.
 *
 * @author Richard Arriaga
 */
class UserHome constructor (
	path: String
): ProjectLocation(LocationType.home, path)
{
	private val scheme =
		if (path.endsWith(".jar")) { "jar:" }
		else { "file://" }

	override fun fullPath(service: AvailProjectService): String =
		"$scheme${System.getProperty("user.home")}/$path"
}

/**
 * The location that is path relative to the project's home directory. By
 * default things that can be edited in this location (e.g. text files) are
 * considered editable by the project.
 *
 * @author Richard Arriaga
 */
class ProjectHome constructor (
	path: String
): ProjectLocation(LocationType.project, path)
{
	private val scheme =
		if (path.endsWith(".jar")) { "jar:" }
		else { "file://" }

	override fun fullPath(service: AvailProjectService): String =
		"$scheme${service.projectDirectory}/$path"

	override val editable: Boolean = true
}

/**
 * The location refers to a network location.
 *
 * TODO could require additional information for access?
 *
 * @author Richard Arriaga
 */
class NetworkLocation constructor (
	path: String
): ProjectLocation(LocationType.project, path)
{
	override fun fullPath(service: AvailProjectService): String = path
}
