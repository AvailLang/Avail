/*
 * Projects.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.anvil.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowSize
import androidx.compose.ui.window.rememberWindowState
import avail.anvil.Anvil
import avail.anvil.Anvil.defaults
import avail.anvil.components.WorkspaceWindow
import com.avail.AvailRuntime
import avail.anvil.file.AvailNode
import avail.anvil.file.DirectoryNode
import avail.anvil.file.EntryPointNode
import avail.anvil.file.ModuleNode
import avail.anvil.file.ModulePackageNode
import avail.anvil.file.ResourceNode
import avail.anvil.file.RootNode
import avail.anvil.utilities.createAvailRuntime
import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoot
import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParser
import com.avail.compiler.CompilerProgressReporter
import com.avail.compiler.GlobalProgressReporter
import com.avail.compiler.problems.Problem
import com.avail.compiler.problems.SimpleProblemHandler
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.files.FileManager
import com.avail.resolver.ModuleRootResolver
import com.avail.resolver.ModuleRootResolverRegistry
import com.avail.resolver.ResolverReference
import com.avail.resolver.ResourceType
import com.avail.utility.json.JSONFriendly
import com.avail.utility.json.JSONObject
import com.avail.utility.json.JSONWriter
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

////////////////////////////////////////////////////////////////////////////////
//                                Projects.                                   //
////////////////////////////////////////////////////////////////////////////////

/**
 * Represents a [ModuleRootResolver] in a [ProjectDescriptor].
 *
 * @author Richard Arriaga
 *
 * @property name
 *   The [ModuleRootResolver.name].
 * @property uri
 *   The [ModuleRootResolver.uri]
 * @property id
 *   The immutable id that uniquely identifies this [ProjectRoot].
 */
data class ProjectRoot constructor(
	var name: String,
	var uri: String,
	val id: String = UUID.randomUUID().toString()
): JSONFriendly, Comparable<ProjectRoot>
{
	/**
	 * The Avail [module][ModuleDescriptor] path. It takes the form:
	 *
	 * `"$name=$uri"`
	 */
	val modulePath: String = "$name=$uri"

	/**
	 * Answer a [ModuleRootResolver] for this [ProjectRoot].
	 *
	 * @param fileManager
	 *   The [FileManager] used to manage the files accessed via the
	 *   [ModuleRootResolver].
	 */
	fun moduleRootResolver(fileManager: FileManager): ModuleRootResolver =
		ModuleRootResolverRegistry.createResolver(name, URI(uri), fileManager)

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(ID) { write(id) }
		writer.at(NAME) { write(name) }
		writer.at(URI) { write(uri) }
	}

	override fun compareTo(other: ProjectRoot): Int =
		if (name == other.name)
		{
			uri.compareTo(other.uri)
		}
		else
		{
			name.compareTo(other.name)
		}

	companion object
	{
		/**
		 * Generic "name" config file key. Used for: [ProjectRoot.uri].
		 */
		internal const val URI = "uri"

		/**
		 * Extract and build a [ProjectRoot] from the provided [JSONObject].
		 *
		 * @param jsonObject
		 *   The `JSONObject` that contains the `ProjectRoot` data.
		 * @return
		 *   The extracted `ProjectRoot`.
		 */
		fun from (jsonObject: JSONObject): ProjectRoot =
			ProjectRoot(
				jsonObject.getString(NAME),
				jsonObject.getString(URI),
				jsonObject.getString(ID))
	}
}

/**
 * Describes the makeup of an Avail [Project].
 *
 * @author Richard Arriaga
 *
 * @property name
 *   The [Project] name.
 * @property renamesFileBody
 *   The path to the [renames file][RenamesFileParser].
 * @property renamesFileBody
 *   The contents of a [renames file][RenamesFileParser].
 * @property roots
 *   The map of [ProjectRoot.name] to [ProjectRoot].
 * @param id
 *   The id that uniquely identifies the project.
 */
