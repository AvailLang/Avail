/*
 * AvailProjectV1.kt
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

@file:Suppress("DuplicatedCode")

package org.availlang.artifact.environment.project

import org.availlang.artifact.AvailArtifactBuildPlan
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProject.Companion.STYLE_FILE_NAME
import org.availlang.artifact.environment.project.AvailProject.Companion.TEMPLATE_FILE_NAME
import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonObject
import java.io.File
import java.io.StringWriter
import java.net.URI
import java.util.UUID

/**
 * Describes [AvailProject.serializationVersion] 1 of an [AvailProject].
 *
 * @author Richard Arriaga
 */
class AvailProjectV1 constructor(
	override val name: String,
	override val darkMode: Boolean,
	override val repositoryLocation: AvailLocation,
	override var localSettings: LocalSettings,
	override val id: String = UUID.randomUUID().toString(),
	override val roots: MutableMap<String, AvailProjectRoot> = mutableMapOf(),
	override var styles: StylingGroup = StylingGroup(),
	override var templateGroup: TemplateGroup = TemplateGroup(),
): AvailProject
{
	override val serializationVersion = AvailProjectV1.serializationVersion
	override val moduleHeaders: MutableSet<ModuleHeaderFileMetadata> =
		mutableSetOf()
	override val manifestMap: MutableMap<String, AvailArtifactManifest> =
		mutableMapOf()

	override val disallow = mutableSetOf<String>()

	override val artifactBuildPlans =
		mutableListOf<AvailArtifactBuildPlan>()

	override fun optionallyInitializeConfigDirectory(
		configPath: String
	): File = AvailProject.optionallyInitializeConfigDirectory(configPath)

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::id.name) { write(id) }
			at(::darkMode.name) { write(darkMode) }
			at(::serializationVersion.name) {
				write(serializationVersion)
			}
			at(::name.name) { write(name) }
			at(::repositoryLocation.name) {
				write(repositoryLocation)
			}
			at(::disallow.name) { writeStrings(disallow) }
			at(::roots.name) { writeArray(availProjectRoots) }
			at(::moduleHeaders.name)
			{
				ModuleHeaderFileMetadata.writeTo(this, moduleHeaders)
			}
		}
	}

	companion object
	{
		/** The serialization version (for access without an instance). */
		const val serializationVersion: Int = 1

		/**
		 * Extract and build a [AvailProjectV1] from the provided
		 * [JSONObject].
		 *
		 * @param projectFileName
		 *   The name of the project file without the path.
		 * @param projectDirectory
		 *   The root directory of the project.
		 * @param obj
		 *   The `JSONObject` that contains the `ProjectDescriptor` data.
		 * @return
		 *   The extracted `ProjectDescriptor`.
		 */
		fun from (
			projectFileName: String,
			projectDirectory: String,
			obj: JSONObject
		): AvailProjectV1
		{
			val dirName = projectFileName.removeSuffix(".json")
			val projectConfigDir = AvailEnvironment.projectConfigPath(
				dirName, projectDirectory)
			AvailProject.optionallyInitializeConfigDirectory(
				projectConfigDir, false)
			val id = obj.getString(AvailProjectV1::id.name)
			val name = obj.getString(AvailProjectV1::name.name)
			val darkMode = obj.getBooleanOrNull(AvailProjectV1::darkMode.name)
				?: true
			val repoLocation = AvailLocation.from(
				projectDirectory,
				obj.getObject(AvailProjectV1::repositoryLocation.name))
			val projectProblems = mutableListOf<ProjectProblem>()
			val localSettings = LocalSettings.from(File(projectConfigDir))
			val styles = StylingGroup(
				jsonObject(
				File(projectConfigDir, STYLE_FILE_NAME).readText()))
			val templateGroup = TemplateGroup(
				jsonObject(
				File(projectConfigDir, TEMPLATE_FILE_NAME).readText()))
			val project = AvailProjectV1(
				name = name,
				darkMode = darkMode,
				repositoryLocation = repoLocation,
				id = id,
				localSettings = localSettings,
				styles = styles,
				templateGroup = templateGroup)

			obj.getArrayOrNull(AvailProjectV1::disallow.name)?.strings?.let {
				project.disallow.addAll(it)
			}

			obj.getArray(
				AvailProjectV1::roots.name
			).mapIndexedNotNull { i, it ->
				val rootObj = it as? JSONObject ?: run {
					projectProblems.add(
						ConfigFileProblem(
							"Malformed Avail project config file, "
								+ "$projectFileName; malformed "
								+ AvailProjectV1::roots.name
								+ " object at position $i"))
					return@mapIndexedNotNull null
				}
				return@mapIndexedNotNull try
				{
					val root = AvailProjectRoot.from(
						project,
						projectFileName,
						projectDirectory,
						rootObj,
						serializationVersion
					)
					if (root.location.scheme == Scheme.JAR)
					{
						root.location.let { loc ->
							val l = loc.scheme.optionalPrefix +
								loc.fullPathNoPrefix
							project.manifestMap.computeIfAbsent(root.name) { _ ->
								AvailArtifactJar(URI(l)).manifest
							}.updateRoot(root)
						}
					}
					root.name to root
				}
				catch (e: Throwable)
				{
					projectProblems.add(
						ConfigFileProblem(
							"Malformed Avail project config"
								+ " file, $projectFileName; malformed "
								+ AvailProjectV1::roots.name
								+ " object at position $i\n${e.message}",
							e))
					null
				}
			}.associateTo(project.roots) { it }
			obj.getArrayOrNull(AvailProjectRoot::moduleHeaders.name)?.let {
				project.moduleHeaders.addAll(
					ModuleHeaderFileMetadata.from(projectConfigDir, it))
			}
			if (projectProblems.isNotEmpty())
			{
				throw AvailProjectException(projectProblems)
			}
			return project.apply {
				artifactBuildPlans.addAll(
					AvailArtifactBuildPlan.readPlans(
						dirName, projectDirectory))
			}
		}
	}
}

/**
 * An [AvailProjectException] is raised by a [factory][AvailProjectV1] that
 * creates an [AvailProject] from a persistent representation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
data class AvailProjectException constructor(
	val problems: List<ProjectProblem>
): Exception()
{
	override val message: String
		get() = StringWriter().let { sw ->
			problems.forEach { it.writeTo(sw) }
			sw.toString()
		}
}
