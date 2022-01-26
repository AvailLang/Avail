/*
 * Projects.kt
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

package org.availlang.ide.anvil.models.project

import avail.AvailRuntime
import avail.builder.AvailBuilder
import avail.builder.ModuleName
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoot
import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.compiler.CompilerProgressReporter
import avail.compiler.GlobalProgressReporter
import avail.compiler.problems.Problem
import avail.compiler.problems.SimpleProblemHandler
import avail.descriptor.module.ModuleDescriptor
import avail.files.FileManager
import avail.persistence.cache.Repositories
import avail.persistence.cache.Repository
import avail.resolver.ModuleRootResolver
import avail.resolver.ModuleRootResolverRegistry
import avail.resolver.ResolverReference
import avail.resolver.ResourceType
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.availlang.ide.anvil.language.AvailFileProblem
import org.availlang.ide.anvil.language.AnvilProjectProblem
import org.availlang.ide.anvil.language.psi.AvailFile
import org.availlang.ide.anvil.listeners.AnvilProjectOpenListener
import org.availlang.ide.anvil.models.AvailNode
import org.availlang.ide.anvil.models.ConfigFileProblem
import org.availlang.ide.anvil.models.DirectoryNode
import org.availlang.ide.anvil.models.EntryPointNode
import org.availlang.ide.anvil.models.InvalidLocation
import org.availlang.ide.anvil.models.LocationProblem
import org.availlang.ide.anvil.models.ModuleNode
import org.availlang.ide.anvil.models.ModulePackageNode
import org.availlang.ide.anvil.models.ModuleRootScanProblem
import org.availlang.ide.anvil.models.ProjectLocation
import org.availlang.ide.anvil.models.ProjectProblem
import org.availlang.ide.anvil.models.ResourceNode
import org.availlang.ide.anvil.models.RootNode
import org.availlang.ide.anvil.models.UnexplainedProblem
import org.availlang.ide.anvil.streams.AnvilOutputStream
import org.availlang.ide.anvil.streams.StreamStyle
import org.availlang.ide.anvil.streams.StyledStreamEntry
import org.availlang.ide.anvil.utilities.Defaults
import org.availlang.ide.anvil.utilities.compactLocalTimestamp
import org.availlang.ide.anvil.utilities.createAvailRuntime
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONReader
import org.availlang.json.JSONWriter
import org.availlang.json.jsonPrettyPrintWriter
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

////////////////////////////////////////////////////////////////////////////////
//                                Projects.                                   //
////////////////////////////////////////////////////////////////////////////////
/**
 * Represents a [ModuleRootResolver] in a [AnvilProjectDescriptor].
 *
 * @author Richard Arriaga
 *
 * @property name
 *   The [ModuleRootResolver.name].
 * @property location
 *   The [ProjectLocation] of this root.
 * @property editable
 *   `true` indicates this root is editable by the project; `false` otherwise.
 * @property id
 *   The immutable id that uniquely identifies this [AnvilProjectRoot].
 */
