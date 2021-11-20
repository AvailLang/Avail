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

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowSize
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import avail.anvil.Anvil
import avail.anvil.Anvil.VERSION
import avail.anvil.Anvil.defaults
import avail.AvailRuntime
import avail.anvil.screens.ProjectWindow
import avail.anvil.streams.AnvilOutputStream
import avail.anvil.streams.StreamStyle
import avail.anvil.streams.StyledStreamEntry
import avail.anvil.utilities.createAvailRuntime
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
import avail.resolver.ModuleRootResolver
import avail.resolver.ModuleRootResolverRegistry
import avail.resolver.ResolverReference
import avail.resolver.ResourceType
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONValue
import org.availlang.json.JSONWriter
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
		windowState: WindowState,
		fileManager: FileManager = FileManager(),
		then: (Project) -> Unit = {}
	): Project =
		Project(this, ProjectState(windowState), fileManager).apply {
			initializeRootsThen({
				walkRoots(then)
			},
			{
//				TODO do something with errors
			})

		}

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
		projectState: ProjectState,
		fileManager: FileManager = FileManager(),
		then: (Project) -> Unit = {}
	): Project =
		Project(this, projectState, fileManager).apply {
			initializeRootsThen({
				walkRoots(then)
			},
				{
//				TODO do something with errors
				})

		}

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(VERSION) { write(ProjectState.CURRENT_SERIALIZATION_VERSION) }
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
		 * The current JSON serialization/deserialization version of
		 * [ProjectDescriptor].
		 */
		const val CURRENT_SERIALIZATION_VERSION = 1

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
			val version = jsonObject.getNumber(VERSION).int
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
 * The state of the project window that should be restored upon opening.
 *
 * @author Richard Arriaga
 *
 * @property windowState
 *   The [WindowState] of the project window.
 */
data class ProjectState constructor(var windowState: WindowState): JSONFriendly
{
	/**
	 * The set of [AvailNode]s that were expanded at Anvil close.
	 */
	internal val expanded = mutableSetOf<String>()

	/**
	 * The horizontal [ScrollState] of the window.
	 */
	internal var horizontalScroll = ScrollState(0)

