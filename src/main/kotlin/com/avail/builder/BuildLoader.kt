/*
 * BuildLoader.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.builder

import com.avail.AvailRuntime
import com.avail.AvailRuntimeSupport.captureNanos
import com.avail.builder.AvailBuilder.Companion.appendCRC
import com.avail.builder.AvailBuilder.LoadedModule
import com.avail.compiler.AvailCompiler
import com.avail.compiler.CompilerProgressReporter
import com.avail.compiler.GlobalProgressReporter
import com.avail.compiler.ModuleHeader
import com.avail.compiler.problems.Problem
import com.avail.compiler.problems.ProblemHandler
import com.avail.compiler.problems.ProblemType.EXECUTION
import com.avail.descriptor.A_Function
import com.avail.descriptor.FiberDescriptor.loaderPriority
import com.avail.descriptor.FiberDescriptor.newLoaderFiber
import com.avail.descriptor.ModuleDescriptor
import com.avail.descriptor.ModuleDescriptor.newModule
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.interpreter.AvailLoader
import com.avail.interpreter.AvailLoader.Phase
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Interpreter.runOutermostFunction
import com.avail.persistence.IndexedRepositoryManager
import com.avail.persistence.IndexedRepositoryManager.*
import com.avail.serialization.Deserializer
import com.avail.serialization.MalformedSerialStreamException
import com.avail.serialization.Serializer
import com.avail.utility.MutableLong
import com.avail.utility.Nulls.stripNull
import com.avail.utility.evaluation.Combinator.recurse
import java.io.ByteArrayOutputStream
import java.lang.String.format
import java.util.*
import java.util.Collections.emptyList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import kotlin.math.min

/**
 * Used for parallel-loading modules in the [module
 * graph][AvailBuilder.moduleGraph].
 *
 * @property availBuilder
 *   The [AvailBuilder] for which we're loading.
 * @property localTracker
 *   The [CompilerProgressReporter] to invoke when a top-level statement is
 *   unambiguously parsed.
 * @property globalTracker
 *   The [GlobalProgressReporter] to invoke when a top-level statement is
 *   unambiguously parsed.
 * @property problemHandler
 *   The [ProblemHandler] to use when compilation [Problem]s are encountered.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `BuildLoader`.
 *
 * @param availBuilder
 *   The [AvailBuilder] for which to load modules.
 * @param localTracker
 *   The [CompilerProgressReporter] to invoke when a top-level statement is
 *   unambiguously parsed.
 * @param globalTracker
 *   The [GlobalProgressReporter] to invoke when a top-level statement is
 *   unambiguously parsed.
 * @param problemHandler
 *   How to handle or report [Problem]s that arise during the build.
 */