data class ProjectDescriptor constructor(
	var name: String,
	var repositoryPath: String = defaults.defaultRepositoryPath,
	var renamesFilePath: String = "",
	var renamesFileBody: String = "",
	val roots: MutableMap<String, ProjectRoot> = mutableMapOf(),
	val id: String = UUID.randomUUID().toString()
): JSONFriendly, Comparable<ProjectDescriptor>
{
	/**
	 * The list of [ProjectRoot]s in this [ProjectDescriptor].
	 */
	val projectRoots: List<ProjectRoot> get() =
		roots.values.toList().sorted()

	/**
	 * Answer a [MutableList] of a copy of all the [projectRoots].
	 */
	val rootsCopy get() = mutableStateListOf<ProjectRoot>().apply {
		projectRoots.forEach { this.add(it.copy()) }
	}

	/**
	 * Add the [ProjectRoot] to this [ProjectDescriptor].
	 *
	 * @param projectRoot
	 *   The `ProjectRoot` to add.
	 */
	fun addRoot (projectRoot: ProjectRoot)
	{
		roots[projectRoot.id] = projectRoot
	}

	/**
	 * Remove the [ProjectRoot] from this [ProjectDescriptor].
	 *
	 * @param projectRoot
	 *   The [ProjectRoot.id] to remove.
	 * @return
	 *   The `ProjectRoot` removed or `null` if not found.
	 */
	fun removeRoot (projectRoot: String): ProjectRoot? =
		roots.remove(projectRoot)

	/**
	 * Create a new [Project] from this [ProjectDescriptor] with the given
	 * [FileManager].
	 *
	 * @param fileManager
	 *   The `FileManager` to use.
	 * @return
	 *   A new `Project`.
	 */
	fun project (
		fileManager: FileManager = FileManager(),
		then: (Project) -> Unit = {}
	): Project =
		Project(this, fileManager).apply {
			initializeRootsThen({
				walkRoots(then)
			},
			{
				// TODO?
				then(this)
			})

		}

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(ID) { write(id) }
		writer.at(NAME) { write(name) }
		writer.at(REPOS_FILE_PATH) { write(repositoryPath) }
		writer.at(RENAMES_FILE_PATH) { write(renamesFilePath) }
		writer.at(RENAMES_FILE_BODY) { write(renamesFileBody) }
		writer.at(ROOTS) {
			startArray()
			projectRoots.forEach {
				startObject()
				it.writeTo(writer)
				endObject()
			}
			endArray()
		}
	}

	override fun compareTo(other: ProjectDescriptor): Int =
		name.compareTo(other.name)

	companion object
	{
		/**
		 * Extract and build a [ProjectDescriptor] from the provided
		 * [JSONObject].
		 *
		 * @param jsonObject
		 *   The `JSONObject` that contains the `ProjectDescriptor` data.
		 * @return
		 *   The extracted `ProjectDescriptor`.
		 */
		fun from (jsonObject: JSONObject): ProjectDescriptor
		{
			val id = jsonObject.getString(ID)
			val name = jsonObject.getString(NAME)
			val repos = jsonObject.getString(REPOS_FILE_PATH)
			val renamesPath = jsonObject.getString(RENAMES_FILE_PATH)
			val roots = mutableMapOf<String, ProjectRoot>()
			jsonObject.getArray(ROOTS).forEach {
				val rootObj = it as? JSONObject ?:
					error("Malformed Anvil config file; malformed Project " +
						"Root in `knownProjects` - `$ROOTS`: $it")
				val root = ProjectRoot.from(rootObj)
				roots[root.id] = root
			}
			val renames = jsonObject.getString(RENAMES_FILE_BODY)
			return ProjectDescriptor(
				name, repos, renamesPath, renames, roots, id)
		}
	}
}

/**
 * Represents an actively open and running project.
 *
 * @author Richard Arriaga
 *
 * @property descriptor
 *   The [ProjectDescriptor] that describes and identifies this [Project].
 * @property fileManager
 *   The [FileManager] that manages files for this [Project].
 */
