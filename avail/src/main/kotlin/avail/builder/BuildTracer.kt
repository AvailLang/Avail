/*
 * BuildTracer.kt
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

import avail.compiler.AvailCompiler
import avail.compiler.problems.Problem
import avail.compiler.problems.ProblemHandler
import avail.compiler.problems.ProblemType.TRACE
import avail.descriptor.fiber.FiberDescriptor.Companion.compilerPriority
import avail.descriptor.fiber.FiberDescriptor.Companion.tracerPriority
import avail.descriptor.module.ModuleDescriptor
import avail.io.SimpleCompletionHandler
import avail.persistence.cache.Repository.ModuleVersionKey
import java.util.logging.Level

/**
 * Used for constructing the module dependency graph.
 *
 * @property availBuilder
 *   The [AvailBuilder] for which to trace modules.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a `BuildTracer`.
 *
 * @param availBuilder
 *   The [AvailBuilder] for which to trace modules.
 */
internal class BuildTracer constructor(val availBuilder: AvailBuilder)
{
	/** The number of trace requests that have been scheduled. */
	private var traceRequests: Int = 0

	/** The number of trace requests that have been completed. */
	private var traceCompletions: Int = 0

	/**
	 * Schedule tracing of the imports of the [module][ModuleDescriptor]
	 * specified by the given [module&#32;name][ModuleName].  The
	 * [traceRequests] counter has been incremented already for this tracing,
	 * and the [traceCompletions] will eventually be incremented by this method,
	 * but only *after* increasing the [traceRequests] for each recursive trace
	 * that is scheduled here.  That ensures the two counters won't accidentally
	 * be equal at any time except after the last trace has completed.
	 *
	 * When traceCompletions finally does reach traceRequests, a notifyAll] will
	 * be sent to the `BuildTracer`.
	 *
	 * @param qualifiedName
	 *   A fully-qualified [module&#32;name][ModuleName].
	 * @param resolvedSuccessor
	 *   The resolved name of the module using or extending this module, or
	 *   `null` if this module is the start of the recursive resolution (i.e.,
	 *   it will be the last one compiled).
	 * @param recursionSet
	 *   An insertion-ordered [set][Set] that remembers all modules visited
	 *   along this branch of the trace.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	private fun scheduleTraceModuleImports(
		qualifiedName: ModuleName,
		resolvedSuccessor: ResolvedModuleName?,
		recursionSet: MutableSet<ResolvedModuleName>,
		problemHandler: ProblemHandler)
	{
		availBuilder.runtime.execute(tracerPriority) {
			if (availBuilder.shouldStopBuild)
			{
				// Even though we're shutting down, we still have to account
				// for the previous increment of traceRequests.
				indicateTraceCompleted()
				return@execute
			}
			val resolvedName: ResolvedModuleName
			try
			{
				AvailBuilder.log(
					Level.FINEST, "Resolve: %s", qualifiedName)
				resolvedName = availBuilder.runtime.moduleNameResolver.resolve(
					qualifiedName, resolvedSuccessor)
			}
			catch (e: Throwable)
			{
				AvailBuilder.log(
					Level.WARNING,
					e,
					"Fail resolution: %s",
					qualifiedName)
				availBuilder.stopBuildReason = "Module graph tracing failed"
				val problem = object : Problem(
					resolvedSuccessor ?: qualifiedName,
					1,
					0,
					TRACE,
					"Module resolution problem:\n{0}",
					e)
				{
					override fun abortCompilation()
					{
						indicateTraceCompleted()
					}
				}
				problemHandler.handle(problem)
				return@execute
			}

			AvailBuilder.log(Level.FINEST, "Trace: %s", resolvedName)
			traceModuleImports(
				resolvedName,
				resolvedSuccessor,
				recursionSet,
				problemHandler)
		}
	}

	/**
	 * Trace the imports of the [module][ModuleDescriptor] specified by the
	 * given [module&#32;name][ModuleName].  If a [Problem] occurs, log it and
	 * set [AvailBuilder.stopBuildReason]. Whether a success or failure happens,
	 * end by invoking [indicateTraceCompleted].
	 *
	 * @param resolvedName
	 *   A resolved [module&#32;name][ModuleName] to trace.
	 * @param resolvedSuccessor
	 *   The resolved name of the module using or extending this module, or
	 *   `null` if this module is the start of the recursive resolution (i.e.,
	 *   it will be the last one compiled).
	 * @param recursionSet
	 *   A [MutableSet] that remembers all modules visited along this branch
	 *   of the trace, and the order they were encountered.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	private fun traceModuleImports(
		resolvedName: ResolvedModuleName,
		resolvedSuccessor: ResolvedModuleName?,
		recursionSet: MutableSet<ResolvedModuleName>,
		problemHandler: ProblemHandler)
	{
		// Detect recursion into this module.
		if (recursionSet.contains(resolvedName))
		{
			val problem = object : Problem(
				resolvedName,
				1,
				0,
				TRACE,
				"Recursive module dependency:\n\t{0}",
				recursionSet.joinToString("\n\t"))
			{
				override fun abortCompilation()
				{
					availBuilder.stopBuildReason =
						"Module graph tracing failed due to recursion"
					indicateTraceCompleted()
				}
			}
			problemHandler.handle(problem)
			return
		}
		val alreadyTraced: Boolean
		synchronized(availBuilder) {
			alreadyTraced =
				availBuilder.moduleGraph.includesVertex(resolvedName)
			if (!alreadyTraced)
			{
				availBuilder.moduleGraph.addVertex(resolvedName)
			}
			if (resolvedSuccessor !== null)
			{
				// Note that a module can be both Extended and Used from the
				// same module.  That's to support selective import and renames.
				availBuilder.moduleGraph.includeEdge(
					resolvedName, resolvedSuccessor)
			}
		}
		if (alreadyTraced)
		{
			indicateTraceCompleted()
			return
		}
		val repository = resolvedName.repository
		repository.commitIfStaleChanges(AvailBuilder.maximumStaleRepositoryMs)
		val sourceFile = resolvedName.resolverReference
		val archive = repository.getArchive(resolvedName.rootRelativeName)
		archive.digestForFile(
			resolvedName,
			false,
			{ digest ->
				val versionKey = ModuleVersionKey(resolvedName, digest)
				val version = archive.getVersion(versionKey)
				if (version !== null)
				{
					// This version was already traced and recorded for a
					// subsequent replay… like right now.  Reuse it.
					val importNames = version.imports
					traceModuleNames(
						resolvedName, importNames, recursionSet, problemHandler)
					indicateTraceCompleted()
					return@digestForFile
				}
				// Trace the source and write it back to the repository.
				availBuilder.runtime.execute(compilerPriority)
				{
					AvailCompiler.create(
						resolvedName,
						availBuilder.runtime,
						availBuilder.textInterface,
						availBuilder.pollForAbort,
						{ _, _, _, _, _ -> },
						this::indicateTraceCompleted,
						problemHandler
					) { compiler ->
						compiler.compilationContext.diagnostics
							.setSuccessAndFailureReporters(
								{
									assert(false) {
										"Should not succeed from header parsing"
									}
								},
								this::indicateTraceCompleted)
						compiler.parseModuleHeader { _, _ ->
							val header = compiler.compilationContext.moduleHeader!!
							val importNames = header.importedModuleNames
							val entryPoints = header.entryPointNames
							val newVersion = repository.ModuleVersion(
								sourceFile.size, importNames, entryPoints)
							availBuilder.serialize(header, newVersion)
							archive.putVersion(versionKey, newVersion)
							traceModuleNames(
								resolvedName,
								importNames,
								recursionSet,
								problemHandler)
							indicateTraceCompleted()
						}
					}
				}
			}
		) { code, ex ->
			// TODO figure out what to do with these!!! Probably report them?
			System.err.println(
				"Received ErrorCode: $code with exception:\n")
			ex?.printStackTrace()
		}
	}

	/**
	 * Trace the imports of the [specified][ResolvedModuleName]
	 * [module][ModuleDescriptor].  Return only when these new *requests* have
	 * been accounted for, so that the current request can be considered
	 * completed in the caller.
	 *
	 * @param moduleName
	 *   The name of the module being traced.
	 * @param importNames
	 *   The local names of the modules referenced by the current one.
	 * @param recursionSet
	 *   An insertion-ordered [set][Set] that remembers all modules visited
	 *   along this branch of the trace.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 */
	private fun traceModuleNames(
		moduleName: ResolvedModuleName,
		importNames: List<String>,
		recursionSet: MutableSet<ResolvedModuleName>,
		problemHandler: ProblemHandler)
	{
		// Copy the recursion set to ensure the independence of each path of the
		// tracing algorithm.
		val newSet = (recursionSet + moduleName).toMutableSet()

		synchronized(this) {
			traceRequests += importNames.size
		}

		// Recurse in parallel into each import.
		importNames.forEach { localImport ->
			val importName = moduleName.asSibling(localImport)
			scheduleTraceModuleImports(
				importName, moduleName, newSet, problemHandler)
		}
	}

