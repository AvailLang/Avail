/*
 * AvailBuilder.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package avail.builder

import avail.AvailRuntime
import avail.compiler.AvailCompiler
import avail.compiler.CompilerProgressReporter
import avail.compiler.FiberTerminationException
import avail.compiler.GlobalProgressReporter
import avail.compiler.ModuleHeader
import avail.compiler.ModuleImport
import avail.compiler.problems.Problem
import avail.compiler.problems.ProblemHandler
import avail.compiler.problems.ProblemType.EXECUTION
import avail.compiler.problems.ProblemType.PARSE
import avail.compiler.problems.ProblemType.TRACE
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.FiberDescriptor.Companion.commandPriority
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunctionForPhrase
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.addImportedNames
import avail.descriptor.module.A_Module.Companion.entryPoints
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.module.ModuleDescriptor.Companion.newModule
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.apparentSendName
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_Tuple.Companion.asSet
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.interpreter.execution.AvailLoader
import avail.interpreter.execution.Interpreter.Companion.debugWorkUnits
import avail.io.SimpleCompletionHandler
import avail.io.TextInterface
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.ModuleArchive
import avail.persistence.cache.Repository.ModuleCompilation
import avail.persistence.cache.Repository.ModuleVersion
import avail.serialization.Serializer
import avail.utility.Graph
import avail.utility.StackPrinter.Companion.trace
import avail.utility.safeWrite
import org.availlang.persistence.IndexedFile
import org.availlang.persistence.IndexedFile.Companion.appendCRC
import java.io.File
import java.lang.String.format
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections.synchronizedList
import java.util.Collections.synchronizedMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Collectors.joining
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.concurrent.read

/**
 * An `AvailBuilder` [compiles][AvailCompiler] and installs into an
 * [Avail&#32;runtime][AvailRuntime] a target [module][ModuleDescriptor] and
 * each of its dependencies.
 *
 * @property runtime
 *   The [runtime][AvailRuntime] into which the [builder][AvailBuilder] will
 *   install the target [module][ModuleDescriptor] and its dependencies.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct an `AvailBuilder` for the provided runtime.
 *
 * @param runtime
 *   The [AvailRuntime] in which to load modules and execute commands.
 */
@Suppress("unused")
class AvailBuilder constructor(val runtime: AvailRuntime)
{
	/** A lock for safely manipulating internals of this builder. */
	private val builderLock = ReentrantReadWriteLock()

	/**
	 * The [text&#32;interface][TextInterface] for the [builder][AvailBuilder]
	 * and downstream components.
	 */
	var textInterface: TextInterface = runtime.textInterface()

	/**
	 * A [Graph] of [ResolvedModuleName]s, representing the relationships
	 * between all modules currently loaded or involved in the current build
	 * action.  Modules are only added here after they have been locally traced
	 * successfully.
	 */
	val moduleGraph = Graph<ResolvedModuleName>()

	/**
	 * A map from each [ResolvedModuleName] to its currently loaded
	 * [LoadedModule].
	 */
	private val allLoadedModules =
		synchronizedMap(mutableMapOf<ResolvedModuleName, LoadedModule>())

	/** Whom to notify when modules load and unload. */
	private val subscriptions = mutableSetOf<(LoadedModule, Boolean)->Unit>()

	/**
	 * If not-`null`, an indication of why the current build should stop.
	 * Otherwise the current build should continue.
	 */
	private val privateStopBuildReason = AtomicReference<String>()

	/** A function for polling for abort requests. */
	val pollForAbort = { this.shouldStopBuild }

	/** How to handle problems during a build. */
	val buildProblemHandler: ProblemHandler = BuilderProblemHandler(
		this, "[%s]: module \"%s\", line %d:%n%s%n")

	/** How to handle problems during command execution. */
	private val commandProblemHandler: ProblemHandler = BuilderProblemHandler(
		this, "[%1\$s]: %4\$s%n")

	/**
	 * Record a new party to notify about module loading and unloading.
	 *
	 * @param subscription
	 *   What to invoke during loads and unloads.
	 */
	fun subscribeToModuleLoading(subscription: (LoadedModule, Boolean)->Unit)
	{
		subscriptions.add(subscription)
	}