class AnvilProjectRoot constructor(
	service: AnvilProjectService,
	var name: String,
	var location: ProjectLocation,
	var editable: Boolean = location.editable,
	val id: String = UUID.randomUUID().toString()
): JSONFriendly, Comparable<AnvilProjectRoot>
{
	/**
	 * The Avail [module][ModuleDescriptor] path. It takes the form:
	 *
	 * `"$name=$uri"`
	 */
	val modulePath: String = "$name=${location.fullPath(service)}"

	/**
	 * Answer a [ModuleRootResolver] for this [AnvilProjectRoot].
	 *
	 * @param fileManager
	 *   The [FileManager] used to manage the files accessed via the
	 *   [ModuleRootResolver].
	 */
	fun moduleRootResolver(fileManager: FileManager): ModuleRootResolver =
		ModuleRootResolverRegistry.createResolver(
			name, URI(modulePath), fileManager)

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(AnvilProjectRoot::id.name) { write(id) }
		writer.at(AnvilProjectRoot::name.name) { write(name) }
		writer.at(AnvilProjectRoot::editable.name) { write(editable) }
		writer.at(AnvilProjectRoot::location.name) { write(location) }
	}

	override fun compareTo(other: AnvilProjectRoot): Int =
		if (editable == other.editable)
		{
			if(name == other.name)
			{
				location.path.compareTo(other.location.path)
			}
			else
			{
				name.compareTo(other.name)
			}
		}
		else
		{
			if (editable) { 1 } else { -1 }
		}

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is AnvilProjectRoot) return false

		if (name != other.name) return false
		if (location != other.location) return false
		if (id != other.id) return false
		if (modulePath != other.modulePath) return false

		return true
	}

	override fun hashCode(): Int
	{
		var result = name.hashCode()
		result = 31 * result + location.hashCode()
		result = 31 * result + id.hashCode()
		result = 31 * result + modulePath.hashCode()
		return result
	}

	companion object
	{
		/**
		 * Extract and build a [AnvilProjectRoot] from the provided [JSONObject].
		 *
		 * @param service
		 *   The running [AnvilProjectService].
		 * @param jsonObject
		 *   The `JSONObject` that contains the `ProjectRoot` data.
		 * @return
		 *   The extracted `ProjectRoot`.
		 */
		fun from (
			service: AnvilProjectService,
			jsonObject: JSONObject
		): AnvilProjectRoot =
			AnvilProjectRoot(
				service,
				jsonObject.getString(AnvilProjectRoot::name.name),
				ProjectLocation.from(service, jsonObject.getObject(
					AnvilProjectRoot::location.name)),
				jsonObject.getBoolean(AnvilProjectRoot::editable.name),
				jsonObject.getString(AnvilProjectRoot::id.name))
	}
}

/**
 * Describes the makeup of an Avail [AnvilProject].
 *
 * @author Richard Arriaga
 *
 * @property name
 *   The [AnvilProject] name.
 * @property repositoryLocation
 *   The [Repository] [ProjectLocation].
 * @property renamesFilePath
 *   The path to the [renames file][RenamesFileParser].
 * @property renamesFileBody
 *   The contents of a [renames file][RenamesFileParser].
 * @property roots
 *   The map of [AnvilProjectRoot.name] to [AnvilProjectRoot].
 * @param id
 *   The id that uniquely identifies the project.
 */