data class Project constructor(
	val descriptor: ProjectDescriptor,
	val fileManager: FileManager = defaults.defaultFileManager
): Comparable<Project>
{
	/**
	 * The [ProjectDescriptor.id] that uniquely represents this [Project].
	 */
	val id: String get() = descriptor.id

	/**
	 * `true` indicates [build] is running, `false` otherwise.
	 */
	private val isBuilding = AtomicBoolean(false)

	/**
	 * The list of String errors received while resolving module roots for this
	 * project.
	 */
	private val moduleRootResolutionErrors = mutableListOf<String>()

	/**
	 * Add a [ProjectRoot] to this [Project].
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
		root: ProjectRoot,
		successHandler: () -> Unit,
		failureHandler: (List<String>)->Unit)
	{
		moduleRoots.addRoot(root.name, root.uri) {
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

	/**
	 * Initialize this [Project]'s [ModuleRoots] with all the [ProjectRoot]s
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
			addRoot(root,
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
	 * The [ModuleRoots] for this [Project].
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
	 * Stop this [Project]'s runtime.
	 */
	fun stopRuntime ()
	{
		runtime.destroy()
		runtime.awaitNoFibers()
	}

	/**
	 * Build the indicated Avail [module][ModuleDescriptor].
	 *
	 * @param qualifiedModuleName
	 *   The [fully qualified module name][ModuleName.qualifiedName].
	 * @param done
	 *   The lambda to run after build is complete.
	 * @return
	 */
	fun build (qualifiedModuleName: String, done: () -> Unit): Boolean
	{
		if (isBuilding.getAndSet(true))
		{
			return false
		}
		val resolvedModuleName =
			builder.runtime.moduleNameResolver.resolve(
				ModuleName(qualifiedModuleName), null)

		val progressReporter: CompilerProgressReporter =
			{   moduleName,
				moduleSizeInBytes,
				currentByteProcessing,
				lineNumber ->

				// Add behavior to present compiler progress on module currently
				// being compiled. This can be used to present the compilation
				// progress on the currently compiling file.
			}
		val globalProgressReporter: GlobalProgressReporter =
			{ bytesCompiled, totalBytesToCompile ->
				// Add behavior to present total compiler progress on all
				// modules being compiled in the course of compiling target
				// module. This can be used to show a counter counting up:
				// "$bytesCompiled / $totalBytesToCompile"
			}
		builder.buildTargetThen(
			resolvedModuleName,
			progressReporter,
			globalProgressReporter,
			object : SimpleProblemHandler
			{
				override fun handleGeneric(
					problem: Problem,
					decider: (Boolean) -> Unit)
				{
					builder.stopBuildReason = "Build failed"
					val problemText = with(problem) {
						val adjustedLine = lineNumber - 5
						"$moduleName, line $adjustedLine:\n$this"
					}
					System.err.println(problemText)
					decider(false)
				}
			}
		) {
			done()
			isBuilding.set(false)
		}
		return true
	}

	/**
	 * The [RootNode]s in this [Project].
	 */
	val rootNodes = ConcurrentHashMap<String, RootNode>()

	/**
	 * The map of [ResolverReference.qualifiedName] to the corresponding
	 * [AvailNode].
	 */
	private val nodes = ConcurrentHashMap<String, AvailNode>()

	/**
	 * Walk all the [ModuleRoot]s populating all the [AvailNode]s ([nodes]) for
	 * this [Project].
	 *
	 * @param then
	 *   The lambda that accepts this project that is run after the entire
	 *   project is walked.
	 */
	fun walkRoots (then: (Project) -> Unit)
	{
		nodes.clear()
		rootNodes.clear()
		runtime.moduleRoots().forEach {
			walkRoot(it, then)
		}
	}

	/**
	 * Walk the provided roots.
	 */
	private fun walkRoot(root: ModuleRoot, then: (Project) -> Unit)
	{
		root.resolver.provideModuleRootTree({ refRoot ->
			val rootNode = RootNode(this, refRoot, root)
			rootNodes[rootNode.reference.qualifiedName] = rootNode
			nodes[refRoot.qualifiedName] = rootNode
			refRoot.walkChildrenThen(true, { visited ->
				when(visited.type)
				{
					ResourceType.MODULE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node = ModuleNode(parent, visited, this)
						nodes[visited.qualifiedName] = node
						parent.addChild(node)
					}
					ResourceType.REPRESENTATIVE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node = ModuleNode(parent, visited, this)
						nodes[visited.qualifiedName] = node
						parent.addChild(node)
					}
					ResourceType.PACKAGE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node =
							ModulePackageNode(parent, visited, this)
						nodes[visited.qualifiedName] = node
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
						nodes[visited.qualifiedName] = node
						parent.addChild(node)
					}
					ResourceType.RESOURCE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node =
							ResourceNode(parent, visited, this)
						nodes[visited.qualifiedName] = node
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
								if (node != null && node is ModuleNode)
								{
									entryPoints.forEach {
										node.entryPointNodes.add(
											EntryPointNode(node, it))
									}
								}
							}
							after()
						}) { then(this) }
				})
		}) { code, e ->
			System.err.println("Error: $code")
			e?.printStackTrace()
		}
	}

	/**
	 * Open the project screen for this [Project].
	 */
	@Composable
	fun OpenProject ()
	{
		val windowSize = rememberSaveable {
			WindowSize(width = 800.dp, height = 600.dp)
		}
		WorkspaceWindow(
			descriptor = descriptor,
			state = rememberWindowState(
				width = windowSize.width,
				height = windowSize.height))
		{
			Anvil.saveConfigToDisk()
			stopRuntime()
			Anvil.closeProject(id)
		}
	}

	override fun compareTo(other: Project): Int =
		descriptor.compareTo(other.descriptor)
}

/**
 * Generic "name" config file key. Used for:
 *  * [ProjectDescriptor.name]
 *  * [ProjectRoot.name]
 */
private const val NAME = "name"

/**
 * Generic "id" config file key. Used for:
 *  * [ProjectRoot.id]
 */
private const val ID = "id"

/**
 * [ProjectDescriptor.repositoryPath] config file key.
 */
private const val REPOS_FILE_PATH = "repositoriesPath"

/**
 * [ProjectDescriptor.renamesFileBody] config file key.
 */
private const val RENAMES_FILE_BODY = "renamesFileBody"

/**
 * [ProjectDescriptor.renamesFileBody] config file key.
 */
private const val RENAMES_FILE_PATH = "renamesFilePath"

/**
 * The [ProjectDescriptor.roots] config file key.
 */
private const val ROOTS = "roots"