	/**
	 * No longer notify the specified party about module loading and unloading.
	 *
	 * @param subscription
	 *   What to no longer invoke during loads and unloads.
	 */
	fun unsubscribeToModuleLoading(subscription: (LoadedModule, Boolean)->Unit)
	{
		subscriptions.remove(subscription)
	}

	/**
	 * Return a list of modules that are currently loaded.  The returned list is
	 * a snapshot of the state and does not change due to subsequent loads or
	 * unloads.
	 *
	 * @return
	 *   The list of modules currently loaded.
	 */
	fun loadedModulesCopy(): List<LoadedModule> =
		builderLock.read { allLoadedModules.values.toList() }

	/**
	 * Look up the currently loaded module with the specified
	 * [resolved&#32;module&#32;name][ResolvedModuleName].  Return `null` if the
	 * module is not currently loaded.
	 *
	 * @param resolvedModuleName
	 *   The name of the module to locate.
	 * @return
	 *   The loaded module or null.
	 */
	fun getLoadedModule(resolvedModuleName: ResolvedModuleName): LoadedModule? =
		builderLock.read { allLoadedModules[resolvedModuleName] }

	/**
	 * Record a freshly loaded module.  Notify subscribers.
	 *
	 * @param resolvedModuleName
	 *   The module's resolved name.
	 * @param loadedModule
	 *   The loaded module.
	 */
	internal fun putLoadedModule(
		resolvedModuleName: ResolvedModuleName,
		loadedModule: LoadedModule)
	{
		builderLock.safeWrite {
			allLoadedModules[resolvedModuleName] = loadedModule
			subscriptions.forEach { it(loadedModule, true) }
		}
	}

	/**
	 * Record the fresh unloading of a module with the given name.  Notify
	 * subscribers.
	 *
	 * @param resolvedModuleName
	 *   The unloaded module's resolved name.
	 */
	internal fun removeLoadedModule(resolvedModuleName: ResolvedModuleName)
	{
		builderLock.safeWrite {
			val loadedModule = allLoadedModules[resolvedModuleName]!!
			allLoadedModules.remove(resolvedModuleName)
			subscriptions.forEach { it(loadedModule, false) }
		}
	}

	/**
	 * Reconcile the [moduleGraph] against the loaded modules, removing any
	 * modules from the graph that are not currently loaded.
	 */
	internal fun trimGraphToLoadedModules()
	{
		for (moduleName in moduleGraph.vertices.toList())
		{
			if (getLoadedModule(moduleName) === null)
			{
				moduleGraph.exciseVertex(moduleName)
			}
		}
		assert(moduleGraph.vertexCount == allLoadedModules.size)
	}

	/**
	 * Check any invariants of the builder that should hold when it is idle.
	 */
	fun checkStableInvariants()
	{
		val loadedRuntimeModules = runtime.loadedModules()
		val moduleGraphSize = moduleGraph.vertexCount
		val allLoadedModulesSize = allLoadedModules.size
		val loadedRuntimeModulesSize = loadedRuntimeModules.mapSize
		assert(moduleGraphSize == allLoadedModulesSize)
		assert(moduleGraphSize == loadedRuntimeModulesSize)
		for (graphModuleName in moduleGraph.vertices.toList())
		{
			val qualifiedAvailName = stringFrom(graphModuleName.qualifiedName)
			assert(allLoadedModules[graphModuleName]!!.module.equals(
				loadedRuntimeModules.mapAt(qualifiedAvailName)))
		}
	}

	/**
	 * `true` iff the current build should stop, `false` otherwise.
	 */
	val shouldStopBuild: Boolean
		get() = privateStopBuildReason.get() !== null

	/**
	 * The reason why the current build should stop, or `null` if a stop is not
	 * currently requested.
	 */
	var stopBuildReason: String?
		get() = privateStopBuildReason.get()
		set(why)
		{
			builderLock.safeWrite {
				val old = privateStopBuildReason.getAndSet(why)
				if (debugWorkUnits)
				{
					println(
					"*****************************************************\n" +
					"*** stopBuildReason: " + old + " → " + why + "\n" +
					"*****************************************************")
				}
			}
		}

	/**
	 * Clear the indication that the build should stop, presumably in
	 * preparation for the next build.
	 */
	private fun clearShouldStopBuild() = privateStopBuildReason.set(null)

	/**
	 * Cancel the build at the next convenient stopping point for each module.
	 */
	fun cancel()
	{
		stopBuildReason = "Canceled"
	}