class AnvilProjectDescriptor constructor(
	var name: String,
	var repositoryLocation: ProjectLocation =
		Defaults.instance.defaultRepositoryPath,
	var renamesFilePath: String = "",
	var renamesFileBody: String = "",
	val roots: MutableMap<String, AnvilProjectRoot> = mutableMapOf(),
	val id: String = UUID.randomUUID().toString(),
	val isActiveProject: Boolean = true
): JSONFriendly, Comparable<AnvilProjectDescriptor>
{
	/**
	 * The list of [AnvilProjectRoot]s in this [AnvilProjectDescriptor].
	 */
	val anvilProjectRoots: List<AnvilProjectRoot> get() =
		roots.values.toList().sorted()

	/**
	 * Add the [AnvilProjectRoot] to this [AnvilProjectDescriptor].
	 *
	 * @param anvilProjectRoot
	 *   The `ProjectRoot` to add.
	 */
	fun addRoot (anvilProjectRoot: AnvilProjectRoot)
	{
		roots[anvilProjectRoot.id] = anvilProjectRoot
	}

	/**
	 * Remove the [AnvilProjectRoot] from this [AnvilProjectDescriptor].
	 *
	 * @param projectRoot
	 *   The [AnvilProjectRoot.id] to remove.
	 * @return
	 *   The `ProjectRoot` removed or `null` if not found.
	 */
	fun removeRoot (projectRoot: String): AnvilProjectRoot? =
		roots.remove(projectRoot)

	/**
	 * Create a new [AnvilProject] from this [AnvilProjectDescriptor] with the given
	 * [FileManager].
	 *
	 * @param service
	 *   The running [AnvilProjectService].
	 * @param fileManager
	 *   The `FileManager` to use.
	 * @param then
	 *   The lambda to run after the Avail Project has been initialized.
	 * @return
	 *   A new `Project`.
	 */
	fun project (
		service: AnvilProjectService,
		fileManager: FileManager = FileManager(),
		then: (AnvilProject) -> Unit = {}
	): AnvilProject =
		AnvilProject(this, service, fileManager).apply {
			initializeRootsThen({
				walkRoots(then)
			},
			{ errors ->
				errors.forEach {
					service.problems.add(ModuleRootScanProblem(it))
				}
			})

		}

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(AnvilProjectDescriptor::id.name) { write(id) }
		writer.at("version") { write(CURRENT_SERIALIZATION_VERSION) }
		writer.at(AnvilProjectDescriptor::name.name) { write(name) }
		writer.at(AnvilProjectDescriptor::repositoryLocation.name)
		{
			write(repositoryLocation)
		}
		writer.at(AnvilProjectDescriptor::renamesFilePath.name)
		{
			write(renamesFilePath)
		}
		writer.at(AnvilProjectDescriptor::renamesFileBody.name)
		{
			write(renamesFileBody)
		}
		writer.at(AnvilProjectDescriptor::roots.name)
		{
			startArray()
			anvilProjectRoots.forEach {
				startObject()
				it.writeTo(writer)
				endObject()
			}
			endArray()
		}
	}

	override fun compareTo(other: AnvilProjectDescriptor): Int =
		name.compareTo(other.name)

	companion object
	{
		/**
		 * The current JSON serialization/deserialization version of
		 * [AnvilProjectDescriptor].
		 */
		const val CURRENT_SERIALIZATION_VERSION = 1

		val EMPTY_PROJECT = AnvilProjectDescriptor(
			"EMPTY", isActiveProject = false)

		/**
		 * Extract and build a [AnvilProjectDescriptor] from the provided
		 * [JSONObject].
		 *
		 * @param service
		 *   The running [AnvilProjectService].
		 * @param jsonObject
		 *   The `JSONObject` that contains the `ProjectDescriptor` data.
		 * @return
		 *   The extracted `ProjectDescriptor`.
		 */
		fun from (
			service: AnvilProjectService,
			jsonObject: JSONObject
		): AnvilProjectDescriptor
		{
			val id = jsonObject.getString(AnvilProjectDescriptor::id.name)
			val name = jsonObject.getString(AnvilProjectDescriptor::name.name)
			val repoLocation = ProjectLocation.from(
				service,
				jsonObject.getObject(
					AnvilProjectDescriptor::repositoryLocation.name))
			val renamesPath = jsonObject.getString(
				AnvilProjectDescriptor::renamesFilePath.name)
			val roots = mutableMapOf<String, AnvilProjectRoot>()
			jsonObject.getArray(AnvilProjectDescriptor::roots.name)
				.forEachIndexed { i, it ->
					val rootObj = it as? JSONObject ?: run {
						service.problems.add(
							ConfigFileProblem(
								"Malformed Anvil config file; malformed " +
									AnvilProjectDescriptor::roots.name +
									" object at position $i: $it"))
						return@forEachIndexed
					}
					val root =
						try
						{
							AnvilProjectRoot.from(service, rootObj)
						}
						catch (e: Throwable)
						{
							service.problems.add(
								ConfigFileProblem(
									"Malformed Anvil config file; malformed " +
										AnvilProjectDescriptor::roots.name +
										" object at position $i: $it"))
							return@forEachIndexed
						}
					roots[root.id] = root
				}
			val renames = jsonObject.getString(
				AnvilProjectDescriptor::renamesFileBody.name
			)
			return AnvilProjectDescriptor(
				name, repoLocation, renamesPath, renames, roots, id)
		}
	}
}

/**
 * The primary [Service] that maintains the [AnvilProject].
 *
 * @property project
 *   The opened IntelliJ [Project] this [Service] is assigned to.
 *
 * @constructor
 * Construct an [AnvilProjectService]. This should not be done manually, but
 * instead use [Project.getService] to create the [Project]. This is done using
 * the [AnvilProjectOpenListener].
 *
 * @param project
 *   The opened IntelliJ [Project] this [Service] is assigned to.
 */