	/**
	 * The vertical [ScrollState] of the window.
	 */
	internal var verticalScroll = ScrollState(0)

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(VERSION) { write(CURRENT_SERIALIZATION_VERSION) }
		writer.at(PLACEMENT) {
			write(windowState.placement.ordinal)
		}
		writer.at(ISMINIMIZED) {
			write(windowState.isMinimized)
		}
		writer.at(POSITIONX) {
			write(windowState.position.x.value)
		}
		writer.at(POSITIONY) {
			write(windowState.position.y.value)
		}
		writer.at(WIDTH) {
			write(windowState.size.width.value)
		}
		writer.at(HEIGHT) {
			write(windowState.size.height.value)
		}
		writer.at(HORIZONTAL) {
			write(horizontalScroll.value)
		}
		writer.at(VERTICAL) {
			write(verticalScroll.value)
		}
	}

	companion object
	{
		/**
		 * The current JSON serialization/deserialization version of
		 * [ProjectState].
		 */
		const val CURRENT_SERIALIZATION_VERSION = 1

		/**
		 * Load the [ProjectState] from the provided [JSONObject].
		 *
		 * @param jsonObject
		 *   The JSON object that contains the state to read.
		 */
		fun fromJson (jsonObject: JSONObject): ProjectState
		{
			val version = jsonObject.getNumber(VERSION).int
			val placement =
				WindowPlacement.values()[
					jsonObject.getNumber(PLACEMENT).int]
			val isMinimized =
				jsonObject.getBoolean(ISMINIMIZED)
			val x = jsonObject.getNumber(POSITIONX).float
			val y = jsonObject.getNumber(POSITIONY).float
			val width = jsonObject.getNumber(WIDTH).float
			val height = jsonObject.getNumber(HEIGHT).float
			val vertical = jsonObject.getNumber(VERTICAL).int
			val horizontal = jsonObject.getNumber(HORIZONTAL).int
			val state = ProjectState(WindowState(
				placement,
				isMinimized,
				WindowPosition(x.dp, y.dp),
				WindowSize(width.dp, height.dp)))
			state.verticalScroll = ScrollState(vertical)
			state.horizontalScroll = ScrollState(horizontal)
			jsonObject.getArray(EXPANDED).forEach {
				state.expanded.add((it as JSONValue).string)
			}
			return state
		}

		// Project State JSON Keys
		private const val PLACEMENT = "placement"
		private const val ISMINIMIZED = "isMinimized"
		private const val POSITIONX = "positionX"
		private const val POSITIONY = "positionY"
		private const val WIDTH = "width"
		private const val HEIGHT = "height"
		private const val VERTICAL = "vertical"
		private const val HORIZONTAL = "horizontal"
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
	val projectState: ProjectState,
	val fileManager: FileManager = defaults.defaultFileManager
): Comparable<Project>, JSONFriendly
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
	 * The output stream to write to.
	 */
	val output = AnvilOutputStream()

	fun outputAsAnnotatedString (isDark: Boolean): AnnotatedString =
		output.toAnvilInputStream().toAnnotatedString(isDark)

	/**
	 * The list of String errors received while resolving module roots for this
	 * project.
	 */
	private val moduleRootResolutionErrors = mutableListOf<String>()

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(ID) { write(id) }
		projectState.writeTo(writer)
		writer.at(EXPANDED)
		{
			startArray()
			nodes.forEach { (t, u) ->
				if (u.isExpanded)
				{
					write(t)
				}
			}
			endArray()
		}
	}

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
		output.write(StyledStreamEntry(
			StreamStyle.COMMAND, "Build $qualifiedModuleName\n"))
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
		val start = System.currentTimeMillis()
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
					output.write(
						StyledStreamEntry(StreamStyle.ERR, problemText))
					decider(false)
				}
			}
		) {
			done()
			output.write(StyledStreamEntry(
				StreamStyle.INFO,
				"Build ended $qualifiedModuleName\n (${System.currentTimeMillis() - start} ms)"))
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
					Thread.sleep(3000)
					then(this)
				}
			}
		}
	}

	/**
	 * Walk the provided roots.
	 */
	private fun walkRoot(root: ModuleRoot, then: (Project) -> Unit)
	{
		root.resolver.provideModuleRootTree({ refRoot ->
			val rootNode = RootNode(this, refRoot, root)
			rootNode.isExpanded = projectState.expanded
				.contains(refRoot.qualifiedName)
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
						node.isExpanded = projectState.expanded
							.contains(visited.qualifiedName)
						parent.addChild(node)
					}
					ResourceType.REPRESENTATIVE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node = ModuleNode(parent, visited, this)
						nodes[visited.qualifiedName] = node
						node.isExpanded = projectState.expanded
							.contains(visited.qualifiedName)
						parent.addChild(node)
					}
					ResourceType.PACKAGE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node =
							ModulePackageNode(parent, visited, this)
						nodes[visited.qualifiedName] = node
						node.isExpanded = projectState.expanded
							.contains(visited.qualifiedName)
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
						node.isExpanded = projectState.expanded
							.contains(visited.qualifiedName)
						parent.addChild(node)
					}
					ResourceType.RESOURCE ->
					{
						val parent =
							nodes[visited.parentName]!!
						val node =
							ResourceNode(parent, visited, this)
						nodes[visited.qualifiedName] = node
						node.isExpanded = projectState.expanded
							.contains(visited.qualifiedName)
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

	/**
	 * Open the project screen for this [Project].
	 */
	@Composable
	fun OpenProject ()
	{
		projectState.windowState = rememberWindowState(
			placement = projectState.windowState.placement,
			isMinimized = projectState.windowState.isMinimized,
			position = projectState.windowState.position,
			width = projectState.windowState.size.width,
			height = projectState.windowState.size.height)
		ProjectWindow(this)
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

/**
 * The [ProjectDescriptor.roots] config file key.
 */
private const val EXPANDED = "expanded"
