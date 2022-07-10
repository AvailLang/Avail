/*
 * AvailProjectConfiguration.kt
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

import avail.environment.AvailWorkbench
import avail.persistence.cache.Repository
import avail.resolver.ModuleRootResolver
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import java.lang.module.ModuleDescriptor
import java.util.UUID

/**
 * Represents a [ModuleRootResolver] in a [AvailProjectConfiguration].
 *
 * @author Richard Arriaga
 *
 * @property projectDirectory
 *   The root directory of this project.
 * @property name
 *   The [ModuleRootResolver.name].
 * @property location
 *   The [ProjectLocation] of this root.
 * @property editable
 *   `true` indicates this root is editable by the project; `false` otherwise.
 * @property id
 *   The immutable id that uniquely identifies this [AvailProjectRoot].
 */
class AvailProjectRoot constructor(
	val projectDirectory: String,
	val scheme: String,
	var name: String,
	var location: ProjectLocation,
	var editable: Boolean = location.editable,
	val id: String = UUID.randomUUID().toString()
): JSONFriendly
{
	/**
	 * The Avail [module][ModuleDescriptor] path. It takes the form:
	 *
	 * `"$name=$uri"`
	 */
	val modulePath: String = "$name=$scheme${location.fullPath(projectDirectory)}"

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(AvailProjectRoot::scheme.name) { write(scheme) }
			at(AvailProjectRoot::id.name) { write(id) }
			at(AvailProjectRoot::name.name) { write(name) }
			at(AvailProjectRoot::editable.name) { write(editable) }
			at(AvailProjectRoot::location.name) { write(location) }
		}
	}

	companion object
	{
		/**
		 * Extract and build a [AvailProjectRoot] from the provided [JSONObject].
		 *
		 * @param projectDirectory
		 *   The root directory of this project.
		 * @param jsonObject
		 *   The `JSONObject` that contains the `ProjectRoot` data.
		 * @return
		 *   The extracted `ProjectRoot`.
		 */
		fun from (
			projectDirectory: String,
			jsonObject: JSONObject
		): AvailProjectRoot =
			AvailProjectRoot(
				projectDirectory,
				jsonObject.getString(AvailProjectRoot::scheme.name),
				jsonObject.getString(AvailProjectRoot::name.name),
				ProjectLocation.from(
					jsonObject.getString(AvailProjectRoot::projectDirectory.name),
					jsonObject.getObject(AvailProjectRoot::location.name)),
				jsonObject.getBoolean(AvailProjectRoot::editable.name),
				jsonObject.getString(AvailProjectRoot::id.name))
	}
}

/**
 * Describes the makeup of an Avail project.
 *
 * @author Richard Arriaga
 *
 * @property name
 *   The name of the Avail project.
 * @property darkMode
 *   `true` indicates use of [AvailWorkbench.darkMode]; `false` for light mode.
 * @property repositoryLocation
 *   The [ProjectLocation] for the [Repository].
 * @property id
 *   The id that uniquely identifies the project.
 * @property roots
 *   The map of [AvailProjectRoot.name] to [AvailProjectRoot].
 */
class AvailProjectConfiguration constructor(
	val name: String,
	val darkMode: Boolean,
	val repositoryLocation: ProjectLocation,
	val id: String = UUID.randomUUID().toString(),
	val roots: MutableMap<String, AvailProjectRoot> = mutableMapOf()
): JSONFriendly
{
	/**
	 * The list of [AvailProjectRoot]s in this [AvailProjectConfiguration].
	 */
	val availProjectRoots: List<AvailProjectRoot> get() =
		roots.values.toList().sortedBy { it.name }

	/**
	 * Add the [AvailProjectRoot] to this [AvailProjectConfiguration].
	 *
	 * @param availProjectRoot
	 *   The `AvailProjectRoot` to add.
	 */
	fun addRoot (availProjectRoot: AvailProjectRoot)
	{
		roots[availProjectRoot.id] = availProjectRoot
	}

	/**
	 * Remove the [AvailProjectRoot] from this [AvailProjectConfiguration].
	 *
	 * @param projectRoot
	 *   The [AvailProjectRoot.id] to remove.
	 * @return
	 *   The `AvailProjectRoot` removed or `null` if not found.
	 */
	fun removeRoot (projectRoot: String): AvailProjectRoot? =
		roots.remove(projectRoot)

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(AvailProjectConfiguration::id.name) { write(id) }
			at(AvailProjectConfiguration::darkMode.name) { write(darkMode) }
			at("version") { write(CURRENT_SERIALIZATION_VERSION) }
			at(AvailProjectConfiguration::name.name) { write(name) }
			at(AvailProjectConfiguration::repositoryLocation.name)
			{
				write(repositoryLocation)
			}
			at(AvailProjectConfiguration::roots.name)
			{
				startArray()
				availProjectRoots.forEach {
					startObject()
					it.writeTo(writer)
					endObject()
				}
				endArray()
			}
		}
	}

	companion object
	{
		/**
		 * The current JSON serialization/deserialization version of
		 * [AvailProjectConfiguration].
		 */
		const val CURRENT_SERIALIZATION_VERSION = 1

		/**
		 * The Avail configuration file name.
		 */
		internal const val CONFIG_FILE_NAME = "config/avail-config.json"

		/**
		 * Extract and build a [AvailProjectConfiguration] from the provided
		 * [JSONObject].
		 *
		 * @param projectDirectory
		 *   The root directory of the project.
		 * @param jsonObject
		 *   The `JSONObject` that contains the `ProjectDescriptor` data.
		 * @return
		 *   The extracted `ProjectDescriptor`.
		 */
		fun from (
			projectDirectory: String,
			jsonObject: JSONObject
		): AvailProjectConfiguration
		{
			val id = jsonObject.getString(AvailProjectConfiguration::id.name)
			val name = jsonObject.getString(AvailProjectConfiguration::name.name)
			val darkMode =
				jsonObject.getBoolean(AvailProjectConfiguration::darkMode.name)
			val repoLocation = ProjectLocation.from(
				projectDirectory,
				jsonObject.getObject(
					AvailProjectConfiguration::repositoryLocation.name))
			val roots = mutableMapOf<String, AvailProjectRoot>()
			val projectProblems = mutableListOf<ProjectProblem>()
			jsonObject.getArray(AvailProjectConfiguration::roots.name)
				.forEachIndexed { i, it ->
					val rootObj = it as? JSONObject ?: run {
						projectProblems.add(
							ConfigFileProblem(
								"Malformed Avail project config file, " +
									"$CONFIG_FILE_NAME; malformed " +
									AvailProjectConfiguration::roots.name +
									" object at position $i"))
						return@forEachIndexed
					}
					val root =
						try
						{
							AvailProjectRoot.from(projectDirectory, rootObj)
						}
						catch (e: Throwable)
						{
							projectProblems.add(
								ConfigFileProblem(
									"Malformed Avail project config" +
										" file, $CONFIG_FILE_NAME; malformed " +
										AvailProjectConfiguration::roots.name +
										" object at position $i"))
							return@forEachIndexed
						}
					roots[root.id] = root
				}
			return AvailProjectConfiguration(
				name, darkMode, repoLocation, id, roots)
		}
	}
}