@Service
class AnvilProjectService constructor(
	override val project: Project
): ProblemsProvider
{
	/**
	 * The path to the JSON file for this project that contains information
	 * about the  [AnvilProjectDescriptor]; `.idea/project.availconfig`
	 */
	private val descriptorFilePath = project.basePath?.let {
		"$it/.idea/$CONFIG_FILE_NAME"
	} ?: ".idea/$CONFIG_FILE_NAME"

	/**
	 * The list of active [ProjectProblem]s.
	 */
	val problems = mutableListOf<ProjectProblem>()

	fun reportProjectProblem (text: String)
	{
		val problem = AnvilProjectProblem(this, text)
		problemsCollector.problemAppeared(problem)
	}

	fun reportAvailFileProblem (
		file: VirtualFile,
		text: String,
		line: Int,
		column: Int)
	{
		val problem = AvailFileProblem(file, this, text, line, column)
		problemsCollector.problemAppeared(problem)
	}

	val problemsCollector = ProblemsCollector.getInstance(project)

	/**
	 * Provide the [InspectionManagerEx] that does....TODO
	 */
	val inspectionManager get() =
		InspectionManager.getInstance(project) as InspectionManagerEx

	/**
	 * The active [AnvilProjectDescriptor].
	 */
	private fun readDescriptor (): AnvilProjectDescriptor =
		project.basePath?.let {
			val descriptorFile = File("$it/.idea/$CONFIG_FILE_NAME")
			if (descriptorFile.exists())
			{
				val reader = JSONReader(descriptorFile.bufferedReader())
				val obj = reader.read()  as? JSONObject
					?: run {
						problems.add(
							ConfigFileProblem(
								"Malformed Anvil config file: " +
									descriptorFile.absolutePath))
						return@let AnvilProjectDescriptor.EMPTY_PROJECT
					}
				val descriptor =
					try
					{
						AnvilProjectDescriptor.from(this, obj)
					}
					catch (e: Throwable)
					{
						problems.add(
							UnexplainedProblem(
								e,
								"Failed to load configuration file: " +
									descriptorFile.absolutePath))
						return@let AnvilProjectDescriptor.EMPTY_PROJECT
					}
				return@let descriptor
			}
			else
			{
				return@let AnvilProjectDescriptor.EMPTY_PROJECT
			}
		} ?: AnvilProjectDescriptor.EMPTY_PROJECT

	/**
	 * The active [AnvilProjectDescriptor].
	 */
	private var descriptor: AnvilProjectDescriptor = readDescriptor()

	fun refreshDescriptor ()
	{
		val reread = readDescriptor()
		descriptor.roots.clear()
		descriptor.roots.putAll(reread.roots)
		descriptor.name = reread.name
		descriptor.renamesFileBody = reread.renamesFileBody
		descriptor.renamesFilePath = reread.renamesFilePath
		descriptor.repositoryLocation = reread.repositoryLocation

		anvilProject.initialize()
		problems.clear()
		anvilProject.initializeRootsThen({
			anvilProject.walkRoots { /* TODO build after walking the roots */}
		},
			{ errors ->
				errors.forEach {
					problems.add(ModuleRootScanProblem(it))
				}
			})
	}

	/**
	 * `true` indicates there is an active Avail project; `false` otherwise.
	 */
	val hasAvailProject get() = descriptor.isActiveProject

	val projectDirectory: String get() = project.basePath!!

	/**
	 * The [AnvilProject] that maintains the [AvailRuntime] and [AvailBuilder].
	 */
	val anvilProject: AnvilProject = descriptor.project(this)

	/**
	 * Save the current Anvil configuration to disk.
	 */
	fun saveConfigToDisk ()
	{
		if (!hasAvailProject || problems.isNotEmpty()) { return }
		val writer =
			jsonPrettyPrintWriter {
				writeObject { anvilProject.descriptor.writeTo(this) }
			}
		try
		{
			val descriptorFile =
				File("$projectDirectory/.idea/project.availconfig")
			Files.newBufferedWriter(descriptorFile.toPath()).use { bw ->
				bw.write(writer.toString())
			}
		}
		catch (e: Throwable)
		{
			throw IOException(
				"Save Anvil config to file failed: $descriptorFilePath",
				e)
		}
	}

	/**
	 * Export the [problems] to a file.
	 */
	fun exportProblemsToDisk ()
	{
		try
		{
			val descriptorFile = File(
				"$projectDirectory/avail-problems-" +
					"${compactLocalTimestamp(System.currentTimeMillis())}.txt")
			Files.newBufferedWriter(descriptorFile.toPath()).use { bw ->
				problems.forEach { it.writeTo(bw) }
			}
		}
		catch (e: Throwable)
		{
			throw IOException(
				"Export of Avail project problems to file failed",
				e)
		}
	}

	override fun toString(): String = anvilProject.descriptor.name

	companion object
	{
		val LOG: Logger = logger<AnvilProjectService>()

		val CONFIG_FILE_NAME = "anvil.config"
	}
}