	/**
	 * Serialize the specified [module&#32;header][ModuleHeader] into the
	 * [module&#32;version][ModuleVersion].
	 *
	 * @param header
	 *   A module header.
	 * @param version
	 *   A module version.
	 */
	internal fun serialize(header: ModuleHeader, version: ModuleVersion)
	{
		val out = IndexedFile.ByteArrayOutputStream(1000)
		header.serializeHeaderOn(Serializer(out))
		appendCRC(out)
		version.putModuleHeader(out.toByteArray())
	}

	/**
	 * A LoadedModule holds state about what the builder knows about a currently
	 * loaded Avail module.
	 *
	 * @property name
	 *   The resolved name of this module.
	 * @property module
	 *   The actual [A_Module] that was plugged into the [AvailRuntime].
	 * @property version
	 *   This module's version, which corresponds to the source code.
	 * @property compilation
	 *   The [ModuleCompilation] which was loaded for this module.  This
	 *   indicates when the compilation happened, and where in the
	 *   [repository][Repository] we can find the [serialized][Serializer]
	 *   module content.
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new `LoadedModule` to represent information about an Avail
	 * module that has been loaded.
	 *
	 * @param name
	 *   The [name][ResolvedModuleName] of the module.
	 * @param sourceDigest
	 *   The module source's cryptographic digest.
	 * @param module
	 *   The actual [A_Module] loaded in the [AvailRuntime].
	 * @param version
	 *   The version of the module source.
	 * @param compilation
	 *   Information about the specific [ModuleCompilation] that is loaded.
	 */
	class LoadedModule constructor(
		val name: ResolvedModuleName,
		sourceDigest: ByteArray,
		internal val module: A_Module,
		internal val version: ModuleVersion,
		internal val compilation: ModuleCompilation)
	{
		/**
		 * The cryptographic [digest][ModuleArchive.digestForFile] of this
		 * module's source code when it was compiled.
		 */
		internal val sourceDigest: ByteArray = sourceDigest.clone()

		/**
		 * Whether this module has been flagged for deletion by the
		 * [BuildUnloader].
		 */
		internal var deletionRequest = false

		/**
		 * Answer the entry points defined by this loaded module.  Since the
		 * header structure does not depend on syntax declared in other modules,
		 * the entry points are a property of the [ModuleVersion].  That's the
		 * entity associated with particular module source code.
		 *
		 * @return
		 *   The [List] of [String]s that are entry points.
		 */
		val entryPoints: List<String> = version.getEntryPoints()
	}