internal class BuildLoader constructor(
	val availBuilder: AvailBuilder,
	private val localTracker: CompilerProgressReporter,
	private val globalTracker: GlobalProgressReporter,
	private val problemHandler: ProblemHandler)
{
	/** The size, in bytes, of all source files that will be built.  */
	private val globalCodeSize: Long

	/** The number of bytes compiled so far.  */
	private val bytesCompiled = AtomicLong(0L)

	init
	{
		var size = 0L
		for (mod in availBuilder.moduleGraph.vertices())
		{
			size += mod.moduleSize
		}
		globalCodeSize = size
	}

	/**
	 * Schedule a build of the specified [module][ModuleDescriptor], on the
	 * assumption that its predecessors have already been built.
	 *
	 * @param target
	 *   The [resolved name][ResolvedModuleName] of the module that should be
	 *   loaded.
	 * @param completionAction
	 *   The action to perform after this module
	 */
	private fun scheduleLoadModule(
		target: ResolvedModuleName,
		completionAction: ()->Unit)
	{
		// Avoid scheduling new tasks if an exception has happened.
		if (availBuilder.shouldStopBuild)
		{
			postLoad(target, 0L)
			completionAction()
			return
		}
		availBuilder.runtime.execute(loaderPriority) {
			if (availBuilder.shouldStopBuild)
			{
				// An exception has been encountered since the earlier check.
				// Exit quickly.
				completionAction()
			}
			else
			{
				loadModule(target, completionAction)
			}
		}
	}

	/**
	 * Load the specified [module][ModuleDescriptor] into the [Avail
	 * runtime][AvailRuntime]. If a current compiled module is available from
	 * the [repository][IndexedRepositoryManager], then simply load it.
	 * Otherwise, [compile][AvailCompiler] the module, store it into the
	 * repository, and then load it.
	 *
	 * Note that the predecessors of this module must have already been loaded.
	 *
	 * @param moduleName
	 *   The [resolved name][ResolvedModuleName] of the module that should be
	 *   loaded.
	 * @param completionAction
	 *   What to do after loading the module successfully.
	 */
	private fun loadModule(
		moduleName: ResolvedModuleName,
		completionAction: ()->Unit)
	{
		globalTracker(bytesCompiled.get(), globalCodeSize)
		// If the module is already loaded into the runtime, then we must not
		// reload it.
		val isLoaded = availBuilder.getLoadedModule(moduleName) != null

		assert(isLoaded == availBuilder.runtime.includesModuleNamed(
			stringFrom(moduleName.qualifiedName)))
		if (isLoaded)
		{
			// The module is already loaded.
			AvailBuilder.log(
				Level.FINEST,
				"Already loaded: %s",
				moduleName.qualifiedName)
			postLoad(moduleName, 0L)
			completionAction()
		}
		else
		{
			val repository = moduleName.repository
			val archive = repository.getArchive(
				moduleName.rootRelativeName)
			val digest = archive.digestForFile(moduleName)
			val versionKey = ModuleVersionKey(moduleName, digest)
			val version = archive.getVersion(versionKey)
				?: error("Version should have been populated during tracing")
			val imports = version.imports
			val resolver = availBuilder.runtime.moduleNameResolver()
			val loadedModulesByName = HashMap<String, LoadedModule>()
			for (localName in imports)
			{
				val resolvedName: ResolvedModuleName
				try
				{
					resolvedName = resolver.resolve(
						moduleName.asSibling(localName), moduleName)
				}
				catch (e: UnresolvedDependencyException)
				{
					availBuilder.stopBuildReason =
						format(
							"A module predecessor was malformed or absent: "
							+ "%s -> %s\n",
							moduleName.qualifiedName,
							localName)
					completionAction()
					return
				}

				val loadedPredecessor =
					availBuilder.getLoadedModule(resolvedName)!!
				loadedModulesByName[localName] = loadedPredecessor
			}
			val predecessorCompilationTimes = LongArray(imports.size)
			for (i in predecessorCompilationTimes.indices)
			{
				val loadedPredecessor = loadedModulesByName[imports[i]]!!
				predecessorCompilationTimes[i] =
					loadedPredecessor.compilation.compilationTime
			}
			val compilationKey =
				ModuleCompilationKey(predecessorCompilationTimes)
			val compilation = version.getCompilation(compilationKey)
			if (compilation != null)
			{
				// The current version of the module is already compiled, so
				// load the repository's version.
				loadRepositoryModule(
					moduleName,
					version,
					compilation,
					versionKey.sourceDigest,
					completionAction)
			}
			else
			{
				// Compile the module and cache its compiled form.
				compileModule(moduleName, compilationKey, completionAction)
			}
		}
	}

	/**
	 * Load the specified [module][ModuleDescriptor] from the
	 * [repository][IndexedRepositoryManager] and into the
	 * [runtime][AvailRuntime].
	 *
	 * Note that the predecessors of this module must have already been loaded.
	 *
	 * @param moduleName
	 *   The [resolved name][ResolvedModuleName] of the module that should be
	 *   loaded.
	 * @param version
	 *   The [ModuleVersion] containing information about this module.
	 * @param compilation
	 *   The [ModuleCompilation] containing information about the particular
	 *   stored compilation of this module in the repository.
	 * @param sourceDigest
	 *   The cryptographic digest of the module's source code.
	 * @param completionAction
	 *   What to do after loading the module successfully.
	 */
	private fun loadRepositoryModule(
		moduleName: ResolvedModuleName,
		version: ModuleVersion,
		compilation: ModuleCompilation,
		sourceDigest: ByteArray,
		completionAction: ()->Unit)
	{
		localTracker(moduleName, moduleName.moduleSize, 0L)
		val module = newModule(
			stringFrom(moduleName.qualifiedName))
		val availLoader = AvailLoader(module, availBuilder.textInterface)
		availLoader.prepareForLoadingModuleBody()
		val fail = { e: Throwable ->
			module.removeFrom(availLoader) {
				postLoad(moduleName, 0L)
				val problem = object : Problem(
					moduleName,
					1,
					1,
					EXECUTION,
					"Problem loading module: {0}",
					e.localizedMessage)
				{
					override fun abortCompilation()
					{
						availBuilder.stopBuildReason = "Problem loading module"
						completionAction()
					}
				}
				problemHandler.handle(problem)
			}
		}
		// Read the module header from the repository.
		try
		{
			val bytes = stripNull(version.moduleHeader)
			val inputStream = AvailBuilder.validatedBytesFrom(bytes)
			val deserializer = Deserializer(inputStream, availBuilder.runtime)
			val header = ModuleHeader(moduleName)
			header.deserializeHeaderFrom(deserializer)
			val errorString = header.applyToModule(module, availBuilder.runtime)
			if (errorString != null)
			{
				throw RuntimeException(errorString)
			}
		}
		catch (e: MalformedSerialStreamException)
		{
			fail(e)
			return
		}
		catch (e: RuntimeException)
		{
			fail(e)
			return
		}

		val deserializer: Deserializer
		try
		{
			// Read the module data from the repository.
			val bytes = stripNull(compilation.bytes)
			val inputStream = AvailBuilder.validatedBytesFrom(bytes)
			deserializer = Deserializer(inputStream, availBuilder.runtime)
			deserializer.currentModule = module
		}
		catch (e: MalformedSerialStreamException)
		{
			fail(e)
			return
		}
		catch (e: RuntimeException)
		{
			fail(e)
			return
		}

		// Run each zero-argument block, one after another.
		recurse { runNext ->
			availLoader.setPhase(Phase.LOADING)
			val function: A_Function?
			try
			{
				function =
					if (availBuilder.shouldStopBuild) null
					else deserializer.deserialize()
			}
			catch (e: MalformedSerialStreamException)
			{
				fail(e)
				return@recurse
			}
			catch (e: RuntimeException)
			{
				fail(e)
				return@recurse
			}

			when
			{
				function != null ->
				{
					val fiber = newLoaderFiber(
						function.kind().returnType(),
						availLoader
					) {
						val code = function.code()
						formatString(
							"Load repo module %s, in %s:%d",
							code.methodName(),
							code.module().moduleName(),
							code.startingLineNumber())
					}
					fiber.textInterface(availBuilder.textInterface)
					val before = captureNanos()
					fiber.setSuccessAndFailureContinuations(
						{
							val after = captureNanos()
							Interpreter.current().recordTopStatementEvaluation(
								(after - before).toDouble(),
								module,
								function.code().startingLineNumber())
							runNext()
						},
						fail)
					availLoader.setPhase(Phase.EXECUTING_FOR_LOAD)
					if (AvailLoader.debugLoadedStatements)
					{
						println(
							module.toString()
								+ ":" + function.code().startingLineNumber()
								+ " Running precompiled -- " + function)
					}
					runOutermostFunction(
						availBuilder.runtime, fiber, function, emptyList())
				}
				availBuilder.shouldStopBuild ->
					module.removeFrom(availLoader) {
						postLoad(moduleName, 0L)
						completionAction()
					}
				else ->
				{
					availBuilder.runtime.addModule(module)
					val loadedModule = LoadedModule(
						moduleName,
						sourceDigest,
						module,
						version,
						compilation)
					availBuilder.putLoadedModule(moduleName, loadedModule)
					postLoad(moduleName, 0L)
					completionAction()
				}
			}
		}
	}

	/**
	 * Compile the specified [module][ModuleDescriptor], store it into the
	 * [repository][IndexedRepositoryManager], and then load it into the [Avail
	 * runtime][AvailRuntime].
	 *
	 * Note that the predecessors of this module must have already been loaded.
	 *
	 * @param moduleName
	 *   The [resolved name][ResolvedModuleName] of the module that should be
	 *   loaded.
	 * @param compilationKey
	 *   The circumstances of compilation of this module.  Currently this is
	 *   just the compilation times (`long`s) of the module's currently loaded
	 *   predecessors, listed in the same order as the module's
	 *   [imports][ModuleHeader.importedModules].
	 * @param completionAction
	 *   What to do after loading the module successfully or unsuccessfully.
	 */
	private fun compileModule(
		moduleName: ResolvedModuleName,
		compilationKey: ModuleCompilationKey,
		completionAction: ()->Unit)
	{
		val repository = moduleName.repository
		val archive = repository.getArchive(moduleName.rootRelativeName)
		val digest = archive.digestForFile(moduleName)
		val versionKey = ModuleVersionKey(moduleName, digest)
		val lastPosition = MutableLong(0L)
		val ranOnce = AtomicBoolean(false)
		AvailCompiler.create(
			moduleName,
			availBuilder.textInterface,
			availBuilder.pollForAbort,
			{ moduleName2, moduleSize, position ->
				assert(moduleName == moduleName2)
				// Don't reach the full module size yet.  A separate update at
				// 100% will be sent after post-loading actions are complete.
				localTracker(
					moduleName, moduleSize, min(position, moduleSize - 1))
				globalTracker(
					bytesCompiled.addAndGet(position - lastPosition.value),
					globalCodeSize)
				lastPosition.value = position
			},
			{
				postLoad(moduleName, lastPosition.value)
				completionAction()
			},
			problemHandler
		) {
			compiler: AvailCompiler ->
			compiler.parseModule(
				{
					module ->
					val old = ranOnce.getAndSet(true)
					assert(!old) { "Completed module compilation twice!" }
					val stream =
						compiler.compilationContext.serializerOutputStream
					// This is the moment of compilation.
					val compilationTime = System.currentTimeMillis()
					val compilation = repository.ModuleCompilation(
						compilationTime, appendCRC(stream.toByteArray()))
					archive.putCompilation(
						versionKey, compilationKey, compilation)

					// Serialize the Stacks comments.
					val out = ByteArrayOutputStream(5000)
					val serializer = Serializer(out, module)
					// TODO MvG - Capture "/**" comments for Stacks.
					//		final A_Tuple comments = fromList(
					//         module.commentTokens());
					val comments = emptyTuple()
					serializer.serialize(comments)
					val version = stripNull(archive.getVersion(versionKey))
					version.putComments(appendCRC(out.toByteArray()))

					repository.commitIfStaleChanges(
						AvailBuilder.maximumStaleRepositoryMs)
					postLoad(moduleName, lastPosition.value)
					availBuilder.putLoadedModule(
						moduleName,
						LoadedModule(
							moduleName,
							versionKey.sourceDigest,
							module,
							version,
							compilation))
					completionAction()
				},
				{
					postLoad(moduleName, lastPosition.value)
					completionAction()
				})
		}
	}

	/**
	 * Report progress related to this module.  In particular, note that the
	 * current module has advanced from its provided lastPosition to the end of
	 * the module.
	 *
	 * @param moduleName
	 *   The [resolved name][ResolvedModuleName] of the module that just
	 *   finished loading.
	 * @param lastPosition
	 *   The last local file position previously reported.
	 */
	private fun postLoad(moduleName: ResolvedModuleName, lastPosition: Long)
	{
		val moduleSize = moduleName.moduleSize
		globalTracker(
			bytesCompiled.addAndGet(moduleSize - lastPosition),
			globalCodeSize)
		localTracker(moduleName, moduleSize, moduleSize)
	}

	/**
	 * Load the modules in the [AvailBuilder.moduleGraph].
	 *
	 * @param afterAll
	 *   What to do after all module loading completes, whether successful or
	 *   not.
	 */
	fun loadThen(afterAll: ()->Unit)
	{
		bytesCompiled.set(0L)
		val vertexCountBefore = availBuilder.moduleGraph.vertexCount()
		availBuilder.moduleGraph.parallelVisitThen(
			{ vertex, done -> scheduleLoadModule(vertex, done) },
			{
				try
				{
					assert(
						availBuilder.moduleGraph.vertexCount()
							== vertexCountBefore)
					availBuilder.runtime
						.moduleNameResolver()
						.commitRepositories()
					// Parallel load has now completed or failed. Clean up any
					// modules that didn't load.  There can be no loaded
					// successors of unloaded modules, so they can all be
					// excised safely.
					availBuilder.trimGraphToLoadedModules()
				}
				finally
				{
					afterAll()
				}
			})
	}

	/**
	 * Load the modules in the [AvailBuilder.moduleGraph], blocking until all
	 * loading completes, whether successful or not.
	 */
	fun load()
	{
		val semaphore = Semaphore(0)
		loadThen { semaphore.release() }
		semaphore.acquireUninterruptibly()
	}
}