/**
 * Provides the [AnvilProjectService] from this [Project].
 */
val Project.anvilProjectService: AnvilProjectService get() = service()

/**
 * Represents an actively open and running project.
 *
 * @author Richard Arriaga
 *
 * @property descriptor
 *   The [AnvilProjectDescriptor] that describes and identifies this [AnvilProject].
 * @property service
 *   The running [AnvilProjectService].
 * @property fileManager
 *   The [FileManager] that manages files for this [AnvilProject].
 */
data class AnvilProject constructor(
	val descriptor: AnvilProjectDescriptor,
	val service: AnvilProjectService,
	val fileManager: FileManager = Defaults.instance.defaultFileManager
): Comparable<AnvilProject>, JSONFriendly
{
	/**
	 * Initialize the project.
	 */
	internal fun initialize ()
	{
		val fullPath = descriptor.repositoryLocation.fullPath(service)
		try
		{
			val file = File(URI(fullPath))
			Repositories.setDirectoryLocation(file)
		}
		catch (e: Throwable)
		{
			service.problems.add(
				LocationProblem(InvalidLocation(
					service,
					fullPath,
					"Could not locate repository location.\n${e.message?:""}")))
		}
	}

	init
	{
		initialize()
	}

	/**
	 * The [AnvilProjectDescriptor.id] that uniquely represents this
	 * [AnvilProject].
	 */
	val id: String get() = descriptor.id

	/**
	 * The project directory where this project is running.
	 */
	val projectDirectory: String get() = service.project.basePath!!

	/**
	 * `true` indicates [build] is running, `false` otherwise.
	 */
	private val isBuilding = AtomicBoolean(false)

	/**
	 * The output stream to write to.
	 */
	val output = AnvilOutputStream()

	/**
	 * The list of String errors received while resolving module roots for this
	 * project.
	 */
	private val moduleRootResolutionErrors = mutableListOf<String>()

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(AnvilProject::id.name) { write(id) }
	}

	/**
	 * Add a [AnvilProjectRoot] to this [AnvilProject].
	 *
	 * @param root
	 *   The `ProjectRoot` to add.
	 * @param successHandler
	 *   The lambda to run if adding the rot was successful.
	 * @param failureHandler
	 *   The lambda that accepts the list of failures to run if adding the root
	 *   fails.
	 */
	internal fun addRoot (
		service: AnvilProjectService,
		root: AnvilProjectRoot,
		successHandler: () -> Unit,
		failureHandler: (List<String>)->Unit)
	{
		try
		{
			moduleRoots.addRoot(root.name, root.location.fullPath(service))
			{
				if (it.isEmpty())
				{
					successHandler()
				}
				else
				{
					failureHandler(it)
				}
			}
		}
		catch (e: IOException)
		{
			service.problems.add(LocationProblem(
				InvalidLocation(
					service,
					root.location.fullPath(service),
					"Could not locate root, ${root.name}, path: " +
						"${root.modulePath}.\n${e.message ?: ""}")
			))
		}
	}

	/**
	 * Initialize this [AnvilProject]'s [ModuleRoots] with all the [AnvilProjectRoot]s
	 * listed in its [descriptor].
	 *
	 * @param successHandler
	 *   The lambda to run if adding the rot was successful.
	 * @param failureHandler
	 *   The lambda that accepts the list of failures to run if adding the root
	 *   fails.
	 */
	fun initializeRootsThen (
		successHandler: ()->Unit,
		failureHandler: (List<String>)->Unit)
	{
		val rootCount = AtomicInteger(descriptor.roots.size)
		val errorList = mutableListOf<String>()
		descriptor.roots.values.forEach { root ->
			addRoot(service, root,
				{
					if (rootCount.decrementAndGet() == 0)
					{
						if (errorList.isEmpty())
						{
							moduleNameResolver
							successHandler()
						}
						else
						{
							moduleRootResolutionErrors.addAll(errorList)
							failureHandler(errorList)
						}
					}
				},
				{
					errorList.addAll(it)
					if (rootCount.decrementAndGet() == 0)
					{
						if (errorList.isEmpty())
						{
							successHandler()
						}
						else
						{
							failureHandler(errorList)
						}
					}
				})
		}
	}

	/**
	 * Answer the [RootNode] of the [ModuleRoot] the given
	 * [ModuleName.qualifiedName] (URI).
	 *
	 * @param moduleUri
	 *   The [URI] that identifies the module.
	 * @return
	 *   The [RootNode] if the Avail module is in one of the included
	 *   [moduleRoots]; `null` otherwise.
	 */
	fun rootForModuleUri (moduleUri: String): RootNode?
	{
		moduleRoots.roots.forEach {
			if (moduleUri.startsWith(it.resolver.uri.path))
			{
				return rootNodes[it.name]!!
			}
		}
		return null
	}

	/**
	 * The [ModuleRoots] for this [AnvilProject].
	 */
	private val moduleRoots: ModuleRoots =
		ModuleRoots(fileManager, "") {
			moduleRootResolutionErrors.addAll(it)
		}

	/**
	 * The [ModuleNameResolver] for this project.
	 */
	val moduleNameResolver: ModuleNameResolver by lazy {
		val renamesFileParser = RenamesFileParser(
			descriptor.renamesFileBody.reader(), moduleRoots)
		renamesFileParser.parse()
	}

	/** The [AvailRuntime] for this project. */
	val runtime by lazy {
		createAvailRuntime(moduleNameResolver, fileManager)
	}

	/** The [AvailBuilder] used to build Avail [Modules][ModuleDescriptor]. */
	val builder: AvailBuilder by lazy {
		AvailBuilder(runtime)
	}

	/**
	 * Stop this [AnvilProject]'s runtime.
	 */
	fun stopRuntime ()
	{
		runtime.destroy()
		runtime.awaitNoFibers()
	}

	/**
	 * Build the indicated Avail [module][ModuleDescriptor].
	 *
	 * @param availFile
	 *   The [AvailFile].
	 * @param qualifiedModuleName
	 *   The
	 *   [fully&#32;qualified&#32;module&#32;name][ModuleName.qualifiedName].
	 * @param globalReporter
	 *   A [global&#32;progress&#32;reporter][GlobalProgressReporter].
	 * @param moduleReporter
	 *   A
	 *   [module&#32;compilation&#32;progress&#32;reporter][CompilerProgressReporter].
	 * @param done
	 *   What to do when the whole build is complete.
	 */
	fun build (
		availFile: AvailFile,
		qualifiedModuleName: String,
		globalReporter: GlobalProgressReporter = { _, _ -> },
		moduleReporter: CompilerProgressReporter = { _, _, _, _, _ -> },
		done: () -> Unit = {}
	)
	{
		if (isBuilding.getAndSet(false))
		{
			output.write(
				StyledStreamEntry(
					StreamStyle.COMMAND, "Build $qualifiedModuleName\n"
				)
			)
			val resolvedModuleName = builder.runtime.moduleNameResolver.resolve(
				ModuleName(qualifiedModuleName), null)
			resolvedModuleName.repository.reopenIfNecessary()
			val start = System.currentTimeMillis()
			builder.buildTargetThen(
				resolvedModuleName,
				moduleReporter,
				globalReporter,
				object : SimpleProblemHandler
				{
					override fun handleGeneric(
						problem: Problem,
						decider: (Boolean)->Unit
					)
					{
						availFile.problems.add(problem)
						builder.stopBuildReason = "Build failed"
						val problemText = with(problem) {
							val adjustedLine = lineNumber - 5
							"$moduleName, line $adjustedLine:\n$this"
						}
						output.write(StyledStreamEntry(
							StreamStyle.ERR, problemText))
						decider(false)
					}
				}
			) {
				done()
				output.write(
					StyledStreamEntry(
						StreamStyle.INFO,
						"Build ended $qualifiedModuleName " +
							"(${System.currentTimeMillis() - start} ms)")
				)
				isBuilding.set(false)
			}
		}
	}

	/**
	 * The [RootNode]s in this [AnvilProject].
	 */
	val rootNodes = ConcurrentHashMap<String, RootNode>()

	/**
	 * The map of [ResolverReference.qualifiedName] to the corresponding
	 * [AvailNode].
	 */
	internal val nodes = ConcurrentHashMap<String, AvailNode>()

	/**
	 * The map of [ResolverReference.qualifiedName] to the corresponding
	 * [AvailNode].
	 */
	internal val nodesURI = ConcurrentHashMap<String, AvailNode>()

	/**
	 * Answer the [ModuleNode] for the given [VirtualFile].
	 *
	 * @param virtualFile
	 *   The [VirtualFile] that represents the target Avail module.
	 * @return
	 *   A [ModuleNode] if it exits in the project; `null` otherwise.
	 */
	fun getModuleNode (virtualFile: VirtualFile): ModuleNode? =
		nodesURI[virtualFile.path] as? ModuleNode

	/**
	 * Walk all the [ModuleRoot]s populating all the [AvailNode]s ([nodes]) for
	 * this [AnvilProject].
	 *
	 * @param then
	 *   The lambda that accepts this project that is run after the entire
	 *   project is walked.
	 */
	fun walkRoots (then: (AnvilProject) -> Unit)
	{
		nodes.clear()
		nodesURI.clear()
		rootNodes.clear()
		val moduleRootsCount = AtomicInteger(runtime.moduleRoots().roots.size)
		if (moduleRootsCount.get() == 0)
		{
			then(this)
			return
		}
		runtime.moduleRoots().forEach {
			walkRoot(it) {
				if (moduleRootsCount.decrementAndGet() == 0)
				{
					// TODO why this?
					Thread.sleep(3000)
					then(this)
				}
			}
		}
	}

	/**
	 * Populate the [node].
	 */
	private fun setNode (reference: ResolverReference, node: AvailNode)
	{
		nodes[reference.qualifiedName] = node
		nodesURI[reference.uri.path] = node
	}

	/**
	 * Walk the provided roots.
	 */
	private fun walkRoot(root: ModuleRoot, then: (AnvilProject) -> Unit)
	{
		root.resolver.provideModuleRootTree({ refRoot ->
			val rootNode = RootNode(this, refRoot, root)
			rootNodes[rootNode.reference.qualifiedName] = rootNode
			// TODO
			setNode(refRoot, rootNode)
			refRoot.walkChildrenThen(true, { visited ->
				when(visited.type)
				{
					ResourceType.MODULE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node = ModuleNode(parent, visited, this)
						setNode(visited, node)
						parent.addChild(node)
					}
					ResourceType.REPRESENTATIVE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node = ModuleNode(parent, visited, this)
						setNode(visited, node)
						parent.addChild(node)
					}
					ResourceType.PACKAGE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node =
							ModulePackageNode(parent, visited, this)
						setNode(visited, node)
						parent.addChild(node)
					}
					ResourceType.ROOT ->
					{
						// shouldn't get here?
					}
					ResourceType.DIRECTORY ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node =
							DirectoryNode(parent, visited, this)
						setNode(visited, node)
						parent.addChild(node)
					}
					ResourceType.RESOURCE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node =
							ResourceNode(parent, visited, this)
						setNode(visited, node)
						parent.addChild(node)
					}
				}
			},
			{
				builder.traceDirectoriesThen(
					{ name, version, after ->
						val entryPoints = version.getEntryPoints()
						if (entryPoints.isNotEmpty())
						{
							val node =
								nodes[name.qualifiedName]
							if (node != null)
							{
								when (node.reference.type)
								{
									ResourceType.MODULE ->
									{
										node as ModuleNode
										entryPoints.forEach {
											node.entryPointNodes.add(
												EntryPointNode(node, it))
										}
									}
									ResourceType.REPRESENTATIVE ->
									{
										node as ModuleNode
										val parent =
											node.parentNode as ModulePackageNode
										entryPoints.forEach {
											parent.entryPointNodes.add(
												EntryPointNode(node, it))
										}
									}
									else -> Unit
								}
							}
						}
						after()
					})
				{
					then(this)
				}
			})
		}) { code, e ->
			System.err.println("Error: $code")
			e?.printStackTrace()
		}
	}

	override fun compareTo(other: AnvilProject): Int =
		descriptor.compareTo(other.descriptor)
}