	/**
	 * A `ModuleTree` is used to capture the structure of nested packages for
	 * use by the graph layout engine.
	 *
	 * @property node
	 *   The private node name to use in the graph layout.
	 * @property label
	 *   The textual label of the corresponding node in the graph layout.
	 * @property resolvedModuleName
	 *   The represented module's [resolved&#32;name][ResolvedModuleName].
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new `ModuleTree`.
	 *
	 * @param node
	 *   The node name.
	 * @param label
	 *   The label to present.
	 * @param resolvedModuleName
	 *   The represented [ResolvedModuleName].
	 */
	internal class ModuleTree constructor(
		val node: String,
		val label: String,
		val resolvedModuleName: ResolvedModuleName?)
	{
		/** The parent [ModuleTree], or null if this is a root. */
		internal var parent: ModuleTree? = null
			private set

		/**
		 * The list of children of this package or module (in which case it must
		 * be empty.
		 */
		val children = mutableListOf<ModuleTree>()

		/**
		 * Add a child to this node.
		 *
		 * @param child
		 *   The `ModuleTree` to add as a child.
		 */
		fun addChild(child: ModuleTree)
		{
			children.add(child)
			child.parent = this
		}

		/**
		 * Compute a quoted label based on the stored [label] [String],
		 * compensating for Graphviz's idiosyncrasies about delimiters. In
		 * particular, only quotes should be escaped with a backslash.  A little
		 * thought shows that this means a backslash can't be the last character
		 * of the quoted string, so we work around it by appending a space in
		 * that case, which will generally be close enough.
		 *
		 * @return
		 *   A [String] suitable for use as a label.
		 */
		val safeLabel: String
			get()
			{
				val addendum =
					if (label[label.length - 1] == '\\') " "
					else ""
				return "\"${label.replace("\"".toRegex(), "\\\"")}$addendum\""
			}

		/**
		 * Enumerate the modules in this tree, invoking the `enter` callback
		 * before visiting children, and invoking the `exit` callback after.
		 * Both callbacks take an [Int] indicating the current depth in the
		 * tree, relative to the initial passed value.
		 *
		 * @param enter
		 *   What to do before each node.
		 * @param exit
		 *   What to do after each node.
		 * @param depth
		 *   The depth of the current invocation.
		 */
		fun recursiveDo(
			enter: (ModuleTree, Int)->Unit,
			exit: (ModuleTree, Int)->Unit,
			depth: Int)
		{
			enter(this, depth)
			val nextDepth = depth + 1
			for (child in children)
			{
				child.recursiveDo(enter, exit, nextDepth)
			}
			exit(this, depth)
		}
	}

	/**
	 * Build the [target][ModuleDescriptor] and its dependencies.
	 *
	 * @param target
	 *   The [canonical&#32;name][ModuleName] of the module that the builder
	 *   must (recursively) load into the [AvailRuntime].
	 * @param localTracker
	 *   A [CompilerProgressReporter].
	 * @param globalTracker
	 *   A [GlobalProgressReporter].
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 * @param originalAfterAll
	 *   What to do after building everything.  This may run in another
	 *   [Thread], possibly long after this method returns.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun buildTargetThen(
		target: ModuleName,
		localTracker: CompilerProgressReporter,
		globalTracker: GlobalProgressReporter,
		problemHandler: ProblemHandler,
		originalAfterAll: ()->Unit)
	{
		val ran = AtomicBoolean(false)
		val safeAfterAll = {
			val old = ran.getAndSet(true)
			assert(!old)
			trimGraphToLoadedModules()
			originalAfterAll()
		}
		clearShouldStopBuild()
		BuildUnloader(this).unloadModified()
		if (shouldStopBuild)
		{
			safeAfterAll()
			return
		}
		BuildTracer(this).traceThen(target, problemHandler) {
			if (moduleGraph.isCyclic)
			{
				problemHandler.handle(
					object : Problem(
						target,
						1,
						0,
						TRACE,
						"Cycle detected in ancestor modules: {0}",
						moduleGraph.firstCycle.stream()
							.map { it.qualifiedName }
							.collect(joining("\n\t", "\n\t", "")))
					{
						override fun abortCompilation()
						{
							stopBuildReason =
								"Module graph has cyclic dependency"
						}
					})
			}
			if (shouldStopBuild)
			{
				safeAfterAll()
				return@traceThen
			}
			val buildLoader = BuildLoader(
				this, localTracker, globalTracker, problemHandler)
			buildLoader.loadThen(safeAfterAll)
		}
	}

	/**
	 * Build the [target][ModuleDescriptor] and its dependencies. Block the
	 * current [Thread] until it's done.
	 *
	 * @param target
	 *   The [canonical&#32;name][ModuleName] of the module that the builder
	 *   must (recursively) load into the [AvailRuntime].
	 * @param localTracker
	 *   A [CompilerProgressReporter].
	 * @param globalTracker
	 *   A [GlobalProgressReporter].
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	fun buildTarget(
		target: ModuleName,
		localTracker: CompilerProgressReporter,
		globalTracker: GlobalProgressReporter,
		problemHandler: ProblemHandler)
	{
		val semaphore = Semaphore(0)
		buildTargetThen(
			target,
			localTracker,
			globalTracker,
			problemHandler)
		{
			semaphore.release()
		}
		semaphore.acquireUninterruptibly()
	}

	/**
	 * Unload the [target&#32;module][ModuleDescriptor] and its dependents.  If
	 * `null` is provided, unload all modules.
	 *
	 * @param target
	 *   The [resolved&#32;name][ResolvedModuleName] of the module to be
	 *   unloaded, or `null` to unload all modules.
	 */
	fun unloadTarget(target: ResolvedModuleName?)
	{
		clearShouldStopBuild()
		BuildUnloader(this).unload(target)
	}

	/**
	 * Generate Stacks documentation for the [target][ModuleDescriptor] and its
	 * dependencies.
	 *
	 * @param target
	 *   The `ModuleName canonical name` of the module for which the
	 *   `AvailBuilder builder` must (recursively) generate documentation.
	 * @param documentationPath
	 *   The [path][Path] to the output
	 *   [directory][BasicFileAttributes.isDirectory] for Stacks documentation
	 *   and data files.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	fun generateDocumentation(
		target: ModuleName,
		documentationPath: Path,
		problemHandler: ProblemHandler)
	{
		clearShouldStopBuild()
		val tracer = BuildTracer(this)
		tracer.traceThen(target, problemHandler) {
			val documentationTracer =
				DocumentationTracer(this, documentationPath)
			if (!shouldStopBuild)
			{
				documentationTracer.load(problemHandler)
			}
			if (!shouldStopBuild)
			{
				documentationTracer.generate(target, problemHandler)
			}
			trimGraphToLoadedModules()
		}
	}

	/**
	 * Generate a graph.
	 *
	 * @param target
	 *   The resolved name of the module whose ancestors to trace.
	 * @param destinationFile
	 *   Where to write the `.gv` **dot** format file.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	fun generateGraph(
		target: ResolvedModuleName,
		destinationFile: File,
		problemHandler: ProblemHandler)
	{
		clearShouldStopBuild()
		val tracer = BuildTracer(this)
		try
		{
			tracer.traceThen(target, problemHandler) {
				val graphTracer = GraphTracer(
					this, target, destinationFile)
				if (!shouldStopBuild)
				{
					graphTracer.traceGraph()
				}
			}
		}
		finally
		{
			trimGraphToLoadedModules()
		}
	}

	/**
	 * Scan all module files in all visible source directories, passing each
	 * [ResolvedModuleName] and corresponding [ModuleVersion] to the provided
	 * function.
	 *
	 * Note that the action may be invoked in multiple [Thread]s simultaneously,
	 * so the client may need to provide suitable synchronization.
	 *
	 * The method may return before tracing has completed, but `afterAll` will
	 * eventually be invoked in some [Thread] after all modules have been
	 * processed.
	 *
	 * @param action
	 *   What to do with each module version.  A function will be passed,
	 *   which should be evaluated to indicate the module has been processed.
	 * @param afterAll
	 *   What to do after all of the modules have been processed.
	 */
	fun traceDirectoriesThen(
		action: (ResolvedModuleName, ModuleVersion, ()->Unit)->Unit,
		afterAll: ()->Unit)
	{
		BuildDirectoryTracer(this, afterAll).traceAllModuleHeaders(action)
	}

	/**
	 * An `EntryPoint` represents a compiled command. It is used to disambiguate
	 * commands.
	 *
	 * @property moduleName
	 *   The [module&#32;name][ResolvedModuleName] of the [module][LoadedModule]
	 *   that declares the entry point.
	 * @property entryPointName
	 *   The name of the entry point.
	 * @property phrase
	 *   The compiled [phrase][A_Phrase] that sends this entry point.
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new `CompiledCommand`.
	 *
	 * @param moduleName
	 *   The [module&#32;name][ResolvedModuleName] of the [module][LoadedModule]
	 *   that declares the entry point.
	 * @param entryPointName
	 *   The name of the entry point.
	 * @param phrase
	 *   The compiled [phrase][A_Phrase] that sends this entry point.
	 */
	class CompiledCommand internal constructor(
		val moduleName: ResolvedModuleName,
		private val entryPointName: String,
		val phrase: A_Phrase)
	{
		override fun toString(): String =
			"${moduleName.qualifiedName} : $entryPointName"
	}

	/**
	 * Attempt to unambiguously parse a command.  Each currently loaded module
	 * that defines at least one entry point takes a shot at parsing the
	 * command.  If more than one is successful, report the ambiguity via the
	 * `onFailure` continuation.  If none are successful, report the failure.
	 * If there was exactly one, compile it into a function and invoke it in a
	 * new fiber.  If the function evaluation succeeds, run the `onSuccess`
	 * continuation with the function's result, except that if the function has
	 * static type ⊤ always pass `nil` instead of the actual value returned by
	 * the function.  If the function evaluation failed, report the failure.
	 *
	 * @param command
	 *   The command to attempt to parse and run.
	 * @param onAmbiguity
	 *   What to do if the entry point is ambiguous. Accepts a [List] of
	 *   [compiled&#32;commands][CompiledCommand] and the function to invoke
	 *   with the selected command (or `null` if no command should be run).
	 * @param onSuccess
	 *   What to do if the command parsed and ran to completion.  It should be
	 *   passed both the result of execution and a cleanup function to invoke
	 *   with a post-cleanup continuation.
	 * @param onFailure
	 *   What to do otherwise.
	 */
	fun attemptCommand(
		command: String,
		onAmbiguity: (List<CompiledCommand>, (CompiledCommand?)->Unit)->Unit,
		onSuccess: (AvailObject, (()->Unit)->Unit)->Unit,
		onFailure: ()->Unit)
	{
		clearShouldStopBuild()
		runtime.execute(commandPriority) {
			scheduleAttemptCommand(
				command, onAmbiguity, onSuccess, onFailure)
		}
	}

	/**
	 * Schedule an attempt to unambiguously parse a command. Each currently
	 * loaded module that defines at least one entry point takes a shot at
	 * parsing the command.  If more than one is successful, report the
	 * ambiguity via the `onFailure` continuation.  If none are successful,
	 * report the failure.  If there was exactly one, compile it into a function
	 * and invoke it in a new fiber.  If the function evaluation succeeds, run
	 * the `onSuccess` continuation with the function's result, except that if
	 * the function has static type ⊤ always pass `nil` instead of the actual
	 * value returned by the function.  If the function evaluation failed,
	 * report the failure.
	 *
	 * @param command
	 *   The command to attempt to parse and run.
	 * @param onAmbiguity
	 *   What to do if the entry point is ambiguous. Accepts a [List] of
	 *   [compiled&#32;commands][CompiledCommand] and the function to invoke
	 *   with the selected command (or `null` if no command should be run).
	 * @param onSuccess
	 *   What to do if the command parsed and ran to completion.  It should be
	 *   passed both the result of execution and a cleanup function to invoke
	 *   with a post-cleanup continuation.
	 * @param onFailure
	 *   What to do otherwise.
	 */
	private fun scheduleAttemptCommand(
		command: String,
		onAmbiguity: (List<CompiledCommand>, (CompiledCommand?)->Unit)->Unit,
		onSuccess: (AvailObject, (()->Unit)->Unit)->Unit,
		onFailure: ()->Unit)
	{
		val modulesWithEntryPoints = mutableSetOf<LoadedModule>()
		for (loadedModule in loadedModulesCopy())
		{
			if (loadedModule.entryPoints.isNotEmpty())
			{
				modulesWithEntryPoints.add(loadedModule)
			}
		}
		if (modulesWithEntryPoints.isEmpty())
		{
			val problem = object : Problem(
				null,
				1,
				1,
				EXECUTION,
				"No entry points are defined by loaded modules")
			{
				override fun abortCompilation() = onFailure()
			}
			commandProblemHandler.handle(problem)
			return
		}

		val allSolutions =
			synchronizedMap(mutableMapOf<LoadedModule, List<A_Phrase>>())
		val allCleanups =
			synchronizedList(mutableListOf<(()->Unit)->Unit>())
		val allProblems =
			synchronizedMap(mutableMapOf<LoadedModule, MutableList<Problem>>())
		val outstanding = AtomicInteger(modulesWithEntryPoints.size)
		val decrement = {
			if (outstanding.decrementAndGet() == 0)
			{
				processParsedCommand(
					allSolutions, // no longer changing
					allProblems, // no longer changing
					onAmbiguity,
					onSuccess,
					parallelCombine(allCleanups), // no longer changing
					onFailure)
			}
		}

		for (loadedModule in modulesWithEntryPoints)
		{
			val module = newModule(
				stringFrom(
					loadedModule.module.moduleNameNative + " (command)"))
			val loader = AvailLoader(runtime, module, runtime.textInterface())
			val moduleImport = ModuleImport.extend(loadedModule.module)
			val header = ModuleHeader(loadedModule.name)
			header.importedModules.add(moduleImport)
			header.applyToModule(loader)
			module.addImportedNames(
				loadedModule.module.entryPoints.valuesAsTuple.asSet)
			val compiler = AvailCompiler(
				header,
				module,
				stringFrom(command),
				textInterface,
				pollForAbort,
				{ _, _, _, _, _ -> },
				object : BuilderProblemHandler(this, "«collection only»")
				{
					override fun handleGeneric(
						problem: Problem,
						decider: (Boolean)->Unit)
					{
						// Clone the problem message into a new problem to avoid
						// running any cleanup associated with aborting the
						// problem a second time.
						val copy = object : Problem(
							problem.moduleName,
							problem.lineNumber,
							problem.characterInFile,
							problem.type,
							"{0}",
							problem.toString())
						{
							override fun abortCompilation()
							{
								// Do nothing.
							}
						}
						allProblems.compute(loadedModule) { _, oldV ->
							val v = oldV ?: mutableListOf()
							v.add(copy)
							v
						}
						decider(false)
					}

					@Suppress("RedundantLambdaArrow")
					override fun handleInternal(
						problem: Problem,
						decider: (Boolean)->Unit
					) {
						SimpleCompletionHandler<Int>(
							{ handleGeneric(problem, decider) },
							{ handleGeneric(problem, decider) }
						).guardedDo {
							textInterface.errorChannel.write(
								problem.toString(), Unit, handler)
						}
					}

					@Suppress("RedundantLambdaArrow")
					override fun handleExternal(
						problem: Problem,
						decider: (Boolean)->Unit)
					{
						// Same as handleInternal (2015.04.24)
						SimpleCompletionHandler<Int>(
							{ handleGeneric(problem, decider) },
							{ handleGeneric(problem, decider) }
						).guardedDo {
							textInterface.errorChannel.write(
								problem.toString(), Unit, handler)
						}
					}
				})
			compiler.parseCommand(
				{ solutions, cleanup ->
					allSolutions[loadedModule] = solutions
					allCleanups.add(cleanup)
					decrement()
				},
				decrement)
		}
	}

	/**
	 * Given a [Collection] of actions, each of which expects a continuation
	 * (called the post-action activity) that instructs it on how to proceed
	 * when it has completed, produce a single action that evaluates this
	 * collection in parallel and defers the post-action activity until every
	 * member has completed.
	 *
	 * @param actions
	 *   A collection of actions.
	 * @return
	 *   The combined action.
	 */
	private fun parallelCombine(
		actions: Collection<(()->Unit)->Unit>): (()->Unit)->Unit
	{
		return { postAction ->
			val count = AtomicInteger(actions.size)
			val decrement = { if (count.decrementAndGet() == 0) postAction() }
			actions.forEach { action ->
				runtime.execute(commandPriority) { action(decrement) }
			}
		}
	}

	/**
	 * Process a parsed command, executing it if there is a single unambiguous
	 * entry point send.
	 *
	 * @param solutions
	 *   A [map][Map] from [loaded][LoadedModule] to the [solutions][A_Phrase]
	 *   that they produced.
	 * @param problems
	 *   A map from loaded modules to the [problems][Problem] that they
	 *   encountered.
	 * @param onAmbiguity
	 *   What to do if the entry point is ambiguous. Accepts a [List] of
	 *   [compiled&#32;commands][CompiledCommand] and the continuation to invoke
	 *   with the selected command (or `null` if no command should be run).
	 * @param onSuccess
	 *   What to do with the result of a successful unambiguous command.
	 * @param postSuccessCleanup
	 *   How to cleanup after running a successful unambiguous command.
	 * @param onFailure
	 *   What to do after a failure.
	 */
	private fun processParsedCommand(
		solutions: Map<LoadedModule, List<A_Phrase>>,
		problems: Map<LoadedModule, List<Problem>>,
		onAmbiguity: (List<CompiledCommand>, (CompiledCommand?)->Unit)->Unit,
		onSuccess: (AvailObject, (()->Unit)->Unit)->Unit,
		postSuccessCleanup: (()->Unit)->Unit,
		onFailure: ()->Unit)
	{
		if (solutions.isEmpty())
		{
			// There were no solutions, so report every problem that was
			// encountered.  Actually, choose the modules that tied for the
			// deepest parse, and only show those problems.
			var deepestPosition = Long.MIN_VALUE
			val deepestProblems = mutableListOf<Problem>()
			for ((_, value) in problems)
			{
				for (problem in value)
				{
					if (problem.characterInFile > deepestPosition)
					{
						deepestPosition = problem.characterInFile
						deepestProblems.clear()
					}
					if (problem.characterInFile == deepestPosition)
					{
						deepestProblems.add(problem)
					}
				}
			}
			for (problem in deepestProblems)
			{
				commandProblemHandler.handle(problem)
			}
			onFailure()
			return
		}
		// Filter the solutions to invocations of entry points.
		val commands = mutableListOf<CompiledCommand>()
		for ((key, value) in solutions)
		{
			val moduleEntryPoints = key.entryPoints
			for (solution in value)
			{
				if (solution.isInstanceOfKind(SEND_PHRASE.mostGeneralType))
				{
					val name = solution.apparentSendName
					val nameString = name.atomName.asNativeString()
					if (moduleEntryPoints.contains(nameString))
					{
						commands.add(CompiledCommand(
							key.name, nameString, solution))
					}
				}
			}
		}
		// If there were no commands, then report a problem.
		if (commands.isEmpty())
		{
			val problem = object : Problem(
				null,
				1,
				1,
				PARSE,
				"The command could be parsed, but not as an invocation of " +
					"an entry point.")
			{
				override fun abortCompilation() = onFailure()
			}
			commandProblemHandler.handle(problem)
			return
		}

		val unambiguous = { command: CompiledCommand ->
			val phrase = command.phrase
			val function = createFunctionForPhrase(phrase, nil, 1)
			val fiber = newFiber(
				function.kind().returnType,
				runtime,
				textInterface,
				commandPriority)
			{
				stringFrom("Running command: $phrase")
			}
			var fiberGlobals = fiber.fiberGlobals
			fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
				CLIENT_DATA_GLOBAL_KEY.atom, emptyMap, true)
			fiber.fiberGlobals = fiberGlobals
			fiber.setSuccessAndFailure(
				{ result -> onSuccess(result, postSuccessCleanup) },
				{ e ->
					if (e is FiberTerminationException)
					{
						onFailure()
					}
					else
					{
						val problem = object : Problem(
							null,
							1,
							1,
							EXECUTION,
							"Error executing command:{0}\n{1}",
							if (e.message !== null) " " + e.message else "",
							trace(e))
						{
							override fun abortCompilation() = onFailure()
						}
						commandProblemHandler.handle(problem)
					}
				})
			runtime.runOutermostFunction(fiber, function, emptyList())
		}

		// If the command was unambiguous, then go ahead and run it.
		if (commands.size == 1)
		{
			unambiguous(commands[0])
			return
		}

		// Otherwise, report the possible commands for disambiguation.
		onAmbiguity(commands) { choice ->
			when (choice) {
				null -> SimpleCompletionHandler<Int>(
					{ onFailure() },
					{ onFailure() }  /* Ignore I/O error */
				).guardedDo {
					textInterface.errorChannel.write(
						"Action was cancelled by user", Unit, handler)
				}
				else -> unambiguous(choice)
			}
		}
	}

	companion object
	{
		/** The [logger][Logger]. */
		private val logger = Logger.getLogger(
			AvailBuilder::class.java.name)

		/**
		 * Whether to debug the builder.
		 */
		private const val debugBuilder = false

		/**
		 * Log the specified message if [debugging][debugBuilder] is enabled.
		 *
		 * @param level
		 *   The [severity&#32;level][Level].
		 * @param format
		 *   The format string.
		 * @param args
		 *   The format arguments.
		 */
		internal fun log(level: Level, format: String, vararg args: Any)
		{
			@Suppress("ConstantConditionIf")
			if (debugBuilder)
			{
				if (logger.isLoggable(level))
				{
					logger.log(level, format(format, *args))
				}
			}
		}

		/**
		 * Log the specified message if [debugging][debugBuilder] is enabled.
		 *
		 * @param level
		 *   The [severity&#32;level][Level].
		 * @param exception
		 *   The [exception][Throwable] that motivated this log entry.
		 * @param format
		 *   The format string.
		 * @param args
		 *   The format arguments.
		 */
		internal fun log(
			level: Level,
			exception: Throwable,
			format: String,
			vararg args: Any)
		{
			@Suppress("ConstantConditionIf")
			if (debugBuilder)
			{
				if (logger.isLoggable(level))
				{
					logger.log(level, format(format, *args), exception)
				}
			}
		}

		/**
		 * The maximum age, in milliseconds, that changes should be left
		 * uncommitted in the repository.  A higher value saves space by causing
		 * the updated metadata to be rewritten at a slower rate, but the next
		 * build may have to repeat a bit more work if the previous build
		 * attempt failed before its data could be committed.
		 */
		internal const val maximumStaleRepositoryMs = 2000L
	}
}