	/**
	 * A module was just traced, so record that fact.  Note that the trace was
	 * either successful or unsuccessful.
	 */
	@Synchronized
	fun indicateTraceCompleted()
	{
		traceCompletions++
		AvailBuilder.log(
			Level.FINEST,
			"Traced one (%d/%d)",
			traceCompletions,
			traceRequests)
		// Avoid spurious wake-ups.
		if (traceRequests == traceCompletions)
		{
			@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
			(this as Object).notifyAll()
		}
	}

	/**
	 * Determine the ancestry graph of the indicated module, recording it in the
	 * [AvailBuilder.moduleGraph].
	 *
	 * @param target
	 *   The ultimate module to load.
	 * @param problemHandler
	 *   How to handle or report [Problem]s that arise during the build.
	 * @param afterAll
	 *   What to do after the entire trace completes.
	 */
	@Suppress("RedundantLambdaArrow")
	fun traceThen(
		target: ModuleName,
		problemHandler: ProblemHandler,
		afterAll: ()->Unit)
	{
		synchronized(this) {
			traceRequests = 1
			traceCompletions = 0
		}
		scheduleTraceModuleImports(
			target, null, mutableSetOf(), problemHandler)
		// Wait until the parallel recursive trace completes.
		synchronized(this) {
			while (traceRequests != traceCompletions)
			{
				try
				{
					@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
					(this as Object).wait()
				}
				catch (e: InterruptedException)
				{
					availBuilder.stopBuildReason = "Trace was interrupted"
				}

			}
			availBuilder.runtime.moduleNameResolver.commitRepositories()
		}
		if (availBuilder.shouldStopBuild)
		{
			SimpleCompletionHandler<Int>(
				{ },
				{ }
			).guardedDo {
				availBuilder.textInterface.errorChannel.write(
					"Load failed.\n", Unit, handler)
			}
		}
		else
		{
			val graphSize: Int
			val completions: Int
			synchronized(this) {
				graphSize = availBuilder.moduleGraph.size
				completions = traceCompletions
			}
			AvailBuilder.log(
				Level.FINER,
				"Traced or kept %d modules (%d edges)",
				graphSize,
				completions)
		}
		afterAll()
	}
}
