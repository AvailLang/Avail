/*
 * BuildLoader.kt
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
import avail.builder.AvailBuilder.LoadedModule
import avail.compiler.AvailCompiler
import avail.compiler.CompilationContext
import avail.compiler.CompilerProgressReporter
import avail.compiler.GlobalProgressReporter
import avail.compiler.ModuleHeader
import avail.compiler.problems.Problem
import avail.compiler.problems.ProblemHandler
import avail.compiler.problems.ProblemType.EXECUTION
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.FiberDescriptor.Companion.loaderPriority
import avail.descriptor.fiber.FiberDescriptor.Companion.newLoaderFiber
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.module.A_Module.Companion.getAndSetTupleOfBlockPhrases
import avail.descriptor.module.A_Module.Companion.removeFrom
import avail.descriptor.module.A_Module.Companion.serializedObjects
import avail.descriptor.module.A_Module.Companion.setManifestEntriesIndex
import avail.descriptor.module.A_Module.Companion.setStylingRecordIndex
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.module.ModuleDescriptor.Companion.newModule
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type.Companion.returnType
import avail.interpreter.execution.AvailLoader
import avail.interpreter.execution.AvailLoader.Phase
import avail.interpreter.execution.Interpreter
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.ModuleCompilation
import avail.persistence.cache.Repository.ModuleCompilationKey
import avail.persistence.cache.Repository.ModuleVersion
import avail.persistence.cache.Repository.ModuleVersionKey
import avail.persistence.cache.Repository.StylingRecord
import avail.serialization.Deserializer
import avail.serialization.Serializer
import avail.utility.evaluation.Combinator.recurse
import org.availlang.persistence.IndexedFile
import org.availlang.persistence.IndexedFile.Companion.appendCRC
import org.availlang.persistence.IndexedFile.Companion.validatedBytesFrom
import org.availlang.persistence.MalformedSerialStreamException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import kotlin.math.min

/**
 * Used for parallel-loading modules in the
 * [module&#32;graph][AvailBuilder.moduleGraph].
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
	/** The size, in bytes, of all source files that will be built. */
	private val globalCodeSize: Long

	/** The number of bytes compiled so far. */
	private val bytesCompiled = AtomicLong(0L)

	init
	{
		var size = 0L
		for (mod in availBuilder.moduleGraph.vertices)
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
	 *   The [resolved&#32;name][ResolvedModuleName] of the module that should
	 *   be loaded.
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
			availBuilder.runtime.execute(loaderPriority, completionAction)
			return
		}
		availBuilder.runtime.execute(loaderPriority) {
			if (availBuilder.shouldStopBuild)
			{
				// An exception has been encountered since the earlier check.
				// Exit quickly.
				availBuilder.runtime.execute(loaderPriority, completionAction)
			}
			else
			{
				loadModule(target, completionAction)
			}
		}
	}

	/**
	 * Load the specified [module][ModuleDescriptor] into the
	 * [Avail&#32;runtime][AvailRuntime]. If a current compiled module is
	 * available from the [repository][Repository], then simply load it.
	 * Otherwise, [compile][AvailCompiler] the module, store it into the
	 * repository, and then load it.
	 *
	 * Note that the predecessors of this module must have already been loaded.
	 *
	 * @param moduleName
	 *   The [resolved&#32;name][ResolvedModuleName] of the module that should
	 *   be loaded.
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
		val isLoaded = availBuilder.getLoadedModule(moduleName) !== null

		assert(
			isLoaded == availBuilder.runtime.includesModuleNamed(
				stringFrom(moduleName.qualifiedName)))
		if (isLoaded)
		{
			// The module is already loaded.
			AvailBuilder.log(
				Level.FINEST,
				"Already loaded: %s",
				moduleName.qualifiedName)
			postLoad(moduleName, 0L)
			// Since no fiber was created for running the module loading, we
			// still have the responsibility to run the completionAction.  If
			// we run it right now in the current thread, it will recurse,
			// potentially deeply, as the already-loaded part of the module
			// graph is skipped in this way.  To avoid this deep stack, queue a
			// task to run the completionAction.
			availBuilder.runtime.execute(loaderPriority, completionAction)
		}
		else
		{
			val repository = moduleName.repository
			val archive = repository.getArchive(moduleName.rootRelativeName)
			archive.digestForFile(
				moduleName,
				false,
				{ digest ->
					val versionKey = ModuleVersionKey(moduleName, digest)
					val version = archive.getVersion(versionKey) ?: error(
						"Version should have been populated during tracing")
					val imports = version.imports
					val resolver = availBuilder.runtime.moduleNameResolver
					val loadedModulesByName = mutableMapOf<String, LoadedModule>()
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
								"A module predecessor was malformed or " +
									"absent: ${moduleName.qualifiedName} " +
									"-> $localName\n"
							completionAction()
							return@digestForFile
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
					if (compilation !== null)
					{
						// The current version of the module is already
						// compiled, so load the repository's version.
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
						compileModule(
							moduleName, compilationKey, completionAction)
					}
				}
			) { code, ex ->
				// TODO figure out what to do with these!!! Probably report them?
				System.err.println(
					"Received ErrorCode: $code with exception:\n")
				ex?.printStackTrace()
			}
		}
	}

	/**
	 * Load the specified [module][ModuleDescriptor] from the
	 * [repository][Repository] and into the
	 * [runtime][AvailRuntime].
	 *
	 * Note that the predecessors of this module must have already been loaded.
	 *
	 * @param moduleName
	 *   The [resolved&#32;name][ResolvedModuleName] of the module that should
	 *   be loaded.
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
		localTracker(moduleName, moduleName.moduleSize, 0L, 0) { null }
		val module = newModule(
			availBuilder.runtime, stringFrom(moduleName.qualifiedName))
		// Set up the block phrases field with an A_Number, so that requests for
		// block phrases will retrieve them from the repository.
		module.getAndSetTupleOfBlockPhrases(
			fromLong(compilation.recordNumberOfBlockPhrases))
		module.setStylingRecordIndex(compilation.recordNumberOfStyling)
		module.setManifestEntriesIndex(
			compilation.recordNumberOfManifestEntries)
		val availLoader = AvailLoader(
			availBuilder.runtime, module, availBuilder.textInterface)
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
					e.localizedMessage ?: e.toString())
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
			val bytes = version.moduleHeader
			val inputStream = validatedBytesFrom(bytes)
			val deserializer = Deserializer(inputStream, availBuilder.runtime)
			val header = ModuleHeader(moduleName)
			header.deserializeHeaderFrom(deserializer)
			val errorString = header.applyToModule(availLoader)
			if (errorString !== null)
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
			val bytes = compilation.bytes
			val inputStream = validatedBytesFrom(bytes)
			deserializer = Deserializer(inputStream, availBuilder.runtime) {
				throw Exception("Not yet implemented") // TODO MvG
			}
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
			availLoader.phase = Phase.LOADING
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
				function !== null ->
				{
					val fiber = newLoaderFiber(
						function.kind().returnType,
						availLoader)
					{
						val code = function.code()
						formatString(
							"Load repo module %s, in %s:%d",
							code.methodName,
							code.module.shortModuleNameNative,
							code.codeStartingLineNumber)
					}
					val before = fiber.fiberHelper.fiberTime()
					fiber.setSuccessAndFailure(
						{
							val after = fiber.fiberHelper.fiberTime()
							Interpreter.current().recordTopStatementEvaluation(
								(after - before).toDouble(), module)
							runNext()
						},
						fail)
					availLoader.phase = Phase.EXECUTING_FOR_LOAD
					if (AvailLoader.debugLoadedStatements)
					{
						println(
							module.toString()
								+ ":" + function.code()
								.codeStartingLineNumber
								+ " Running precompiled -- " + function)
					}
					availBuilder.runtime.runOutermostFunction(
						fiber, function, emptyList())
				}
				availBuilder.shouldStopBuild ->
					module.removeFrom(availLoader) {
						postLoad(moduleName, 0L)
						completionAction()
					}
				else ->
				{
					module.serializedObjects(deserializer.serializedObjects())
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
	 * [repository][Repository], and then load it into the
	 * [Avail&#32;runtime][AvailRuntime].
	 *
	 * Note that the predecessors of this module must have already been loaded.
	 *
	 * @param moduleName
	 *   The [resolved&#32;name][ResolvedModuleName] of the module that should
	 *   be loaded.
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
		archive.digestForFile(
			moduleName,
			false,
			{ digest ->
				val versionKey = ModuleVersionKey(moduleName, digest)
				var lastPosition = 0L
				val ranOnce = AtomicBoolean(false)
				AvailCompiler.create(
					moduleName,
					availBuilder.runtime,
					availBuilder.textInterface,
					availBuilder.pollForAbort,
					reporter = {
							moduleName2, moduleSize, position, line, phrase ->
						assert(moduleName == moduleName2)
						// Don't reach the full module size yet.  A separate
						// update at 100% will be sent after post-loading
						// actions are complete.
						localTracker(
							moduleName,
							moduleSize,
							min(position, moduleSize - 1),
							line,
							phrase)
						globalTracker(
							bytesCompiled.addAndGet(position - lastPosition),
							globalCodeSize)
						lastPosition = position
					},
					afterFail = {
						postLoad(moduleName, lastPosition)
						completionAction()
					},
					problemHandler,
					succeed = { compiler: AvailCompiler ->
						compiler.parseModule(
							onSuccess = { module ->
								val old = ranOnce.getAndSet(true)
								assert(!old) {
									"Completed module compilation twice!"
								}
								val context = compiler.compilationContext
								val stream = context.serializerOutputStream
								appendCRC(stream)

								// Also produce the serialization of the
								// module's tuple of block phrases.
								val blockPhrasesOutputStream =
									IndexedFile.ByteArrayOutputStream(5000)
								val bodyObjectsTuple =
									context.serializer.serializedObjectsTuple()
								val bodyObjectsMap =
									mutableMapOf<AvailObject, Int>()
								bodyObjectsTuple.forEachIndexed {
										zeroIndex, element ->
									bodyObjectsMap[element] = zeroIndex + 1
								}
								// Ensure the primed objects are always at
								// strictly negative indices.
								val delta = bodyObjectsMap.size + 1
								val blockPhraseSerializer = Serializer(
									blockPhrasesOutputStream,
									module
								) { obj ->
									when (val i = bodyObjectsMap[obj])
									{
										null -> 0
										else -> i - delta
									}
								}
								blockPhraseSerializer.serialize(
									module.getAndSetTupleOfBlockPhrases(nil))
								appendCRC(blockPhrasesOutputStream)
								val loader = context.loader
								val manifestEntries = loader.manifestEntries!!

								// This is the moment of compilation.
								val compilationTime = System.currentTimeMillis()
								val compilation = repository.ModuleCompilation(
									compilationTime,
									stream.toByteArray(),
									blockPhrasesOutputStream.toByteArray(),
									manifestEntries,
									assembleStylingRecord(context))
								archive.putCompilation(
									versionKey, compilationKey, compilation)

								// Serialize the Stacks comments.
								val out = IndexedFile.ByteArrayOutputStream(100)
								// TODO MvG - Capture "/**" comments for Stacks.
								//		final A_Tuple comments = fromList(
								//         module.commentTokens());
								//val comments = emptyTuple
								//val stacksSerializer = Serializer(out, module)
								//stacksSerializer.serialize(comments)
								appendCRC(out)
								val version = archive.getVersion(versionKey)!!
								version.putComments(out.toByteArray())

								module.getAndSetTupleOfBlockPhrases(
									fromLong(
										compilation.recordNumberOfBlockPhrases))
								module.setManifestEntriesIndex(
									compilation.recordNumberOfManifestEntries)
								module.setStylingRecordIndex(
									compilation.recordNumberOfStyling)
								repository.commitIfStaleChanges(
									AvailBuilder.maximumStaleRepositoryMs)
								postLoad(moduleName, lastPosition)
								module.serializedObjects(bodyObjectsTuple)
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
							afterFail = {
								postLoad(moduleName, lastPosition)
								completionAction()
							})
					})
			}
		) { code, ex ->
			// TODO figure out what to do with these!!! Probably report them?
			System.err.println(
				"Received ErrorCode: $code with exception:\n")
			ex?.printStackTrace()
		}
	}

	private fun assembleStylingRecord(
		context: CompilationContext
	): StylingRecord
	{
		val loader = context.loader
		val converter = context.surrogateIndexConverter
		val styleRanges = loader.lockStyles {
			map { (start, pastEnd, style) ->
				val utf16Start =
					converter.availIndexToJavaIndex(start.toInt())
				val utf16PastEnd =
					converter.availIndexToJavaIndex(pastEnd.toInt())
				(utf16Start until utf16PastEnd) to style
			}
		}
		val uses = loader.lockUsesToDefinitions {
			map { (useStart, usePastEnd, defRange) ->
				val utf16UseStart =
					converter.availIndexToJavaIndex(useStart.toInt())
				val utf16UsePastEnd =
					converter.availIndexToJavaIndex(usePastEnd.toInt())
				val utf16DefStart =
					converter.availIndexToJavaIndex(defRange.first.toInt())
				val utf16DefEnd =
					converter.availIndexToJavaIndex(defRange.last.toInt())
				Pair(
					(utf16UseStart until utf16UsePastEnd),
					(utf16DefStart .. utf16DefEnd))
			}
		}
		return StylingRecord(styleRanges, uses)
	}

	/**
	 * Report progress related to this module.  In particular, note that the
	 * current module has advanced from its provided lastPosition to the end of
	 * the module.
	 *
	 * @param moduleName
	 *   The [resolved&#32;name][ResolvedModuleName] of the module that just
	 *   finished loading.
	 * @param lastPosition
	 *   The last local file position previously reported.
	 */
	private fun postLoad(moduleName: ResolvedModuleName, lastPosition: Long)
	{
		val moduleSize = moduleName.moduleSize
		val newPosition = bytesCompiled.addAndGet(moduleSize - lastPosition)
		// Don't report progress if the build is canceled.
		if (!availBuilder.shouldStopBuild)
		{
			globalTracker(newPosition, globalCodeSize)
		}
		localTracker(moduleName, moduleSize, moduleSize, Int.MAX_VALUE) {
			null
		}
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
		val vertexCountBefore = availBuilder.moduleGraph.vertexCount
		availBuilder.moduleGraph.parallelVisitThen(
			{ vertex, done -> scheduleLoadModule(vertex, done) },
			{
				try
				{
					assert(
						availBuilder.moduleGraph.vertexCount
							== vertexCountBefore)
					availBuilder.runtime
						.moduleNameResolver
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
